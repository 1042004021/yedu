# 任务 - Task05

## 1. 基本信息

| 字段 | 值 |
|------|-----|
| 任务ID | Task05 |
| 关联需求 | 用户指令：App 支持输入服务器 IP/端口，接入上传、云端作品列表和下载 |
| 创建时间 | 2026-11-13 |
| 负责人 | 蒋磊 |
| 预计工时 | 0.8 人天 |
| 优先级 | P1 |

## 2. 任务信息

### 2.1 任务名称

App 云端作品库接入

### 2.2 任务描述

- 增加云服务器地址设置，支持 HTTP IP:端口和 HTTPS 域名。
- 自动注册/刷新设备 Token，调用云端作品创建、预签名、发布、列表和下载接口。
- 上传改为首页长按本地作品触发，封面默认上传，可选 images 图包；图包写临时文件并用文件流上传，显示上传进度和错误。
- 新增云端作品列表页，只展示云端作品并支持搜索、刷新和下载；服务器配置收进右上角齿轮。
- 下载后解压 images 到新的本地剧本目录，并刷新剧本库。

### 2.3 拆分方式

按功能点拆分为单任务：网络层、会话、上传、云端列表、下载导入和 UI。

### 2.4 依赖关系

| 前置任务 | 依赖说明 |
|----------|----------|
| Task04 | 使用 R2 云端作品库 API |
| Task01 | 复用等比图片展示组件 |

### 2.5 缺失资料

| 编号 | 缺失项 | 说明 | 影响范围 |
|------|--------|------|----------|
| — | 无 | 后端接口和 R2 环境已准备 | — |

## 3. 技术方案（如有）

### 3.1 实现思路

- 新增 `CloudApiClient`，用现有 OkHttp + kotlinx.serialization 调用后端。
- 设备 Token 保存在 `GalgameSettings`，过期自动刷新；服务器地址变化时清空会话。
- 上传时把当前 `script.json`、封面和 images 打包成 `pack.zip`，先创建草稿，再按预签名 URL 直传，最后 finalize。
- 下载时先获取 script/pack 签名 URL，图包下载到临时文件后流式解压，校验剧本后创建新本地目录并刷新剧本库。
- 新增 `CloudScreen`，从首页右上角进入；页面只保留云端列表、搜索和下载，服务器地址设置收进右上角齿轮弹窗；上传入口移到首页长按本地作品。

### 3.2 计划涉及文件清单

| 文件路径 | 操作类型 | 说明 |
|----------|----------|------|
| `data/CloudModels.kt` | 新增 | 云端 API 数据模型 |
| `net/CloudApiClient.kt` | 新增 | 设备、作品、预签名、上传和下载网络层 |
| `data/GalgameSettings.kt` | 修改 | 服务器地址和设备 Token 持久化 |
| `GalgameViewModel.kt` | 修改 | 云会话、上传、列表、下载导入状态 |
| `ui/CloudScreen.kt` | 新增 | 云端作品库页面 |
| `ui/Components.kt` | 修改 | 云端 URL 封面等比展示 |
| `MainActivity.kt`、`ui/LibraryScreen.kt` | 修改 | 云端入口和导航 |
| `README.md` | 修改 | 使用说明 |

## 4. 验收标准

- [x] 可输入服务器 IP/端口并测试连接
- [x] 可在首页长按本地作品上传剧本、默认封面和可选图片包
- [x] 可浏览、搜索、刷新云端作品
- [x] 可下载云端剧本和图片包到本地
- [x] 服务器地址变更会清理旧设备会话
- [x] 构建和单元测试通过
- [x] 模拟器完成一次真实 R2 上传、列表、下载联调

## 5. 接口设计（如有）

使用 Task04 的后端接口：

| 接口 | 用途 |
|------|------|
| `POST /api/v1/device/register` | 注册设备 |
| `POST /api/v1/device/refresh` | 刷新设备 Token |
| `POST /api/v1/works` | 创建草稿 |
| `POST /api/v1/works/:id/presign` | 获取上传 URL |
| `POST /api/v1/works/:id/finalize` | 发布作品 |
| `GET /api/v1/works` | 云端列表/搜索 |
| `GET /api/v1/works/:id/download` | 获取下载 URL |

## 6. 涉及技能（如有）

| 技能名称 | 用途说明 |
|----------|----------|
| funi-dev-process | 任务、实现索引和验证记录管理 |

## 7. 交付物

- App 云端作品库入口和服务器配置
- 上传、列表、搜索和下载功能
- 下载后本地导入与图片解压
- 最新调试 APK

