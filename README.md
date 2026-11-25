# 数据库工坊（Database Studio）

Android 数据库浏览与编辑工具，用 Root 权限直接读写 `/data/data/<包名>/...` 等受保护目录里的 SQLite / SQLCipher 数据库，界面为 MIUIX 风格。

- 包名：`com.rootdb.studio`
- 版本：1.3（versionCode 4）
- 目标平台：compileSdk / targetSdk = **37（Android 17）**，minSdk 26（Android 8.0）
- 在真机一加 12（PJD110，Android 17 / SDK 37，KernelSU root）上完成安装与功能验证

## 1.3 更新

- 首页标题「数据库工坊」改为小标题样式：居中显示并贴近状态栏，去掉大标题折叠态留下的空白；内容整体上移。


## 1.2 更新（界面结构拆分）

1. **文件浏览独立成页**：首页只保留状态卡片、文件权限、快捷入口与最近访问；点快捷入口 / 最近记录 / 「打开指定路径」后进入独立的「文件浏览」页。
2. **操作区固定在顶部**：搜索框、「刷新」按钮和「上一级」都不再跟随列表滚动——列表翻到最底部仍然可见可点；点击搜索框弹出输入法时也不会被键盘遮住（窗口 adjustResize，操作区始终在键盘之上）。
3. **最近访问回到首页**：原来的「浏览 · 路径」位置改成最近访问列表，最新在最前、自动去重，只保留 **10 条**，标题右侧可「清空」；点任意一条直接进入该目录。
4. **返回层级更清晰**：文件浏览页左上角箭头或系统返回 → 首页；数据库详情页返回 → 文件浏览页（目录内容自动补载）；浏览页内「上一级」仍回到父目录并还原上次滚动位置。


## 1.1 更新（7 项界面与体验改进）

| # | 改进 | 实现方式 | 真机验证 |
| --- | --- | --- | --- |
| 1 | 实时监控文本不再贴边被圆角裁切 | 监控卡片显式 `insideMargin = PaddingValues(horizontal = 20.dp, vertical = 14.dp)`，正文加 `lineHeight = 18.sp`；说明类副标题允许多行（`subtitleMaxLines`） | ✅ 正文左边界距卡片圆角约 23dp，长文本完整换行 |
| 2 | 当前目录内搜索 | 「上一级」上方新增卡片式搜索栏（前置放大镜、非空时一键清空），按名称不区分大小写过滤，顶部提示「匹配 N / M 项」；列表上滑时大标题自动折叠，避免文字压在内容上 | ✅ 输入 `png` 显示「匹配「png」：12 / 37 项」，清空按钮可用 |
| 3 | 快捷入口改为根目录 `/` | 原 `/data/adb` 换成 `/`（描述「根目录（系统全部文件）」），并给四个快捷入口都加了图标 | ✅ 从 `/` 能列出 `apex`、`bootstrap-apex`、`config` 等真实根目录项 |
| 4 | 返回上级时回到上次位置 | `LazyListState` 持续记录可见项索引与偏移，`loadPath()` 切换前按路径存档；目录加载完成后还原，首次进入的新目录自动回到顶部 | ✅ `/` 滚动到 `my_bigball` → 进 `my_carrier` → 「上一级」回到原位（`my_bigball` 仍在同一 Y 坐标） |
| 5 | 文件夹 / 文件类型图标 | 新增 `ui/FileIcons.kt`：按扩展名分类 20 类（目录、数据库、图片、视频、音频、播放列表、字幕、压缩包、应用包、二进制、代码、网页、文本、PDF、文档、表格、演示、字体、证书密钥、日志），用 `material-icons-extended` 对应图标并按主题色板着色 | ✅ `/sdcard/Download` 放入 13 种测试文件，逐个显示为 文件夹 / 数据库文件 / 应用包 / 证书或密钥 / 代码文件 / 文本文件 / PDF 文档 / 字体文件 / 二进制文件 / 日志文件 / 视频 / 压缩包 / 图片 / 音频 |
| 6 | 明暗主题跟随系统 | `MainActivity` 改用 `ThemeController(colorSchemeMode = ColorSchemeMode.MonetSystem)`，`MiuixTheme(controller = …)`；补充 `values-night/themes.xml`（深色窗口背景与状态栏反色） | ✅ `cmd uimode night yes` 后背景采样 RGB(0,11,46)、卡片 RGB(3,22,66)，浅色模式为 RGB(246,245,255) |
| 7 | 动态配色（壁纸取色） | 同一 `ThemeController` 走 MIUIX 的 Monet 分支：Android 12+ 读系统调色板（`Settings.Secure.theme_customization_overlay_packages`），12 以下回落 MD3 角色映射 | ✅ logcat 出现 `DynamicColors: System palette info: seedColor=…, paletteStyle=Vibrant, colorSpec=Spec2025`，界面主色随系统调色板变化 |

