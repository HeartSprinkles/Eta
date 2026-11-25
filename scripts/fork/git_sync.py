#!/usr/bin/env python3
"""Fork maintenance 1.0.0: fast-forward tracking, rebase candidate, exact-lease promotion.

Never execute this file from a rebased candidate with a write token. The workflow
copies its controller version to RUNNER_TEMP before touching candidate files.
"""
from __future__ import annotations

import argparse
import json
import os
from pathlib import Path
import re
import subprocess
import sys


class SyncError(RuntimeError):
    pass


def git(*args: str, check: bool = True) -> str:
    result = subprocess.run(["git", *args], text=True, stdout=subprocess.PIPE,
                            stderr=subprocess.PIPE)
    if check and result.returncode:
        raise SyncError(f"git {' '.join(args)} failed:\n{result.stdout}{result.stderr}")
    return result.stdout.strip()


def ancestor(base: str, head: str) -> bool:
    return subprocess.run(["git", "merge-base", "--is-ancestor", base, head],
                          stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL).returncode == 0


def require_clean() -> None:
    if git("status", "--porcelain"):
        raise SyncError("Worktree must be clean; commit or explicitly save changes first.")


def validate_sha(value: str) -> str:
    if not re.fullmatch(r"[0-9a-f]{40}", value):
        raise SyncError("Expected an exact 40-character commit SHA.")
    return value


def remote_sha(branch: str) -> str:
    git("check-ref-format", "--branch", branch)
    lines = git("ls-remote", "--heads", "origin", f"refs/heads/{branch}").splitlines()
    if len(lines) != 1:
        raise SyncError(f"Expected one existing remote branch: {branch}")
    return validate_sha(lines[0].split()[0])


def push(*args: str) -> None:
    # Deliberately no automatic retry: inspect remote state after an uncertain failure.
    git("push", *args)


def write_outputs(values: dict[str, str]) -> None:
    print(json.dumps(values, indent=2))
    output = os.environ.get("GITHUB_OUTPUT")
    if output:
        with open(output, "a", encoding="utf-8") as stream:
            for key, value in values.items():
                if "\n" in value or "\r" in value:
                    raise SyncError("Output values must not contain line breaks.")
                stream.write(f"{key}={value}\n")


def check_stack(tracking: str, custom: str) -> None:
    if not ancestor(tracking, custom):
        raise SyncError("custom-main does not contain the recorded tracking baseline; stop and repair branch roles.")
    if git("rev-list", "--merges", f"{tracking}..{custom}"):
        raise SyncError("Custom patch stack contains merge commits; stop for manual cleanup.")


