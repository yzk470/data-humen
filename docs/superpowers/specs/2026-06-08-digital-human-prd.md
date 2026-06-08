# 数字人虚拟助手系统 — PRD 需求文档

> **版本**: v1.0
> **日期**: 2026-06-08
> **状态**: 已确认

---

## 1. 产品概述

### 1.1 产品定位

基于 Spring Boot + WebRTC 构建的 **AI 驱动 2D 数字人虚拟助手系统**。用户通过浏览器与数字人进行实时对话，数字人以 Live2D 骨骼动画形象呈现，支持语音和文字双模式交互，面向企业内部/学校等小规模场景（几百人量级）提供智能客服与虚拟助手服务。

### 1.2 核心价值

- **低延迟实时交互**：WebRTC 承载音频流与动画参数同步，用户体验流畅
- **情感化表达**：数字人根据对话语义自动匹配表情和语气，提升亲和力
- **可配置可定制**：管理员可切换形象、音色、人设，适配不同业务场景
- **轻量部署**：AI 能力全走云端 API，无需 GPU 服务器，单台机器即可支撑

### 1.3 关键决策记录

| 维度 | 决策 | 说明 |
|---|---|---|
| 场景定位 | AI 驱动虚拟助手/智能客服 | 浏览器端实时对话 |
| 视觉呈现 | 2D 骨骼动画 | Live2D Cubism SDK for Web |
| 对话引擎 | DeepSeek API | Prompt 中附带情绪标签，规则兜底 |
| 交互方式 | 文字 + 语音双输入，语音输出 | 全双工 WebRTC 音频通道 |
| 语音方案 | ASR 云端 API + TTS 云端 API | 不本地部署 AI 模型 |
| 情绪表达 | 6 种情绪标签 | neutral/happy/puzzled/surprised/sorry/thinking |
| 口型同步 | 浏览器端 AudioContext FFT 分析 | 服务端不参与口型计算 |
| 架构模式 | 轻量中继架构 | Spring Boot 编排，AI 全走云端 |
| 部署规模 | 小规模生产 | 单台服务器，几百人并发 |
| 前端框架 | Vue 3 | Element Plus 组件库 |
| 数据存储 | MySQL 持久化 + Redis 热缓存 | Cache-Aside 模式 |
| 配置管理 | 管理后台可配置 | 形象/音色/人设 Prompt |

---

## 2. 系统架构

### 2.1 整体架构图

```
┌─────────────────────────────────────────────────────┐
│                   浏览器客户端                        │
│  ┌──────────┐ ┌──────────┐ ┌────────────────────┐   │
│  │ Live2D   │ │ 音频采集  │ │ WebRTC Peer        │   │
│  │ 渲染引擎 │ │ (麦克风)  │ │ (音频 + 数据通道)   │   │
│  └──────────┘ └──────────┘ └────────────────────┘   │
├─────────────────────────────────────────────────────┤
│                Spring Boot 服务端                     │
│  ┌──────────┐ ┌──────────┐ ┌────────────────────┐   │
│  │ WebRTC   │ │ 会话管理  │ │ API 编排层          │   │
│  │ 信令服务 │ │ (多用户)  │ │ (ASR→LLM→TTS链)    │   │
│  └──────────┘ └──────────┘ └────────────────────┘   │
│  ┌──────────┐ ┌──────────┐ ┌────────────────────┐   │
│  │ 管理后台 │ │ 情绪计算  │ │ 动画参数生成        │   │
│  │ (配置)   │ │ (情感分析)│ │ (口型+表情映射)     │   │
│  └──────────┘ └──────────┘ └────────────────────┘   │
│  ┌──────────┐ ┌──────────┐                          │
│  │ MySQL    │ │ Redis    │                          │
│  │ 持久化   │ │ 热缓存   │                          │
│  └──────────┘ └──────────┘                          │
├─────────────────────────────────────────────────────┤
│                   外部 API 层                         │
│  ┌──────────┐ ┌──────────┐ ┌────────────────────┐   │
│  │ DeepSeek │ │ ASR 云   │ │ TTS 云             │   │
│  │ (对话)   │ │ (语音识别)│ │ (语音合成+情感)    │   │
│  └──────────┘ └──────────┘ └────────────────────┘   │
└─────────────────────────────────────────────────────┘
```

