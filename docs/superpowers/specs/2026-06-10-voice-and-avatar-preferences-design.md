# 音色与 Live2D 形象可选/可切换设计

日期：2026-06-10

## 目标

为数字人系统增加两类可配置、可切换能力：

- 后台维护可选 `TTS 音色` 列表，并设置默认音色
- 后台维护可选 `Live2D 形象` 列表，并设置默认形象
- 用户在聊天页可以随时切换音色和形象
- 用户选择会持久化，后续会话继续生效

## 已确认范围

### 包含

- 后台新增可选音色列表维护
- 后台新增可选 Live2D 形象列表维护
- 后台设置默认音色、默认形象
- 聊天页增加音色选择器和形象选择器
- 用户切换形象后，当前页面立即刷新形象
- 用户切换音色后，下一条助手语音开始使用新音色
- 用户偏好保存到服务端
- 用户身份先按固定用户处理

### 不包含

- 自动发现阿里云全部音色或模型
- 训练或生成新音色
- 多用户登录体系
- 历史会话回放时还原当时的音色/形象快照
- 对话大模型切换

## 现状

当前系统已有这些基础：

- 后端已有 `t_dh_config` 配置表
- 后端已有 `tts_voice_id`、`live2d_model_path` 等零散配置
- 前端已有后台管理页和聊天页
- 前端 `Live2DCanvas` 已支持按 `modelPath` 加载形象
- 后端 TTS 已切到阿里云 DashScope WebSocket SDK，当前可用音色为 `longyingxiao_v3`

当前缺口是：

- 可选项池不存在，只能存一个当前值
- 默认值与用户选择没有分层
- 用户偏好没有独立存储
- 聊天页没有公开切换入口

## 总体设计

系统分为三层：

1. 后台配置池
2. 用户偏好
3. 聊天页运行态

### 1. 后台配置池

后台负责维护“系统允许用户选择什么”：

- 可选 `voice id` 列表
- 可选 `Live2D modelPath` 列表
- 默认 `voice id`
- 默认 `modelPath`

后台配置池只定义选择范围和系统默认值，不直接代表某个用户当前正在使用的值。

### 2. 用户偏好

用户偏好负责维护“这个用户当前想用什么”：

- `tts_voice_id`
- `live2d_model_path`

读取顺序：

1. 有用户偏好时，优先使用用户偏好
2. 用户没有偏好时，回退后台默认值

### 3. 聊天页运行态

聊天页启动时拉取当前用户的有效配置，并加载：

- 当前可选音色列表
- 当前可选形象列表
- 当前有效音色
- 当前有效形象

用户切换形象时立即刷新当前 Live2D。
用户切换音色时，从下一条语音回复开始生效。

## 数据模型

## 配置池存储

本轮优先复用 `t_dh_config`，避免引入更重的配置管理表。

新增配置键：

- `tts_voice_options`
- `live2d_model_options`
- `default_tts_voice_id`
- `default_live2d_model_path`

### `tts_voice_options`

建议存对象数组，而不是只存字符串，便于后台展示名称。

示例：

```json
[
  { "label": "莹晓", "value": "longyingxiao_v3" },
  { "label": "阳光男声", "value": "longanyang" }
]
```

### `live2d_model_options`

建议结构：

```json
[
  {
    "label": "默认 Haru",
    "value": "/models/generated/avatar_default/Haru.model3.json"
  },
  {
    "label": "小雅",
    "value": "/models/generated/avatar_xiaoya/Haru.model3.json"
  }
]
```

后续如果需要缩略图，可扩展为：

```json
{
  "label": "小雅",
  "value": "/models/generated/avatar_xiaoya/Haru.model3.json",
  "thumbnail": "/models/generated/avatar_xiaoya/thumbnail.png"
}
```

## 用户偏好表

新增表：`t_user_preference`

建议字段：

- `id` bigint 主键
- `user_id` varchar(64) 唯一
- `tts_voice_id` varchar(128)
- `live2d_model_path` varchar(512)
- `updated_at` datetime

约束：

- `user_id` 唯一
- `tts_voice_id` 与 `live2d_model_path` 可为空
- 为空时表示该项回退默认值

### 用户身份

当前阶段不引入登录。

先按固定用户处理，服务端和前端统一使用一个固定 `userId`。实现阶段可以先约定一个固定值，例如：

- `default-user`

后续接入真实登录时，只需要把固定 `userId` 替换为真实账号标识，不影响整体结构。

## 接口设计

## 后台配置接口

建议新增统一接口：

- `GET /api/admin/config/preferences`
- `PUT /api/admin/config/preferences`

### `GET /api/admin/config/preferences`

返回：

```json
{
  "voiceOptions": [
    { "label": "莹晓", "value": "longyingxiao_v3" }
  ],
  "modelOptions": [
    { "label": "默认 Haru", "value": "/models/generated/avatar_default/Haru.model3.json" }
  ],
  "defaultVoiceId": "longyingxiao_v3",
  "defaultModelPath": "/models/generated/avatar_default/Haru.model3.json"
}
```

### `PUT /api/admin/config/preferences`

更新：

- `voiceOptions`
- `modelOptions`
- `defaultVoiceId`
- `defaultModelPath`

保存前校验：

- 默认音色必须出现在 `voiceOptions`
- 默认形象必须出现在 `modelOptions`
- `voiceOptions.value` 不能重复
- `modelOptions.value` 不能重复

## 用户偏好接口

建议新增：

- `GET /api/user/preferences`
- `PUT /api/user/preferences`

### `GET /api/user/preferences`

返回值直接包含：

- 系统可选项池
- 系统默认值
- 当前用户生效值

示例：