> 图标体积说明：`material-icons-extended` 使 debug APK 从约 18 MB 增加到约 29 MB（未开 R8 时会打包整包图标类）；如需更小体积，可在 release 构建开启 `isMinifyEnabled` 由 R8 裁掉未用图标。

主题说明：应用不设独立主题开关，深色/浅色跟随系统的「深色模式」，配色由系统壁纸调色板动态生成；系统切换深色时界面即时刷新，无需重启应用。若机型不支持动态取色（Android 12 以下），自动回落到 MD3 默认色板。

## 功能对照需求

| 需求 | 实现方式 | 真机验证 |
| --- | --- | --- |
| Root 访问受保护目录 | 交互式 `su` shell，并通过 `nsenter -t 1 -m` 进入 init 的全局挂载命名空间，规避应用私有命名空间只看到少量目录的问题 | ✅ `/data/data` 列出 597 个真实包名（与 `adb shell ls` 一致） |
| MIUIX 界面 | `top.yukonga.miuix.kmp:miuix:0.8.8`，大标题 TopAppBar + 卡片 + 胶囊按钮/页签 | ✅ 见截图 |
| 可视化浏览数据库 | 表列表 → 行列表（横向滚动表格、列头、rowid），分页每页 50 行 | ✅ 打开 `/sdcard/Download/demo.db`、`/data/data/bin.mt.plus/databases/ua.db` 均正常 |
| 修改数据 | 单元格编辑（自动/文本/整数/小数/BLOB/NULL 六种写入类型）、按 rowid 更新、删除行、新增行、任意 SQL | ✅ 修改 `users.name` 后 `写回原库`，设备上的 db 文件内容经 Python `sqlite3` 复核已变更 |
| 多种解密算法 + 直接编辑加密字段 | AES(ECB/CBC/GCM)、DES、3DES、XOR、Base64；密钥/IV 支持文本、十六进制、Base64；密文编码支持 Base64/HEX；表页可开「解密显示」，编辑框内提供「按密文原样保存 / 保存时加密 / 解密预览」 | ✅ Base64 字段解出 `test-secret`、`{"uid":1001}`；非加密字段自动保持原样，不会被解成乱码 |
| 目标 Android 17 + 导出到本地 | AGP 9.2.1 + Gradle 9.4.1 + Compose 1.10.5，targetSdk 37；`导出到下载` 通过 MediaStore 写入 `/sdcard/Download` | ✅ 导出文件出现在 `/sdcard/Download/demo.db.20261125_031318.db` |
| 实时监控 + 备份恢复 + 历史记录 | 1.5s 轮询主库/WAL/SHM 的大小与 mtime，统计外部变更次数；备份可新建/导出/恢复/删除；历史记录记录每次改值与写回，支持单条撤销 | ✅ 外部 `touch` 数据库后「检测到外部变更：1 次」；恢复备份后 `bobZ` 回滚为 `bob`；历史记录列出 `alice → alice2`、`bob → bobZ` |

## 安全设计（避免把数据改坏）

