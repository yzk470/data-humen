# 二次元形象换皮 Live2D 数字人 — 设计文档

> **版本**: v2.0
> **日期**: 2026-06-09
> **状态**: 待审批
> **依赖**: Live2D Cubism SDK for Web 官方 Sample Model（Haru/Mao）

---

## 1. 概述

### 1.1 目标

为数字人虚拟助手系统增加"上传二次元图片 → 生成个性化 Live2D 形象"功能。

### 1.2 核心思路

- 使用 1 个通用的 Live2D 模型骨架（官方 Sample Model），`.model3.json` + 骨骼/变形器保持不变
- 管理员上传二次元角色图片，直接提取面部区域
- 将面部区域替换到 Live2D 纹理图集中
- 终端用户在对话页面可以从形象库中选择自己喜欢的形象

### 1.3 关键决策

| 维度 | 决策 | 说明 |
|---|---|---|
| Live2D 模型 | Cubism SDK 官方 Sample（Haru/Mao） | 免费，纹理图集结构已知 |
| 输入图片 | 二次元/插画风格图片 | 无需 AI 转换，直接贴图 |
| 替换策略 | 仅面部区域替换 | 身体/头发/服装保留原样 |
| 使用场景 | 管理员生成多个形象 + 终端用户自选 | 管理后台生成形象库，用户端可选 |
| 文件存储 | 服务端本地目录 | `digital-human-web/public/models/` |

---

## 2. 架构设计

### 2.1 整体流程

**管理端（生成形象）**：

```
┌─────────────────────────────────────────────────────────────┐
│                      管理后台 (Vue 3)                        │
│  AdminPanel.vue                                              │
│  ├─ 二次元图片上传 (<el-upload>)                             │
│  ├─ 形象名称输入                                             │
│  └─ 形象库管理（查看/删除）                                  │
└──────────────────────┬──────────────────────────────────────┘
                       │ POST /api/admin/avatar/upload (multipart/form-data)
                       ▼
┌─────────────────────────────────────────────────────────────┐
│                   Spring Boot 服务端                          │
│                                                              │
│  AdminController                                              │
│    └─ uploadAvatar(image, name) → AvatarService.generate()   │
│                                                              │
│  AvatarService (新建)                                        │
│    ├─ 1. 保存原始图片                                        │
│    ├─ 2. TextureService.replaceFace(image, baseTexture)      │
│    └─ 3. ModelFileService.createModel(...)                   │
│                                                              │
│  TextureService (新建)                                        │
│    ├─ 裁剪/缩放 → 适配面部 UV 区域                           │
│    └─ 纹理合成（替换面部，边缘羽化）                          │
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
│  │   └── haru.2048/           # 原始纹理目录                │
│  ├── generated/               # 生成的形象                  │
│  │   ├── avatar_001/                                        │
│  │   │   ├── meta.json         # {name, thumbnail, ...}     │
│  │   │   ├── haru.model3.json  # 复制自 base/              │
│  │   │   └── haru.2048/        # 含替换后的纹理             │
│  │   └── avatar_002/                                        │
│  │       └── ...                                            │
│  └── default → generated/avatar_001  # 默认形象             │
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
│  PUT /api/session/{id}/avatar → 用户切换形象                  │
└─────────────────────────────────────────────────────────────┘
```

### 2.2 模块依赖关系

```
TextureService (新建)     ←── AvatarService (新建)
ModelFileService (新建)   ←── AvatarService (新建)
AvatarService (新建)      ←── AdminController (修改)
                             ←── AppConfig (修改，新增 avatar 配置)
                             ←── SessionController (修改，新增用户切换形象 API)
```

与现有模块的关系：
- 复用 `dh-storage` 的 `ConfigStorageService` 管理配置
- 扩展 `AdminController` 增加上传和形象管理 API
- 扩展 `SessionController` 增加用户切换形象 API
- 与 `dh-orchestrator` / `dh-emotion` / `dh-signaling` / `dh-connector` **无耦合**

---

## 3. 模块详细设计

### 3.1 TextureService（纹理处理服务）

**职责**：将二次元图片的面部替换到 Live2D 纹理图集中

