# 照片换皮 Live2D 数字人 — 设计文档

> **版本**: v1.0
> **日期**: 2026-06-09
> **状态**: 待审批
> **依赖**: Live2D Cubism SDK for Web 官方 Sample Model（Haru/Mao）

---

## 1. 概述

### 1.1 目标

为数字人虚拟助手系统增加"上传照片 → 自动生成个性化 Live2D 形象"功能。

### 1.2 核心思路

- 使用 1 个通用的 Live2D 模型骨架（官方 Sample Model），`.model3.json` + 骨骼/变形器保持不变
- 管理员上传多张照片，生成多个不同形象（如不同发型/风格的角色）
- 调用 AI 风格迁移 API 将每张照片转为插画风格
- 将风格化后的面部区域替换到 Live2D 纹理图集中
- 终端用户在对话页面可以从形象库中选择自己喜欢的形象

### 1.3 关键决策

| 维度 | 决策 | 说明 |
|---|---|---|
| Live2D 模型 | Cubism SDK 官方 Sample（Haru/Mao） | 免费，纹理图集结构已知 |
| AI 服务 | 阿里云百炼 / 通义万象（wanx） | OpenAI 兼容接口格式，详见下方推荐 |
| 替换策略 | 仅面部区域替换 | 身体/头发/服装保留原样 |
| 使用场景 | 管理员生成多个形象 + 终端用户自选 | 管理后台生成形象库，用户端可选 |
| 文件存储 | 服务端本地目录 | `digital-human-web/public/models/` |
| 接口模式 | OpenAI 兼容格式 | 与现有 DeepSeekConnector 模式一致 |

---

## 2. 架构设计

### 2.1 整体流程

**管理端（生成形象）**：

```
┌─────────────────────────────────────────────────────────────┐
│                      管理后台 (Vue 3)                        │
│  AdminPanel.vue                                              │
│  ├─ 照片上传 (<el-upload>)                                   │
│  ├─ 形象名称/描述编辑                                        │
│  ├─ 生成中的进度提示                                         │
│  └─ 形象库管理（查看/删除）                                  │
└──────────────────────┬──────────────────────────────────────┘
                       │ POST /api/admin/avatar/upload (multipart/form-data)
                       ▼
┌─────────────────────────────────────────────────────────────┐
│                   Spring Boot 服务端                          │
│                                                              │
│  AdminController                                              │
│    └─ uploadAvatar(photo, name) → AvatarService.generate()   │
│                                                              │
│  AvatarService (新建)                                        │
│    ├─ 1. 保存原始照片                                        │
│    ├─ 2. StyleTransferConnector.execute(photo) → 风格化图    │
│    ├─ 3. TextureService.replaceFace(stylizedImg, baseTexture)│
│    └─ 4. ModelFileService.createModel(...)                   │
│                                                              │
│  StyleTransferConnector (新建)                                │
│    └─ POST 阿里云 AI API (OpenAI 兼容格式)                   │
│       image → anime/illustration style                       │
│                                                              │
│  TextureService (新建)                                        │
│    ├─ 人脸检测 + 裁剪                                        │
│    ├─ 几何变形适配 UV 区域                                   │
│    └─ 纹理合成（替换面部，保留其余）                          │
│                                                              │
│  ModelFileService (新建)                                      │
│    ├─ 管理生成的模型目录结构                                  │
│    └─ CRUD 生成的形象记录                                    │
└──────────────────────┬──────────────────────────────────────┘
                       │ 写入文件系统
                       ▼
┌─────────────────────────────────────────────────────────────┐
│              digital-human-web/public/models/                │
│                                                              │
│  models/                                                     │
│  ├── base/                    # 基础模型（不变）             │
│  │   ├── haru.model3.json                                   │
│  │   ├── haru.2048/           # 原始纹理目录                │
│  │   └── haru_base.texture.png  # 原始纹理图（含身体等）    │
│  ├── generated/               # 生成的形象                  │
│  │   ├── avatar_001/                                        │
│  │   │   ├── meta.json         # {name, thumbnail, ...}     │
│  │   │   ├── haru.model3.json  # 复制自 base/              │
│  │   │   └── haru.2048/        # 含替换后的纹理             │
│  │   └── avatar_002/                                        │
│  │       └── ...                                            │
│  └── default → generated/avatar_001  # 默认形象（初始激活） │
└─────────────────────────────────────────────────────────────┘
```

**用户端（选择形象）**：

