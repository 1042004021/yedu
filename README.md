# 夜读（Yedu）

独立的 Galgame / 视觉小说引擎 APK：只导入一个 JSON 剧本，由 APK 解析分支、播放台词，并在后台按 JSON 里的图片清单调用端侧 Stable Diffusion 生图。

## 特性

- JSON 剧本：节点、选项、`next`、`ending`、effects、存档全部由 JSON 驱动。
- 多剧本库：首页以封面卡片展示，每次导入生成独立目录、存档和图片目录，可同时保留多个 GAL。
- 封面优先：导入后先排队生成起始图作为剧本封面，首页卡片自动更新。
- 剧情拓扑：按层级查看分支，点击节点可跳转继续剧情。
- 画廊：展示当前剧本的全部图片；未生成的图片置灰，生成后自动变为可查看；缩略图和全屏预览都保持原始比例。
- 单张重绘：画廊每张图都有编辑入口，可修改提示词并立即重绘该 CG；提示词会写回当前剧本的 `script.json`。
- 云端作品库：首页长按本地作品可上传剧本、封面和可选图片包到 R2；云端页只浏览/下载作品，服务器地址在右上角设置。
- 阅读体验：旁白使用独立旁白区和衬线斜体，角色台词使用角色名标签；切幕时延迟生图队列并降低背景模糊开销。
- 生图顺序：直线剧情按节点顺序；分支先轮流各生成一张，选定分支后优先顺序生成该分支。
- 全屏播放器：固定 250dp 台词框、长文本自动分页、选项面板、结局页；生成图按原始比例完整显示，空白处用同图模糊铺底。
- 本地生图：端侧 SD1.5 / SDXL，不依赖云端 LLM 写剧情。
- 模型多目录：支持公共存储、Download、自定义父目录，不必 adb 推 data。
- 精简设置：模型选择/下载、步数、CFG、画风、负面词、超分、下载源。

## 构建

前置：JDK 17、Android SDK（platform 34、build-tools 34.0.0）。

```bash
export JAVA_HOME=/usr/lib/jvm/java-17-temurin
./gradlew assembleDebug
# 产物：app/build/outputs/apk/debug/app-debug.apk
```

首次构建前必须放一份二进制（仓库不存二进制）：

- `app/src/main/jniLibs/arm64-v8a/libstable_diffusion_core.so`
- `app/src/main/assets/qnnlibs/` 下的 QNN 运行库

说明见 [`doc/native库提取说明.md`](doc/native库提取说明.md)。

## 安装

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

包名：`com.taverndream.yedu`

## 导入剧本

1. 打开「夜读」。
2. 点「导入 JSON 剧本」。
3. 选择剧本 JSON；导入成功后自动进入第一幕。再次导入会新增一个剧本，不会覆盖已有剧本。

示例与格式说明：

- [`doc/示例/Galgame剧本示例.json`](doc/示例/Galgame剧本示例.json)
- [`doc/示例/Galgame-JSON编写说明.md`](doc/示例/Galgame-JSON编写说明.md)

## 模型目录

App 会扫描：

1. `filesDir/models`
2. `Android/data/com.taverndream.yedu/files/models`
3. `/storage/emulated/0/Yedu/models`
4. `/storage/emulated/0/Galgame/models`
5. `/storage/emulated/0/TavernDream/models`
6. `/storage/emulated/0/Download/Yedu/models`
7. `/storage/emulated/0/Download/Galgame/models`
8. `/storage/emulated/0/Download/TavernDream/models`
9. 设置页自定义父目录

把模型完整解压成：

```text
/storage/emulated/0/Download/Yedu/models/realvisxl/
├── unet.bin
├── clip.bin
├── vae_decoder.bin
├── tokenizer.json
└── ...
```

Android 11+ 在设置页点「授权所有文件访问」，再点「刷新目录」。

详细说明见 [`doc/示例/模型目录说明.md`](doc/示例/模型目录说明.md)。

## 云端作品库

首页右上角点「云端」进入云端作品页；默认云端地址为：

```text
https://yedu.104021.xyz
```

点右上角齿轮可改成自定义地址，例如局域网：

```text
http://192.168.1.10:8787
```

或 Cloudflare Tunnel 的 HTTPS 域名。不填协议时，局域网 IP 默认使用 `http://`，域名默认使用 `https://`；也可以手动写完整地址。连接成功后可以：

- 在云端作品页查看、搜索和下载作品，下载后自动解压图片到本地剧本库；
- 回到首页长按本地作品，选择「上传到云端」；封面默认上传，可选是否包含 `images/` 图包；
- 长按菜单还可以导出 JSON、导出完整作品 ZIP（`script.json` + `images/`），或删除本地作品。

图包使用临时文件和文件流上传/下载，单文件上限 512MB，不会整体读入内存。云端作品列表和封面会本地缓存，重新进入云端页时优先使用缓存；需要更新时点搜索/刷新。

后端部署见 [`server/README.md`](server/README.md)。

## 数据位置

每个剧本都有独立的目录：

```text
filesDir/galgame_json/
├── scripts/
│   ├── <剧本目录>/
│   │   ├── script.json
│   │   ├── save.json
│   │   └── images/
│   └── <另一个剧本目录>/
└── last_script.txt
```

旧版本的单个 `galgame_json/script.json` 会在首次启动时自动迁移到 `scripts/` 下，不会丢失已有剧本、存档和图片。

与 Tavern Dream 的包名和数据完全独立，可同时安装。

## Yedu Cloud 云作品库服务（可选）

仓库内附一个 Cloudflare R2 云端作品库后端：

- 用户上传 `script.json`、可选封面和图包到 R2；
- 服务端保存标题、简介、作者、标签等元数据到 SQLite；
- 提供公开作品列表、搜索、详情和下载 URL；
- R2 预签名 URL 不需要绑定自定义域名，也不需要 ICP 备案；
- AK/SK 只放服务端环境变量，不进入 APK。

```bash
cd server
cp .env.example .env
# 填 R2 Account ID、Access Key、Secret、Bucket 和 SERVER_SECRET
npm install
npm start
```

接口、Docker 部署、Cloudflare Tunnel 和 Android 上传流程见 [`server/README.md`](server/README.md)。App 端已接入云端作品库，支持长按本地上传、浏览、搜索和下载导入。

## 许可证

原创代码按 Apache-2.0 许可；local-dream 衍生部分继续按 CC BY-NC 4.0，第三方组件保留各自许可。含 local-dream 部分的构建整体受非商业限制，不能整体宣称可商用。见 `LICENSE`、`NOTICE`、`LICENSES/` 和 `THIRD_PARTY_NOTICES.md`。