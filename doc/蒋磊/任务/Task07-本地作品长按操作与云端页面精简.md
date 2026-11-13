# 任务 - Task07

## 1. 基本信息

| 字段 | 值 |
|------|-----|
| 任务ID | Task07 |
| 关联需求 | 用户指令：封面默认上传；云端页只展示列表/下载，服务器设置放右上角；上传改到首页长按；长按支持删除本地、导出 JSON、导出完整作品（含图包） |
| 创建时间 | 2026-11-13 |
| 负责人 | 蒋磊 |
| 预计工时 | 0.5 人天 |
| 优先级 | P1 |

## 2. 任务信息

### 2.1 任务名称

本地作品长按操作与云端页面精简

### 2.2 任务描述

- 云端作品页只保留云端作品列表、搜索、刷新和下载。
- 服务器地址配置移到云端页右上角齿轮弹窗。
- 首页本地作品卡片支持长按，弹出操作菜单：
  - 上传到云端（封面默认上传，可选包含图片包）；
  - 导出 JSON；
  - 导出完整作品 ZIP（`script.json` + `images/`）；
  - 删除本地作品（二次确认，删除图片和存档）。
- 图包超过 32MB 的上限放宽到 512MB，上传/下载均使用文件流，不整体读入内存。
- 上传封面优先使用起始图/当前封面，缺失时回退到第一张已生成图片。

### 2.3 拆分方式

按交互拆分为三块：本地长按菜单与上传弹窗、删除/导出数据操作、云端页服务器设置与列表精简。

### 2.4 依赖关系

| 前置任务 | 依赖说明 |
|----------|----------|
| Task05 | 复用云端 API、设备会话和下载导入 |
| Task04 | R2 预签名上传和下载 |
| Task01 | 复用封面等比展示 |

### 2.5 缺失资料

| 编号 | 缺失项 | 说明 | 影响范围 |
|------|--------|------|----------|
| — | 无 | 交互和导出格式已按用户描述实现 | — |

## 3. 技术方案（如有）

### 3.1 实现思路

- `LibraryScreen` 的 `ScriptCoverCard` 增加 `combinedClickable` 长按；长按打开 `ModalBottomSheet` 操作菜单。
- 上传弹窗读取本地作品目录，不依赖当前选中作品；封面优先 `entry.coverPath`，缺失时按图片清单顺序回退。
- 图片包写入 `cacheDir` 临时 zip，使用 OkHttp `File.asRequestBody` 流式上传；下载先写入临时文件，再流式解压。
- 删除调用 `deleteScript(key)`，递归删除本地作品目录；删除当前作品后自动切换到下一个或回到空状态。
- 导出使用 Android `CreateDocument`，JSON 直接写原文件；完整作品用 `ZipOutputStream` 流式写 `script.json` 和 `images/`。
- `CloudScreen` 移除服务器配置卡片和上传卡片；右上角齿轮打开服务器地址弹窗。

### 3.2 计划涉及文件清单

| 文件路径 | 操作类型 | 说明 |
|----------|----------|------|
| `app/src/main/java/com/taverndream/yedu/ui/LibraryScreen.kt` | 修改 | 长按菜单、上传弹窗、删除确认、导出 launcher |
| `app/src/main/java/com/taverndream/yedu/ui/CloudScreen.kt` | 修改 | 只保留列表/下载，右上角服务器设置 |
| `app/src/main/java/com/taverndream/yedu/GalgameViewModel.kt` | 修改 | 按 key 上传、删除、导出、流式图包 |
| `app/src/main/java/com/taverndream/yedu/net/CloudApiClient.kt` | 修改 | 文件流上传、流式下载到文件 |
| `server/src/config.js`、`.env.example`、`README.md` | 修改 | 单文件上限 512MB |
| `README.md` | 修改 | 使用说明 |

## 4. 验收标准

- [x] 云端页只展示云端作品列表和下载入口
- [x] 服务器地址设置位于云端页右上角
- [x] 首页长按本地作品弹出操作菜单
- [x] 上传时封面默认上传，缺封面时使用第一张已生成图片
- [x] 长按可删除本地作品，并有二次确认
- [x] 长按可导出 JSON
- [x] 长按可导出完整作品 ZIP（`script.json` + `images/`）
- [x] 图片包上限 512MB，上传/下载使用文件流
- [x] 构建、单元测试和后端测试通过

## 5. 接口设计（如有）

沿用 Task05 的云端接口，无新增服务端接口；后端仅调整 `MAX_UPLOAD_BYTES` 默认值。

## 6. 涉及技能（如有）

| 技能名称 | 用途说明 |
|----------|----------|
| funi-dev-process | 任务与实现索引管理 |

## 7. 交付物

- 首页长按操作菜单
- 本地删除、JSON 导出、完整作品 ZIP 导出
- 云端页精简与右上角服务器设置
- 512MB 流式上传/下载
- 最新调试 APK

## 8. 测试要点（供测试参考）

