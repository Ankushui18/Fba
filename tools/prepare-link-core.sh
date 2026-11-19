#!/usr/bin/env bash
# Create a reproducible Link-only source copy; never patch the offline checkout.
set -euo pipefail
root=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)
core=${1:?Pass the clean FBNeo checkout}
copy=${2:?Pass the build directory for FBNeo Link}
rev=2da48555c13ce5d011edf2d6ec829b7393c070af
link_patch="$root/android/patches/fbneo-lockstep.patch"
session_patch="$root/android/patches/fbneo-session.patch"
hiscore_patch="$root/android/patches/fbneo-hiscore.patch"
[[ $(git -C "$core" rev-parse HEAD) == "$rev" && -z $(git -C "$core" status --porcelain) ]] || { echo "Need the clean FBNeo pin $rev" >&2; exit 1; }
if [[ ! -d $copy/.git ]]; then
  mkdir -p "$(dirname -- "$copy")"
  git clone -q --shared --no-checkout -- "$core" "$copy"
  git -C "$copy" checkout -q --detach "$rev"
fi
[[ $(git -C "$copy" rev-parse HEAD) == "$rev" ]]
core_diff() {
  git -C "$copy" diff --no-ext-diff --no-color -U1 HEAD | sed '/^diff --git /d; /^index /d; s/^\(@@ .* @@\).*/\1/'
}
if [[ -z $(git -C "$copy" status --porcelain --untracked-files=no) ]]; then
  git -C "$copy" apply "$session_patch"
elif core_diff | cmp -s - "$link_patch"; then
  # Reuse a checkout with exactly the previously published lockstep patch.
  git -C "$copy" apply -R "$link_patch"
  git -C "$copy" apply "$session_patch"
fi
if core_diff | cmp -s - "$session_patch"; then
  git -C "$copy" apply "$hiscore_patch"
fi
# Only these versioned patches may differ from the pinned core.
if ! core_diff | cmp -s - <(cat "$hiscore_patch" "$session_patch"); then
  echo "Unexpected local changes in Link core: $copy" >&2
  exit 1
fi