```json
{
  "voiceOptions": [
    { "label": "莹晓", "value": "longyingxiao_v3" }
  ],
  "modelOptions": [
    { "label": "默认 Haru", "value": "/models/generated/avatar_default/Haru.model3.json" }
  ],
  "defaultVoiceId": "longyingxiao_v3",
  "defaultModelPath": "/models/generated/avatar_default/Haru.model3.json",
  "currentVoiceId": "longyingxiao_v3",
  "currentModelPath": "/models/generated/avatar_default/Haru.model3.json"
}
```

`current*` 的计算规则：

- 用户已保存偏好且偏好仍在可选池内：返回用户值
- 用户没有保存偏好：返回默认值
- 用户保存值已失效：回退默认值

### `PUT /api/user/preferences`

请求体：

```json
{
  "voiceId": "longyingxiao_v3",
  "modelPath": "/models/generated/avatar_default/Haru.model3.json"
}
```

校验规则：

- `voiceId` 必须在后台 `voiceOptions` 内
- `modelPath` 必须在后台 `modelOptions` 内
- 非法值拒绝保存

## 后台页面设计

后台页面分为两块，不让管理员直接编辑原始 JSON。

### 音色管理

功能：

- 展示当前可选音色列表
- 新增一条 `label + voice id`
- 删除一条音色
- 设置默认音色

说明：

- 后台录入的是阿里云已有 `voice id`
- 不负责验证该 `voice id` 是否绝对可用；实现阶段可增加“测试播放”作为增强项，但不在本轮必做范围

### 形象管理

功能：

- 展示当前可切换形象列表
- 从已有形象中加入可选池
- 删除一条形象
- 设置默认形象

说明：

- 这里的形象来源于现有 Live2D 形象资源
- 建议后台选择时复用当前已有形象列表，而不是手工输入 `modelPath`

## 聊天页设计

聊天页增加两个切换控件：

- 音色选择器
- 形象选择器

位置建议：

- 放在聊天页主区域顶部，或 Live2D 展示区附近
- 优先保证切换入口可见，不藏进深层设置抽屉

### 初始化

页面加载时：

1. 请求 `GET /api/user/preferences`
2. 初始化音色列表和形象列表
3. 初始化当前选中值
4. `Live2DCanvas` 使用 `currentModelPath` 加载形象

### 切换形象

切换后行为：

1. 前端立即更新 `currentModelPath`
2. `Live2DCanvas` 立刻重载新形象
3. 异步调用 `PUT /api/user/preferences` 保存

生效范围：

- 当前页面立即可见
- 后续会话继续生效

### 切换音色

切换后行为：

1. 前端立即更新 `currentVoiceId`
2. 异步调用 `PUT /api/user/preferences` 保存
3. 下一条助手语音调用 TTS 时使用新的 `voiceId`

生效范围：

- 当前页面选择器立即变化
- 当前正在播放的音频不强制中断
- 下一条助手语音开始生效

## 后端运行时行为

## 回复编排

聊天编排层在生成 TTS 音频时，不再只读全局默认音色，而是按固定用户读取当前有效音色：

1. 用户偏好音色
2. 后台默认音色
3. TTS 连接器内部兜底音色

## Live2D 默认模型

聊天页首次进入或刷新时：

1. 用户偏好形象
2. 后台默认形象
3. 现有基础默认模型路径

## 容错策略

### 用户偏好失效

如果后台删除了某个音色或形象，而用户之前正好保存了这个值：

- 接口返回时回退到默认值
- 前端显示默认值
- 不返回失效值

### 保存失败

用户切换后若保存用户偏好失败：

- 前端保留当前页面本地状态
- 给出“保存失败，刷新后可能恢复默认”的提示
- 不阻断当前页面切换体验

### TTS 失败

若用户选择的音色不可用：

1. 先尝试用户音色
2. 失败后回退后台默认音色
3. 默认音色也失败时，再回退连接器内置兜底

这样能降低由于单个音色配置错误导致整条语音链路失效的概率。

### 模型文件失效

若某个 `modelPath` 文件被删除或不可读取：

- 服务端返回默认形象
- 前端不继续尝试失效路径

## 实现边界

本轮只实现“录入已有值并选择使用”，不实现：

- 阿里云音色自动拉取
- 形象自动扫描后实时同步后台池
- 音色可用性自动探测
- 正在播放语音时的动态换声

## 测试设计

## 后端测试

- 配置池读写成功
- 默认值必须属于可选项池
- 用户偏好保存合法值成功
- 用户偏好保存非法值失败
- 用户无偏好时返回默认值
- 用户有偏好时返回用户值
- 用户偏好值失效时回退默认值

## 前端测试

- 页面初始化能正确显示当前音色与形象
- 切换形象会立即刷新 Live2D
- 切换音色后下一条消息使用新音色
- 保存失败时页面不崩溃且有提示
- 后台修改默认值后，新用户进入页面能看到新默认值

## 迁移与兼容

为了兼容现有系统：

- 旧的 `tts_voice_id`、`live2d_model_path` 可以保留作为历史配置项
- 新逻辑优先读新的默认配置键和用户偏好
- 若新配置缺失，可短期回退旧键值

这样可以降低一次性切换风险。

## 推荐实现顺序

1. 新增配置池读写逻辑
2. 新增 `t_user_preference`
3. 新增后台统一配置接口
4. 新增用户偏好接口
5. 改造聊天编排读取用户音色
6. 改造聊天页加入音色/形象切换
7. 改造后台页加入“可选项池 + 默认值”管理

## 结论

推荐方案为：

- 后台维护可选音色与可选形象池
- 后台维护默认音色与默认形象
- 用户维护自己的音色与形象偏好
- 聊天页即时切换当前形象，并让下一条语音使用用户当前选中的音色

这个方案满足当前需求，同时保留了未来扩展到真实用户体系和更多配置项的空间。
