# RootDB Studio

A root-powered SQLite / SQLCipher browser and editor for Android, built with Jetpack Compose + MIUIX.

[中文说明](README.zh-CN.md)

- Package: `com.rootdb.studio`
- Android 8.0+ (minSdk 26), compile/target SDK 37
- Languages: English (default) + 简体中文, follows system locale
- No network permission
- License: [MIT](LICENSE)
- Changelog: [CHANGELOG.md](CHANGELOG.md)

## Features

- Browse protected directories via root (`/data/data`, `/data/system`), or internal storage without root
- Table view with grid lines, proportional draggable scrollbar and paging
- Search across all columns of large tables, with highlighted matches
- Edit cells (auto / text / integer / real / BLOB / NULL), insert or delete rows, run arbitrary SQL
- Decrypt and edit AES / DES / 3DES / XOR / Base64 fields in place
- Live monitor, backups (create / export / restore / delete), undoable history
- Every edit lands on a working copy; "Write back" leaves a `*.dbstudio.bak` next to the source

## Install

Download the APK from **Releases** and install it. Root is optional; it is required for `/data/data`.

## Build

Requirements: JDK 21, Android SDK with `platforms;android-37.0` (or `android-37.2`) and `build-tools;37.0.0`.

```bash
./gradlew :app:assembleRelease        # Windows: gradlew.bat
```

- Output: `app/build/outputs/apk/release/app-release.apk`.
- Signing reads `keystore.properties` (`storeFile / storePassword / keyAlias / keyPassword`), which is not committed. Without it the APK is built unsigned.
- Release builds run R8 (minify + resource shrinking). Keep `app/build/outputs/mapping/release/mapping.txt` to deobfuscate crash reports.
- `local.properties` is not committed — create it with `sdk.dir=<your SDK path>`.

## Release workflow

`.github/workflows/release.yml` runs manually only: Actions → **Release APK** → *Run workflow* → enter a version tag such as `vX.Y.Z`.

- `vX.Y.Z` becomes `versionName=X.Y.Z` and `versionCode = major × 10000 + minor × 100 + patch`, passed as Gradle properties (no file edits).
- An existing release or tag with the same version is deleted and rebuilt first. An annotated tag is created right before a single `gh release create` publishes the APK as the only asset; GitHub generates `Source code (zip)` / `Source code (tar.gz)` itself.
- Required secrets: `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.

## Internationalization

UI strings live in `app/src/main/res/values/strings.xml` (English) and `values-zh/strings.xml` (简体中文). To add a language: copy `values/strings.xml` to `values-<lang>/strings.xml`, translate the values, and add a `<locale>` entry to `app/src/main/res/xml/locales_config.xml`.

## Data safety

- Every edit runs on a working copy; the source file is touched only by "Write back".
- `<file>.dbstudio.bak` is created before writing back and before restoring a backup.
- Original owner and permissions are restored after write-back.

## License

MIT © 2026 RootDB Studio contributors — see [LICENSE](LICENSE).
