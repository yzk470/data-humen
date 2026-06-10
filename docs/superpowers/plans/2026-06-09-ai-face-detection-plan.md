# AI 智能面部识别 — 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 上传二次元图片后，点击"AI 识别人脸"按钮，后端调用通义千问 VL 自动返回面部坐标，前端裁剪框自动吸附，管理员可微调。

**Architecture:** 新建 QwenVLConnector（OpenAI 兼容格式），AdminController 新增 detect-face 端点，ImageCropper 新增 presetCrop prop，AdminPanel 新增 AI 按钮。

**Tech Stack:** 阿里云百炼 通义千问 VL (qwen-vl-max) + RestTemplate + Vue 3 Canvas API

**Spec Reference:** `docs/superpowers/specs/2026-06-09-ai-face-detection-design.md`

---

## Task 1: AppConfig + application.yml 配置

**Files:**
- Modify: `digital-human-server/src/main/java/com/dh/server/config/AppConfig.java`
- Modify: `digital-human-server/src/main/resources/application.yml`

- [ ] **Step 1: 在 AppConfig 中新增 QwenVL 内部类**

在 `AppConfig.java` 的 `private Avatar avatar = new Avatar();` 之后添加：

```java
    private QwenVL qwenVl = new QwenVL();

    @Data
    public static class QwenVL {
        private String apiKey;
        private String apiUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1";
        private String model = "qwen-vl-max";
    }
```

- [ ] **Step 2: 在 application.yml 中新增 qwen-vl 配置**

```yaml
  qwen-vl:
    api-key: ${QWEN_VL_API_KEY:}
    api-url: https://dashscope.aliyuncs.com/compatible-mode/v1
    model: qwen-vl-max
```

- [ ] **Step 3: 验证编译**

```bash
cd digital-human-server
mvn compile
```
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add digital-human-server/src/main/java/com/dh/server/config/AppConfig.java digital-human-server/src/main/resources/application.yml
git commit -m "feat: add QwenVL config properties"
```

---

## Task 2: QwenVLConnector

**Files:**
- Create: `digital-human-server/src/main/java/com/dh/server/connector/QwenVLConnector.java`

- [ ] **Step 1: 创建 QwenVLConnector**

```java
package com.dh.server.connector;

import com.dh.server.config.AppConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Slf4j
@Component
public class QwenVLConnector {

    private final AppConfig appConfig;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public QwenVLConnector(AppConfig appConfig) {
        this.appConfig = appConfig;
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
    }

    @Data
    public static class FaceRegion {
        private int x;
        private int y;
        private int width;
        private int height;
    }

    /**
     * 调用通义千问 VL 识别图片中的面部区域。
     *
     * @param imageBytes 图片字节数组
     * @return 面部矩形区域
     * @throws RuntimeException API 调用失败时抛出
     */
    public FaceRegion detectFace(byte[] imageBytes) {
        long startTime = System.currentTimeMillis();
        try {
            String base64 = Base64.getEncoder().encodeToString(imageBytes);
            String imageUrl = "data:image/png;base64," + base64;

            // 构建 OpenAI 兼容请求体
            List<Map<String, Object>> content = new ArrayList<>();
            Map<String, Object> imagePart = new HashMap<>();
            imagePart.put("type", "image_url");
            imagePart.put("image_url", Map.of("url", imageUrl));
            content.add(imagePart);

            Map<String, Object> textPart = new HashMap<>();
            textPart.put("type", "text");
            textPart.put("text", "识别图中人物的面部区域。仅返回JSON对象，格式为：{\"x\":数字,\"y\":数字,\"width\":数字,\"height\":数字}。x和y是面部左上角相对于图片左上角的像素坐标，width和height是面部的像素宽高。不要返回任何其他文字。");
            content.add(textPart);

            Map<String, Object> body = new HashMap<>();
            body.put("model", appConfig.getQwenVl().getModel());
            body.put("messages", List.of(Map.of("role", "user", "content", content)));
            body.put("temperature", 0.1);
            body.put("max_tokens", 200);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(appConfig.getQwenVl().getApiKey());

            String requestBody = objectMapper.writeValueAsString(body);
            HttpEntity<String> entity = new HttpEntity<>(requestBody, headers);

            ResponseEntity<String> response = restTemplate.exchange(
                appConfig.getQwenVl().getApiUrl() + "/chat/completions",
                HttpMethod.POST,
                entity,
                String.class
            );

            log.info("QwenVL 响应: {} (耗时 {}ms)", response.getBody(),
                System.currentTimeMillis() - startTime);

            // 解析响应
            String replyText = extractReplyText(response.getBody());
            FaceRegion region = parseFaceRegion(replyText);
            region = clampToImage(region);
            return region;

        } catch (Exception e) {
            log.error("QwenVL API 调用失败", e);
            throw new RuntimeException("AI 识别失败: " + e.getMessage());
        }
    }

    private String extractReplyText(String responseBody) throws Exception {
        JsonNode root = objectMapper.readTree(responseBody);
        return root.path("choices").get(0)
            .path("message").path("content").asText();
    }