### 2.2 数据流向

**语音对话流水线**：
```
用户语音 → 浏览器麦克风 → WebRTC 音频通道 → Spring Boot
  → ASR API → 识别文本
  → DeepSeek API → LLM 回复 + 情绪标签
  → TTS API → 合成语音
  → WebRTC (TTS 音频 + Data Channel 动画参数) → 浏览器
  → Live2D 播放口型/表情 + 扬声器播放语音
```

**文字对话流水线**：
```
用户文字 → HTTP POST → Spring Boot → DeepSeek API
  → 回复文本 + 情绪标签 → TTS → HTTP Response (音频 + 动画参数)
```

### 2.3 部署拓扑

```
┌─────────────────────────────────────────────┐
│                  服务器 (单台)                │
│                                             │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  │
│  │ Nginx    │  │ Spring   │  │ MySQL    │  │
│  │ (静态文件) │  │ Boot Jar │  │ 8.0      │  │
│  │ SSL 终结  │  │ :8080    │  │ :3306    │  │
│  └──────────┘  └──────────┘  └──────────┘  │
│                       │                     │
│  ┌──────────┐         │                     │
│  │ Redis    │─────────┘                     │
│  │ :6379    │                               │
│  └──────────┘                               │
└─────────────────────────────────────────────┘
```

---

## 3. 服务端模块设计

### 3.1 模块划分

```
digital-human-server/
├── dh-common/             # 公共基础（统一响应、配置、工具类）
├── dh-signaling/          # WebRTC 信令模块
├── dh-session/            # 会话管理模块
├── dh-orchestrator/       # API 编排层（核心流水线）
├── dh-connector/          # 外部 API 连接器（DeepSeek / ASR / TTS）
├── dh-emotion/            # 情绪计算模块
├── dh-admin/              # 管理后台模块
└── dh-storage/            # 数据存储模块（MySQL + Redis 操作封装）
```

### 3.2 模块职责

#### dh-signaling — WebRTC 信令模块

| 功能 | 说明 |
|---|---|
| WebSocket 信令通道 | SDP Offer/Answer 交换、ICE Candidate 中继 |
| 房间管理 | createRoom / joinRoom / leaveRoom |
| 连接状态监听 | 断线检测、心跳保活、自动重连 |

#### dh-session — 会话管理模块

| 功能 | 说明 |
|---|---|
| 多用户会话上下文 | 每用户独立 sessionId，绑定对话历史 |
| 会话生命周期 | 创建 / 活跃 / 超时自动清理 |
| 并发隔离 | ThreadLocal 或 sessionId 绑定，用户间互不干扰 |

#### dh-orchestrator — 核心编排层

| 功能 | 说明 |
|---|---|
| 语音流水线 | ASR → LLM → Emotion → TTS（顺序编排） |
| 文字流水线 | LLM → Emotion → TTS（跳过 ASR） |
| 超时与降级 | 单个 API 超时/失败时的兜底策略 |
| 流式支持 | LLM 流式输出可选（后续迭代） |

#### dh-connector — 外部 API 连接器

| 连接器 | 职责 |
|---|---|
| DeepSeekConnector | 封装 DeepSeek Chat API，发送系统 Prompt + 对话历史 |
| AsrConnector | 封装 ASR 云端 API，音频 → 文本 |
| TtsConnector | 封装 TTS 云端 API（支持情感合成），文本 → Base64 PCM/MP3 |

设计原则：
- 统一接口抽象（`Connector<TInput, TOutput>`）
- 可插拔实现（更换 ASR/TTS 供应商无需改动编排层）
- 连接池复用 HTTP 连接

#### dh-emotion — 情绪计算模块

| 功能 | 说明 |
|---|---|
| LLM 标签提取 | 从 DeepSeek 回复中解析 `[EMOTION:标签]` |
| 规则兜底 | 关键词词典匹配，LLM 未输出标签时降级 |
| 参数映射 | 情绪标签 → Live2D 参数值 |
| 平滑过渡 | 相邻帧间线性插值（~300ms 过渡时间） |

