"""Small disposable Git fixtures, executed only on GitHub Actions."""
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from unittest import mock

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import git_sync as sync


def command(cwd, *args):
    result = subprocess.run(["git", *args], cwd=cwd, text=True, capture_output=True)
    if result.returncode:
        raise AssertionError(result.stdout + result.stderr)
    return result.stdout.strip()


class SynchronizationSafetyTest(unittest.TestCase):
    def setUp(self):
        self.previous_cwd = Path.cwd()
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)
        self.origin = self.root / "origin.git"
        self.upstream = self.root / "upstream.git"
        self.source = self.root / "source"
        self.work = self.root / "work"
        command(self.root, "init", "--bare", "--initial-branch=main", str(self.origin))
        command(self.root, "init", "--bare", "--initial-branch=main", str(self.upstream))
        command(self.root, "init", "--initial-branch=main", str(self.source))
        self.identity(self.source)
        self.base = self.commit(self.source, "upstream.txt", "original\n", "base upstream")
        command(self.source, "remote", "add", "upstream", str(self.upstream))
        command(self.source, "remote", "add", "origin", str(self.origin))
        command(self.source, "push", "upstream", "main")
        command(self.source, "push", "origin", "main", "HEAD:refs/heads/upstream-tracking")
        command(self.root, "clone", str(self.origin), str(self.work))
        self.identity(self.work)
        command(self.work, "switch", "--create", "custom-main")
        self.old_custom = self.commit(self.work, "fork.txt", "my customization\n", "one custom feature")
        command(self.work, "push", "origin", "custom-main")
        command(self.work, "remote", "add", "upstream", str(self.upstream))
        os.chdir(self.work)

    def tearDown(self):
        os.chdir(self.previous_cwd)
        self.temp.cleanup()

    def identity(self, repo):
        command(repo, "config", "user.name", "Fork Test")
        command(repo, "config", "user.email", "fork-test@example.invalid")
        command(repo, "config", "commit.gpgSign", "false")

    def commit(self, repo, file, text, message):
        (repo / file).write_text(text)
        command(repo, "add", file)
        command(repo, "commit", "-m", message)
        return command(repo, "rev-parse", "HEAD")

    def upstream_update(self, file="new-upstream.txt", text="next upstream\n"):
        sha = self.commit(self.source, file, text, "update upstream")
        command(self.source, "push", "upstream", "main")
        return sha

    def prepare(self):
        return sync.prepare("main", "sync-candidate/upstream-123-1")

    def test_already_current_is_noop(self):
        values = self.prepare()
        self.assertEqual("false", values["changed"])
        self.assertEqual(self.old_custom, sync.remote_sha("custom-main"))
        self.assertEqual(self.base, sync.remote_sha("upstream-tracking"))

    def test_tracking_fast_forwards_and_customization_is_rebased_linearly(self):
        new_upstream = self.upstream_update()
        values = self.prepare()
        self.assertEqual("true", values["changed"])
        self.assertEqual(new_upstream, sync.remote_sha("upstream-tracking"))
        self.assertEqual(self.old_custom, sync.remote_sha("custom-main"))
        self.assertTrue(sync.ancestor(new_upstream, values["candidate_sha"]))
        self.assertEqual("", sync.git("rev-list", "--merges", f'{new_upstream}..{values["candidate_sha"]}'))
        self.assertEqual("my customization\n", (self.work / "fork.txt").read_text())
        result = sync.promote(self.old_custom, new_upstream, values["candidate_sha"],
                              values["candidate_branch"], "backup/custom-main-123-1")
        self.assertEqual(values["candidate_sha"], sync.remote_sha("custom-main"))
        self.assertEqual(self.old_custom, sync.remote_sha(result["backup"]))

    def test_polluted_tracking_is_not_overwritten(self):
        command(self.origin, "update-ref", "refs/heads/upstream-tracking", self.old_custom)
        self.upstream_update()
        with self.assertRaisesRegex(sync.SyncError, "rewritten or tracking was polluted"):
            self.prepare()
        self.assertEqual(self.old_custom, sync.remote_sha("upstream-tracking"))
        self.assertEqual(self.old_custom, sync.remote_sha("custom-main"))

    def test_rewritten_upstream_is_rejected(self):
        command(self.source, "switch", "--orphan", "rewritten")
        rewritten = self.commit(self.source, "replacement.txt", "unrelated history\n", "new root")
        command(self.upstream, "fetch", str(self.source), "rewritten:refs/heads/new-root-fixture")
        command(self.upstream, "update-ref", "refs/heads/main", rewritten)
        with self.assertRaisesRegex(sync.SyncError, "rewritten"):
            self.prepare()
        self.assertEqual(self.base, sync.remote_sha("upstream-tracking"))

    def test_conflict_does_not_publish_partial_custom_main(self):
        self.old_custom = self.commit(self.work, "upstream.txt", "custom edit\n", "custom hook")
        command(self.work, "push", "origin", "custom-main")
        new_upstream = self.upstream_update("upstream.txt", "upstream edit\n")
        with self.assertRaisesRegex(sync.SyncError, "Rebase stopped"):
            self.prepare()
        self.assertEqual(self.old_custom, sync.remote_sha("custom-main"))
        self.assertEqual(new_upstream, sync.remote_sha("upstream-tracking"))
        self.assertFalse(sync.git("ls-remote", "--heads", "origin", "refs/heads/sync-candidate/upstream-123-1"))

    def test_modified_custom_main_blocks_promotion(self):
        new_upstream = self.upstream_update()
        values = self.prepare()
        concurrent = self.commit(self.work, "concurrent.txt", "another developer\n", "new work")
        command(self.origin, "fetch", str(self.work), "HEAD:refs/heads/concurrent-fixture")
        command(self.origin, "update-ref", "refs/heads/custom-main", concurrent, self.old_custom)
        with self.assertRaisesRegex(sync.SyncError, "changed during validation"):
            sync.promote(self.old_custom, new_upstream, values["candidate_sha"],
                         values["candidate_branch"], "backup/custom-main-123-1")
        self.assertEqual(concurrent, sync.remote_sha("custom-main"))

    def test_exact_lease_rejects_race_after_backup(self):
        new_upstream = self.upstream_update()
        values = self.prepare()
        concurrent = self.commit(self.work, "concurrent.txt", "new work\n", "concurrent commit")
        command(self.origin, "fetch", str(self.work), "HEAD:refs/heads/concurrent-fixture")
        original_push = sync.push

        def push_with_race(*args):
            original_push(*args)
            if any("refs/heads/backup/" in arg for arg in args):
                command(self.origin, "update-ref", "refs/heads/custom-main", concurrent, self.old_custom)

        with mock.patch.object(sync, "push", side_effect=push_with_race):
            with self.assertRaises(sync.SyncError):
                sync.promote(self.old_custom, new_upstream, values["candidate_sha"],
                             values["candidate_branch"], "backup/custom-main-123-1")
        self.assertEqual(concurrent, sync.remote_sha("custom-main"))
        self.assertEqual(self.old_custom, sync.remote_sha("backup/custom-main-123-1"))

    def test_local_tracking_is_not_reset(self):
        command(self.work, "branch", "upstream-tracking", self.old_custom)
        self.upstream_update()
        with self.assertRaisesRegex(sync.SyncError, "never reset"):
            self.prepare()
        self.assertEqual(self.old_custom, command(self.work, "rev-parse", "upstream-tracking"))
        self.assertEqual(self.base, sync.remote_sha("upstream-tracking"))


if __name__ == "__main__":
    unittest.main()