    private FaceRegion parseFaceRegion(String text) throws Exception {
        // 清理可能的多余字符
        String json = text.trim();
        // 去掉 markdown 代码块标记
        json = json.replaceAll("```json\\s*", "").replaceAll("```\\s*", "");
        JsonNode node = objectMapper.readTree(json);
        FaceRegion region = new FaceRegion();
        region.setX(node.path("x").asInt());
        region.setY(node.path("y").asInt());
        region.setWidth(node.path("width").asInt());
        region.setHeight(node.path("height").asInt());
        return region;
    }

    private FaceRegion clampToImage(FaceRegion r) {
        // 确保坐标非负
        if (r.x < 0) r.x = 0;
        if (r.y < 0) r.y = 0;
        if (r.width < 64) r.width = 128;
        if (r.height < 64) r.height = 128;
        return r;
    }
}
```

- [ ] **Step 2: 验证编译**

```bash
cd digital-human-server
mvn compile
```
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add digital-human-server/src/main/java/com/dh/server/connector/QwenVLConnector.java
git commit -m "feat: add QwenVLConnector for AI face detection via Qwen VL"
```

---

## Task 3: QwenVLConnector 单元测试

**Files:**
- Create: `digital-human-server/src/test/java/com/dh/server/connector/QwenVLConnectorTest.java`

- [ ] **Step 1: 编写测试**

```java
package com.dh.server.connector;

import com.dh.server.config.AppConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class QwenVLConnectorTest {

    private QwenVLConnector connector;
    private AppConfig appConfig;

    @BeforeEach
    void setUp() {
        appConfig = new AppConfig();
        appConfig.getQwenVl().setApiKey("test-key");
        appConfig.getQwenVl().setApiUrl("https://dashscope.aliyuncs.com/compatible-mode/v1");
        appConfig.getQwenVl().setModel("qwen-vl-max");
        connector = new QwenVLConnector(appConfig);
    }

    @Test
    void shouldConstructSuccessfully() {
        assertNotNull(connector);
    }

    @Test
    void shouldFailGracefullyWithInvalidApiKey() {
        byte[] imageBytes = new byte[]{0x01, 0x02, 0x03};
        RuntimeException e = assertThrows(RuntimeException.class, () -> {
            connector.detectFace(imageBytes);
        });
        assertTrue(e.getMessage().contains("AI 识别失败"));
    }

    @Test
    void shouldCreateFaceRegionCorrectly() {
        QwenVLConnector.FaceRegion region = new QwenVLConnector.FaceRegion();
        region.setX(100);
        region.setY(50);
        region.setWidth(200);
        region.setHeight(200);
        assertEquals(100, region.getX());
        assertEquals(50, region.getY());
        assertEquals(200, region.getWidth());
        assertEquals(200, region.getHeight());
    }
}
```

注：实际 API 调用测试需要有效的 API Key，通过 `@Disabled` 标记。上述测试验证连接器构造和基本行为。

- [ ] **Step 2: 运行测试**

```bash
cd digital-human-server
mvn test -Dtest=QwenVLConnectorTest
```
Expected: 3 tests PASS（其中 API 调用测试可能需要 API Key）

- [ ] **Step 3: Commit**

```bash
git add digital-human-server/src/test/java/com/dh/server/connector/QwenVLConnectorTest.java
git commit -m "test: add QwenVLConnector unit tests"
```

---

## Task 4: AdminController detect-face 端点

**Files:**
- Modify: `digital-human-server/src/main/java/com/dh/server/controller/AdminController.java`

- [ ] **Step 1: 注入 QwenVLConnector 并新增端点**

在 AdminController 字段区添加：

```java
    private final QwenVLConnector qwenVLConnector;
```

在 AdminController 类末尾（`}` 之前）添加：

```java
    @PostMapping("/avatar/detect-face")
    public Result<Map<String, Integer>> detectFace(
            @RequestParam("image") MultipartFile image) {

        if (image.isEmpty()) {
            throw new BusinessException(400, "请选择图片文件");
        }

        if (image.getSize() > 10 * 1024 * 1024) {
            throw new BusinessException(400, "图片大小不能超过 10MB");
        }

        try {
            byte[] imageBytes = image.getBytes();
            QwenVLConnector.FaceRegion region = qwenVLConnector.detectFace(imageBytes);
            return Result.ok(Map.of(
                "x", region.getX(),
                "y", region.getY(),
                "width", region.getWidth(),
                "height", region.getHeight()
            ));
        } catch (Exception e) {
            log.error("AI 人脸检测失败", e);
            throw new BusinessException(500, "AI 识别失败，请手动框选面部区域");
        }
    }
```

在文件头部新增 import：

```java
import com.dh.server.connector.QwenVLConnector;
```

- [ ] **Step 2: 验证编译**

