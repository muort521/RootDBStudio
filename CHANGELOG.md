# 更新记录（Changelog）

数据库工坊（`com.rootdb.studio`）版本变更记录。版本号对应 `app/build.gradle.kts` 中的 `versionName` / `versionCode`。

## 1.4 — versionCode 5

- **无 root 也能浏览内部存储**：Shell 在 su 全部失败时回退成应用自身 uid 的 `sh -c sh`，配合「所有文件访问权限」即可列目录、读文件，不再直接失败。`hasRoot` 仍按 `id -u` 判定，不会把普通 shell 误报为 root。
- **内部存储入口改用 `/storage/emulated/0`**：`/sdcard`、`/storage/self/primary` 在无 root 时是应用挂载视图里的软链，常常列不出内容；现在所有路径入口（快捷入口、最近访问、「打开指定路径」、命令行 `--es openPath`）都会先归一化成 `/storage/emulated/0`，最近访问里的旧 `/sdcard` 记录也会自动迁移。
- **「重试」会真正重开 shell**：`resetRootCache()` 现在一并关闭当前会话，用户之后授予 root 权限时可以检测到 root，而不是继续沿用无 root 的旧 shell。
- 首页 root 提示改为「无 root 时仍可浏览 `/storage/emulated/0`；`/data/data` 等受保护目录需 root 授权」。
- 浏览内部存储时标题显示为「内部存储」（而不是最后一段目录名 `0`），该目录为顶层，不再显示「上一级」。

## 1.3 — versionCode 4

- 首页标题「数据库工坊」改为小标题样式：居中显示并贴近状态栏，去掉大标题折叠态留下的空白；内容整体上移。

## 1.2 — versionCode 3（界面结构拆分）

1. **文件浏览独立成页**：首页只保留状态卡片、文件权限、快捷入口与最近访问；点快捷入口 / 最近记录 / 「打开指定路径」后进入独立的「文件浏览」页。
2. **操作区固定在顶部**：搜索框、「刷新」按钮和「上一级」都不再跟随列表滚动——列表翻到最底部仍然可见可点；点击搜索框弹出输入法时也不会被键盘遮住（窗口 adjustResize，操作区始终在键盘之上）。
3. **最近访问回到首页**：原来的「浏览 · 路径」位置改成最近访问列表，最新在最前、自动去重，只保留 10 条，标题右侧可「清空」；点任意一条直接进入该目录。
4. **返回层级更清晰**：文件浏览页左上角箭头或系统返回 → 首页；数据库详情页返回 → 文件浏览页（目录内容自动补载）；浏览页内「上一级」仍回到父目录并还原上次滚动位置。

## 1.1 — versionCode 2（7 项界面与体验改进）

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

## 1.0 — versionCode 1（首个可用版本）

- 用交互式 root shell（优先 `nsenter -t 1 -m` 进入 init 挂载命名空间）浏览 `/data/data`、`/data/system`、`/`、`/sdcard` 等目录，含文件大小、修改时间与权限列。
- 打开 SQLite / SQLCipher 数据库：表列表 → 行列表（横向滚动、列头、rowid、每页 50 行），单元格编辑（自动/文本/整数/小数/BLOB/NULL）、按 rowid 更新、删除行、新增行、执行任意 SQL。
- 写回安全链路：所有改动先作用于工作副本，`写回原库` 时生成 `.dbstudio.bak` 并恢复原属主与权限。
- 字段解密显示：AES(ECB/CBC/GCM)、DES、3DES、XOR、Base64，密钥/IV 支持文本、十六进制、Base64。
- 实时监控（1.5s 轮询主库/WAL/SHM 的大小与修改时间，统计外部变更次数）、备份新建/导出/恢复/删除、历史记录与单条撤销、导出到系统下载目录。
