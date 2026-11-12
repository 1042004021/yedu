# Galgame JSON 编写说明

对应版本：`0.10.0`（versionCode 29）  
适用范围：Galgame 页面导入的单文件 JSON 剧本。APK 只解析 JSON、运行分支并在后台生图，不再依赖世界书或 LLM 实时写剧情。

## 1. 最小结构

```json
{
  "format": "taverndream.galgame/1",
  "id": "my-story",
  "title": "我的剧本",
  "start": "n1",
  "nodes": [
    {
      "id": "n1",
      "title": "第一幕",
      "image": "scene_1",
      "lines": [
        {
          "speaker": "苏糖",
          "text": "你来了。"
        }
      ],
      "next": "n2"
    },
    {
      "id": "n2",
      "title": "结局",
      "ending": true,
      "image": "scene_2",
      "lines": [
        {
          "speaker": "苏糖",
          "text": "明天见。"
        }
      ]
    }
  ],
  "images": [
    {
      "id": "scene_1",
      "prompt": "旧公寓走廊，暖黄壁灯，雨夜，视觉小说场景 CG"
    },
    {
      "id": "scene_2",
      "prompt": "公寓客厅，暖色灯光，两人对视，视觉小说结局 CG"
    }
  ]
}
```

- `start` 必须是真实存在的节点 ID。
- `nodes` 是剧情节点列表。
- `images` 必须放在 JSON 最后，统一写所有图片提示词。
- `format` 当前固定写 `taverndream.galgame/1`。

## 2. 节点字段

| 字段 | 必填 | 说明 |
|------|------|------|
| `id` | 是 | 节点 ID，整个 JSON 内唯一 |
| `title` | 否 | 幕标题 |
| `location` | 否 | 地点，显示在顶部状态区 |
| `time` | 否 | 时间，显示在顶部状态区 |
| `narration` | 否 | 旁白，显示在台词列表最前 |
| `image` | 否 | 本节点默认图片 ID；不写则沿用上一张 |
| `lines` | 否 | 台词列表 |
| `choices` | 否 | 选项列表；没有选项时走 `next` |
| `next` | 否 | 点击“继续剧情”后跳转的节点 ID |
| `ending` | 否 | `true` 表示结局节点 |
| `effects` | 否 | 进入节点时应用一次的效果 |

非结局节点必须至少有 `next` 或 `choices` 之一，否则校验会报“死路”。

## 3. 台词字段

```json
{
  "speaker": "苏糖",
  "text": "你站门口多久了？",
  "expression": "警惕",
  "pose": "半掩门",
  "image": "close_1"
}
```

| 字段 | 必填 | 说明 |
|------|------|------|
| `speaker` | 是 | 说话角色名 |
| `text` | 是 | 台词文本 |
| `expression` | 否 | 表情提示，可用于以后扩展 |
| `pose` | 否 | 动作提示，可用于以后扩展 |
| `image` | 否 | 这一句显示时切换到的图片 ID |

## 4. 图片规则（重点）

1. `node.image` 指定本节点默认图片。
2. `line.image` 指定某一句台词显示时切换的图片。
3. 如果 `node.image` 没写：沿用当前剧情路径上**上一张已经显示的图**。
4. 如果 `line.image` 没写：使用本节点 `image`；本节点也没写，就继续沿用上一张。
5. `start` 节点至少要指定一张图：节点 `image` 或某句 `line.image`。
6. 图片 ID 必须出现在末尾 `images` 数组里，且 `images[].id` 不能重复。
7. 图片提示词为空会校验失败；缺图时 APK 会显示绘制中占位，不会阻塞文字。

示例：下面 `n2` 没写 `image`，会自动沿用 `n1` 当前显示的图。

```json
{
  "id": "n2",
  "title": "第二幕",
  "lines": [
    { "speaker": "苏糖", "text": "进来吧。" }
  ],
  "next": "n3"
}
```

## 5. 图片清单 `images`

必须放在 JSON 末尾：

```json
"images": [
  {
    "id": "scene_1",
    "prompt": "旧公寓四楼走廊，暖黄壁灯，雨夜，粉发少女半掩门，电影感，视觉小说场景 CG",
    "negative": "text, watermark, bad anatomy, low quality",
    "kind": "scene",
    "ratio": "3:4",
    "seed": 101
  }
]
```