```
┌─────────────────────────────────────────────────────────────┐
│                     用户对话页面 (ChatView)                   │
│                                                              │
│  ┌──────────────────────┐                                   │
│  │   Live2D 渲染区       │   右上角:                          │
│  │                      │   ┌─────────────────┐             │
│  │   当前选中的形象      │   │ 🎭 切换形象  ▼  │             │
│  │                      │   │  · 形象A (默认)  │             │
│  │                      │   │  · 形象B         │             │
│  └──────────────────────┘   │  · 形象C         │             │
│                              └─────────────────┘             │
│  GET /api/avatar/list → 获取可用形象列表                      │
│  POST /api/session/{id}/avatar → 用户切换形象                 │
└─────────────────────────────────────────────────────────────┘
```

### 2.2 模块依赖关系

```
StyleTransferConnector (新建)  ←── AvatarService (新建)
TextureService (新建)          ←── AvatarService (新建)
ModelFileService (新建)        ←── AvatarService (新建)
AvatarService (新建)           ←── AdminController (修改)
                                  ←── AppConfig (修改，新增 AI 配置)
```

与现有模块的关系：
- 复用 `dh-connector` 模块的 `Connector<TInput, TOutput>` 接口
- 复用 `dh-storage` 的 `ConfigStorageService` 管理配置
- 扩展 `AdminController` 增加上传和形象管理 API
- 与 `dh-orchestrator` / `dh-emotion` / `dh-signaling` **无耦合**

---

## 3. 模块详细设计

### 3.1 StyleTransferConnector（AI 风格迁移连接器）

**职责**：将真实照片转为插画/二次元风格

**接口模式**：OpenAI 兼容格式，与现有 `DeepSeekConnector` 一致

**推荐 AI 服务**：

| 优先级 | 服务 | endpoint | 说明 |
|---|---|---|---|
| 推荐 | 阿里云百炼 - 通义万象 | `https://dashscope.aliyuncs.com/compatible-mode/v1` | 原生 OpenAI 兼容，支持图片输入 |
| 备选 | Replicate `animegan-v2` | `https://api.replicate.com/v1` | 专做照片转动漫，效果稳定 |

技术验证阶段可先用 Replicate 的免费额度快速跑通，生产环境切换到百炼。两个 API 我们都在 `StyleTransferConnector` 中预留适配能力。

```java
// 目录: digital-human-server/src/main/java/com/dh/server/connector/
@Component
public class StyleTransferConnector implements Connector<byte[], byte[]> {

    // 配置项 (application.yml):
    // app.style-transfer:
    //   api-key: ${STYLE_TRANSFER_API_KEY:}
    //   api-url: https://dashscope.aliyuncs.com/compatible-mode/v1  (示例)
    //   model: wanx-v1  (示例)

    @Override
    public byte[] execute(byte[] photoBytes) {
        // 1. 将照片 Base64 编码
        // 2. 构建 OpenAI 兼容请求体:
        //    {
        //      "model": "wanx-v1",
        //      "messages": [{
        //        "role": "user",
        //        "content": [
        //          {"type": "text", "text": "将这张照片转为日系二次元插画风格，保持面部特征"},
        //          {"type": "image_url", "image_url": {"url": "data:image/jpeg;base64,xxx"}}
        //        ]
        //      }]
        //    }
        // 3. POST 到阿里云 API
        // 4. 解析响应中的图片 → 返回字节数组
    }
}
```

**输入**：原始照片的字节数组（JPEG/PNG）

**输出**：风格化后的图片字节数组（PNG）

**错误处理**：
- API 超时 30 秒 → 抛出 `BusinessException("AI 风格迁移超时，请重试")`
- API 返回非 200 → 抛出 `BusinessException("AI 服务异常: " + msg)`
- 返回图片为空 → 降级使用原始照片（不做风格化）

### 3.2 TextureService（纹理处理服务）

**职责**：将风格化后的面部替换到 Live2D 纹理图集中

```java
// 目录: digital-human-server/src/main/java/com/dh/server/avatar/
@Service
public class TextureService {

    /**
     * 将风格化后的面部图像替换到基础纹理的面部区域。
     *
     * @param faceImage    风格化后的面部图片
     * @param baseTexture  原始 Live2D 纹理图集
     * @param faceRegion   面部在图集中的矩形区域 (已知值, 根据模型确定)
     * @return 合成后的新纹理图字节数组
     */
    public byte[] replaceFaceRegion(byte[] faceImage, byte[] baseTexture,
                                     Rectangle faceRegion) {
        // 1. 将 faceImage 缩放到 faceRegion 尺寸
        // 2. 对 faceImage 做轻微几何变形，匹配面部 UV 布局
        // 3. 将 baseTexture 中 faceRegion 区域替换为处理后的 faceImage
        // 4. 边缘羽化（alpha 渐变），使替换区域与周围自然过渡
        // 5. 返回合成后的纹理 PNG
    }

    /**
     * 边缘羽化：替换区域边界做高斯模糊渐变，避免生硬的接缝
     */
    private BufferedImage featherEdges(BufferedImage composite, Rectangle region) {
        // 边界 ~10px 的 alpha 渐变
    }
}
```