```java
// 目录: digital-human-server/src/main/java/com/dh/server/avatar/
@Service
public class TextureService {

    /**
     * 将二次元图片的面部区域替换到基础纹理中。
     *
     * @param faceImage    上传的二次元图片
     * @param baseTexture  原始 Live2D 纹理图集
     * @param faceRegion   面部在图集中的矩形区域 (x, y, w, h)
     * @return 合成后的新纹理图字节数组 (PNG)
     */
    public byte[] replaceFaceRegion(byte[] faceImage, byte[] baseTexture,
                                     Rectangle faceRegion) {
        // 1. 将 faceImage 缩放到 faceRegion 尺寸
        // 2. 对 faceImage 做轻微几何变形，匹配面部 UV 布局
        // 3. 将 baseTexture 中 faceRegion 区域替换为处理后的 faceImage
        // 4. 边缘羽化（alpha 渐变），使替换区域与周围自然过渡
        // 5. 返回合成后的纹理 PNG
    }
}
```

**面部区域坐标**：打开模型纹理图集（如 `haru.2048/texture_00.png`），人工标记面部所在的矩形区域，写入配置。

### 3.2 ModelFileService（模型文件管理）

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
     * 4. 复制基础纹理目录到新目录
     * 5. 用合成后的纹理替换面部纹理文件
     * 6. 生成缩略图
     * 7. 写入 meta.json ({name, createdAt})
     */
    public String createModel(String avatarId, String name, byte[] newTexture,
                               String textureFileName) { ... }

    /** 列出所有已生成的形象 */
    public List<AvatarInfo> listAvatars() { ... }

    /** 删除指定形象 */
    public void deleteAvatar(String avatarId) { ... }

    @Data
    public static class AvatarInfo {
        private String id;              // avatar_001
        private String name;            // "知性女助理"
        private String modelPath;       // /models/generated/avatar_001/haru.model3.json
        private String thumbnailPath;   // /models/generated/avatar_001/thumbnail.png
        private LocalDateTime createdAt;
    }
}
```

### 3.3 AvatarService（编排服务）

**职责**：编排整个换皮流水线

```java
// 目录: digital-human-server/src/main/java/com/dh/server/avatar/
@Service
public class AvatarService {

    private final TextureService textureService;
    private final ModelFileService modelFileService;

    /**
     * 换皮流水线：
     *   二次元图片 → 面部替换 → 保存模型
     */
    public ModelFileService.AvatarInfo generateAvatar(byte[] imageBytes, String name) {
        // 1. 读取基础纹理
        byte[] baseTexture = modelFileService.loadBaseTexture();

        // 2. 面部替换
        byte[] newTexture = textureService.replaceFaceRegion(
            imageBytes, baseTexture, getFaceRegion()
        );

        // 3. 创建模型文件
        String avatarId = "avatar_" + System.currentTimeMillis();
        String modelPath = modelFileService.createModel(
            avatarId, name, newTexture, "texture_00.png"
        );

        // 4. 返回形象信息
        return modelFileService.getAvatarInfo(avatarId);
    }
}
```

### 3.4 API 设计

#### 管理端 API（AdminController 扩展，需 Basic Auth）

| 接口 | 方法 | 说明 |
|---|---|---|
| `POST /api/admin/avatar/upload` | POST (multipart) | 上传二次元图片+名称，生成新形象 |
| `GET /api/admin/avatar/list` | GET | 列出所有已生成的形象 |
| `DELETE /api/admin/avatar/{id}` | DELETE | 删除指定形象 |

**`POST /api/admin/avatar/upload`**：

```
Request:  multipart/form-data
  - image: 二次元图片文件 (required, PNG/JPG)
  - name:  形象名称 (required), e.g. "知性女助理"

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
| `GET /api/avatar/list` | GET | 获取可用形象列表 |
| `PUT /api/session/{sessionId}/avatar` | PUT | 切换当前会话使用的形象 |

**`GET /api/avatar/list`** 响应：

```json
{
  "code": 200,
  "data": {
    "avatars": [
      { "id": "avatar_001", "name": "知性女助理", "thumbnailPath": "..." },
      { "id": "avatar_002", "name": "活力少年", "thumbnailPath": "..." }
    ],
    "defaultId": "avatar_001"
  }
}
```