## 8. 测试要点（供测试参考）

- 服务器地址带/不带 `http://` 都能规范化。
- 地址变更后旧 Token 不会误用。
- 上传包含/不包含图片包都能成功。
- 云端作品封面、作者、标签、大小和下载次数正确。
- 下载后剧本和图片能正常打开。
- 网络错误、Token 过期、服务器地址错误有明确提示。

## 9. 实现索引（任务完成时必填）

### 9.1 最终实现方案

- `CloudApiClient` 统一封装 HTTP 请求、设备注册/刷新、草稿/预签名/发布、列表和下载；同时兼容 R2 预签名 PUT 和七牛表单上传。
- `GalgameSettings` 保存 `cloudBaseUrl/deviceId/token/expiresAt/author`；地址变化时清空会话。
- `GalgameViewModel` 增加云端状态、自动会话、上传打包、单作品下载和本地导入。
- 上传使用 `script.json` + 当前封面 + 可选 `images/` 打包 `pack.zip`；先创建草稿，再上传，最后 finalize。
- 下载校验 script 后创建新本地目录，解压图片，刷新剧本库并选中新作品。
- `CloudScreen` 只提供搜索、列表卡片和下载；服务器配置、连接测试收进右上角齿轮弹窗；上传入口移到首页长按本地作品。
- `BlurredFitImageUrl` 让云端封面继续使用等比 + 模糊背景展示。

### 9.2 实际代码改动

| 类型 | 文件路径 | 关键位置 | 改动说明 |
|------|----------|----------|----------|
| 新增 | `app/src/main/java/com/taverndream/yedu/data/CloudModels.kt` | 数据模型 | 会话、作品、上传、下载模型 |
| 新增 | `app/src/main/java/com/taverndream/yedu/net/CloudApiClient.kt` | `CloudApiClient` | 网络请求、签名上传、下载 |
| 修改 | `app/src/main/java/com/taverndream/yedu/data/GalgameSettings.kt` | 云会话字段 | 地址、设备 Token、作者 |
| 修改 | `app/src/main/java/com/taverndream/yedu/GalgameViewModel.kt` | 云端状态与方法 | 上传、列表、下载、会话 |
| 新增 | `app/src/main/java/com/taverndream/yedu/ui/CloudScreen.kt` | `CloudScreen`、`CloudWorkCard` | 云端作品库 UI |
| 修改 | `app/src/main/java/com/taverndream/yedu/ui/Components.kt` | `BlurredFitImageUrl` | 云端封面展示 |
| 修改 | `app/src/main/java/com/taverndream/yedu/MainActivity.kt`、`ui/LibraryScreen.kt` | Cloud 导航、长按上传入口 | 页面接入 |
| 修改 | `README.md` | 云端作品库说明 | 使用文档 |

### 9.3 接口、数据与外部依赖影响

| 类型 | 名称 | 影响说明 |
|------|------|----------|
| 接口 | Task04 云作品库 API | App 新增调用方 |
| 数据 | `GalgameSettings` 云会话 | 本地 SharedPreferences，不涉及数据库 |
| 数据 | 下载目录 | 新剧本目录 + images 解压，不覆盖已有剧本 |
| 外部依赖 | OkHttp、kotlinx.serialization | 项目已有依赖 |

### 9.4 验证记录与已知风险

| 项目 | 结果 | 说明 |
|------|------|------|
| 编译 | 通过 | `./gradlew :app:compileDebugKotlin` |
| 单元测试 | 通过 | 现有 9 个测试通过 |
| 云端连接 | 通过 | 模拟器连接 `http://10.0.2.2:8787`，显示连接成功 |
| 真实 R2 上传/列表/下载 | 通过 | 临时后端 + 真实 R2 完成上传、列表显示、下载解压；测试 3 个对象已清理 |
| 已知风险 | 待真机验收 | 模拟器网络地址为 `10.0.2.2`；手机需使用局域网 IP 或 HTTPS 域名 |
| 已知风险 | 上传失败会留草稿 | 后端暂无删除接口，失败草稿需要后续清理 |
| 上限 | 单文件 512MB | 图包使用临时文件和流式上传/下载，避免整体读入内存 |

## 10. 修改记录

| 时间 | 修改内容 | 修改原因 | 确认人 |
|------|----------|----------|--------|
| 2026-11-13 | 完成 App 云端作品库、上传、列表和下载 | 用户需求 | 用户 |
| 2026-11-13 | 上传入口移到首页长按，云端页精简为列表/下载，服务器设置移到右上角 | 用户补充需求，详见 Task07 | 用户 |
