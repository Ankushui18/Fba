#!/usr/bin/env bash
# Create a reproducible Link-only source copy; never patch the offline checkout.
set -euo pipefail
root=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)
core=${1:?Pass the clean FBNeo checkout}
copy=${2:?Pass the build directory for FBNeo Link}
rev=2da48555c13ce5d011edf2d6ec829b7393c070af
patch="$root/android/patches/fbneo-lockstep.patch"
[[ $(git -C "$core" rev-parse HEAD) == "$rev" && -z $(git -C "$core" status --porcelain) ]] || { echo "Need the clean FBNeo pin $rev" >&2; exit 1; }
if [[ ! -d $copy/.git ]]; then
  mkdir -p "$(dirname -- "$copy")"
  git clone -q --shared --no-checkout -- "$core" "$copy"
  git -C "$copy" checkout -q --detach "$rev"
fi
[[ $(git -C "$copy" rev-parse HEAD) == "$rev" ]]
if [[ -z $(git -C "$copy" status --porcelain --untracked-files=no) ]]; then
  git -C "$copy" apply "$patch"
fi
# Only the versioned lockstep patch may differ. Normalize diff headers for comparison.
if ! git -C "$copy" diff --no-ext-diff --no-color -U1 HEAD | sed -n '/^--- a\//,$ { s/^\(@@ .* @@\).*/\1/; p; }' | cmp -s - "$patch"; then
  echo "Unexpected local changes in Link core: $copy" >&2
  exit 1
fi
