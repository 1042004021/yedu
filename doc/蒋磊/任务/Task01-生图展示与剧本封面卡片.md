# 任务 - Task01

## 1. 基本信息

| 字段 | 值 |
|------|-----|
| 任务ID | Task01 |
| 关联需求 | 用户指令：生成图不裁剪、原始比例、空白模糊背景；首页卡片化；导入时优先生成封面 |
| 创建时间 | 2026-11-13 |
| 负责人 | 蒋磊 |
| 预计工时 | 0.3 人天 |
| 优先级 | P1 |

## 2. 任务信息

### 2.1 任务名称

生成图等比展示、首页剧本封面卡片与封面优先生成

### 2.2 任务描述

- 播放页、画廊、首页封面中的生成图不再裁剪，按原始比例完整显示；图片之外的空白用同一张图的模糊背景填充。
- 首页剧本列表改为封面卡片网格，保留继续、从头开始和导入入口。
- 导入剧本后，把起始画面作为封面图加入生成队列首位；封面生成完成后自动更新首页卡片。

### 2.3 拆分方式

按功能点拆分为单任务：图片展示组件、首页卡片、封面字段与生成优先级。

### 2.4 依赖关系

| 前置任务 | 依赖说明 |
|----------|----------|
| 无 | 基于现有夜读多剧本库与生图队列扩展 |

### 2.5 缺失资料

| 编号 | 缺失项 | 说明 | 影响范围 |
|------|--------|------|----------|
| — | 无 | 现有 JSON 结构、图片清单和生成队列已足够 | — |

## 3. 技术方案（如有）

### 3.1 实现思路

- 新增 `BlurredFitImage`：背景层使用 `ContentScale.Crop` + 模糊 + 暗化，前景层使用 `ContentScale.Fit`，保证原图比例完整。
- 播放页背景、画廊缩略图和全屏预览统一使用该组件。
- `GalgameLibraryEntry` 增加封面图 ID/路径；封面取起始节点的显式图片，否则取起始节点第一句图片，最后回退到图片清单第一张。
- 生图队列继续使用“当前画面优先”；导入后当前画面就是起始封面，因此封面优先进入队列，生成后通过 `updateCurrentCover` 更新首页卡片。
- 首页使用两列封面卡片：封面、标题、幕数/图片数、进度、继续和从头按钮。

### 3.2 计划涉及文件清单

| 文件路径 | 操作类型 | 说明 |
|----------|----------|------|
| `app/src/main/java/com/taverndream/yedu/ui/Components.kt` | 修改 | 新增等比图片 + 模糊背景组件 |
| `app/src/main/java/com/taverndream/yedu/ui/PlayerScreen.kt` | 修改 | 播放背景改用等比展示 |
| `app/src/main/java/com/taverndream/yedu/ui/GalleryDialog.kt` | 修改 | 画廊缩略图和预览改用等比展示 |
| `app/src/main/java/com/taverndream/yedu/ui/LibraryScreen.kt` | 修改 | 首页改为封面卡片网格 |
| `app/src/main/java/com/taverndream/yedu/GalgameViewModel.kt` | 修改 | 封面字段、封面识别与生成后更新 |
| `README.md` | 修改 | 同步展示和封面行为说明 |

## 4. 验收标准

- [x] 播放页生成图不裁剪，按原始比例显示，空白处为同图模糊背景
- [x] 画廊缩略图和全屏预览不裁剪
- [x] 首页为封面卡片网格，显示继续/从头操作
- [x] 导入后封面优先进入生图队列，生成后首页卡片自动更新
- [x] 构建通过，现有单元测试通过

## 5. 接口设计（如有）

不适用。本次不新增或修改后端接口。

## 6. 涉及技能（如有）

| 技能名称 | 用途说明 |
|----------|----------|
| funi-dev-process | 任务、实现索引和验证记录管理 |

## 7. 交付物