**面部区域坐标**：需要根据选用的 Live2D 模型实际纹理来确定。通过打开模型纹理图集（如 `haru.2048/texture_00.png`），人工标记面部所在的矩形区域（x, y, width, height），作为配置写入。

### 3.3 ModelFileService（模型文件管理）

**职责**：管理生成的形象文件目录结构

```java
// 目录: digital-human-server/src/main/java/com/dh/server/avatar/
@Service
public class ModelFileService {

    private final String modelsRoot;    // digital-human-web/public/models/
    private final String baseModelDir;  // models/base/
    private final String generatedDir;  // models/generated/

    /**
     * 从基础模型创建一份新的个性化模型。
     *
     * 1. 生成唯一 ID: avatar_{timestamp}
     * 2. 创建目录: models/generated/{id}/
     * 3. 复制基础 .model3.json 到新目录
     * 4. 复制基础纹理目录到新目录（软链接方式节省空间）
     * 5. 用合成后的纹理替换面部纹理文件
     * 6. 返回新模型的路径
     */
    public String createModel(String avatarId, byte[] newTexture, String textureFileName) {
        // 创建目录结构
        // 复制 .model3.json
        // 复制纹理目录，替换指定的纹理文件
        // 返回模型 JSON 路径: /models/generated/{id}/xxx.model3.json
    }

    /** 列出所有已生成的形象 */
    public List<AvatarInfo> listAvatars() { ... }

    /** 删除指定形象 */
    public void deleteAvatar(String avatarId) { ... }

    @Data
    public static class AvatarInfo {
        private String id;           // avatar_001
        private String name;         // "知性女助理"
        private String modelPath;    // /models/generated/avatar_001/haru.model3.json
        private String thumbnailPath;      // 缩略图路径
        private LocalDateTime createdAt;
    }
}
```

### 3.4 AvatarService（编排服务）

**职责**：编排整个换皮流水线

```java
// 目录: digital-human-server/src/main/java/com/dh/server/avatar/
@Service
public class AvatarService {

    private final StyleTransferConnector styleTransfer;
    private final TextureService textureService;
    private final ModelFileService modelFileService;
    private final ConfigStorageService configStorage;

    /**
     * 完整的换皮流水线：
     *   上传图片 → [是二次元?跳过:AI风格迁移] → 面部替换 → 保存模型
     *
     * @param photoBytes 上传的图片
     * @param name       形象名称
     * @param isAnime    图片是否已是二次元风格（true 则跳过 AI 风格化）
     */
    public ModelFileService.AvatarInfo generateAvatar(byte[] photoBytes, String name,
                                                       boolean isAnime) {
        // 1. 风格化（二次元图片跳过此步）
        byte[] stylizedImage;
        if (isAnime) {
            stylizedImage = photoBytes;  // 直接使用原图
        } else {
            stylizedImage = styleTransfer.execute(photoBytes);
        }

        // 2. 读取基础纹理
        byte[] baseTexture = modelFileService.loadBaseTexture();

        // 3. 面部替换
        byte[] newTexture = textureService.replaceFaceRegion(
            stylizedImage, baseTexture, getFaceRegion()
        );

        // 4. 创建模型文件
        String avatarId = "avatar_" + System.currentTimeMillis();
        String modelPath = modelFileService.createModel(avatarId, newTexture,
            "texture_00.png");

        // 5. 更新系统配置（激活新形象）
        configStorage.setConfig("live2d_model_path", modelPath);

        // 6. 返回形象信息
        return modelFileService.getAvatarInfo(avatarId);
    }

    /** 切换激活的形象 */
    public void switchAvatar(String avatarId) { ... }
}
```

### 3.5 API 设计

分为两类：**管理端 API**（AdminController，需认证）和 **用户端 API**（公开，供终端用户选择形象）。

#### 管理端 API（AdminController 扩展）

| 接口 | 方法 | 说明 |
|---|---|---|
| `POST /api/admin/avatar/upload` | POST (multipart) | 上传照片+名称，触发生成流水线 |
| `GET /api/admin/avatar/list` | GET | 列出所有已生成的形象 |
| `DELETE /api/admin/avatar/{id}` | DELETE | 删除指定形象 |

