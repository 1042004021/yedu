# Qualcomm QAIRT / QNN 2.39.0.250925

QNN 为 Qualcomm Technologies, Inc. 的专有软件，不受本项目 CC BY-NC 4.0 或 MNN Apache-2.0 许可覆盖。

## 官方来源与条款核对

- SDK：https://softwarecenter.qualcomm.com/api/download/software/sdks/Qualcomm_AI_Runtime_Community/All/2.39.0.250925/v2.39.0.250925.zip
- 产品入口：https://www.qualcomm.com/developer/software/qualcomm-ai-engine-direct-sdk
- 条款原件：SDK 内 `qairt/2.39.0.250925/LICENSE.pdf`（TERMS AND CONDITIONS OF USE / AI Stack License）。
- 原件 SHA-256：`ec1dccfdcba5c6e64126e84199b8362bf4999107bfa567ebe831dbb4c461692b`。
- 核对日期：2026-11-02。

原 PDF 页脚带 QTI Proprietary/Confidential 标记，因此不在公开仓库重新托管全文；请从官方取得并阅读完整协议。本摘要不替代正式协议，不代表 Qualcomm 认可本项目，也不构成法律意见。

## 需要遵守的主要条件

- 第 1(iv) 条：允许仅以目标代码形式、作为应用组成部分分发和再许可；不允许单独分发 SDK。
- 第 2(a)、2(b) 条：不得逆向等，不得移除或更改版权、专有信息和权利声明。
- 第 2(c)、2(d)、2(e) 条：使用者负责输入、输出及活动的合法性，遵守第三方条款与禁止用途限制，注意高风险用途警示。
- 第 10(f) 条：遵守适用出口管制及制裁规定；公开下载不自动免除这些义务。
- 第 10(h) 条：不得移除或更改 NOTICE，遵守其中第三方许可。原文 `NOTICE.txt`、`QNN_NOTICE.txt` 随此目录保留。
- 第 3(b) 条：该许可不授予专利等其他权利；第 8 条规定终止条件。
- 协议规定下载及／或使用表示接受。开发者与分发者须自行阅读并确认自身符合协议，不以本摘要替代接受行为或法律判断。

`NOTICE.txt` 与 `QNN_NOTICE.txt` 均原样提取自上述官方 SDK，SHA-256 同为：
`c8a22fa8b9ed3e9266c4a3366965474cef5cb147ee2a1f33e488bbb24a071c09`。

## 当前库来源核对边界

将本地 `assets/qnnlibs/` 的 20 个库与官方 ZIP 中央目录比对：19 个文件的大小和 CRC32 匹配（非 SHA-256 全量验证）；`libQnnHtpV81Skel.so` 不匹配。

该文件内部版本字符串仍为 `v2.39.0.250925215840_163802`，但不能仅凭版本字符串认定来源完全相同。

| 文件 | 大小 | SHA-256 |
|---|---:|---|
| 本地 V81 Skel | 10248204 | `716e073e09922922bf977dee2ae0e2342f9c6c35ee54d64cba36bb1490172b3d` |
| 官方 `lib/hexagon-v81/unsigned/` V81 Skel | 10293348 | `218d8902920183449c0a608de12e226ecac9ddad180333610ac103a04a7ef023` |

没有擅自替换已经可用的本地库。公开 APK 前仍需查清差异或使用官方库并完成对应真机回归。
