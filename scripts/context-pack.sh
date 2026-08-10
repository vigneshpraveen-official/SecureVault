#!/usr/bin/env bash
# Bundles the AI consistency layer into a single file for pasting into chat UIs that
# don't have repo access (ChatGPT web, Gemini web). See master §15.
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
out_dir="${repo_root}/build"
out_file="${out_dir}/context-pack.md"

mkdir -p "${out_dir}"

current_state="$(awk '/^## CURRENT STATE/{flag=1} /^## NEXT UP/{flag=0} flag' "${repo_root}/docs/progress.md")"

{
  echo "# SecureVault — Context Pack"
  echo "_Generated $(date -u +"%Y-%m-%dT%H:%M:%SZ") by scripts/context-pack.sh_"
  echo
  echo "## docs/ai/CONTEXT.md"
  cat "${repo_root}/docs/ai/CONTEXT.md"
  echo
  echo "## docs/ai/CONVENTIONS.md"
  cat "${repo_root}/docs/ai/CONVENTIONS.md"
  echo
  echo "## docs/progress.md — CURRENT STATE"
  echo "${current_state}"
  echo
  echo "## docs/api-contract.md"
  cat "${repo_root}/docs/api-contract.md"
} > "${out_file}"

char_count=$(wc -c < "${out_file}")
echo "Wrote ${out_file}"
echo "Total characters: ${char_count}"
