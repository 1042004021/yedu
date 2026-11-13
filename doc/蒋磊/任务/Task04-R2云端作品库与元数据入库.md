# 任务 - Task04

## 1. 基本信息

| 字段 | 值 |
|------|-----|
| 任务ID | Task04 |
| 关联需求 | 用户指令：用户上传本地 GAL 剧本和可选图包到 Cloudflare，回传标题封面等元数据入库，并提供云端作品列表与下载 |
| 创建时间 | 2026-11-13 |
| 负责人 | 蒋磊 |
| 预计工时 | 0.8 人天 |
| 优先级 | P1 |

## 2. 任务信息

### 2.1 任务名称

Cloudflare R2 云端作品库、元数据入库与下载

### 2.2 任务描述

- 默认存储驱动切换为 Cloudflare R2，保留七牛驱动可选。
- 用户注册设备后创建作品草稿，获取 R2 预签名 PUT URL，直传 `script.json`、可选封面和可选 `pack.zip`。
- 文件上传完成后调用 finalize，把标题、简介、作者、标签、幕数、图片数和文件大小写入 SQLite。
- 提供公开作品列表、搜索、详情和下载地址；草稿不进入公开列表。
- 服务端只签名和存元数据，不代理文件。

### 2.3 拆分方式

按功能点拆分为单任务：R2 驱动、作品数据模型、上传流程、公开列表/下载、部署与测试。

### 2.4 依赖关系

| 前置任务 | 依赖说明 |
|----------|----------|
| Task03 | 复用设备身份、key 校验和限流 |

### 2.5 缺失资料

| 编号 | 缺失项 | 说明 | 影响范围 |
|------|--------|------|----------|
| M001 | 真实 R2 Account ID / API Token / Bucket | 代码用环境变量占位，测试使用假凭证 | 真实上传下载联调 |
| M002 | App 端上传界面和打包逻辑 | 本次仅后端，App 尚未接入 | 端到端用户流程 |
| M003 | 内容审核和举报策略 | 公开 UGC 上线前需要产品策略 | 正式公开发布 |

## 3. 技术方案（如有）

### 3.1 实现思路

- `STORAGE_DRIVER=s3|qiniu` 选择驱动；默认 `s3`，即 R2 兼容 S3 协议。
- R2 使用 AWS SDK v3 生成预签名 PUT/GET URL；预签名不需要绑定自定义域名，也不需要 ICP 备案。
- 设备注册后由服务端生成 deviceId，作品 key 固定为 `yedu/<deviceId>/works/<workId>/` 下。
- 作品先建草稿；presign 返回每个文件的 PUT URL；App 上传后 finalize。
- finalize 用 `HeadObject` 检查文件是否存在并取得大小，再写入元数据并发布。
- SQLite 存作品元数据和下载次数；公开列表按发布时间倒序，支持标题/作者/简介搜索。
- 封面和下载文件统一由服务端签发短期 GET URL。

### 3.2 计划涉及文件清单

| 文件路径 | 操作类型 | 说明 |
|----------|----------|------|
| `server/src/config.js` | 修改 | 增加 S3/R2 配置和 DB 路径 |
| `server/src/storageService.js` | 新增 | R2/七牛统一存储驱动、预签名、HEAD |
| `server/src/db.js` | 新增 | SQLite 初始化 |
| `server/src/workRepository.js` | 新增 | 作品草稿、发布、列表、下载计数 |
| `server/src/app.js` | 修改 | 作品接口、公开列表和下载 |
| `server/src/server.js` | 修改 | 注入数据库和作品仓储 |
| `server/test/api.test.js` | 新增 | 完整 API 流程测试 |
| `server/test/storage.test.js` | 新增 | R2 预签名 URL 测试 |
| `server/Dockerfile`、`server/docker-compose.yml` | 修改 | 数据卷、DB 路径、R2 默认配置 |
| `server/.env.example`、`server/README.md` | 修改 | R2 配置和接口文档 |
| `README.md` | 修改 | 根说明入口 |

## 4. 验收标准

- [x] 默认使用 R2 预签名上传/下载，不强制自定义域名
- [x] 设备可创建草稿、获取 script/cover/pack 上传 URL
- [x] 文件上传完成并 finalize 后元数据入库、状态变为 published
- [x] 公开列表只显示 published 作品，支持分页和关键词搜索
- [x] 详情和下载接口返回封面、剧本、图包签名 URL
- [x] 其他设备不能上传/发布别人的作品
- [x] SQLite 元数据持久化，Docker 使用命名卷
- [x] 构建、接口测试和 Docker 运行通过

## 5. 接口设计（如有）

| 接口名 | 方法 | 路径 | 说明 |
|--------|------|------|------|
| 注册设备 | POST | `/api/v1/device/register` | 返回 deviceId 和设备 Token |
| 刷新设备 Token | POST | `/api/v1/device/refresh` | 旧 Token 换新 Token |
| 创建作品草稿 | POST | `/api/v1/works` | 标题、简介、作者、标签、幕数、图片数 |
| 获取上传 URL | POST | `/api/v1/works/:id/presign` | script 必传，cover/pack 可选 |
| 发布作品 | POST | `/api/v1/works/:id/finalize` | 校验文件并写入元数据 |
| 作品列表 | GET | `/api/v1/works` | 分页、`q` 搜索 |
| 作品详情 | GET | `/api/v1/works/:id` | 只返回已发布作品 |
| 下载地址 | GET | `/api/v1/works/:id/download` | 返回 script/pack/cover 签名 URL |
| 健康检查 | GET | `/health` | 驱动和服务状态 |

