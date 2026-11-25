# RootDB Studio · 数据库工坊

A root-powered SQLite / SQLCipher browser and editor for Android, built with Jetpack Compose + MIUIX.
安卓上的 SQLite / SQLCipher 数据库浏览与编辑工具，可直接读写 `/data/data/<包名>/…` 等受保护目录，界面为 MIUIX 风格。

- Package / 包名：`com.rootdb.studio`
- Version / 版本：2.0.2（versionCode 14），changes in [CHANGELOG.md](CHANGELOG.md)
- Android 8.0+（minSdk 26），compile/target SDK 37（Android 17）
- Languages / 语言：English (default) + 简体中文, auto by system locale
- License / 许可证：[MIT](LICENSE)
- No network permission · 不申请网络权限

## Features · 功能

| English | 中文 |
| --- | --- |
| Browse protected dirs via root (`/data/data`, `/data/system`) or internal storage without root | root 下浏览 `/data/data`、`/data/system`；无 root 也能浏览内部存储 |
| Table view with grid lines, proportional draggable scrollbar, paging | 表格带横竖网格线、按比例显示且可拖动的横向滚动条、分页 |
| Search across all columns (large tables), matched text highlighted | 全列搜索（大表可用），命中片段高亮 |
| Edit cells (auto / text / integer / real / BLOB / NULL), delete or insert rows, run any SQL | 编辑单元格（自动/文本/整数/小数/BLOB/NULL）、删行、插行、执行任意 SQL |
| Decrypt AES / DES / 3DES / XOR / Base64 fields in place, edit them safely | 就地解密并安全编辑 AES / DES / 3DES / XOR / Base64 字段 |
| Live monitor, backups (create / export / restore / delete), undoable history | 实时监控、备份（新建/导出/恢复/删除）、可撤销的历史记录 |
| All writes go to a working copy; “Write back” also leaves `*.dbstudio.bak` | 所有修改先落在工作副本；写回时自动留 `*.dbstudio.bak` |

## Install · 安装

Download the APK from **Releases** and install it. Root is optional but required for `/data/data`.
从 **Releases** 下载 APK 安装即可；root 可选，访问 `/data/data` 时需要。

## Build · 构建

Requirements: JDK 21, Android SDK with `platforms;android-37.0` (or `android-37.2`) and `build-tools;37.0.0`.

```bash
./gradlew :app:assembleRelease        # Windows: gradlew.bat
```

Output: `app/build/outputs/apk/release/app-release.apk`.
Release signing reads `keystore.properties` (`storeFile / storePassword / keyAlias / keyPassword`), which is not committed. Without it the APK is built unsigned.
产物在 `app/build/outputs/apk/release/app-release.apk`；release 签名依赖根目录 `keystore.properties`（未提交），缺失时产物未签名。

`local.properties` is not committed: create it with `sdk.dir=<your SDK path>`.
`local.properties` 也未提交，请自行写入 `sdk.dir`。

## Release workflow · 发布流程（手动）

`.github/workflows/release.yml` builds and publishes **one GitHub Release** containing only the APK. Source downloads (`Source code (zip)` / `Source code (tar.gz)`) are generated automatically by GitHub at publish time — there is no custom source zip asset. It only runs manually:

Actions → **Release APK** → *Run workflow* → version, e.g. `v2.0.2`

- `v2.0.2` → `versionName=2.0.2`, `versionCode=20002` (passed to Gradle, no file edits).
- If a Release or tag with the same version already exists, it is deleted and rebuilt first, so stale source timestamps never remain.
- The APK is uploaded to a **draft** release created under a temporary tag; at publish time the tag is renamed to `vX.Y.Z`, which is the moment GitHub creates that tag and generates `Source code (zip)` / `Source code (tar.gz)`. The source downloads therefore never precede the APK, and both show the same publish time.
- Repository secrets required: `KEYSTORE_BASE64` (base64 of the `.jks`), `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.

发布只会创建一个 Release，只附 APK；源码由 GitHub 在发布时自动生成 `Source code (zip)` / `Source code (tar.gz)`，不再上传自定义源码 zip。只能手动触发：Actions → **Release APK** → Run workflow → 输入 `v2.0.2`。若同版本已有 Release 或 tag，会先删除重建，避免沿用旧时间戳。APK 先传到用临时 tag 建的草稿 Release，正式发布时把 tag 改回 `v2.0.2`——GitHub 正是在这一刻创建正式 tag 并生成源码包，因此两个源码下载不会早于 APK，显示的是同一发布时间。需要在仓库 Secrets 中配置上面 4 个签名相关变量。

## Internationalization · 多语言

All UI strings live in `app/src/main/res/values/strings.xml` (English) and `values-zh/strings.xml` (中文). To add a language: copy `values/strings.xml` to `values-<lang>/strings.xml`, translate values, add a `<locale>` entry in `app/src/main/res/xml/locales_config.xml`.

所有界面文案都在 `res/values/strings.xml`（英文）与 `values-zh/strings.xml`（中文）中。新增语言：复制 `values/strings.xml` 到 `values-<语言>/strings.xml` 翻译，并在 `res/xml/locales_config.xml` 追加 `<locale>`，无需改代码。

## Data safety · 数据安全

- Every edit runs on a working copy; the source file is only touched by “Write back”.
- Before writing back (and before restoring a backup) the app creates `<file>.dbstudio.bak` next to the source.
- Original owner and permissions are restored after write-back.
- 所有修改先作用于工作副本，「写回原库」才覆盖源文件；写回前与恢复备份前都会在源文件旁生成 `.dbstudio.bak`，写回后恢复原属主与权限。

## License · 许可证

MIT © 2026 RootDB Studio contributors — see [LICENSE](LICENSE).