- 所有写操作先作用于应用内的**工作副本**，点「写回原库」才会覆盖源文件。
- 写回前自动在源文件旁生成 `<原文件>.dbstudio.bak`，并在恢复备份前再留一份同样的 `.bak`。
- 写回时恢复原始属主与权限（`chown uid:gid` + `chmod`），root 属主库（实测 `root:root 600`）写回后属主/权限保持不变。
- 备份文件存放在应用私有目录 `files/backups`，可导出到系统下载目录。
- 声明 `MANAGE_EXTERNAL_STORAGE` 以便 Android 13+ 正常列出 `/sdcard`；不申请网络权限以外的隐私权限，应用不联网。

## 使用步骤

1. 安装 APK，打开应用，首屏会显示 Root 状态（未授权时点「重试」，在 KernelSU/Magisk 里给本应用授权）。
2. 如需浏览 `/sdcard`，点「所有文件访问权限」跳到系统设置授权。
3. 从「快捷入口」进 `/`（根目录）、`/data/data`、`/data/system`、`/sdcard`，或点首页「最近访问」里的一条记录，进入独立的「文件浏览」页；该页顶部固定着搜索框、「刷新」和「上一级」，列表滚到底或弹出输入法都不影响操作。目录内用搜索框按文件名过滤。
4. 打开数据库时：普通 SQLite 留空密钥直接打开；SQLCipher 加密库填 32 位十六进制密钥（如微信 `EnMicroMsg.db`）。
5. 浏览表：点表名进入表格页，点单元格改值，点「SQL」跑任意语句，点「加密设置」配置字段解密算法，点「解密显示：开」查看明文。
6. 改完点「写回原库」；在「备份恢复」页先「新建备份」可随时回滚；「历史记录」页可撤销单条改动。

## 已知限制

- SQLCipher：能够加载引擎（实测用错误密钥打开明文库会正确报 `file is not a database`），但手头没有真实加密库样本，**未做加密库端到端验证**。
- 解密显示是「按当前配置的算法去解每个文本字段」，算法配错时会把普通字段显示为原文（已做可读性过滤，不会显示乱码）。
- 导出/恢复对正在被其它进程占用的数据库可能失败，建议先备份再操作。

## 构建方式

环境要求：JDK 21、Android SDK（platforms;android-37）、Gradle 9.4.1。仓库已配置阿里云镜像（`maven.aliyun.com`），无需访问 dl.google.com。

```powershell
$env:JAVA_HOME = "C:\Program Files\Java\latest\jdk-21"
gradle -p . :app:assembleDebug --console=plain
```

产物位于 `app/build/outputs/apk/debug/app-debug.apk`；发布版：

```powershell
gradle -p . :app:assembleRelease --console=plain
```

产物位于 `app/build/outputs/apk/release/app-release.apk`，已用 `keystore/rootdb-release.jks`（alias `rootdb`）签名，`apksigner verify` 显示 V2 签名证书 `CN=RootDB Studio`。签名密码等写在根目录 `keystore.properties`（`storeFile` / `storePassword` / `keyAlias` / `keyPassword`），该文件不随发布包分发。没有这个文件时 release 会构建成功但未签名，需要自备 keystore 再签。

发布版与调试版签名不同，二者不能互相覆盖安装：换签名安装前先卸载，应用内数据（最近访问、历史、备份）会被清掉。


`local.properties` 未随包提供，请自行创建并写入：

```
sdk.dir=E\:\\home\\SDK
```

技术栈：AGP 9.2.1（内置 Kotlin 2.3.20，不再需要 `kotlin-android` 插件）、Compose Multiplatform 1.10.3 对应的 androidx.compose 1.10.5、MIUIX 0.8.8（`ThemeController` + Monet 动态取色）、androidx.compose.material:material-icons-extended 1.7.8（文件类型图标）、androidx.sqlite 2.7.1、net.zetetic:sqlcipher-android 4.20.0。
