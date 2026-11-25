# 数据库工坊（Database Studio）

Android 数据库浏览与编辑工具，用 Root 权限直接读写 `/data/data/<包名>/...` 等受保护目录里的 SQLite / SQLCipher 数据库，界面为 MIUIX 风格。

- 包名：`com.rootdb.studio`
- 版本：1.7（versionCode 8），历史版本变更见 [CHANGELOG.md](CHANGELOG.md)
- 目标平台：compileSdk / targetSdk = **37（Android 17）**，minSdk 26（Android 8.0）
- 在真机一加 12（PJD110，Android 17 / SDK 37，KernelSU root）上完成安装与功能验证

主题说明：应用不设独立主题开关，深色/浅色跟随系统的「深色模式」，配色由系统壁纸调色板动态生成；系统切换深色时界面即时刷新，无需重启应用。若机型不支持动态取色（Android 12 以下），自动回落到 MD3 默认色板。

## 功能对照需求

| 需求 | 实现方式 | 真机验证 |
| --- | --- | --- |
| Root 访问受保护目录 | 交互式 `su` shell，并通过 `nsenter -t 1 -m` 进入 init 的全局挂载命名空间，规避应用私有命名空间只看到少量目录的问题 | ✅ `/data/data` 列出 597 个真实包名（与 `adb shell ls` 一致） |
| 无 root 浏览内部存储 | su 全部失败时回退成应用自身 uid 的 `sh`；`/sdcard` 等路径统一归一化为 `/storage/emulated/0`，靠「所有文件访问权限」读写 | ✅ 无 root 真机列出 `/storage/emulated/0`，并直接打开其中的 `demo.db`（3 个表） |
| MIUIX 界面 | `top.yukonga.miuix.kmp:miuix:0.8.8`，卡片 + 胶囊按钮/页签；首页小标题栏居中置顶，数据库页使用大标题 | ✅ 见截图 |
| 可视化浏览数据库 | 表列表 → 行列表（横向滚动表格、列头、rowid），分页每页 50 行 | ✅ 打开 `/storage/emulated/0/Download/demo.db`、`/data/data/bin.mt.plus/databases/ua.db` 均正常 |
| 修改数据 | 单元格编辑（自动/文本/整数/小数/BLOB/NULL 六种写入类型）、按 rowid 更新、删除行、新增行、任意 SQL | ✅ 修改 `users.name` 后 `写回原库`，设备上的 db 文件内容经 Python `sqlite3` 复核已变更 |
| 多种解密算法 + 直接编辑加密字段 | AES(ECB/CBC/GCM)、DES、3DES、XOR、Base64；密钥/IV 支持文本、十六进制、Base64；密文编码支持 Base64/HEX；表页可开「解密显示」，编辑框内提供「按密文原样保存 / 保存时加密 / 解密预览」 | ✅ Base64 字段解出 `test-secret`、`{"uid":1001}`；非加密字段自动保持原样，不会被解成乱码 |
| 目标 Android 17 + 导出到本地 | AGP 9.2.1 + Gradle 9.4.1 + Compose 1.10.5，targetSdk 37；`导出到下载` 通过 MediaStore 写入 `/sdcard/Download` | ✅ 导出文件出现在 `/sdcard/Download/demo.db.20261125_031318.db` |
| 实时监控 + 备份恢复 + 历史记录 | 1.5s 轮询主库/WAL/SHM 的大小与 mtime，统计外部变更次数；备份可新建/导出/恢复/删除；历史记录记录每次改值与写回，支持单条撤销 | ✅ 外部 `touch` 数据库后「检测到外部变更：1 次」；恢复备份后 `bobZ` 回滚为 `bob`；历史记录列出 `alice → alice2`、`bob → bobZ` |

## 安全设计（避免把数据改坏）

- 所有写操作先作用于应用内的**工作副本**，点「写回原库」才会覆盖源文件。
- 写回前自动在源文件旁生成 `<原文件>.dbstudio.bak`，并在恢复备份前再留一份同样的 `.bak`。
- 写回时恢复原始属主与权限（`chown uid:gid` + `chmod`），root 属主库（实测 `root:root 600`）写回后属主/权限保持不变。
- 备份文件存放在应用私有目录 `files/backups`，可导出到系统下载目录。
- 声明 `MANAGE_EXTERNAL_STORAGE` 以便 Android 13+ 正常列出 `/storage/emulated/0`；不申请网络权限以外的隐私权限，应用不联网。

## 使用步骤

1. 安装 APK，打开应用，首屏会显示 Root 状态（未授权时点「检查」重新检测，在 KernelSU/Magisk 里给本应用授权）。
2. 如需浏览内部存储（`/storage/emulated/0`），点「所有文件访问权限」跳到系统设置授权；没有 root 也能浏览这里。
3. 从「快捷入口」进 `/`（根目录）、`/data/data`、`/data/system`、`/storage/emulated/0`，或点首页「最近访问」里的一条记录，进入独立的「文件浏览」页；该页顶部固定着搜索框、「刷新」和「上一级」，列表滚到底或弹出输入法都不影响操作。目录内用搜索框按文件名过滤。
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
