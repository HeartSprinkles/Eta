#!/usr/bin/env bash
set -euo pipefail
cd "$(git rev-parse --show-toplevel)"
git_dir=$(git rev-parse --absolute-git-dir)
target="$git_dir/fork-hooks"
existing=$(git config --local --get core.hooksPath || true)
if [[ -n "$existing" && "$existing" != "$target" ]]; then
  echo "Existing core.hooksPath=$existing; integrate manually rather than overwrite it." >&2
  exit 1
fi
if [[ -z "$existing" ]] && find "$git_dir/hooks" -maxdepth 1 -type f ! -name '*.sample' | grep -q .; then
  echo 'Existing custom Git hooks found; integrate manually rather than hide them.' >&2
  exit 1
fi
branch=$(git ls-remote --symref upstream HEAD | awk '$1 == "ref:" && $3 == "HEAD" {sub("refs/heads/", "", $2); print $2}')
git check-ref-format --branch "$branch" >/dev/null
mkdir -p "$target"
cp scripts/fork/hooks/pre-commit scripts/fork/hooks/pre-push "$target/"
chmod 755 "$target/pre-commit" "$target/pre-push"
printf '%s\n' "$branch" > "$target/upstream-default"
git config --local core.hooksPath "$target"
git config --global rerere.enabled true
git remote set-url --push upstream DISABLED
printf 'Installed fork hooks; upstream default=%s; upstream pushes disabled; rerere=true.\n' "$branch"
