# Upstream synchronization

This fork is `HeartSprinkles/Eta`; the source repository is `Mangi-11/Eta`.

## Branches and flow

- `main` is the fork's product and release branch. Feature work should reach it through reviewed PRs.
- `feat/*` branches are short-lived development branches.
- `sync/upstream-main` is maintained by the **Sync Upstream** workflow. It contains merge commits from fork `main` and upstream `main`; do not rebase, squash, or force-push it.
- The workflow runs daily at 07:17 UTC and can also be started from **Actions → Sync Upstream → Run workflow** on `main`.

The workflow fetches only `Mangi-11/Eta:main`, merges it into a candidate based on the current fork `main`, and creates or updates one PR back to the fork's `main`. It does not merge PRs, run candidate code, access signing secrets, or push to upstream. Its job token is limited to writing repository contents and pull requests. The branch is pushed with a normal fast-forward update; a race or conflict fails without force-pushing.

The first sync PR also establishes the retained fork features on `main`. Review the [fork feature register](FORK_FEATURES.md) and the PR's regression checklist; do not assume that a clean textual merge proves the fork-specific behavior still works.

## Review and merge

1. Review the upstream change set, especially `.github/workflows/`, build scripts, signing/version configuration, application identity, and update sources.
2. Require the `Lint / Test / Debug Build` check and review the fork-feature checklist in the PR body. If GitHub holds a workflow run for approval, a maintainer must approve it after reviewing the PR.
3. Merge with **Create a merge commit**. This preserves upstream ancestry and lets future syncs build on the integrated history. Do not squash or rebase the long-lived sync PR.
4. Keep normal branch protection and required checks on `main`; do not enable force pushes for the sync branch.

## Conflicts and recovery

A conflict stops the workflow before the remote branch is updated. The Actions log prints the conflicting paths. Resolve the existing PR branch manually, retain both upstream behavior and the fork invariants in `FORK_FEATURES.md`, run the CI checks, and push an ordinary commit. The next scheduled or manually dispatched run can then advance the same branch.

Never use `git push --force`, `--force-with-lease`, mirror pushes, or an automated reset for synchronization. If the sync branch has been rewritten or the PR was squash/rebase-merged, stop automation and have a maintainer restore a merge-commit-based history before continuing; do not work around it by overwriting remote history.

To pause automation, disable **Sync Upstream** in the repository's Actions settings. Re-enable it after resolving the cause. If a sync PR is intentionally rejected, close it and disable or adjust the workflow before the next run, rather than letting a daily job recreate the same PR.

## Repository setup

GitHub Actions must be enabled, and repository Actions settings must allow the workflow's `GITHUB_TOKEN` to create pull requests. The workflow only runs against this fork's `main`; `workflow_dispatch` from another branch exits without making changes. No personal access token or signing secret is needed.