**情绪 ↔ Live2D 参数映射表**：

| 情绪标签 | 核心参数 | 典型场景 |
|---|---|---|
| neutral (中性) | Happy=0.5 | 默认状态、陈述事实 |
| happy (开心) | Happy=0.9, EyeOpen=1.0 | 打招呼、好消息 |
| puzzled (疑惑) | BrowY=0.6, EyeOpen=0.7, Surprise=0.3 | 用户问题不明确 |
| surprised (惊讶) | Surprise=0.9, EyeOpen=1.0, MouthOpenY=0.4 | 意外信息 |
| sorry (抱歉) | Sad=0.6, BrowY=-0.3, Happy=0.2 | 无法解答时 |
| thinking (思考) | BrowY=0.4, EyeOpen=0.5, Happy=0.3 | 处理中/搜索知识 |

#### dh-admin — 管理后台

| 功能 | 说明 |
|---|---|
| 数字人形象管理 | 上传/切换 Live2D 模型文件 |
| TTS 音色配置 | 切换 TTS 音色、语速、音调 |
| 系统 Prompt 编辑 | 编辑角色人设、领域知识 |
| 对话日志查询 | 按时间/用户查询历史对话 |

#### dh-storage — 数据存储模块

| 存储 | 用途 | 说明 |
|---|---|---|
| MySQL | 对话历史持久化 | 全量消息存储，按 sessionId 分区 |
| Redis | 热数据缓存 | 最近 10 轮对话、会话状态、配置快照 |

**Redis 键设计**：

| Key | Value | TTL |
|---|---|---|
| `session:{id}:history` | 最近 10 轮对话 JSON | 30 min |
| `session:{id}:config` | 数字人配置快照 | 10 min |
| `session:{id}:rtc_state` | WebRTC 连接状态 | 5 min |

**缓存策略**：写操作先 MySQL → 后更新 Redis；读操作先 Redis → 未命中查 MySQL → 回填 Redis。

### 3.3 依赖关系

```
dh-common  ←── 所有模块依赖
dh-storage ←── dh-session, dh-admin 依赖
dh-connector ←── dh-orchestrator 依赖
dh-emotion  ←── dh-orchestrator 依赖
dh-session  ←── dh-orchestrator + dh-signaling 依赖
dh-orchestrator ←── dh-signaling 调用（处理对话请求）
dh-admin  →  dh-session（查询日志）
```

---

## 4. WebRTC 通道与数据协议

### 4.1 通道规划

| 通道 | 类型 | 方向 | 承载内容 |
|---|---|---|---|
| Audio Track | 媒体通道 | 双向 | 上行：用户麦克风 Opus 音频；下行：服务端 TTS 合成语音 |
| Data Channel | 数据通道 | 服务端→浏览器 | Live2D 动画参数 JSON + 当前话本文本 |

> 文字输入走独立 HTTP API（`POST /api/chat/text`），不占用 Data Channel。

### 4.2 信令流程

```
浏览器                      Spring Boot
  |                             |
  |--- WS Connect ------------->|  建立信令通道
  |--- createSession ---------->|  创建会话 → 返回 sessionId
  |--- SDP Offer -------------->|  浏览器生成 Offer
  |<-- SDP Answer --------------|  服务端缓存 Offer → 生成 Answer
  |--- ICE Candidate ---------->|  双方交换 ICE
  |<-- ICE Candidate -----------|
  |=== WebRTC 连接建立 ========|
  |=== Audio Track 双向传输 ====|
  |=== Data Channel 就绪 =======|
```

### 4.3 Data Channel 动画数据协议

JSON 格式，每帧一次（~33ms，30fps）：

```json
{
  "type": "animation_frame",
  "timestamp": 1717838400123,
  "params": {
    "ParamMouthOpenY": 0.75,
    "ParamMouthForm": 0.3,
    "ParamEyeOpen": 1.0,
    "ParamBrowY": 0.0,
    "ParamAngry": 0.0,
    "ParamHappy": 0.85,
    "ParamSad": 0.0,
    "ParamSurprise": 0.0
  },
  "audio_playing": true,
  "text": "您好，请问有什么可以帮您？"
}
```