| 字段 | 必填 | 说明 |
|------|------|------|
| `id` | 是 | 图片 ID，供节点/台词引用 |
| `prompt` | 是 | 生图正面提示词，建议英文 tag 或简短英文描述 |
| `negative` | 否 | 负面词；为空时使用设置里的默认负面词 |
| `kind` | 否 | `scene` 或 `event_cg`；事件 CG 会在顶部标记“事件 CG” |
| `ratio` | 否 | 默认 `3:4`，也可写 `1:1`、`4:3` |
| `seed` | 否 | 固定种子；不写则随机 |

APK 会按清单逐张生成缺失图片，当前显示的图片优先，其余在后台继续。没有图片包，也不需要导入图片文件。

## 6. 选项与分支

```json
"choices": [
  {
    "id": "enter",
    "text": "走进去",
    "hint": "接受邀请",
    "next": "n2a",
    "effects": {
      "affection": { "苏糖": 2 },
      "flags": { "entered_room": true },
      "items": ["热茶"]
    }
  },
  {
    "id": "ask",
    "text": "先问她为什么这么晚",
    "next": "n2b"
  }
]
```

- 每个选项必须有唯一 `id`、可读 `text` 和跳转 `next`。
- 玩家选择后，先应用该选项的 `effects`，再进入 `next`。
- `next` 必须指向存在的节点。
- 当前版本不支持条件表达式；如果某个选项要按好感/flag 出现或隐藏，需要用不同节点和静态分支表达。
- 自由输入框不参与 JSON 剧本运行，会提示“JSON 剧本不支持自由输入”。

## 7. 变量和效果

```json
"effects": {
  "affection": { "苏糖": 2, "周宁": -1 },
  "flags": { "met_su": true },
  "items": ["钥匙"]
}
```

- `affection`：对指定角色好感加减，范围 0~100。
- `flags`：布尔标记，可用于你自己的剧情记录。
- `items`：追加道具，重复道具会去重。

节点 `effects` 在进入节点时应用；选项 `effects` 在跳转前应用。

## 8. 结局

```json
{
  "id": "ending_1",
  "title": "结局 · 热茶",
  "ending": true,
  "image": "scene_tea",
  "lines": [
    { "speaker": "苏糖", "text": "明天也可以来。" }
  ]
}
```

结局节点没有选项时，底部显示“结局”和“重新开始”。

## 9. 存档与恢复

APK 自动保存当前节点、变量和路径历史。退出重进后会从最后一个节点恢复；图片已生成的会继续复用，没生成的会继续补。

## 10. 直线推进

节点没有 `choices` 时，最后一句台词显示完后，**点击画面或台词框**会直接按 `next` 进入下一节点，不再显示“继续剧情”按钮。有 `choices` 时只显示 JSON 定义的选项；`ending: true` 的节点显示结局和“重新开始”。

## 11. 导入方式

1. 打开「夜读」独立 APK。
2. 点击「导入 JSON 剧本」，选择本文件。
3. 导入成功后会自动进入第一幕。

生图模型可以放在公共存储，目录见《模型目录说明.md》。

不需要新建世界，也不需要导入图片包。

## 12. 校验会拒绝的情况

- `start` 不存在；
- 节点 ID 重复；
- 选项 ID 在同一节点内重复；
- `next` 或 `choices[].next` 指向不存在的节点；
- 引用了不存在的图片 ID；
- 图片缺少 `prompt`；
- 非结局节点既没有 `next` 也没有 `choices`；
- 存在从 `start` 不可达的节点；
- JSON 格式错误。

## 13. 当前限制

- 当前只支持整张图片切换，不支持“背景 + 多立绘位置”的分层渲染。
- 不支持条件表达式、随机分支、图片变量替换。
- 图片提示词直接交给生图后端，建议写具体地点、人物、服装、表情、动作、光线和构图。
- 同一角色跨图一致性仍受生图模型限制；固定 `seed` 和详细描述可以减少漂移。
- 旧 AI Galgame 的运行时不再使用；对应旧存档保留在本地但不会进入新 JSON 流程。

## 14. 示例文件

仓库内示例：

```text
doc/蒋磊/示例/Galgame剧本示例.json
```

手机内推送位置：

```text
/sdcard/Download/Galgame剧本示例.json
/sdcard/Download/Galgame-JSON编写说明.md
```