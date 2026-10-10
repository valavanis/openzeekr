# OpenZeekr test builds

Sideload builds of branch `ccr-4ab0708f-tm248z` (PR #1) for testing at the car. Not an official release.

| File | Source | SHA-256 |
|---|---|---|
| `OpenZeekr-0.2-diag-ea55d2d-arm64.apk` | commit `ea55d2d` | `bc5064d7caf0e7bb3323815f18c670d00c405e756b5271591a767d3275197ee8` |

- Release build (R8), **arm64-v8a only** (other ABIs removed to keep the file small).
- Built without `secrets.properties`: no keys are baked in; enter your own in Settings or import your config.
- Signed with a private sideload key (certificate SHA-256 `5ec7650ccaa8b741464bcd14126212f3411440a280ab3339bd76a2dceb235f7d`), **not** the upstream release key. Android will not install it over an app signed with another key; uninstalling first deletes the digital key, sign-in and calibration (use Settings › Export, and Key › Remove key, before uninstalling).
- To report a problem at the car: Settings › Diagnostics › Record diagnostics, then Share.