- 口型参数 `ParamMouthOpenY` 在**浏览器端**根据 TTS 音频实时 FFT 分析计算，服务端不参与
- 情绪参数在每句话开始时设定，句内平滑过渡（300ms 线性插值）
- `text` 字段用于前端显示字幕（可选）

---

## 5. 口型同步与音频分析

### 5.1 方案

口型同步在**浏览器端**完成，流程如下：

```
TTS 音频播放 → AudioContext.createMediaElementSource()
                      │
              ┌───────┴───────┐
              │ AnalyserNode   │
              │ FFT 频域分析   │
              │ (2048 采样点)  │
              └───────┬───────┘
                      │
              ┌───────┴───────┐
              │ 计算 RMS 能量  │
              │ (均方根幅值)   │
              └───────┬───────┘
                      │
              ┌───────┴───────┐
              │ RMS → MouthY  │
              │ 映射 [0,1]    │
              │ 每帧更新      │
              └───────────────┘
                      │
                      ▼
              Live2D.ParamMouthOpenY
```

- 采样率：浏览器默认（通常 44.1kHz 或 48kHz）
- FFT 大小：2048（平衡精度与性能）
- RMS 到 MouthOpenY 的映射使用分段线性函数（可微调灵敏度）

### 5.2 待机动画

无对话时：
- 呼吸动画（`ParamBreath` 周期性正弦波，周期 ~4s）
- 自动眨眼（`ParamEyeLOpen` / `ParamEyeROpen` 随机闭眼，间隔 3-6s）
- 随机微表情（偶尔微笑或轻微头部摆动）

---

## 6. API 设计

### 6.1 核心对话 API

| 接口 | 方法 | 请求 | 响应 | 说明 |
|---|---|---|---|---|
| `/api/session/create` | POST | `{}` | `{sessionId}` | 创建新会话 |
| `/api/session/{id}` | GET | — | `{status, createTime, lastActiveTime}` | 查询会话 |
| `/api/session/{id}` | DELETE | — | `{success}` | 关闭会话 |
| `/api/chat/text` | POST | `{sessionId, text}` | `{text, emotion, audioBase64, animationParams[]}` | 文字对话 |
| `/ws/signaling` | WebSocket | — | — | WebRTC 信令 |

### 6.2 管理后台 API

| 接口 | 方法 | 说明 |
|---|---|---|
| `/api/admin/config/prompt` | GET / PUT | 系统 Prompt CRUD |
| `/api/admin/config/tts-voice` | GET / PUT | TTS 音色/语速/音调配置 |
| `/api/admin/config/model` | GET / PUT | Live2D 模型文件管理 |
| `/api/admin/config/avatar` | GET / PUT | 当前生效的形象配置 |
| `/api/admin/logs?page=&size=&sessionId=` | GET | 对话日志分页查询 |

### 6.3 统一响应格式

```json
{
  "code": 200,
  "message": "success",
  "data": { ... }
}
```

错误码分段：
- `2xx` — 成功
- `4xx` — 客户端错误（参数校验、会话不存在等）
- `5xx` — 服务端/外部 API 错误

---

## 7. 数据模型

### 7.1 核心实体

```sql
-- 会话表
CREATE TABLE t_session (
    id            VARCHAR(64)   PRIMARY KEY,
    status        VARCHAR(16)   NOT NULL DEFAULT 'ACTIVE',  -- ACTIVE / IDLE / CLOSED
    user_ip       VARCHAR(64),
    created_at    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_active_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    closed_at     DATETIME,
    INDEX idx_status (status),
    INDEX idx_last_active (last_active_at)
);

-- 消息表
CREATE TABLE t_message (
    id            BIGINT        PRIMARY KEY AUTO_INCREMENT,
    session_id    VARCHAR(64)   NOT NULL,
    role          VARCHAR(16)   NOT NULL,  -- USER / ASSISTANT
    text          TEXT          NOT NULL,
    emotion       VARCHAR(32),             -- 情绪标签
    audio_url     VARCHAR(512),            -- TTS 音频文件 URL (可选)
    created_at    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_session_time (session_id, created_at),
    FOREIGN KEY (session_id) REFERENCES t_session(id)
);

-- 数字人配置表
CREATE TABLE t_dh_config (
    id                    BIGINT        PRIMARY KEY AUTO_INCREMENT,
    config_key            VARCHAR(64)   NOT NULL UNIQUE,
    config_value          TEXT          NOT NULL,
    description           VARCHAR(256),
    updated_at            DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);
```

