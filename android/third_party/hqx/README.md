# HQx

Maxim Stepin and Cameron Zemek's HQx filters, from
[upstream a1c7d415](https://github.com/grom358/hqx/tree/a1c7d415f4bb7b947c4c2e47faf220b0148f4bac).
Licensed under LGPL-2.1-or-later; see `COPYING`, `AUTHORS`, and each file's notice.

This directory contains the entire scaler library source used by the app.
The scalers use a bounded lazy color cache instead of the eager 64 MiB lookup
table. They fetch the per-thread cache once per frame, not per pixel, on older
Android versions that emulate TLS. The color math and pixel rules are
unchanged. The CLI and its image dependencies aren't included. The APK links
`libhqx.so` as a separate shared library; it is not linked into FBNeo. To
replace it, substitute a compatible HQx build (same ABI and `_32_rb` entry
points) in `build-apk.sh`'s staging step, or change the files here, then rebuild
and sign with your own key for a test install. The frontend does not check or
restrict modifications to HQx.
