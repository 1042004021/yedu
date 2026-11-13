# 任务 - Task03

## 1. 基本信息

| 字段 | 值 |
|------|-----|
| 任务ID | Task03 |
| 关联需求 | 用户指令：家用服务器部署七牛云签名后端，仅下发上传 Token / 下载签名 URL |
| 创建时间 | 2026-11-13 |
| 负责人 | 蒋磊 |
| 预计工时 | 0.5 人天 |
| 优先级 | P1 |

## 2. 任务信息

### 2.1 任务名称

七牛云上传 Token 与私有下载签名服务

### 2.2 任务描述

- 在夜读仓库新增独立 Node.js 签名服务，部署到用户家用服务器。
- App 注册设备后获得设备 Token，再请求上传 Token 和私有下载签名 URL。
- 服务端只签名，不代理文件；文件直接上传/下载七牛。
- AK/SK 只通过环境变量注入，不写入 APK 或仓库。
- 提供 Docker、Docker Compose、HTTPS 反代示例和接口说明。

### 2.3 拆分方式

按功能点拆分为单任务：设备身份、七牛签名、服务端安全校验、部署与测试。

### 2.4 依赖关系

| 前置任务 | 依赖说明 |
|----------|----------|
| 无 | 独立后端服务，后续由 App 云备份功能接入 |

### 2.5 缺失资料

| 编号 | 缺失项 | 说明 | 影响范围 |
|------|--------|------|----------|
| M001 | 七牛 Bucket、域名、AK/SK | 用户实际环境值；代码用环境变量占位 | 真实上传/下载联调 |
| M003 | 生产下载域名与备案 | 七牛测试域名仅 30 天且不能生产；正式使用必须绑定自定义域名（国内通常需 ICP 备案） | 正式发布下载链路 |
| M002 | 服务器域名与 HTTPS | 手机正式访问需要 HTTPS；已提供 Caddy 示例 | App 接入地址 |

## 3. 技术方案（如有）

### 3.1 实现思路

- 使用 Node.js 20+、Express、七牛官方 Node SDK。
- `POST /api/v1/device/register` 由服务端生成随机 deviceId 和设备 Token，避免客户端领取其他设备目录。
- `POST /api/v1/device/refresh` 允许用本服务签发的旧 Token 刷新。
- 所有签名接口要求 `Authorization: Bearer <deviceToken>`。
- key 必须位于 `yedu/<deviceId>/`，限制扩展名为 png/jpg/jpeg/webp/json，并拒绝路径穿越。
- 上传 Token 使用 `bucket:key` scope，限制有效期、文件大小和 MIME。
- 私有空间下载返回带过期时间的签名 URL；公开空间直接返回域名 URL。
- 内存限流按 IP/设备限制请求频率；部署为单实例时足够。

### 3.2 计划涉及文件清单

| 文件路径 | 操作类型 | 说明 |
|----------|----------|------|
| `server/package.json`、`server/package-lock.json` | 新增 | Node 依赖与启动/测试脚本 |
| `server/src/config.js` | 新增 | 环境变量读取与校验 |
| `server/src/security.js` | 新增 | 设备 Token、key 校验 |
| `server/src/rateLimit.js` | 新增 | 内存限流 |
| `server/src/qiniuService.js` | 新增 | 上传 Token、私有下载 URL |
| `server/src/app.js` | 新增 | HTTP 接口 |
| `server/src/server.js` | 新增 | 启动与优雅退出 |
| `server/test/app.test.js` | 新增 | 接口测试 |
| `server/Dockerfile`、`server/docker-compose.yml` | 新增 | 容器部署 |
| `server/.env.example` | 新增 | 环境变量模板 |
| `server/deploy/Caddyfile.example` | 新增 | HTTPS 反代示例 |
| `server/README.md` | 新增 | 部署和接口说明 |
| `README.md` | 修改 | 根说明增加后端入口 |

## 4. 验收标准

- [x] 设备注册、Token 刷新接口可用
- [x] 上传 Token 可签发，scope 限定到单文件 key
- [x] 私有空间下载 URL 带有效过期时间
- [x] 非当前设备目录、非法扩展名、路径穿越被拒绝
- [x] 未认证请求返回 401
- [x] Node 测试通过
- [x] Docker 镜像构建和健康检查通过

## 5. 接口设计（如有）

| 接口名 | 方法 | 路径 | 说明 |
|--------|------|------|------|
| 健康检查 | GET | `/health` | 服务状态 |
| 注册设备 | POST | `/api/v1/device/register` | 返回 deviceId、设备 Token、key 前缀 |
| 刷新设备 Token | POST | `/api/v1/device/refresh` | 旧 Token 换新 Token |
| 当前设备 | GET | `/api/v1/me` | 返回 deviceId 和 key 前缀 |
| 上传 Token | POST | `/api/v1/qiniu/upload-token` | 请求体 `{key}` |
| 下载 URL | GET | `/api/v1/qiniu/download-url?key=...` | 返回公开 URL 或私有签名 URL |

## 6. 涉及技能（如有）

| 技能名称 | 用途说明 |
|----------|----------|
| funi-dev-process | 任务、实现索引和验证记录管理 |