### 7.2 Java 实体

```java
// 会话
Session {
    String sessionId;       // UUID
    String status;          // ACTIVE / IDLE / CLOSED
    List<Message> history;  // 不持久化到 MySQL，仅运行时
    Instant createdAt;
    Instant lastActiveAt;
}

// 消息
Message {
    Long id;
    String sessionId;
    String role;            // USER / ASSISTANT
    String text;
    String emotion;
    String audioUrl;
    Instant createdAt;
}

// 数字人运行时配置 (从 t_dh_config 加载)
DigitalHumanConfig {
    String systemPrompt;        // 人设 Prompt
    String ttsVoiceId;          // TTS 音色
    Float speechSpeed;          // 语速 (0.5 ~ 2.0)
    Float pitch;                // 音调 (-20 ~ 20)
    String live2dModelPath;     // Live2D 模型文件路径
}

// WebRTC 会话 (Redis 存储)
RtcSession {
    String sessionId;
    String peerConnectionState; // NEW / CONNECTING / CONNECTED / DISCONNECTED / FAILED / CLOSED
    Instant connectedAt;
}
```

---

## 8. LLM Prompt 设计

### 8.1 系统 Prompt 结构

```
你是【角色名】，一个专业的【领域/行业】客服助手。

## 性格特点
- 【性格描述1】
- 【性格描述2】

## 回答要求
- 回复简洁、友好，控制在 100 字以内
- 使用口语化表达，让对话自然流畅
- 如果用户问题超出知识范围，诚实告知并引导用户补充信息

## 情绪表达
每次回复请在末尾附加情绪标签，格式为 [EMOTION:标签名]。
可选标签：
- neutral：中性陈述
- happy：开心/积极
- puzzled：疑惑/不确定
- surprised：惊讶
- sorry：抱歉/遗憾
- thinking：思考/处理中

## 对话历史
（最近 10 轮对话上下文，由系统自动填充）

## 当前用户问题
{用户输入文本}
```

### 8.2 情绪标签解析

1. 正则提取 `\[EMOTION:(\w+)\]` → 匹配成功则使用 LLM 标签
2. 未匹配 → 降级为规则匹配（关键词词典）
3. 规则也未匹配 → 默认 `neutral`

---

## 9. 前端设计

### 9.1 技术栈

| 层 | 技术 |
|---|---|
| 框架 | Vue 3 + Composition API |
| 构建 | Vite |
| UI 组件库 | Element Plus |
| 状态管理 | Pinia |
| Live2D | Cubism SDK for Web |
| WebRTC | 浏览器原生 RTCPeerConnection |
| 音频 | MediaDevices + AudioContext API |

### 9.2 目录结构

```
digital-human-web/
├── public/
│   └── models/                  # Live2D 模型文件 (.model3.json + 纹理)
├── src/
│   ├── components/
│   │   ├── Live2DCanvas.vue     # Live2D 渲染画布
│   │   ├── ChatPanel.vue        # 对话字幕展示（可选）
│   │   ├── InputBar.vue         # 文字输入框 + 语音按钮
│   │   ├── VoiceButton.vue      # 语音录制按钮
│   │   ├── AvatarSelector.vue   # 数字人形象选择（管理后台）
│   │   └── AdminPanel.vue       # 管理配置页
│   ├── composables/             # Vue3 组合式函数
│   │   ├── useRtcClient.js      # WebRTC 管理
│   │   ├── useSignaling.js      # WebSocket 信令
│   │   ├── useAudioAnalyzer.js  # 音频分析 → 口型
│   │   ├── useLive2dDriver.js   # Live2D 参数驱动
│   │   ├── useSession.js        # 会话管理
│   │   └── useChat.js           # 对话状态
│   ├── services/
│   │   └── api.js               # HTTP API 封装
│   ├── stores/
│   │   ├── session.js           # 会话状态
│   │   ├── chat.js              # 对话消息
│   │   └── avatar.js            # 数字人配置
│   ├── views/
│   │   ├── ChatView.vue         # 对话主页面
│   │   └── AdminView.vue        # 管理后台页面
│   ├── router/
│   │   └── index.js             # 路由配置
│   ├── App.vue
│   └── main.js
├── index.html
├── vite.config.js
└── package.json
```