- 长按本地卡片不误触「继续/从头」按钮。
- 上传弹窗关闭前能看到进度；失败时保留错误。
- 没有封面时上传第一张已生成图片作为封面。
- 删除当前作品后能正确切换到下一个作品或空状态。
- 导出 JSON 可被重新导入。
- 导出 ZIP 内含 `script.json` 和 `images/`，下载/解压后目录结构完整。
- 512MB 图包上传/下载不 OOM，失败时有明确提示。

## 9. 实现索引（任务完成时必填）

### 9.1 最终实现方案

- 首页卡片使用 `combinedClickable`，长按打开 `LocalWorkActionSheet`；卡片点击仍进入阅读。
- `UploadCloudDialog` 默认勾选图片包、默认上传封面；作者名沿用 `settings.cloudAuthor`。
- `GalgameViewModel.uploadScript(key, includePack, author)` 从作品目录读取数据，不再依赖当前选中作品；封面回退顺序为 `coverPath` → 图片清单第一张已生成图片。
- 图包写 `cacheDir/cloud-pack-*.zip`，用 `File.asRequestBody` 文件流上传；下载用临时文件 + `ZipInputStream` 流式解压。
- `deleteScript(key)` 删除目录并刷新剧本库；删除当前作品时切到下一个作品。
- `exportScriptJson(uri, key)` 和 `exportWorkZip(uri, key)` 使用 SAF `CreateDocument` 写用户选择的位置。
- `CloudScreen` 仅保留云端列表、搜索、刷新、下载；右上角齿轮打开 `CloudServerDialog`。

### 9.2 实际代码改动

| 类型 | 文件路径 | 关键位置 | 改动说明 |
|------|----------|----------|----------|
| 修改 | `app/src/main/java/com/taverndream/yedu/ui/LibraryScreen.kt` | `ScriptLibraryList`、`ScriptCoverCard` | 长按菜单、上传弹窗、删除确认、导出 launcher |
| 修改 | `app/src/main/java/com/taverndream/yedu/ui/CloudScreen.kt` | `CloudScreen`、`CloudServerDialog` | 只保留列表/下载，右上角服务器设置 |
| 修改 | `app/src/main/java/com/taverndream/yedu/GalgameViewModel.kt` | `uploadScript`、`deleteScript`、`exportScriptJson`、`exportWorkZip`、`importWorkPackage`、`buildImagePack` | 按 key 上传、删除、导入/导出完整作品、流式图包 |
| 修改 | `app/src/main/java/com/taverndream/yedu/net/CloudApiClient.kt` | `uploadFile`、`downloadToFile` | 文件流上传与流式下载 |
| 修改 | `server/src/config.js`、`server/.env.example`、`server/README.md` | `MAX_UPLOAD_BYTES` | 单文件上限改为 512MB |
| 修改 | `README.md` | 云端作品库说明 | 长按上传、导出、删除和 512MB |

### 9.3 接口、数据与外部依赖影响

| 类型 | 名称 | 影响说明 |
|------|------|----------|
| 接口 | 云端作品 API | 无新增接口，上传文件大小上限调整 |
| 数据 | 本地作品目录 | 删除会递归移除 `script.json`、`save.json` 和 `images/` |
| 数据 | 导出文件 | 使用 SAF，不申请额外存储权限 |
| 外部依赖 | OkHttp / java.util.zip | 项目已有，无新增依赖 |

### 9.4 验证记录与已知风险

| 项目 | 结果 | 说明 |
|------|------|------|
| App 编译 | 通过 | `./gradlew :app:compileDebugKotlin` |
| App 单元测试 | 通过 | 现有 9 个测试通过 |
| APK 构建 | 通过 | `./gradlew :app:assembleDebug` |
| 后端测试 | 通过 | `npm test`，7 个测试通过 |
| 后端重启 | 通过 | 重建 `yedu-cloud:latest` 并重启容器，`/health` 正常 |
| 后端上限 | 通过 | 容器内 `loadConfig().maxUploadBytes = 536870912` |
| 真机验收 | 待用户验收 | 按用户要求不代测，由用户安装最新 APK 后体验 |
| 已知风险 | 删除不可恢复 | 删除前二次确认，目录内图片和存档一并删除 |
| 已知风险 | 导出 ZIP 体积较大 | 使用流式写入，不整体读入内存 |

## 10. 修改记录

| 时间 | 修改内容 | 修改原因 | 确认人 |
|------|----------|----------|--------|
| 2026-11-13 | 完成长按操作、删除、导出、云端页精简和 512MB 流式传输 | 用户补充需求 | 用户 |
| 2026-11-13 | 增加按钮按压缩放反馈、云端作品骨架屏和操作加载指示 | 用户补充体验需求 | 用户 |
| 2026-11-13 | 增加导航按钮触感/动画、页面切换动画和云端列表/封面缓存 | 用户补充体验需求 | 用户 |
| 2026-11-13 | 支持导入完整作品包 ZIP（script.json + images/） | 用户补充功能需求 | 用户 |