## 6. 涉及技能（如有）

| 技能名称 | 用途说明 |
|----------|----------|
| funi-dev-process | 任务、实现索引和验证记录管理 |

## 7. 交付物

- Cloudflare R2 兼容的签名与作品库后端
- SQLite 元数据库
- Docker + 数据卷部署
- 7 个后端接口测试
- R2 配置和接口说明

## 8. 测试要点（供测试参考）

- 未上传脚本时不能发布。
- 只上传 script、不上传 cover/pack 时可以发布。
- 上传 cover 或 pack 后，列表和下载返回对应签名 URL。
- 不同设备的作品目录和发布权限隔离。
- 草稿不出现在公开列表，发布后才出现。
- 搜索标题/作者/简介、分页和下载次数正确。
- 预签名 URL 过期后重新请求即可。

## 9. 实现索引（任务完成时必填）

### 9.1 最终实现方案

- `config.js` 支持 `STORAGE_DRIVER=s3|qiniu`；默认 S3 兼容 R2，读取 R2 Account ID、Access Key、Secret、Bucket、Region、Endpoint 和 DB 路径。
- `storageService.js` 统一封装上传/下载/HEAD：R2 用 AWS SDK v3 生成预签名 PUT/GET，七牛返回表单上传 Token 和私有下载 URL。
- `db.js` 使用 Node 22 内置 `node:sqlite` 创建 `works` 表和索引；Docker 使用 `/app/data` 命名卷。
- `workRepository.js` 负责草稿创建、文件 key 更新、发布、公开列表、搜索、下载计数。
- `app.js` 新增作品创建、presign、finalize、列表、详情、下载接口；设备归属校验和公开限流。
- 上传 key 固定为 `script.json`、`cover.<ext>`、`pack.zip`，避免客户端自选路径。
- finalize 用 R2 `HeadObject` 校验文件存在并计算总大小，成功后写入 published 状态。
- 公开列表和下载返回短期签名 URL；封面在列表中直接签发。
- Dockerfile 创建可写的 `/app/data`，Compose 挂载 `yedu-data` 卷；R2 不需要自定义域名或备案。

### 9.2 实际代码改动

| 类型 | 文件路径 | 关键位置 | 改动说明 |
|------|----------|----------|----------|
| 修改 | `server/src/config.js` | `loadConfig` | 增加 s3/qiniu 驱动分支、R2 配置和 DB 路径 |
| 新增 | `server/src/storageService.js` | `createStorageService`、`createUpload`、`createDownload`、`stat` | R2 预签名和七牛兼容 |
| 新增 | `server/src/db.js` | `createDatabase` | SQLite works 表和索引 |
| 新增 | `server/src/workRepository.js` | `createWork`、`updateDraftFiles`、`finalizeWork`、`listPublished` | 作品元数据仓储 |
| 修改 | `server/src/app.js` | 作品路由、`publicWork`、`metadataFromBody`、`fileSpec` | 上传、发布、列表、下载接口 |
| 修改 | `server/src/server.js` | 数据库注入、优雅关闭 | 启动流程 |
| 新增 | `server/test/api.test.js` | 6 个作品流程测试 | 草稿、发布、列表、下载、权限、校验 |
| 新增 | `server/test/storage.test.js` | R2 预签名测试 | URL、签名、头、过期时间 |
| 修改 | `server/Dockerfile`、`server/docker-compose.yml` | 数据卷、DB_PATH、健康检查 | 容器部署 |
| 修改 | `server/.env.example`、`server/README.md`、根 `README.md` | R2 配置、接口、部署 | 使用说明 |

### 9.3 接口、数据与外部依赖影响

| 类型 | 名称 | 影响说明 |
|------|------|----------|
| 接口 | 作品创建/presign/finalize/list/detail/download | 新增云作品库 API |
| 数据 | SQLite `works` 表 | 新增元数据、文件 key、大小、状态、下载次数 |
| 存储 | R2 对象 key | `yedu/<deviceId>/works/<workId>/script.json`、`cover.*`、`pack.zip` |
| 外部依赖 | `@aws-sdk/client-s3`、`@aws-sdk/s3-request-presigner` | R2 预签名 |
| 外部依赖 | Node 22.5+ `node:sqlite` | 元数据库，无额外原生依赖 |
| 外部依赖 | Cloudflare R2 | 不要求自定义域名/ICP；需要 API Token 和 Bucket |

### 9.4 验证记录与已知风险

| 项目 | 结果 | 说明 |
|------|------|------|
| 自动化测试 | 通过 | `npm test`，7 个测试通过 |
| 主机启动烟测 | 通过 | 健康检查、空列表、设备注册、创建草稿实际请求成功 |
| Docker 构建 | 通过 | `docker build -t yedu-r2-signer:test server` |
| Docker 运行 | 通过 | 容器内 `/health` 和空列表请求成功 |
| 已知风险或待验收项 | 待真实 R2 联调 | 未使用真实 R2 Account ID/Token/Bucket 上传和下载 |
| 已知风险或待验收项 | App 尚未接入 | Android 上传界面、打包图包和云端列表页未实现 |
| 已知风险或待验收项 | 公开 UGC 治理 | 暂无内容审核、举报、删除、账号体系；公开发布前需补 |
| 已知风险或待验收项 | SQLite 单实例 | 适合家用单实例；多实例部署需换共享数据库 |

## 10. 修改记录

| 时间 | 修改内容 | 修改原因 | 确认人 |
|------|----------|----------|--------|
| 2026-11-13 | 完成 R2 云端作品库、元数据入库和下载接口 | 用户需求 | 用户 |