## 7. 交付物

- `server/` 下的七牛签名服务
- Docker / Compose 部署文件
- HTTPS 反代示例
- 接口与 Android 上传说明
- 6 个后端接口测试

## 8. 测试要点（供测试参考）

- 设备注册返回随机 deviceId，不能用客户端指定 ID 领取其他目录。
- 上传 Token 只能用于当前设备前缀内的单个 key。
- 私有下载 URL 的 `e` 参数是未来时间戳，过期后不能下载。
- 非法扩展名、`..`、反斜杠、空路径段被拒绝。
- 错误或过期设备 Token 无法调用签名接口。
- 容器健康检查可访问 `/health`。

## 9. 实现索引（任务完成时必填）

### 9.1 最终实现方案

- 新增 `server/` Node.js 服务，Express 提供设备、Token 和七牛签名接口。
- `config.js` 从环境变量读取七牛 AK/SK、Bucket、域名、私有标记、有效期、上传大小和限流配置，并校验必填项。
- `security.js` 使用 HMAC-SHA256 生成设备 Token；`POST /api/v1/device/register` 由服务端生成随机 deviceId，刷新接口允许本服务签发的旧 Token 换新。
- `security.js` 的 `validateObjectKey` 限定 `yedu/<deviceId>/` 前缀、扩展名和路径段，阻断路径穿越。
- `qiniuService.js` 用七牛 Node SDK 生成上传 Token；私有下载 URL 传入绝对过期时间，公开空间直接拼接域名。
- `app.js` 提供注册、刷新、当前设备、上传 Token、下载 URL 和健康检查，并用内存限流保护接口。
- Dockerfile 使用 `node:22-alpine`、非 root 用户和健康检查；Compose 通过 `.env` 注入密钥。
- 根 README 增加后端入口，`server/README.md` 提供部署、接口、key 规则和 Android 直传说明。

### 9.2 实际代码改动

| 类型 | 文件路径 | 关键位置 | 改动说明 |
|------|----------|----------|----------|
| 新增 | `server/src/config.js` | `loadConfig` | 环境变量读取与校验 |
| 新增 | `server/src/security.js` | `createDeviceToken`、`decodeDeviceToken`、`validateObjectKey` | 设备 Token 与 key 安全校验 |
| 新增 | `server/src/rateLimit.js` | `createRateLimiter` | 单实例内存限流 |
| 新增 | `server/src/qiniuService.js` | `uploadToken`、`downloadUrl` | 七牛上传 Token 与私有下载签名 |
| 新增 | `server/src/app.js` | 设备、签名、健康检查路由 | HTTP API |
| 新增 | `server/src/server.js` | 启动、SIGTERM/SIGINT | 服务入口 |
| 新增 | `server/test/app.test.js` | 6 个接口测试 | 注册、刷新、签名、越权、未认证、扩展名 |
| 新增 | `server/Dockerfile`、`server/docker-compose.yml` | 容器部署 | 生产启动 |
| 新增 | `server/.env.example`、`server/deploy/Caddyfile.example` | 配置与 HTTPS | 部署模板 |
| 新增 | `server/README.md` | 部署与接口文档 | 使用说明 |
| 修改 | `README.md` | 云备份签名服务 | 根说明入口 |

### 9.3 接口、数据与外部依赖影响

| 类型 | 名称 | 影响说明 |
|------|------|----------|
| 接口 | 设备注册/刷新、上传 Token、下载 URL | 新增独立后端接口，App 尚未接入 |
| 数据 | `yedu/<deviceId>/...` | 七牛对象 key 约定，不涉及数据库 |
| 外部依赖 | 七牛 Node SDK、Express | 仅服务端依赖，不进入 APK |
| 外部依赖 | 七牛 Bucket / 域名 / AK/SK | 需用户环境变量注入，仓库不保存 |

### 9.4 验证记录与已知风险

| 项目 | 结果 | 说明 |
|------|------|------|
| Node 测试 | 通过 | `npm test`，6 个测试通过 |
| Docker 构建 | 通过 | `docker build -t yedu-signer:test server` |
| 容器运行 | 通过 | `/health` 和 `/api/v1/device/register` 实际请求成功 |
| 签名结构验证 | 通过 | 上传 Token policy 的 scope、deadline、大小、MIME 正确；私有 URL `e` 为未来时间戳 |
| 已知风险或待验收项 | 待真实环境联调 | 尚未用真实七牛 AK/SK、Bucket 和域名上传/下载；App 端尚未接入 |
| 已知风险或待验收项 | 生产域名未绑定 | 七牛测试域名仅 30 天且不能用于生产；正式环境需绑定自定义域名并完成备案 |
| 已知风险或待验收项 | 轻量设备身份 | 当前不是账号体系，设备 Token 不可撤销；正式多用户建议加登录、配额和吊销机制 |

## 10. 修改记录

| 时间 | 修改内容 | 修改原因 | 确认人 |
|------|----------|----------|--------|
| 2026-11-13 | 完成七牛签名服务、Docker 部署和接口测试 | 用户需求 | 用户 |