def prepare(upstream_branch: str, candidate_branch: str) -> dict[str, str]:
    require_clean()
    git("check-ref-format", "--branch", upstream_branch)
    git("check-ref-format", "--branch", candidate_branch)
    if not re.fullmatch(r"sync-candidate/upstream-[0-9]+-[0-9]+", candidate_branch):
        raise SyncError("Candidate branch must be unique to a numeric workflow run and attempt.")
    git("fetch", "upstream", "--prune")
    git("fetch", "origin", "--prune", "+refs/heads/upstream-tracking:refs/remotes/origin/upstream-tracking",
        "+refs/heads/custom-main:refs/remotes/origin/custom-main")
    old_tracking = git("rev-parse", "refs/remotes/origin/upstream-tracking")
    old_custom = git("rev-parse", "refs/remotes/origin/custom-main")
    new_tracking = git("rev-parse", f"refs/remotes/upstream/{upstream_branch}")
    check_stack(old_tracking, old_custom)
    if not ancestor(old_tracking, new_tracking):
        raise SyncError("Upstream history was rewritten or tracking was polluted. Never overwrite upstream-tracking.")
    # Confirm the captured remote refs have not moved before advancing tracking.
    if remote_sha("upstream-tracking") != old_tracking or remote_sha("custom-main") != old_custom:
        raise SyncError("A remote baseline moved during preparation; rerun from a fresh snapshot.")
    git("switch", "--detach", old_tracking)
    if git("branch", "--list", "upstream-tracking"):
        if git("rev-parse", "upstream-tracking") != old_tracking:
            raise SyncError("Local upstream-tracking differs from the captured remote; never reset it.")
    else:
        git("branch", "upstream-tracking", old_tracking)
    git("switch", "upstream-tracking")
    git("merge", "--ff-only", new_tracking)
    # A normal push rejects a server-side race and cannot rewrite tracking history.
    push("origin", "upstream-tracking:refs/heads/upstream-tracking")
    values = {"changed": "false", "old_custom": old_custom, "new_tracking": new_tracking}
    if old_tracking == new_tracking:
        return values
    git("switch", "--create", candidate_branch, old_custom)
    rebase = subprocess.run(["git", "-c", "core.hooksPath=/dev/null", "-c", "rerere.enabled=false",
                             "rebase", "upstream-tracking"], text=True,
                            stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    if rebase.returncode:
        conflicts = git("diff", "--name-only", "--diff-filter=U", check=False)
        git("rebase", "--abort")
        raise SyncError("Rebase stopped; custom-main is unchanged. Conflicts:\n" +
                        conflicts + "\n" + rebase.stdout + rebase.stderr)
    new_custom = git("rev-parse", "HEAD")
    check_stack(new_tracking, new_custom)
    # This workflow never writes an existing candidate, even by a fast-forward.
    if git("ls-remote", "--heads", "origin", f"refs/heads/{candidate_branch}"):
        raise SyncError("Candidate branch already exists; use a new workflow attempt.")
    push("origin", f"HEAD:refs/heads/{candidate_branch}")
    values.update(changed="true", candidate_sha=new_custom, candidate_branch=candidate_branch)
    return values


def promote(old_custom: str, new_tracking: str, candidate_sha: str,
            candidate_branch: str, backup_branch: str) -> dict[str, str]:
    require_clean()
    for value in (old_custom, new_tracking, candidate_sha):
        validate_sha(value)
    for branch in (candidate_branch, backup_branch):
        git("check-ref-format", "--branch", branch)
    if not re.fullmatch(r"sync-candidate/upstream-[0-9]+-[0-9]+", candidate_branch):
        raise SyncError("Unexpected candidate ref.")
    if not re.fullmatch(r"backup/custom-main-[0-9]+-[0-9]+", backup_branch):
        raise SyncError("Unexpected backup ref.")
    git("fetch", "origin", "--prune", "+refs/heads/custom-main:refs/remotes/origin/custom-main",
        "+refs/heads/upstream-tracking:refs/remotes/origin/upstream-tracking",
        f"refs/heads/{candidate_branch}:refs/remotes/origin/{candidate_branch}")
    if remote_sha("custom-main") != old_custom:
        raise SyncError("custom-main changed during validation; the tested candidate will not overwrite new work.")
    if remote_sha("upstream-tracking") != new_tracking or remote_sha(candidate_branch) != candidate_sha:
        raise SyncError("Tracking or candidate ref changed during validation; stop.")
    check_stack(new_tracking, candidate_sha)
    if git("ls-remote", "--heads", "origin", f"refs/heads/{backup_branch}"):
        raise SyncError("Backup already exists; do not overwrite it.")
    push("origin", f"{old_custom}:refs/heads/{backup_branch}")
    # The only non-fast-forward update in the entire protocol is this exact leased custom ref.
    push(f"--force-with-lease=refs/heads/custom-main:{old_custom}", "origin",
         f"{candidate_sha}:refs/heads/custom-main")
    return {"custom_main": candidate_sha, "upstream_tracking": new_tracking, "backup": backup_branch}


def configure_remotes(config: dict[str, str]) -> str:
    repository = os.environ.get("GITHUB_REPOSITORY")
    if repository != config["origin"]:
        raise SyncError("Automation is restricted to the configured personal fork.")
    url = git("remote", "get-url", "origin")
    allowed = {f'https://github.com/{config["origin"]}.git', f'https://github.com/{config["origin"]}'}
    if url not in allowed:
        raise SyncError("origin does not match the configured fork.")
    upstream = f'https://github.com/{config["upstream"]}.git'
    if "upstream" in git("remote").splitlines():
        git("remote", "set-url", "upstream", upstream)
    else:
        git("remote", "add", "upstream", upstream)
    git("remote", "set-url", "--push", "upstream", "DISABLED")
    git("config", "user.name", "github-actions[bot]")
    git("config", "user.email", "41898282+github-actions[bot]@users.noreply.github.com")
    git("config", "commit.gpgSign", "false")
    git("config", "rerere.enabled", "true")
    branch = subprocess.check_output(["gh", "api", f'repos/{config["upstream"]}',
                                      "--jq", ".default_branch"], text=True).strip()
    git("check-ref-format", "--branch", branch)
    return branch


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("action", choices=("prepare", "promote"))
    parser.add_argument("--config", required=True)
    args = parser.parse_args()
    try:
        config = json.loads(Path(args.config).read_text(encoding="utf-8"))
        branch = configure_remotes(config)
        run_id, attempt = os.environ["GITHUB_RUN_ID"], os.environ["GITHUB_RUN_ATTEMPT"]
        if not (run_id.isdigit() and attempt.isdigit()):
            raise SyncError("Invalid workflow run identity.")
        if args.action == "prepare":
            values = prepare(branch, f"sync-candidate/upstream-{run_id}-{attempt}")
        else:
            values = promote(os.environ["OLD_CUSTOM"], os.environ["NEW_TRACKING"],
                             os.environ["CANDIDATE_SHA"], os.environ["CANDIDATE_BRANCH"],
                             f"backup/custom-main-{run_id}-{attempt}")
        write_outputs(values)
        summary = os.environ.get("GITHUB_STEP_SUMMARY")
        if summary:
            with open(summary, "a", encoding="utf-8") as stream:
                stream.write("## Fork synchronization\n\n```json\n" + json.dumps(values, indent=2) + "\n```\n")
        return 0
    except (SyncError, KeyError, subprocess.CalledProcessError) as error:
        print(str(error), file=sys.stderr)
        summary = os.environ.get("GITHUB_STEP_SUMMARY")
        if summary:
            with open(summary, "a", encoding="utf-8") as stream:
                stream.write("## Synchronization stopped safely\n\n```text\n" + str(error) + "\n```\n")
        return 1


if __name__ == "__main__":
    sys.exit(main())