### 9.3 前端初始化流程

```
1. 加载 Live2D 模型文件 → 初始化 Cubism SDK → 渲染待机动画
2. POST /api/session/create → 获取 sessionId
3. 建立 WebSocket 信令连接
4. SDP 协商 → 建立 WebRTC 连接
   4a. getUserMedia → 创建 Audio Track（麦克风上行）
   4b. 监听 ontrack → 接收 TTS 音频（下行）
   4c. 监听 Data Channel → 接收动画参数
5. 用户交互：
   - 文字输入 → HTTP POST /api/chat/text
   - 语音输入 → 麦克风采集通过 WebRTC 音频通道发送
6. 收到 TTS 音频 → AudioContext 实时分析 → 驱动口型
7. 收到动画参数 → Live2D 表情更新
8. 空闲状态 → 播放待机动画（呼吸/眨眼/随机微表情）
```

---

## 10. 非功能性需求

### 10.1 性能

| 指标 | 目标 |
|---|---|
| WebRTC 连接建立时间 | < 3 秒 |
| 语音对话端到端延迟 | < 2 秒（从用户说完到数字人开始回复） |
| 文字对话响应时间 | < 3 秒（含 TTS 合成） |
| Live2D 渲染帧率 | ≥ 30fps |
| 并发会话数 | ≥ 200 |

### 10.2 可用性

- 服务可用性 ≥ 99%（计划内维护窗口除外）
- 外部 API 超时 10 秒后降级处理，返回友好错误提示
- 会话空闲 30 分钟后自动标记为 IDLE，60 分钟后自动关闭

### 10.3 安全

- 管理后台需基础认证（用户名/密码）
- API Key（DeepSeek / ASR / TTS）仅服务端持有，不暴露到前端
- 对话日志脱敏处理（掩码手机号/身份证等敏感信息）
- Nginx 层 HTTPS 加密

### 10.4 扩展性

- 服务端无状态设计，Spring Boot 节点可水平扩展
- ASR / TTS 连接器通过接口抽象，支持切换供应商
- DeepSeek API 可通过适配器切换为其他 LLM

---

## 11. 项目里程碑（建议）

| 阶段 | 内容 | 预计产出 |
|---|---|---|
| M1 - 基础搭建 | Spring Boot 项目骨架、Vue 3 项目骨架、数据库建表 | 可启动的空项目 |
| M2 - 核心流水线 | DeepSeek + TTS 打通，文字对话跑通 | 文字对话可用 |
| M3 - WebRTC 集成 | 信令服务 + 音频通道 + 浏览器客户端 | 语音对话可用 |
| M4 - Live2D 集成 | Cubism SDK 渲染 + 情绪参数驱动 + 口型同步 | 数字人可视交互 |
| M5 - 管理后台 | 配置管理页面 + 对话日志查询 | 完整可配置 |
| M6 - 联调优化 | 端到端测试 + 性能调优 + 文档 | 生产就绪 |

---

## 12. 风险与应对

| 风险 | 影响 | 应对措施 |
|---|---|---|
| DeepSeek API 不稳定 | 数字人无法对话 | 文字对话模式降级提示；后续增加备用 LLM |
| TTS 云端延迟高 | 用户等待时间长 | 流式 TTS 分段推送；预生成常用语缓存 |
| Live2D SDK 兼容性 | 部分浏览器无法渲染 | 降级为静态头像 + 仅语音播报 |
| WebRTC NAT 穿透失败 | P2P 连接无法建立 | 部署 TURN 中继服务器作为兜底 |

---

## 13. 变更记录

| 版本 | 日期 | 变更内容 |
|---|---|---|
| v1.0 | 2026-06-08 | 初版 PRD，完成架构设计与模块定义 |
