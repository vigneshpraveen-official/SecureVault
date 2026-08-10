#!/usr/bin/env bash
# Submission sync per docs/securevault_master.md §7.3.
#
# NOT run automatically by any AI agent. The developer runs this by hand, only when a
# major mentor task block is complete, and only after the mentor has actually given
# push/branch instructions for the central repo (see docs/decisions.md ADR-006 — as of
# S0.1 no 'central' remote is configured and none should be added without that go-ahead).
#
# Usage: scripts/sync-submission.sh <submission-branch-name>
#   e.g. scripts/sync-submission.sh vigneshpraveen-official
set -euo pipefail

CENTRAL_URL="https://github.com/springboardmentor1295d-arch/SecureVault.git"
BRANCH_NAME="${1:-}"

if [[ -z "${BRANCH_NAME}" ]]; then
  echo "Usage: $0 <submission-branch-name>" >&2
  exit 1
fi

if [[ "${BRANCH_NAME}" == "main" ]]; then
  echo "Refusing: target branch must not be 'main'." >&2
  exit 1
fi

repo_root="$(git rev-parse --show-toplevel)"
cd "${repo_root}"

if [[ -n "$(git status --porcelain)" ]]; then
  echo "Refusing: working tree is dirty. Commit or stash first." >&2
  exit 1
fi

source_branch="$(git rev-parse --abbrev-ref HEAD)"
if [[ "${source_branch}" == "HEAD" ]]; then
  echo "Refusing: currently in detached HEAD. Checkout a real branch first." >&2
  exit 1
fi

if ! git remote get-url central >/dev/null 2>&1; then
  echo "Remote 'central' is not configured."
  echo "This script will not add it automatically — confirm the mentor has given"
  echo "push/branch instructions, then run:"
  echo "  git remote add central ${CENTRAL_URL}"
  exit 1
fi

echo "Fetching central..."
git fetch central

echo "Checking out submission branch '${BRANCH_NAME}' from central/main..."
git checkout -B "${BRANCH_NAME}" central/main

echo "Bringing in project contents from '${source_branch}'..."
git checkout "${source_branch}" -- backend docker-compose.yml
if git cat-file -e "${source_branch}:frontend" 2>/dev/null; then
  git checkout "${source_branch}" -- frontend
fi

echo "Removing README.md and requirements.txt (never present on the submission branch)..."
rm -f README.md requirements.txt

echo "Running mvn clean verify — must pass before pushing..."
mvn -f backend/pom.xml clean verify

git add -A

echo "Scanning staged files for secrets before commit..."
staged_files="$(git diff --cached --name-only)"
forbidden_patterns=('(^|/)\.env$' '(^|/)\.env\.' '\.key$' '\.pem$' '(^|/)application-local\.yml$' 'secret')
for pattern in "${forbidden_patterns[@]}"; do
  if echo "${staged_files}" | grep -Eiq "${pattern}"; then
    echo "REFUSING TO COMMIT: a staged file matches forbidden pattern '${pattern}':" >&2
    echo "${staged_files}" | grep -Ei "${pattern}" >&2
    git reset
    exit 1
  fi
done

commit_message="SecureVault: ${2:-task block} — ${BRANCH_NAME}"
git commit -m "${commit_message}"

echo "Pushing to central/${BRANCH_NAME}..."
git push central "${BRANCH_NAME}"

echo
echo "Pushed. Now message the mentor with the branch name: ${BRANCH_NAME}"