```bash
cd digital-human-server
mvn compile
```
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add digital-human-server/src/main/java/com/dh/server/controller/AdminController.java
git commit -m "feat: add POST /api/admin/avatar/detect-face endpoint"
```

---

## Task 5: ImageCropper — presetCrop prop

**Files:**
- Modify: `digital-human-web/src/components/ImageCropper.vue`

- [ ] **Step 1: 新增 presetCrop prop，监听变化自动吸附**

在 `defineProps` 中添加：

```javascript
const props = defineProps({
  imageFile: {
    type: File,
    default: null
  },
  presetCrop: {
    type: Object,
    default: null
    // { x: 100, y: 50, width: 200, height: 200 }
  }
})
```

添加 watch 监听 presetCrop 变化：

```javascript
watch(() => props.presetCrop, (preset) => {
  if (preset && img && imageLoaded.value) {
    cropX.value = preset.x
    cropY.value = preset.y
    cropW.value = preset.width
    cropH.value = preset.height
    nextTick(() => draw())
    emitCrop()
  }
})
```

确保 `watch` 已在 `import { ref, watch, nextTick } from 'vue'` 导入（现有代码已有）。

- [ ] **Step 2: 验证前端构建**

```bash
cd digital-human-web
npm run build
```
Expected: 构建成功

- [ ] **Step 3: Commit**

```bash
git add digital-human-web/src/components/ImageCropper.vue
git commit -m "feat: add presetCrop prop to ImageCropper for AI face detection results"
```

---

## Task 6: AdminPanel — AI 识别人脸按钮

**Files:**
- Modify: `digital-human-web/src/components/AdminPanel.vue`
- Modify: `digital-human-web/src/services/api.js`

- [ ] **Step 1: 在 api.js 新增 detectFace API**

在 `api.js` 中追加：

```javascript
export function detectFace(imageFile) {
  const formData = new FormData()
  formData.append('image', imageFile)
  return adminApi.post('/avatar/detect-face', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}
```

- [ ] **Step 2: 在 AdminPanel.vue 新增 AI 按钮和状态**

在选择图片按钮之后、ImageCropper 之前添加：

```vue
      <el-form-item v-if="selectedImage" label="AI 辅助">
        <el-button
          type="warning"
          @click="aiDetectFace"
          :loading="aiDetecting"
          :disabled="!selectedImage"
        >
          🤖 AI 识别人脸
        </el-button>
        <span v-if="aiDetected" style="color: #67C23A; margin-left: 8px;">✓ 已识别</span>
      </el-form-item>
```

在 script 中添加：

```javascript
import { detectFace } from '../services/api'

const aiDetecting = ref(false)
const aiDetected = ref(false)
const presetCrop = ref(null)

async function aiDetectFace() {
  if (!selectedImage.value) return
  aiDetecting.value = true
  aiDetected.value = false
  try {
    const { data } = await detectFace(selectedImage.value)
    if (data.code === 200) {
      presetCrop.value = data.data
      aiDetected.value = true
      ElMessage.success('AI 已识别面部区域，可手动微调')
    } else {
      ElMessage.warning(data.message || 'AI 识别失败')
    }
  } catch (e) {
    ElMessage.error('AI 识别失败，请手动框选')
  } finally {
    aiDetecting.value = false
  }
}
```

在 `onImageSelected` 中重置 AI 状态：

```javascript
function onImageSelected(uploadFile) {
  selectedImage.value = uploadFile.raw
  cropData.value = null
  aiDetected.value = false
  presetCrop.value = null
}
```

更新 ImageCropper 标签传入 presetCrop：

```vue
        <ImageCropper
          :imageFile="selectedImage"
          :presetCrop="presetCrop"
          @crop-change="onCropChange"
        />
```

- [ ] **Step 3: 验证前端构建**

```bash
cd digital-human-web
npm run build
```
Expected: 构建成功

- [ ] **Step 4: Commit**

```bash
git add digital-human-web/src/services/api.js digital-human-web/src/components/AdminPanel.vue
git commit -m "feat: add AI face detection button to AdminPanel"
```

---

## Task 7: 端到端验证

- [ ] **Step 1: 运行全部后端测试**

```bash
cd digital-human-server
mvn test
```
Expected: 所有测试通过

- [ ] **Step 2: 启动后端并测试 detect-face API**

```bash
# 在另一个终端启动后端
cd digital-human-server && mvn spring-boot:run

# 测试 API（需要 QWEN_VL_API_KEY 环境变量或配置）
curl -u "admin:dhAdmin2024" -X POST http://localhost:9091/api/admin/avatar/detect-face \
  -F "image=@../digital-human-web/public/models/豆包.png"
```
Expected: 返回面部坐标 JSON

- [ ] **Step 3: 启动前端并测试 UI**

```bash
cd digital-human-web && npm run dev
```
访问 http://localhost:5173/admin → 选择图片 → 点击"AI 识别人脸" → 裁剪框自动定位

- [ ] **Step 4: Commit 最终版**

```bash
git add -A
git commit -m "test: end-to-end verification of AI face detection"
```

---

## 依赖关系

```
Task 1 (Config)
    │
Task 2 (QwenVLConnector)
    │
    ├── Task 3 (Test)
    │
    └── Task 4 (detect-face endpoint)
            │
            └── Task 5+6 (前端: ImageCropper + AdminPanel)
                    │
                    Task 7 (E2E 验证)
```

任务 1-4 串行执行（后端），任务 5-6 可并行（前端），任务 7 最后跑全量。
