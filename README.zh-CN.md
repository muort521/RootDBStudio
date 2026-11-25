# 数据库工坊 · RootDB Studio

基于 root 的 Android SQLite / SQLCipher 数据库浏览与编辑工具，使用 Jetpack Compose + MIUIX 构建。

[English](README.md)

- 包名：`com.rootdb.studio`
- Android 8.0+（minSdk 26），compile / target SDK 37
- 语言：简体中文 + English（默认），跟随系统语言
- 不申请网络权限
- 许可证：[MIT](LICENSE)
- 更新记录：[CHANGELOG.md](CHANGELOG.md)

## 功能

- root 下浏览 `/data/data`、`/data/system` 等受保护目录；无 root 也能浏览内部存储
- 表格带横竖网格线、按比例显示且可拖动的横向滚动条、分页
- 全列搜索（大表可用），命中片段高亮
- 编辑单元格（自动 / 文本 / 整数 / 小数 / BLOB / NULL）、插行、删行、执行任意 SQL
- 就地解密并编辑字段：AES（ECB/CBC/CTR/CFB/OFB/GCM）、DES（ECB/CBC）、3DES（ECB/CBC）、Blowfish、ChaCha20 / ChaCha20-Poly1305、SM4、RSA（PKCS#1 v1.5 / OAEP-SHA256）、XOR、Base64；算法与密钥按「库 + 表」分别记忆
- 打开 SQLCipher 4 加密库，并自动兼容 SQLCipher 3.x 旧库（`cipher_compatibility=3` 回退）
- 「加密与密钥」独立页面：字段解密配置按库+表管理（可编辑/删除/清空），整库密码用 Android Keystore 加密记忆、支持就地修改，加密库再次打开无需重输；开启解密时单元格编辑框显示明文，保存自动加密回写
- 实时监控、备份（新建 / 导出 / 恢复 / 删除）、可撤销的历史记录
- 所有修改先落在工作副本；「写回原库」时在源文件旁保留 `*.dbstudio.bak`

## 安装

从 **Releases** 下载 APK 安装即可。root 可选，访问 `/data/data` 时需要。

## 构建

环境要求：JDK 21，Android SDK 含 `platforms;android-37.0`（或 `android-37.2`）与 `build-tools;37.0.0`。

```bash
./gradlew :app:assembleRelease        # Windows 用 gradlew.bat
```

- 产物：`app/build/outputs/apk/release/app-release.apk`。
- release 签名读取根目录 `keystore.properties`（`storeFile / storePassword / keyAlias / keyPassword`），该文件未提交；缺失时产物未签名。
- release 构建启用 R8（代码混淆 + 资源压缩），请保留 `app/build/outputs/mapping/release/mapping.txt` 以还原线上堆栈。
- `local.properties` 未提交，请自行写入 `sdk.dir=<你的 SDK 路径>`。

## 发布流程

`.github/workflows/release.yml` 仅支持手动触发：Actions → **Release APK** → *Run workflow* → 输入版本号如 `vX.Y.Z`。

- `vX.Y.Z` 对应 `versionName=X.Y.Z`、`versionCode = 主版本×10000 + 次版本×100 + 修订号`，通过 Gradle 参数传入，无需改文件。
- 同版本已有的 Release 或 tag 会先删除再重建。发布前创建 annotated tag，随后用一条 `gh release create` 发布，APK 为唯一附件；源码包 `Source code (zip)` / `Source code (tar.gz)` 由 GitHub 自动生成。
- 需要的 Secrets：`KEYSTORE_BASE64`、`KEYSTORE_PASSWORD`、`KEY_ALIAS`、`KEY_PASSWORD`。

## 多语言

界面文案位于 `app/src/main/res/values/strings.xml`（英文）与 `values-zh/strings.xml`（简体中文）。新增语言：复制 `values/strings.xml` 为 `values-<语言>/strings.xml` 翻译，并在 `app/src/main/res/xml/locales_config.xml` 追加 `<locale>` 条目。

## 数据安全

- 所有修改先作用于工作副本，「写回原库」才会覆盖源文件。
- 写回前与恢复备份前，都会在源文件旁生成 `<文件名>.dbstudio.bak`。
- 写回后恢复原文件的属主与权限。

## 许可证

MIT © 2026 RootDB Studio contributors — 见 [LICENSE](LICENSE)。
