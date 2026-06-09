# AI 智能面部识别 — 设计文档

> **版本**: v1.0
> **日期**: 2026-06-09
> **状态**: 已确认
> **依赖**: 阿里云百炼 通义千问 VL（qwen-vl-max）

---

## 1. 概述

### 1.1 目标

在上传二次元图片生成 Live2D 形象时，用 AI 自动识别面部区域，替代管理员手动框选。

### 1.2 核心思路

- 管理员选择图片后，点击"AI 识别人脸"按钮
- 后端调用通义千问 VL 多模态模型，返回面部坐标
- 前端裁剪框自动吸附到 AI 给出的位置
- 管理员可微调后确认生成

---

## 2. 架构

```
AdminPanel.vue
  ├─ [选择图片]
  ├─ [🤖 AI识别人脸]  → POST /api/admin/avatar/detect-face
  │                          │
  │                          ▼
  │                    QwenVLConnector (新建)
  │                      └─ 调用通义千问 VL API
  │                         image → "识别面部区域" → {x,y,w,h}
  │                          │
  │                          ▼ 返回坐标
  ├─ ImageCropper (裁剪框自动吸附到AI坐标)
  ├─ [微调确认]
  └─ [开始生成]
```

---

## 3. 模块设计

### 3.1 QwenVLConnector

```java
// 目录: digital-human-server/src/main/java/com/dh/server/connector/
@Component
public class QwenVLConnector {

    // OpenAI 兼容格式
    // 输入: byte[] imageBytes
    // 输出: FaceRegion {x, y, width, height}
    public FaceRegion detectFace(byte[] imageBytes) { ... }

    @Data
    public static class FaceRegion {
        private int x, y, width, height;
    }
}
```

**API 调用方式**（与 DeepSeekConnector 模式一致，RestTemplate + OpenAI 兼容 JSON）：

```json
{
  "model": "qwen-vl-max",
  "messages": [{
    "role": "user",
    "content": [
      {"type": "image_url", "image_url": {"url": "data:image/png;base64,..."}},
      {"type": "text", "text": "识别图中人物的面部区域，仅返回JSON: {\"x\":数字,\"y\":数字,\"width\":数字,\"height\":数字}"}
    ]
  }]
}
```

**错误处理**：
- API 超时 15s → `BusinessException("AI 识别超时，请重试或手动框选")`
- API 返回非 JSON → `BusinessException("AI 返回格式异常，请手动框选")`
- 坐标超出图片范围 → 裁剪到图片边界内

### 3.2 API

`POST /api/admin/avatar/detect-face`（AdminController 新增）

```
Request:  multipart/form-data { image }
Response: { "code": 200, "data": { "x": 100, "y": 50, "width": 300, "height": 300 } }
```

### 3.3 配置（application.yml）

```yaml
app:
  qwen-vl:
    api-key: ${QWEN_VL_API_KEY:}
    api-url: https://dashscope.aliyuncs.com/compatible-mode/v1
    model: qwen-vl-max
```

### 3.4 前端

**ImageCropper.vue** — 新增 `presetCrop` prop，传入后裁剪框自动定位到指定坐标。

**AdminPanel.vue** — 新增"🤖 AI识别人脸"按钮，调用 detect-face API，结果传给 ImageCropper。

---

## 4. 错误处理

| 场景 | 策略 |
|---|---|
| AI API 超时/失败 | 提示用户手动框选，不阻塞流程 |
| AI 返回坐标异常 | 使用默认居中裁剪 |
| API Key 未配置 | 隐藏 AI 按钮，仅显示手动裁剪 |

---

## 5. 文件清单

### 新建

| 文件 | 说明 |
|---|---|
| `connector/QwenVLConnector.java` | 通义千问 VL 连接器 |
| `controller/dto/FaceRegionResponse.java` | 响应 DTO |

### 修改

| 文件 | 变更 |
|---|---|
| `config/AppConfig.java` | 新增 QwenVL 配置属性 |
| `application.yml` | 新增 qwen-vl 配置 |
| `controller/AdminController.java` | 新增 detect-face 端点 |
| `components/ImageCropper.vue` | 新增 presetCrop prop |
| `components/AdminPanel.vue` | 新增 AI 识别按钮 + 加载状态 |

---

## 6. 变更记录

| 版本 | 日期 | 变更 |
|---|---|---|
| v1.0 | 2026-06-09 | 初版 |
