# 夜读（Yedu）

独立的 Galgame / 视觉小说引擎 APK：只导入一个 JSON 剧本，由 APK 解析分支、播放台词，并在后台按 JSON 里的图片清单调用端侧 Stable Diffusion 生图。

## 特性

- JSON 剧本：节点、选项、`next`、`ending`、effects、存档全部由 JSON 驱动。
- 多剧本库：每次导入生成独立目录、存档和图片目录，可同时保留多个 GAL。
- 剧情拓扑：按层级查看分支，点击节点可跳转继续剧情。
- 画廊：展示当前剧本的全部图片；未生成的图片置灰，生成后自动变为可查看。
- 生图顺序：直线剧情按节点顺序；分支先轮流各生成一张，选定分支后优先顺序生成该分支。
- 全屏播放器：固定 250dp 台词框、长文本自动分页、选项面板、结局页。
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

## 许可证

非商业源码项目。原创部分按 CC BY-NC 4.0；第三方组件保留各自许可。见 `LICENSE`、`LICENSES/` 和 `THIRD_PARTY_NOTICES.md`。