- 夜读 APK 调试构建
- 等比图片展示组件
- 首页封面卡片
- 封面生成与自动更新逻辑

## 8. 测试要点（供测试参考）

- 竖图、横图、方图在播放页和画廊中都不能被裁切。
- 图片比例与屏幕不一致时，空白区域应显示模糊背景而不是纯黑边。
- 导入新剧本后，首页出现新卡片；封面图应优先生成并自动出现。
- 多个 GAL 的封面、存档和继续/从头操作互不影响。

## 9. 实现索引（任务完成时必填）

### 9.1 最终实现方案

- 新增 `BlurredFitImage` 统一图片展示：模糊背景层填充容器，前景图用 `ContentScale.Fit` 保持原始比例；Android 12 以下不做模糊，仅保留暗化背景。
- 播放页 `PlayerBackground`、画廊 `GalleryCard` 和预览、首页封面卡片全部使用该组件。
- 首页从纵向大卡片改为两列封面卡片；每张卡显示封面、标题、幕数/图片数、生成进度、继续和从头操作。
- 剧本库条目新增 `coverPath`，封面按“起始节点图片 → 起始节点第一句图片 → 图片清单第一张”选择。
- 导入时当前画面即封面，生图规划器把当前图片放队列首位；生成完成后 `updateCurrentCover` 更新首页封面。

### 9.2 实际代码改动

| 类型 | 文件路径 | 关键位置 | 改动说明 |
|------|----------|----------|----------|
| 修改 | `app/src/main/java/com/taverndream/yedu/ui/Components.kt` | `BlurredFitImage` | 新增等比前景和模糊背景展示组件 |
| 修改 | `app/src/main/java/com/taverndream/yedu/ui/PlayerScreen.kt` | `PlayerBackground` | 播放页背景改用 `BlurredFitImage` |
| 修改 | `app/src/main/java/com/taverndream/yedu/ui/GalleryDialog.kt` | `GalleryCard`、预览 `Dialog` | 缩略图和全屏预览保持原始比例 |
| 修改 | `app/src/main/java/com/taverndream/yedu/ui/LibraryScreen.kt` | `ScriptLibraryList`、`ScriptCoverCard`、`MiniAction` | 首页改为两列封面卡片 |
| 修改 | `app/src/main/java/com/taverndream/yedu/GalgameViewModel.kt` | `GalgameLibraryEntry`、`coverImageIdOf`、`imageFile`、`updateCurrentCover`、`startImageWorker` | 增加封面路径、识别封面并按生成进度更新 |
| 修改 | `README.md` | 特性说明 | 同步图片展示、封面卡片和封面优先生成说明 |

### 9.3 接口、数据与外部依赖影响

| 类型 | 名称 | 影响说明 |
|------|------|----------|
| 接口 | 无 | 不涉及接口 |
| 数据 | `GalgameLibraryEntry.coverPath` | 内存态字段，不涉及数据库迁移；由图片文件推导 |
| 外部依赖 | Android RenderEffect / Compose blur | Android 12+ 使用真实模糊；更低版本退化为暗化背景 |

### 9.4 验证记录与已知风险

| 项目 | 结果 | 说明 |
|------|------|------|
| 自动化测试 | 通过 | `./gradlew :app:testDebugUnitTest`，9 个测试通过 |
| 构建与静态检查 | 通过 | `./gradlew :app:assembleDebug` |
| 模拟器验证 | 通过 | 已验证两列封面卡片、已有封面展示、画廊灰图/已生成图、播放页等比背景 |
| 已知风险或待验收项 | 待真机确认 | 当前模拟器未配置 SD 模型，未实际观察封面生成完成后的自动刷新；Android 12 以下无真实模糊 |

## 10. 修改记录

| 时间 | 修改内容 | 修改原因 | 确认人 |
|------|----------|----------|--------|
| 2026-11-13 | 完成等比展示、首页封面卡片和封面优先生成 | 用户需求 | 用户 |