**`POST /api/admin/avatar/upload`**：

```
Request:  multipart/form-data
  - photo:   图片文件 (required)
  - name:    形象名称 (required), e.g. "知性女助理"
  - isAnime: "true" / "false" (optional, default false)
             上传的已是二次元图片时设为 true，跳过 AI 风格迁移

Response: {
  "code": 200,
  "data": {
    "id": "avatar_001",
    "name": "知性女助理",
    "modelPath": "/models/generated/avatar_001/haru.model3.json",
    "thumbnailPath": "/models/generated/avatar_001/thumbnail.png",
    "createdAt": "2026-06-09T12:00:00"
  }
}
```

#### 用户端 API（公开）

| 接口 | 方法 | 说明 |
|---|---|---|
| `GET /api/avatar/list` | GET | 获取可用形象列表（供用户浏览选择） |
| `PUT /api/session/{sessionId}/avatar` | PUT | 用户切换当前会话使用的形象 |

**`GET /api/avatar/list`** 响应：

```json
{
  "code": 200,
  "data": {
    "avatars": [
      {
        "id": "avatar_001",
        "name": "知性女助理",
        "thumbnailPath": "/models/generated/avatar_001/thumbnail.png"
      },
      {
        "id": "avatar_002",
        "name": "活力少年",
        "thumbnailPath": "/models/generated/avatar_002/thumbnail.png"
      }
    ],
    "defaultId": "avatar_001"
  }
}
```

**`PUT /api/session/{sessionId}/avatar`** 请求/响应：

```
Request:  { "avatarId": "avatar_002" }
Response: { "code": 200, "data": { "modelPath": "/models/generated/avatar_002/haru.model3.json" } }
```

会话级别存储用户的选择：前端调用后，`Live2DCanvas` 切换到对应的 `modelPath`。会话断开后不持久化（下次进入恢复默认）。

### 3.6 配置扩展（application.yml）

```yaml
app:
  style-transfer:
    api-key: ${STYLE_TRANSFER_API_KEY:}
    api-url: https://dashscope.aliyuncs.com/compatible-mode/v1
    model: wanx-v1
  avatar:
    base-model: models/base/haru.model3.json
    face-region: { x: 512, y: 128, width: 256, height: 256 }  # 根据实际模型确定
```

### 3.7 前端改造

#### AdminPanel.vue — 新增"形象管理"区块

```
┌──────────────────────────────────────────────┐
│  数字人形象管理                                │
│                                               │
│  ┌───────────────────────────────────┐        │
│  │  上传图片生成新形象                  │        │
│  │  形象名称: [________]              │        │
│  │  [选择图片]                        │        │
│  │  ☐ 已是二次元风格（跳过 AI 转换）    │        │
│  │  [开始生成]                        │        │
│  │  生成中 ⏳ ...                     │        │
│  └───────────────────────────────────┘        │
│                                               │
│  形象库（共 N 个）：                           │
│  ┌────────┐ ┌────────┐ ┌────────┐           │
│  │ 缩略图  │ │ 缩略图  │ │ 缩略图  │           │
│  │ 知性女  │ │ 活力少年│ │ 温柔姐  │           │
│  │ [删除]  │ │ [删除]  │ │ [删除]  │           │
│  └────────┘ └────────┘ └────────┘           │
└──────────────────────────────────────────────┘
```

#### ChatView.vue — 用户端新增"切换形象"入口

```
┌─────────────────────────────────┐
│  Live2D 渲染区                   │
│                    ┌──────────┐ │
│                    │ 🎭 形象  ▼│ │
│                    │ · 知性女  │ │
│                    │ · 活力少年│ │
│                    │ · 温柔姐  │ │
│                    └──────────┘ │
│  当前正在说话的数字人            │
└─────────────────────────────────┘
```

**组件变更清单**：

| 文件 | 变更类型 | 说明 |
|---|---|---|
| `AdminPanel.vue` | 修改 | 新增照片上传+名称输入、形象库管理（列表+删除） |
| `ChatView.vue` | 修改 | 新增形象切换下拉菜单 |
| `Live2DCanvas.vue` | 修改 | 支持动态切换 `modelPath`（监听 prop 变化重新加载模型） |
| `AvatarSelector.vue` | 修改 | 动态加载后台形象列表 |
| `stores/avatar.js` | 修改 | 新增 `uploadPhoto()`、`listAvatars()`、`deleteAvatar()`、`switchAvatar()` |
| `services/api.js` | 修改 | 新增 avatar 相关 API 调用 |