**`PUT /api/session/{sessionId}/avatar`**：

```
Request:  { "avatarId": "avatar_002" }
Response: { "code": 200, "data": { "modelPath": "/models/generated/avatar_002/haru.model3.json" } }
```

用户的形象选择存储在 Session 级别，断开后恢复默认。

### 3.5 配置扩展（application.yml）

```yaml
app:
  avatar:
    base-model: models/base/haru.model3.json
    face-region: { x: 512, y: 128, width: 256, height: 256 }  # 根据实际模型确定
```

### 3.6 前端改造

#### AdminPanel.vue — 新增"形象管理"区块

```
┌──────────────────────────────────────────────┐
│  数字人形象管理                                │
│                                               │
│  ┌───────────────────────────────────┐        │
│  │  上传二次元图片生成新形象           │        │
│  │  形象名称: [________]              │        │
│  │  [选择图片] [开始生成]             │        │
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
└─────────────────────────────────┘
```

**组件变更清单**：

| 文件 | 变更类型 | 说明 |
|---|---|---|
| `AdminPanel.vue` | 修改 | 新增二次元图片上传+名称输入、形象库管理 |
| `ChatView.vue` | 修改 | 新增形象切换下拉菜单 |
| `Live2DCanvas.vue` | 修改 | 支持动态切换 `modelPath` |
| `stores/avatar.js` | 修改 | 新增 `uploadAvatar()`、`listAvatars()`、`deleteAvatar()`、`switchAvatar()` |
| `services/api.js` | 修改 | 新增 avatar 相关 API 调用 |

### 3.7 Live2D 模型准备

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
```

**面部区域确定**：打开 `texture_00.png`，人工标注面部矩形坐标，写入配置。

---

## 4. 错误处理

| 异常场景 | 处理策略 |
|---|---|
| 上传文件为空 | `BusinessException(400, "请选择图片文件")` |
| 文件不是图片 | `BusinessException(400, "仅支持 PNG/JPG 格式")` |
| 文件过大（>10MB） | `BusinessException(400, "图片大小不能超过 10MB")` |
| 文件系统写入失败 | `BusinessException(500, "模型保存失败，请检查磁盘空间")` |
| 删除不存在的形象 | `BusinessException(404, "形象不存在")` |
| 删除默认形象 | `BusinessException(400, "不能删除默认形象")` |

---

## 5. 文件清单

### 新建文件

| 文件 | 说明 |
|---|---|
| `dh-server/.../avatar/AvatarService.java` | 换皮流水线编排 |
| `dh-server/.../avatar/TextureService.java` | 纹理处理（面部缩放+替换+羽化） |
| `dh-server/.../avatar/ModelFileService.java` | 模型文件管理 |

### 修改文件

| 文件 | 变更 |
|---|---|
| `dh-server/.../config/AppConfig.java` | 新增 `avatar` 配置属性 |
| `dh-server/.../controller/AdminController.java` | 新增上传/列表/删除 API |
| `dh-server/.../controller/SessionController.java` | 新增用户切换形象 API |
| `dh-server/src/main/resources/application.yml` | 新增 `app.avatar` 配置 |
| `dh-web/src/components/AdminPanel.vue` | 新增图片上传、形象列表 UI |
| `dh-web/src/components/ChatView.vue` | 新增形象切换下拉菜单 |
| `dh-web/src/components/Live2DCanvas.vue` | 支持动态切换 modelPath |
| `dh-web/src/stores/avatar.js` | 新增 avatar actions |
| `dh-web/src/services/api.js` | 新增 avatar API 封装 |

---

## 6. 测试策略

| 测试类 | 测试内容 |
|---|---|
| `TextureServiceTest` | 给定测试图片和纹理，验证面部替换和羽化效果 |
| `ModelFileServiceTest` | 验证目录创建、文件复制、meta.json 读写、缩略图生成 |
| `AvatarServiceTest` | Mock 依赖，验证流水线编排逻辑 |

---

## 7. 变更记录

| 版本 | 日期 | 变更内容 |
|---|---|---|
| v1.0 | 2026-06-09 | 初版（含 AI 风格迁移） |
| v2.0 | 2026-06-09 | 去掉 AI 风格迁移，仅支持二次元图片直接贴图 |
