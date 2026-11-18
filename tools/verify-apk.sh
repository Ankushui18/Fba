#!/usr/bin/env bash
set -euo pipefail
root=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)
apk=${1:?Usage: tools/verify-apk.sh FILE.apk [--link]}
sdk=${ANDROID_SDK_ROOT:-${ANDROID_HOME:?Set ANDROID_SDK_ROOT to the Android SDK}}
bt="$sdk/build-tools/${ANDROID_BUILD_TOOLS:-36.1.0}"
export PATH="${JAVA_HOME:?Set JAVA_HOME to a JDK}/bin:$PATH"
"$bt/zipalign" -c -P 16 4 "$apk"
signatures=$("$bt/apksigner" verify --verbose --print-certs "$apk")
printf '%s\n' "$signatures"
for scheme in v2 v3; do
  grep -q "^Verified using $scheme scheme .*: true$" <<< "$signatures"
done
python3 "$root/tools/check_offline_apk.py" "$bt/aapt2" "$apk" "${@:2}"