### 3.8 Live2D 模型准备

**获取官方 Sample Model**：

1. 从 [Live2D Cubism SDK for Web](https://www.live2d.com/download/cubism-sdk/) 下载
2. 解压后 `Samples/Resources/` 目录下包含 Haru、Mao 等示例模型
3. 模型目录结构（以 Haru 为例）：

```
Haru/
├── haru.model3.json          # 模型定义文件
├── haru.physics3.json        # 物理演算
├── haru.pose3.json           # 姿态
├── haru.cdi3.json            # 显示信息
├── haru.2048/                # 纹理目录
│   └── texture_00.png        # 纹理图集（包含面部区域）
├── haru.motion3.json         # 动作
└── expressions/              # 表情定义
    ├── happy.exp3.json
    ├── sad.exp3.json
    └── ...
```

**面部区域确定**：打开 `texture_00.png`，人工标注面部矩形坐标，写入 `application.yml` 的 `app.avatar.face-region` 配置。

**模型部署**：
- 将 Haru 模型文件复制到 `digital-human-web/public/models/base/` 目录
- 基础纹理 `texture_00.png` 作为合成的底图

---

## 4. 错误处理与降级

| 异常场景 | 处理策略 |
|---|---|
| AI API 超时（>30s） | 返回错误提示，建议用户重试 |
| AI API 返回非200 | 返回 `BusinessException`，提示 AI 服务异常 |
| AI 返回图片为空/无效 | **降级**：跳过风格化，原始照片直接贴图 |
| 照片中未检测到人脸 | 返回 `BusinessException("未检测到人脸，请上传清晰的正面照片")` |
| 文件系统写入失败 | 返回 `BusinessException("模型保存失败")` |
| 并发上传（同一管理员多次点击） | 前端按钮 loading 状态禁用；后端不额外加锁 |

---

## 5. 文件清单

### 新建文件

| 文件 | 说明 |
|---|---|
| `dh-server/.../connector/StyleTransferConnector.java` | AI 风格迁移连接器 |
| `dh-server/.../config/StyleTransferConfig.java` | 风格迁移配置属性 |
| `dh-server/.../avatar/AvatarService.java` | 换皮流水线编排 |
| `dh-server/.../avatar/TextureService.java` | 纹理处理（面部替换+羽化） |
| `dh-server/.../avatar/ModelFileService.java` | 模型文件管理 |

### 修改文件

| 文件 | 变更 |
|---|---|
| `dh-server/.../config/AppConfig.java` | 新增 `style-transfer` 和 `avatar` 配置属性 |
| `dh-server/.../controller/AdminController.java` | 新增上传/列表/切换/删除 API |
| `dh-server/src/main/resources/application.yml` | 新增配置项 |
| `dh-web/src/components/AdminPanel.vue` | 新增照片上传、形象列表 UI |
| `dh-web/src/components/AvatarSelector.vue` | 动态加载形象列表 |
| `dh-web/src/stores/avatar.js` | 新增 API actions |
| `dh-web/src/services/api.js` | 新增 avatar API 封装 |

---

## 6. 测试策略

### 单元测试

| 测试类 | 测试内容 |
|---|---|
| `StyleTransferConnectorTest` | Mock AI API 返回，验证请求格式和响应解析 |
| `TextureServiceTest` | 给定测试图片，验证面部替换和羽化效果 |
| `ModelFileServiceTest` | 验证目录创建、文件复制、清理 |
| `AvatarServiceTest` | Mock 所有依赖，验证流水线编排逻辑 |

### 集成测试

- 端到端：上传照片 → 检查生成的模型文件 → 前端加载模型
- 降级测试：AI API 不可用时，验证降级到直接贴图

### 前端测试

- AdminPanel：上传按钮状态、生成中 loading、结果展示
- AvatarSelector：加载形象列表、切换激活

---

## 7. 风险与注意事项

| 风险 | 应对 |
|---|---|
| 阿里云 AI 的 "插画风格迁移" 效果不稳定 | 提供预览功能，生成后管理员可查看效果，不满意可重试或手动调整 |
| 不同照片脸型/光照差异导致贴图违和 | TextureService 增加亮度/色温自适应调整 |
| Live2D 模型升级，面部区域坐标改变 | 面部区域作为可配置项，通过 Admin UI 可视化调整 |
| 生成的模型文件占用磁盘空间 | 提供删除功能；纹理使用 WebP 压缩 |

---

## 8. 变更记录

| 版本 | 日期 | 变更内容 |
|---|---|---|
| v1.0 | 2026-06-09 | 初版设计文档 |
