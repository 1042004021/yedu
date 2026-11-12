# 第三方来源与许可声明

本清单记录已识别的主要来源，不是全部传递依赖的法律合规认证。
本项目并非以下项目的官方客户端，也不表示得到原作者认可或背书。

## 1. local-dream

- 作者／项目：xororz / local-dream
- 来源：https://github.com/xororz/local-dream
- 本项目使用版本：v2.4.3
- 对应许可证：https://github.com/xororz/local-dream/blob/v2.4.3/LICENSE
- 许可：CC BY-NC 4.0；完整文本见 `LICENSES/CC-BY-NC-4.0-local-dream.txt`。
- 已确认的改编：`app/src/main/java/com/taverndream/sd/SdBackendService.kt`，源于上游 BackendService，适配本项目服务、启动和状态管理。
- 关联集成：`sd/` 中的 HTTP 客户端、模型目录与下载、运行库加载及超分调用与上游内核协议衔接；这些文件的逐段来源仍应在后续审查中核实，不宣称全部独立原创。
- 本项目改动：接入 Compose 角色聊天、后台生成后插图、SDXL 模型参数与低内存启动等。改编不代表原作者认可。
- 生图可执行文件 `libstable_diffusion_core.so` 未纳入 Git。排除二进制并不消除改编源码本身的署名和许可义务。

## 2. MNN（v0.7 起不再分发）

- 作者／项目：Alibaba Group / MNN
- 来源：https://github.com/alibaba/MNN
- 许可：Apache License 2.0；完整文本见 `LICENSES/Apache-2.0-MNN.txt`。
- 说明：v0.7（Task13，2026-11-03）已彻底移除本地 LLM；`app/src/main/cpp/mnn/` 与 `libMNN.so` 均已删除，当前 APK 不再包含 MNN。本节仅保留历史记录。

## 2.1 llama.cpp / ggml（v0.7 起不再分发）

- 作者／项目：The ggml authors / llama.cpp
- 来源：https://github.com/ggml-org/llama.cpp
- 历史版本：**v0.8.0**，tag 对应提交 `6ebec9ad2a90f270f0db4a8669c18511d2e82995`。
- 许可：MIT；完整文本见 `LICENSES/MIT-llama.cpp.txt`。
- 说明：v0.7（Task13，2026-11-03）已彻底移除本地 LLM；`app/src/main/cpp/llama.cpp/`、JNI 封装和相关 native 库均已删除，当前 APK 不再包含 llama.cpp / ggml。本节仅保留历史记录。

## 3. SillyTavern：功能参考与角色卡格式兼容

- 来源：https://github.com/SillyTavern/SillyTavern
- 上游许可：AGPL-3.0；https://github.com/SillyTavern/SillyTavern/blob/release/LICENSE
- 本项目使用 Kotlin/Compose 实现角色卡解析与对话，源码注释记录了对角色卡格式和 Chat Completion 结构的参考。
- 当前未确认有直接复制的 SillyTavern 实现；不能据此作出“完全没有衍生代码”的法律保证。若后续确认存在复制或改编，应保留原声明并评估 AGPL 与其他许可的兼容性，再决定如何发布。
- 仅兼容数据格式不等于获得角色卡内容和插画的授权。

## 4. Gradle 与 Maven 依赖

- Gradle Wrapper：Apache-2.0，https://github.com/gradle/gradle/blob/master/LICENSE
- AndroidX / Compose、OkHttp、kotlinx.coroutines、kotlinx.serialization、Coil：主要直接依赖采用 Apache-2.0；实际版本以 `app/build.gradle.kts` 为准。
- Kotlin 与工具链：遵守各自发行版许可。
- 此处不替代实际构建产物的依赖清单。公开分发 APK 前，应检查实际解析的依赖、传递依赖及随附 NOTICE。

## 5. 不在上述项目许可范围内的内容

- Qualcomm QNN 运行库：已读取官方 QAIRT 2.39.0.250925 的 AI Stack License。第 1(iv) 条允许目标代码作为应用组成部分分发和再许可，不允许单独分发 SDK；必须同时遵守其余条件。见 `LICENSES/Qualcomm-QAIRT-2.39.0.250925/README.md`，随附 NOTICE 保留原文。
- 模型权重（包括 Qwen、SD、Illustrious 和超分模型）：转换文件与基础模型可能有不同条款，需逐个核对；下载地址不是授权证明。
- 角色卡、头像、聊天记录及生成图片：不随本仓库授权；需另行考虑内容权利和隐私。
- Android NDK 的 C++ 运行库等打包组件：适用对应工具链的许可证和第三方声明。

## 6. 源码公开与 APK 分发的边界

本仓库公开源码不表示已经审查通过所有 APK 分发义务。
当前不在 Git 中保存 native 运行库、APK、模型、个人角色卡或日志。
QNN 本身并非一律禁止随 APK 分发。当前仍有一个 V81 Skel 库与官方包大小及 CRC32 不匹配，且其他 native 组件及传递依赖的分发核对未全部完成，因此尚未发布 APK Release 附件。
构建时将本声明、项目许可及 `LICENSES/` 复制到 APK 的 `assets/licenses/`；这不将 QNN 重新许可为 CC BY-NC 或 Apache-2.0。

核对日期：2026-11-02。本文是项目来源记录，不构成法律意见。
