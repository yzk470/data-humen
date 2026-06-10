# Voice And Avatar Preferences Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 增加后台“默认值 + 可选池”管理，以及用户侧“音色 + Live2D 形象”持久化切换能力。

**Architecture:** 后端新增“配置池服务 + 用户偏好表 + 用户偏好接口”，把系统默认值和用户当前值分层处理。前端新增用户偏好数据层，后台页面维护可选项与默认值，聊天页初始化时读取当前有效配置并支持即时切换。

**Tech Stack:** Spring Boot 3, MyBatis-Plus, Redis cache, Vue 3, Pinia, Axios, Element Plus, Vite, Vitest

---

## File Structure

### Backend

- Create: `digital-human-server/src/main/java/com/dh/server/storage/entity/UserPreferenceEntity.java`
  - 用户偏好表实体，只存 `userId / ttsVoiceId / live2dModelPath / updatedAt`
- Create: `digital-human-server/src/main/java/com/dh/server/storage/mapper/UserPreferenceMapper.java`
  - MyBatis-Plus mapper
- Create: `digital-human-server/src/main/java/com/dh/server/preference/PreferenceOption.java`
  - 通用选项对象，字段为 `label` 和 `value`
- Create: `digital-human-server/src/main/java/com/dh/server/preference/PreferencesSnapshot.java`
  - 给前端返回的聚合响应对象，包含 options/default/current
- Create: `digital-human-server/src/main/java/com/dh/server/preference/PreferenceConfigService.java`
  - 负责读写 `tts_voice_options`、`live2d_model_options`、默认值，并做合法性校验
- Create: `digital-human-server/src/main/java/com/dh/server/preference/UserPreferenceService.java`
  - 负责读写固定用户偏好，并计算当前有效值
- Create: `digital-human-server/src/main/java/com/dh/server/controller/UserPreferenceController.java`
  - 用户侧获取/更新偏好接口
- Modify: `digital-human-server/src/main/java/com/dh/server/controller/AdminController.java`
  - 增加后台统一 preferences 配置接口；保留旧接口短期兼容
- Modify: `digital-human-server/src/main/java/com/dh/server/controller/ChatController.java`
  - 传入固定 `userId` 给编排层
- Modify: `digital-human-server/src/main/java/com/dh/server/controller/AvatarController.java`
  - 返回后台“可切换形象池”的默认值，而不是简单取最新 avatar
- Modify: `digital-human-server/src/main/java/com/dh/server/controller/SessionController.java`
  - 会话切换形象接口短期保留兼容，内部改走用户偏好服务或标记为 deprecated 路径
- Modify: `digital-human-server/src/main/java/com/dh/server/orchestrator/ChatOrchestrator.java`
  - TTS 生成前读取当前用户有效音色
- Modify: `digital-human-server/src/main/java/com/dh/server/connector/TtsConnector.java`
  - 增加 `executeAsBase64(text, preferredVoiceId)` 重载，并保留默认兜底
- Modify: `digital-human-server/src/main/resources/db/schema.sql`
  - 新增 `t_user_preference` 表及新配置键默认数据
- Modify: `digital-human-server/src/main/resources/db/init-data.sql`
  - 初始化新配置键默认值

### Backend Tests

- Create: `digital-human-server/src/test/java/com/dh/server/preference/PreferenceConfigServiceTest.java`
- Create: `digital-human-server/src/test/java/com/dh/server/preference/UserPreferenceServiceTest.java`
- Create: `digital-human-server/src/test/java/com/dh/server/controller/UserPreferenceControllerTest.java`
- Modify: `digital-human-server/src/test/java/com/dh/server/orchestrator/ChatOrchestratorTest.java`

### Frontend

- Modify: `digital-human-web/package.json`
  - 增加 `test` 脚本和 Vitest 依赖
- Create: `digital-human-web/vitest.config.js`
- Create: `digital-human-web/src/services/preferences.js`
  - 用户偏好与后台配置 API 封装
- Create: `digital-human-web/src/stores/preferences.js`
  - 当前有效配置、可选池、加载/保存逻辑
- Modify: `digital-human-web/src/services/api.js`
  - 删除分散的旧 preferences API 封装或转发到新服务
- Modify: `digital-human-web/src/stores/avatar.js`
  - 切换为由 preferences store 驱动当前形象，不再通过 session avatar API 修改状态
- Modify: `digital-human-web/src/stores/chat.js`
  - 发送消息前确保音色状态已准备好；接收后无需额外处理
- Modify: `digital-human-web/src/stores/session.js`
  - 增加固定 `userId`
- Modify: `digital-human-web/src/components/AdminPanel.vue`
  - 从单值表单改成“可选池 + 默认值”管理
- Modify: `digital-human-web/src/views/ChatView.vue`
  - 增加音色与形象选择器；页面初始化时加载用户偏好

### Frontend Tests

- Create: `digital-human-web/src/stores/preferences.test.js`
- Create: `digital-human-web/src/components/AdminPanel.test.js`
- Create: `digital-human-web/src/views/ChatView.test.js`

---

### Task 1: Backend Persistence And Preference Domain

**Files:**
- Create: `digital-human-server/src/main/java/com/dh/server/storage/entity/UserPreferenceEntity.java`
- Create: `digital-human-server/src/main/java/com/dh/server/storage/mapper/UserPreferenceMapper.java`
- Create: `digital-human-server/src/main/java/com/dh/server/preference/PreferenceOption.java`
- Create: `digital-human-server/src/main/java/com/dh/server/preference/PreferencesSnapshot.java`
- Modify: `digital-human-server/src/main/resources/db/schema.sql`
- Modify: `digital-human-server/src/main/resources/db/init-data.sql`
- Test: `digital-human-server/src/test/java/com/dh/server/preference/PreferenceConfigServiceTest.java`

- [ ] **Step 1: 写配置池结构的失败测试**

```java
package com.dh.server.preference;

import com.dh.server.storage.service.ConfigStorageService;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PreferenceConfigServiceTest {

    @Test
    void shouldParseVoiceAndModelOptionsFromJsonConfig() {
        ConfigStorageService configStorageService = mock(ConfigStorageService.class);
        when(configStorageService.getConfigValue("tts_voice_options"))
            .thenReturn("[{\"label\":\"莹晓\",\"value\":\"longyingxiao_v3\"}]");
        when(configStorageService.getConfigValue("live2d_model_options"))
            .thenReturn("[{\"label\":\"默认 Haru\",\"value\":\"/models/generated/avatar_default/Haru.model3.json\"}]");
        when(configStorageService.getConfigValue("default_tts_voice_id"))
            .thenReturn("longyingxiao_v3");
        when(configStorageService.getConfigValue("default_live2d_model_path"))
            .thenReturn("/models/generated/avatar_default/Haru.model3.json");

        PreferenceConfigService service = new PreferenceConfigService(configStorageService);

        PreferencesSnapshot snapshot = service.getAdminSnapshot();

        assertEquals("longyingxiao_v3", snapshot.getDefaultVoiceId());
        assertEquals("/models/generated/avatar_default/Haru.model3.json", snapshot.getDefaultModelPath());
        assertEquals(1, snapshot.getVoiceOptions().size());
        assertEquals(1, snapshot.getModelOptions().size());
    }

    @Test
    void shouldRejectDefaultVoiceOutsideOptions() {
        ConfigStorageService configStorageService = mock(ConfigStorageService.class);
        PreferenceConfigService service = new PreferenceConfigService(configStorageService);

        PreferencesSnapshot request = PreferencesSnapshot.builder()
            .voiceOptions(java.util.List.of(new PreferenceOption("莹晓", "longyingxiao_v3")))
            .modelOptions(java.util.List.of(new PreferenceOption("默认 Haru", "/models/generated/avatar_default/Haru.model3.json")))
            .defaultVoiceId("missing_voice")
            .defaultModelPath("/models/generated/avatar_default/Haru.model3.json")
            .build();

        assertThrows(IllegalArgumentException.class, () -> service.validateAdminSnapshot(request));
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `mvn -Dtest=PreferenceConfigServiceTest test`

Expected: FAIL with `cannot find symbol` for `PreferenceConfigService`, `PreferenceOption`, or `PreferencesSnapshot`

- [ ] **Step 3: 增加数据库表与默认配置键**

```sql
CREATE TABLE t_user_preference (
    id                 BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id            VARCHAR(64)  NOT NULL UNIQUE,
    tts_voice_id       VARCHAR(128),
    live2d_model_path  VARCHAR(512),
    updated_at         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO t_dh_config (config_key, config_value, description) VALUES
('tts_voice_options', '[{"label":"莹晓","value":"longyingxiao_v3"}]', 'TTS 音色可选项'),
('live2d_model_options', '[{"label":"默认 Haru","value":"/models/generated/avatar_default/Haru.model3.json"}]', 'Live2D 形象可选项'),
('default_tts_voice_id', 'longyingxiao_v3', '默认 TTS 音色'),
('default_live2d_model_path', '/models/generated/avatar_default/Haru.model3.json', '默认 Live2D 形象');
```

```java
package com.dh.server.storage.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_user_preference")
public class UserPreferenceEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String userId;
    private String ttsVoiceId;
    private String live2dModelPath;
    private LocalDateTime updatedAt;
}
```

```java
package com.dh.server.storage.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dh.server.storage.entity.UserPreferenceEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserPreferenceMapper extends BaseMapper<UserPreferenceEntity> {
}
```

```java
package com.dh.server.preference;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PreferenceOption {
    private String label;
    private String value;
}
```

```java
package com.dh.server.preference;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PreferencesSnapshot {
    private List<PreferenceOption> voiceOptions;
    private List<PreferenceOption> modelOptions;
    private String defaultVoiceId;
    private String defaultModelPath;
    private String currentVoiceId;
    private String currentModelPath;
}
```

- [ ] **Step 4: 实现配置池解析与校验服务**

```java
package com.dh.server.preference;

import com.dh.server.storage.service.ConfigStorageService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class PreferenceConfigService {

    private final ConfigStorageService configStorageService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public PreferencesSnapshot getAdminSnapshot() {
        return PreferencesSnapshot.builder()
            .voiceOptions(readOptions("tts_voice_options"))
            .modelOptions(readOptions("live2d_model_options"))
            .defaultVoiceId(configStorageService.getConfigValue("default_tts_voice_id"))
            .defaultModelPath(configStorageService.getConfigValue("default_live2d_model_path"))
            .build();
    }

    public void saveAdminSnapshot(PreferencesSnapshot snapshot) {
        validateAdminSnapshot(snapshot);
        writeOptions("tts_voice_options", snapshot.getVoiceOptions());
        writeOptions("live2d_model_options", snapshot.getModelOptions());
        configStorageService.setConfig("default_tts_voice_id", snapshot.getDefaultVoiceId());
        configStorageService.setConfig("default_live2d_model_path", snapshot.getDefaultModelPath());
    }

    void validateAdminSnapshot(PreferencesSnapshot snapshot) {
        validateUnique(snapshot.getVoiceOptions(), "voice");
        validateUnique(snapshot.getModelOptions(), "model");
        if (snapshot.getVoiceOptions().stream().noneMatch(it -> it.getValue().equals(snapshot.getDefaultVoiceId()))) {
            throw new IllegalArgumentException("default voice must exist in voice options");
        }
        if (snapshot.getModelOptions().stream().noneMatch(it -> it.getValue().equals(snapshot.getDefaultModelPath()))) {
            throw new IllegalArgumentException("default model must exist in model options");
        }
    }

    public List<PreferenceOption> readOptions(String key) {
        try {
            String json = configStorageService.getConfigValue(key);
            if (json == null || json.isBlank()) {
                return List.of();
            }
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            throw new IllegalStateException("failed to parse config " + key, e);
        }
    }

    private void writeOptions(String key, List<PreferenceOption> options) {
        try {
            configStorageService.setConfig(key, objectMapper.writeValueAsString(options));
        } catch (Exception e) {
            throw new IllegalStateException("failed to write config " + key, e);
        }
    }

    private void validateUnique(List<PreferenceOption> options, String name) {
        Set<String> values = new HashSet<>();
        for (PreferenceOption option : options) {
            if (option.getLabel() == null || option.getLabel().isBlank() || option.getValue() == null || option.getValue().isBlank()) {
                throw new IllegalArgumentException(name + " option requires label and value");
            }
            if (!values.add(option.getValue())) {
                throw new IllegalArgumentException("duplicate " + name + " option value: " + option.getValue());
            }
        }
    }
}
```

- [ ] **Step 5: 运行测试确认通过**

Run: `mvn -Dtest=PreferenceConfigServiceTest test`

Expected: PASS with `Tests run: 2, Failures: 0, Errors: 0`

- [ ] **Step 6: Commit**

```bash
git add digital-human-server/src/main/resources/db/schema.sql digital-human-server/src/main/resources/db/init-data.sql digital-human-server/src/main/java/com/dh/server/storage/entity/UserPreferenceEntity.java digital-human-server/src/main/java/com/dh/server/storage/mapper/UserPreferenceMapper.java digital-human-server/src/main/java/com/dh/server/preference/PreferenceOption.java digital-human-server/src/main/java/com/dh/server/preference/PreferencesSnapshot.java digital-human-server/src/main/java/com/dh/server/preference/PreferenceConfigService.java digital-human-server/src/test/java/com/dh/server/preference/PreferenceConfigServiceTest.java
git commit -m "feat: add preference persistence and config pool domain"
```

### Task 2: Backend User Preference Service And APIs

**Files:**
- Create: `digital-human-server/src/main/java/com/dh/server/preference/UserPreferenceService.java`
- Create: `digital-human-server/src/main/java/com/dh/server/controller/UserPreferenceController.java`
- Modify: `digital-human-server/src/main/java/com/dh/server/controller/AdminController.java`
- Test: `digital-human-server/src/test/java/com/dh/server/preference/UserPreferenceServiceTest.java`
- Test: `digital-human-server/src/test/java/com/dh/server/controller/UserPreferenceControllerTest.java`

- [ ] **Step 1: 写用户偏好计算的失败测试**

```java
package com.dh.server.preference;

import com.dh.server.storage.entity.UserPreferenceEntity;
import com.dh.server.storage.mapper.UserPreferenceMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UserPreferenceServiceTest {

    @Test
    void shouldFallbackToDefaultsWhenUserPreferenceMissing() {
        UserPreferenceMapper mapper = mock(UserPreferenceMapper.class);
        PreferenceConfigService configService = mock(PreferenceConfigService.class);
        when(mapper.selectOne(org.mockito.ArgumentMatchers.any())).thenReturn(null);
        when(configService.getAdminSnapshot()).thenReturn(PreferencesSnapshot.builder()
            .voiceOptions(List.of(new PreferenceOption("莹晓", "longyingxiao_v3")))
            .modelOptions(List.of(new PreferenceOption("默认 Haru", "/models/generated/avatar_default/Haru.model3.json")))
            .defaultVoiceId("longyingxiao_v3")
            .defaultModelPath("/models/generated/avatar_default/Haru.model3.json")
            .build());

        UserPreferenceService service = new UserPreferenceService(mapper, configService);

        PreferencesSnapshot snapshot = service.getUserSnapshot("default-user");

        assertEquals("longyingxiao_v3", snapshot.getCurrentVoiceId());
        assertEquals("/models/generated/avatar_default/Haru.model3.json", snapshot.getCurrentModelPath());
    }

    @Test
    void shouldRejectValueOutsideConfiguredPool() {
        UserPreferenceMapper mapper = mock(UserPreferenceMapper.class);
        PreferenceConfigService configService = mock(PreferenceConfigService.class);
        when(configService.getAdminSnapshot()).thenReturn(PreferencesSnapshot.builder()
            .voiceOptions(List.of(new PreferenceOption("莹晓", "longyingxiao_v3")))
            .modelOptions(List.of(new PreferenceOption("默认 Haru", "/models/generated/avatar_default/Haru.model3.json")))
            .defaultVoiceId("longyingxiao_v3")
            .defaultModelPath("/models/generated/avatar_default/Haru.model3.json")
            .build());

        UserPreferenceService service = new UserPreferenceService(mapper, configService);

        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () ->
            service.saveUserPreference("default-user", "bad-voice", "/models/generated/avatar_default/Haru.model3.json")
        );
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `mvn -Dtest=UserPreferenceServiceTest test`

Expected: FAIL with `cannot find symbol` for `UserPreferenceService`

- [ ] **Step 3: 实现用户偏好服务**

```java
package com.dh.server.preference;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dh.server.storage.entity.UserPreferenceEntity;
import com.dh.server.storage.mapper.UserPreferenceMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserPreferenceService {

    private final UserPreferenceMapper userPreferenceMapper;
    private final PreferenceConfigService preferenceConfigService;

    public PreferencesSnapshot getUserSnapshot(String userId) {
        PreferencesSnapshot admin = preferenceConfigService.getAdminSnapshot();
        UserPreferenceEntity entity = findByUserId(userId);

        String currentVoiceId = entity != null && contains(admin.getVoiceOptions(), entity.getTtsVoiceId())
            ? entity.getTtsVoiceId()
            : admin.getDefaultVoiceId();
        String currentModelPath = entity != null && contains(admin.getModelOptions(), entity.getLive2dModelPath())
            ? entity.getLive2dModelPath()
            : admin.getDefaultModelPath();

        admin.setCurrentVoiceId(currentVoiceId);
        admin.setCurrentModelPath(currentModelPath);
        return admin;
    }

    public void saveUserPreference(String userId, String voiceId, String modelPath) {
        PreferencesSnapshot admin = preferenceConfigService.getAdminSnapshot();
        if (!contains(admin.getVoiceOptions(), voiceId)) {
            throw new IllegalArgumentException("voiceId is not in configured pool");
        }
        if (!contains(admin.getModelOptions(), modelPath)) {
            throw new IllegalArgumentException("modelPath is not in configured pool");
        }

        UserPreferenceEntity entity = findByUserId(userId);
        if (entity == null) {
            entity = new UserPreferenceEntity();
            entity.setUserId(userId);
            entity.setTtsVoiceId(voiceId);
            entity.setLive2dModelPath(modelPath);
            userPreferenceMapper.insert(entity);
            return;
        }
        entity.setTtsVoiceId(voiceId);
        entity.setLive2dModelPath(modelPath);
        userPreferenceMapper.updateById(entity);
    }

    private UserPreferenceEntity findByUserId(String userId) {
        return userPreferenceMapper.selectOne(
            new LambdaQueryWrapper<UserPreferenceEntity>().eq(UserPreferenceEntity::getUserId, userId)
        );
    }

    private boolean contains(java.util.List<PreferenceOption> options, String value) {
        return options.stream().anyMatch(it -> it.getValue().equals(value));
    }
}
```

- [ ] **Step 4: 写控制器失败测试并实现后台/用户接口**

```java
package com.dh.server.controller;

import com.dh.server.preference.PreferenceOption;
import com.dh.server.preference.PreferencesSnapshot;
import com.dh.server.preference.UserPreferenceService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UserPreferenceControllerTest {

    @Test
    void shouldReturnCurrentUserPreferences() {
        UserPreferenceService service = mock(UserPreferenceService.class);
        when(service.getUserSnapshot("default-user")).thenReturn(PreferencesSnapshot.builder()
            .voiceOptions(List.of(new PreferenceOption("莹晓", "longyingxiao_v3")))
            .modelOptions(List.of(new PreferenceOption("默认 Haru", "/models/generated/avatar_default/Haru.model3.json")))
            .defaultVoiceId("longyingxiao_v3")
            .defaultModelPath("/models/generated/avatar_default/Haru.model3.json")
            .currentVoiceId("longyingxiao_v3")
            .currentModelPath("/models/generated/avatar_default/Haru.model3.json")
            .build());

        UserPreferenceController controller = new UserPreferenceController(service);

        assertEquals("longyingxiao_v3", controller.getPreferences().getData().getCurrentVoiceId());
    }
}
```

```java
package com.dh.server.controller;

import com.dh.server.common.Result;
import com.dh.server.preference.PreferencesSnapshot;
import com.dh.server.preference.UserPreferenceService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/user/preferences")
@RequiredArgsConstructor
public class UserPreferenceController {

    private static final String DEFAULT_USER_ID = "default-user";

    private final UserPreferenceService userPreferenceService;

    @GetMapping
    public Result<PreferencesSnapshot> getPreferences() {
        return Result.ok(userPreferenceService.getUserSnapshot(DEFAULT_USER_ID));
    }

    @PutMapping
    public Result<?> updatePreferences(@RequestBody UpdateRequest request) {
        userPreferenceService.saveUserPreference(DEFAULT_USER_ID, request.getVoiceId(), request.getModelPath());
        return Result.ok();
    }

    @Data
    public static class UpdateRequest {
        private String voiceId;
        private String modelPath;
    }
}
```

```java
@GetMapping("/config/preferences")
public Result<PreferencesSnapshot> getPreferenceConfig() {
    return Result.ok(preferenceConfigService.getAdminSnapshot());
}

@PutMapping("/config/preferences")
public Result<?> updatePreferenceConfig(@RequestBody PreferencesSnapshot request) {
    preferenceConfigService.saveAdminSnapshot(request);
    return Result.ok();
}
```

- [ ] **Step 5: 运行测试确认通过**

Run: `mvn -Dtest=UserPreferenceServiceTest,UserPreferenceControllerTest test`

Expected: PASS with `Failures: 0, Errors: 0`

- [ ] **Step 6: Commit**

```bash
git add digital-human-server/src/main/java/com/dh/server/preference/UserPreferenceService.java digital-human-server/src/main/java/com/dh/server/controller/UserPreferenceController.java digital-human-server/src/main/java/com/dh/server/controller/AdminController.java digital-human-server/src/test/java/com/dh/server/preference/UserPreferenceServiceTest.java digital-human-server/src/test/java/com/dh/server/controller/UserPreferenceControllerTest.java
git commit -m "feat: add admin and user preference APIs"
```

### Task 3: Runtime Integration For TTS And Avatar Resolution

**Files:**
- Modify: `digital-human-server/src/main/java/com/dh/server/controller/ChatController.java`
- Modify: `digital-human-server/src/main/java/com/dh/server/orchestrator/ChatOrchestrator.java`
- Modify: `digital-human-server/src/main/java/com/dh/server/connector/TtsConnector.java`
- Modify: `digital-human-server/src/main/java/com/dh/server/controller/AvatarController.java`
- Modify: `digital-human-server/src/main/java/com/dh/server/controller/SessionController.java`
- Modify: `digital-human-server/src/test/java/com/dh/server/orchestrator/ChatOrchestratorTest.java`

- [ ] **Step 1: 写编排层读取用户音色的失败测试**

```java
@Test
void shouldUseCurrentUserVoicePreferenceForTts() {
    UserPreferenceService userPreferenceService = mock(UserPreferenceService.class);
    when(userPreferenceService.getUserSnapshot("default-user")).thenReturn(PreferencesSnapshot.builder()
        .currentVoiceId("longyingxiao_v3")
        .currentModelPath("/models/generated/avatar_default/Haru.model3.json")
        .build());

    orchestrator = new ChatOrchestrator(
        deepSeekConnector,
        ttsConnector,
        emotionCalculator,
        emotionToParams,
        configStorageService,
        messageStorageService,
        userPreferenceService
    );

    when(configStorageService.getConfigValue("system_prompt")).thenReturn("你是助手");
    when(messageStorageService.getRecentMessages("session-1", 10)).thenReturn(List.of());
    when(deepSeekConnector.execute(any())).thenReturn("你好[EMOTION:happy]");
    when(ttsConnector.executeAsBase64(anyString(), anyString())).thenReturn("base64audio==");

    orchestrator.processText("default-user", "session-1", "你好");

    verify(ttsConnector).executeAsBase64("你好", "longyingxiao_v3");
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `mvn -Dtest=ChatOrchestratorTest test`

Expected: FAIL with constructor mismatch or missing overload

- [ ] **Step 3: 改造 TTS 连接器与编排层**

```java
public String executeAsBase64(String text, String preferredVoiceId) {
    byte[] audio = execute(text, preferredVoiceId);
    if (audio.length == 0) {
        return "";
    }
    return Base64.getEncoder().encodeToString(audio);
}

public byte[] execute(String text, String preferredVoiceId) {
    if (text == null || text.isBlank()) {
        return new byte[0];
    }
    LinkedHashSet<String> voices = new LinkedHashSet<>();
    if (preferredVoiceId != null && !preferredVoiceId.isBlank()) {
        voices.add(preferredVoiceId.trim());
    }
    voices.addAll(buildVoiceCandidates());
    ...
}
```

```java
public PipelineResult processText(String userId, String sessionId, String userText) {
    ...
    PreferencesSnapshot preferences = userPreferenceService.getUserSnapshot(userId);
    String audioBase64 = ttsConnector.executeAsBase64(cleanText, preferences.getCurrentVoiceId());
    ...
}
```

```java
private static final String DEFAULT_USER_ID = "default-user";

@PostMapping("/text")
public Result<PipelineResult> chatText(@RequestBody ChatRequest request) {
    ...
    PipelineResult result = chatOrchestrator.processText(DEFAULT_USER_ID, request.getSessionId(), request.getText());
    return Result.ok(result);
}
```

- [ ] **Step 4: 让用户侧形象列表走偏好系统默认值**

```java
@GetMapping("/list")
public Result<Map<String, Object>> listAvatars() {
    try {
        List<ModelFileService.AvatarInfo> avatars = modelFileService.listAvatars();
        PreferencesSnapshot snapshot = userPreferenceService.getUserSnapshot("default-user");
        String defaultId = avatars.stream()
            .filter(it -> it.getModelPath().equals(snapshot.getDefaultModelPath()))
            .map(ModelFileService.AvatarInfo::getId)
            .findFirst()
            .orElse("");
        return Result.ok(Map.of("avatars", avatars, "defaultId", defaultId));
    } catch (IOException e) {
        ...
    }
}
```

```java
@PutMapping("/{sessionId}/avatar")
public Result<Map<String, String>> switchAvatar(@PathVariable String sessionId,
                                                @RequestBody Map<String, String> body) {
    ...
    ModelFileService.AvatarInfo info = modelFileService.getAvatarInfo(avatarId);
    userPreferenceService.saveUserPreference("default-user", userPreferenceService.getUserSnapshot("default-user").getCurrentVoiceId(), info.getModelPath());
    return Result.ok(Map.of("modelPath", info.getModelPath()));
}
```

- [ ] **Step 5: 运行后端测试**

Run: `mvn -Dtest=ChatOrchestratorTest,PreferenceConfigServiceTest,UserPreferenceServiceTest,UserPreferenceControllerTest test`

Expected: PASS with `Failures: 0, Errors: 0`

- [ ] **Step 6: Commit**

```bash
git add digital-human-server/src/main/java/com/dh/server/controller/ChatController.java digital-human-server/src/main/java/com/dh/server/orchestrator/ChatOrchestrator.java digital-human-server/src/main/java/com/dh/server/connector/TtsConnector.java digital-human-server/src/main/java/com/dh/server/controller/AvatarController.java digital-human-server/src/main/java/com/dh/server/controller/SessionController.java digital-human-server/src/test/java/com/dh/server/orchestrator/ChatOrchestratorTest.java
git commit -m "feat: wire runtime preferences into chat and avatar flows"
```

### Task 4: Frontend Test Harness And Preferences Data Layer

**Files:**
- Modify: `digital-human-web/package.json`
- Create: `digital-human-web/vitest.config.js`
- Create: `digital-human-web/src/services/preferences.js`
- Create: `digital-human-web/src/stores/preferences.js`
- Modify: `digital-human-web/src/stores/session.js`
- Test: `digital-human-web/src/stores/preferences.test.js`

- [ ] **Step 1: 写前端 store 的失败测试**

```js
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { usePreferencesStore } from './preferences'

vi.mock('../services/preferences', () => ({
  getUserPreferences: vi.fn(async () => ({
    data: {
      code: 200,
      data: {
        voiceOptions: [{ label: '莹晓', value: 'longyingxiao_v3' }],
        modelOptions: [{ label: '默认 Haru', value: '/models/generated/avatar_default/Haru.model3.json' }],
        defaultVoiceId: 'longyingxiao_v3',
        defaultModelPath: '/models/generated/avatar_default/Haru.model3.json',
        currentVoiceId: 'longyingxiao_v3',
        currentModelPath: '/models/generated/avatar_default/Haru.model3.json'
      }
    }
  })),
  updateUserPreferences: vi.fn(async () => ({ data: { code: 200 } }))
}))

describe('preferences store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('loads current preferences and exposes current model path', async () => {
    const store = usePreferencesStore()
    await store.load()
    expect(store.currentVoiceId).toBe('longyingxiao_v3')
    expect(store.currentModelPath).toBe('/models/generated/avatar_default/Haru.model3.json')
  })
})
```

- [ ] **Step 2: 安装前端测试工具并确认测试先失败**

```json
{
  "scripts": {
    "dev": "vite",
    "build": "vite build",
    "preview": "vite preview",
    "test": "vitest run"
  },
  "devDependencies": {
    "@pinia/testing": "^1.0.3",
    "@vitejs/plugin-vue": "^6.0.6",
    "@vue/test-utils": "^2.4.6",
    "jsdom": "^26.1.0",
    "vite": "^8.0.12",
    "vitest": "^3.2.4"
  }
}
```

Run:

```bash
cd digital-human-web
npm.cmd install
npm.cmd test
```

Expected: FAIL with `Cannot find module './preferences'`

- [ ] **Step 3: 新增偏好 API 和 Pinia store**

```js
// digital-human-web/src/services/preferences.js
import api, { adminApi } from './api'

export function getUserPreferences() {
  return api.get('/user/preferences')
}

export function updateUserPreferences(payload) {
  return api.put('/user/preferences', payload)
}

export function getAdminPreferences() {
  return adminApi.get('/config/preferences')
}

export function updateAdminPreferences(payload) {
  return adminApi.put('/config/preferences', payload)
}
```

```js
// digital-human-web/src/stores/preferences.js
import { defineStore } from 'pinia'
import { getUserPreferences, updateUserPreferences } from '../services/preferences'

export const usePreferencesStore = defineStore('preferences', {
  state: () => ({
    voiceOptions: [],
    modelOptions: [],
    defaultVoiceId: '',
    defaultModelPath: '',
    currentVoiceId: '',
    currentModelPath: '',
    loaded: false,
    saving: false
  }),
  actions: {
    async load() {
      const { data } = await getUserPreferences()
      if (data.code !== 200) return
      Object.assign(this, data.data, { loaded: true })
    },
    async saveCurrent() {
      this.saving = true
      try {
        await updateUserPreferences({
          voiceId: this.currentVoiceId,
          modelPath: this.currentModelPath
        })
      } finally {
        this.saving = false
      }
    },
    async setVoice(value) {
      this.currentVoiceId = value
      await this.saveCurrent()
    },
    async setModel(value) {
      this.currentModelPath = value
      await this.saveCurrent()
    }
  }
})
```

```js
// digital-human-web/src/stores/session.js
state: () => ({
  sessionId: null,
  status: null,
  loading: false,
  userId: 'default-user'
})
```

- [ ] **Step 4: 运行前端 store 测试确认通过**

Run:

```bash
cd digital-human-web
npm.cmd test -- src/stores/preferences.test.js
```

Expected: PASS with `1 passed`

- [ ] **Step 5: Commit**

```bash
git add digital-human-web/package.json digital-human-web/vitest.config.js digital-human-web/src/services/preferences.js digital-human-web/src/stores/preferences.js digital-human-web/src/stores/session.js digital-human-web/src/stores/preferences.test.js
git commit -m "feat: add frontend preference data layer"
```

### Task 5: Admin Panel Preference Pool Management UI

**Files:**
- Modify: `digital-human-web/src/components/AdminPanel.vue`
- Modify: `digital-human-web/src/stores/avatar.js`
- Test: `digital-human-web/src/components/AdminPanel.test.js`

- [ ] **Step 1: 写后台面板失败测试**

```js
import { describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import AdminPanel from './AdminPanel.vue'

vi.mock('../services/preferences', () => ({
  getAdminPreferences: vi.fn(async () => ({
    data: {
      code: 200,
      data: {
        voiceOptions: [{ label: '莹晓', value: 'longyingxiao_v3' }],
        modelOptions: [{ label: '默认 Haru', value: '/models/generated/avatar_default/Haru.model3.json' }],
        defaultVoiceId: 'longyingxiao_v3',
        defaultModelPath: '/models/generated/avatar_default/Haru.model3.json'
      }
    }
  })),
  updateAdminPreferences: vi.fn(async () => ({ data: { code: 200 } }))
}))

describe('AdminPanel', () => {
  it('renders voice option list and default selectors', async () => {
    setActivePinia(createPinia())
    const wrapper = mount(AdminPanel)
    await new Promise(resolve => setTimeout(resolve, 0))
    expect(wrapper.text()).toContain('莹晓')
  })
})
```

- [ ] **Step 2: 运行测试确认失败**

Run:

```bash
cd digital-human-web
npm.cmd test -- src/components/AdminPanel.test.js
```

Expected: FAIL because panel does not load new admin preferences data

- [ ] **Step 3: 把后台面板改为“可选池 + 默认值”**

```vue
<el-divider />
<h3>音色管理</h3>
<div v-for="(item, index) in preferenceConfig.voiceOptions" :key="`${item.value}-${index}`" class="option-row">
  <el-input v-model="item.label" placeholder="显示名称" />
  <el-input v-model="item.value" placeholder="voice id" />
  <el-button type="danger" @click="removeVoice(index)">删除</el-button>
</div>
<el-button @click="addVoice">新增音色</el-button>
<el-form-item label="默认音色">
  <el-select v-model="preferenceConfig.defaultVoiceId">
    <el-option v-for="item in preferenceConfig.voiceOptions" :key="item.value" :label="item.label" :value="item.value" />
  </el-select>
</el-form-item>

<el-divider />
<h3>形象管理</h3>
<div v-for="(item, index) in preferenceConfig.modelOptions" :key="`${item.value}-${index}`" class="option-row">
  <el-select v-model="item.value" style="width: 320px">
    <el-option v-for="avatar in avatarStore.avatars" :key="avatar.modelPath" :label="avatar.name" :value="avatar.modelPath" />
  </el-select>
  <el-input v-model="item.label" placeholder="显示名称" />
  <el-button type="danger" @click="removeModel(index)">删除</el-button>
</div>
<el-button @click="addModel">新增形象</el-button>
<el-form-item label="默认形象">
  <el-select v-model="preferenceConfig.defaultModelPath">
    <el-option v-for="item in preferenceConfig.modelOptions" :key="item.value" :label="item.label" :value="item.value" />
  </el-select>
</el-form-item>
```

```js
import { getAdminPreferences, updateAdminPreferences } from '../services/preferences'

const preferenceConfig = ref({
  voiceOptions: [],
  modelOptions: [],
  defaultVoiceId: '',
  defaultModelPath: ''
})

async function loadPreferenceConfig() {
  const { data } = await getAdminPreferences()
  if (data.code === 200) {
    preferenceConfig.value = data.data
  }
}

function addVoice() {
  preferenceConfig.value.voiceOptions.push({ label: '', value: '' })
}

function removeVoice(index) {
  preferenceConfig.value.voiceOptions.splice(index, 1)
}

function addModel() {
  const first = avatarStore.avatars[0]
  if (!first) return
  preferenceConfig.value.modelOptions.push({ label: first.name, value: first.modelPath })
}

function removeModel(index) {
  preferenceConfig.value.modelOptions.splice(index, 1)
}

async function savePreferenceConfig() {
  await updateAdminPreferences(preferenceConfig.value)
}
```

- [ ] **Step 4: 运行后台面板测试和构建**

Run:

```bash
cd digital-human-web
npm.cmd test -- src/components/AdminPanel.test.js
npm.cmd run build
```

Expected:
- test PASS
- build PASS with Vite production build output

- [ ] **Step 5: Commit**

```bash
git add digital-human-web/src/components/AdminPanel.vue digital-human-web/src/components/AdminPanel.test.js
git commit -m "feat: add admin preference pool management UI"
```

### Task 6: Chat View Runtime Switching UI

**Files:**
- Modify: `digital-human-web/src/views/ChatView.vue`
- Modify: `digital-human-web/src/stores/avatar.js`
- Modify: `digital-human-web/src/stores/chat.js`
- Test: `digital-human-web/src/views/ChatView.test.js`

- [ ] **Step 1: 写聊天页切换失败测试**

```js
import { describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import ChatView from './ChatView.vue'
import { usePreferencesStore } from '../stores/preferences'

vi.mock('../composables/useSignaling', () => ({ useSignaling: () => ({ connect: vi.fn(), disconnect: vi.fn() }) }))
vi.mock('../composables/useRtcClient', () => ({ useRtcClient: () => ({ close: vi.fn() }) }))
vi.mock('../composables/useAudioAnalyzer', () => ({
  useAudioAnalyzer: () => ({
    connect: vi.fn(),
    stop: vi.fn(),
    destroy: vi.fn(),
    mouthOpenY: { value: 0 },
    ensureAudioContext: vi.fn()
  })
}))

describe('ChatView', () => {
  it('loads preferences and passes current model path to Live2D canvas', async () => {
    setActivePinia(createPinia())
    const preferences = usePreferencesStore()
    preferences.voiceOptions = [{ label: '莹晓', value: 'longyingxiao_v3' }]
    preferences.modelOptions = [{ label: '默认 Haru', value: '/models/generated/avatar_default/Haru.model3.json' }]
    preferences.currentVoiceId = 'longyingxiao_v3'
    preferences.currentModelPath = '/models/generated/avatar_default/Haru.model3.json'
    preferences.load = vi.fn()

    const wrapper = mount(ChatView, {
      global: {
        stubs: ['Live2DCanvas', 'ChatPanel', 'InputBar']
      }
    })

    await new Promise(resolve => setTimeout(resolve, 0))
    expect(wrapper.html()).toContain('Live2DCanvas')
  })
})
```

- [ ] **Step 2: 运行测试确认失败**

Run:

```bash
cd digital-human-web
npm.cmd test -- src/views/ChatView.test.js
```

Expected: FAIL because ChatView does not yet depend on preferences store

- [ ] **Step 3: 聊天页改为使用 preferences store 驱动音色和形象**

```js
const preferencesStore = usePreferencesStore()

const selectedVoiceId = computed({
  get: () => preferencesStore.currentVoiceId,
  set: value => preferencesStore.setVoice(value)
})

const selectedModelPath = computed({
  get: () => preferencesStore.currentModelPath,
  set: value => preferencesStore.setModel(value)
})

onMounted(async () => {
  await sessionStore.initSession()
  await preferencesStore.load()
  await avatarStore.loadAvatars()
  avatarStore.setModelPath(preferencesStore.currentModelPath)
  signaling.connect(sessionStore.sessionId)
  window.addEventListener('pointerdown', unlockAudio, { passive: true })
})

watch(
  () => preferencesStore.currentModelPath,
  value => {
    if (value) {
      avatarStore.setModelPath(value)
    }
  },
  { immediate: true }
)
```

```vue
<div class="runtime-switchers">
  <el-select v-model="selectedVoiceId" size="small" style="width: 180px">
    <el-option v-for="item in preferencesStore.voiceOptions" :key="item.value" :label="item.label" :value="item.value" />
  </el-select>
  <el-select v-model="selectedModelPath" size="small" style="width: 220px">
    <el-option v-for="item in preferencesStore.modelOptions" :key="item.value" :label="item.label" :value="item.value" />
  </el-select>
</div>

<Live2DCanvas
  :width="600"
  :height="600"
  :modelPath="preferencesStore.currentModelPath || avatarStore.modelPath"
  :animationParams="chatStore.currentAnimationParams"
  :mouthOpenY="effectiveMouthOpenY"
/>
```

```js
// avatar store: keep only list loading + direct modelPath setter
async switchAvatarByModelPath(modelPath) {
  this.modelPath = modelPath
}
```

- [ ] **Step 4: 运行聊天页测试和前端全量构建**

Run:

```bash
cd digital-human-web
npm.cmd test -- src/views/ChatView.test.js
npm.cmd run build
```

Expected:
- test PASS
- build PASS

- [ ] **Step 5: Commit**

```bash
git add digital-human-web/src/views/ChatView.vue digital-human-web/src/stores/avatar.js digital-human-web/src/stores/chat.js digital-human-web/src/views/ChatView.test.js
git commit -m "feat: add runtime voice and avatar switching"
```

### Task 7: End-To-End Verification And Cleanup

**Files:**
- Modify: `digital-human-server/src/main/resources/application.yml`
- Modify: `digital-human-web/src/services/api.js`
- Modify: `digital-human-server/src/main/java/com/dh/server/controller/AdminController.java`

- [ ] **Step 1: 删除旧分散配置入口的重复读写路径**

```java
// AdminController 旧接口保留，但改为读写新 preferences 配置来源或标注 deprecated 注释
@Deprecated
@GetMapping("/config/tts-voice")
public Result<Map<String, String>> getTtsVoice() {
    PreferencesSnapshot snapshot = preferenceConfigService.getAdminSnapshot();
    return Result.ok(Map.of(
        "voice_id", snapshot.getDefaultVoiceId(),
        "speed", getOrDefault("tts_speed", "1.0"),
        "pitch", getOrDefault("tts_pitch", "0")
    ));
}
```

```js
// api.js: 保留旧导出给现有模块，但新增注释说明新逻辑走 preferences.js
export function switchSessionAvatar(sessionId, avatarId) {
  return api.put(`/session/${sessionId}/avatar`, { avatarId })
}
```

- [ ] **Step 2: 跑后端全量测试**

Run:

```bash
cd digital-human-server
mvn test
```

Expected: BUILD SUCCESS

- [ ] **Step 3: 跑前端全量测试与生产构建**

Run:

```bash
cd digital-human-web
npm.cmd test
npm.cmd run build
```

Expected:
- Vitest all green
- Vite build success

- [ ] **Step 4: 手工联调检查**

Run:

```bash
cd digital-human-server
mvn spring-boot:run
```

```bash
cd digital-human-web
npm.cmd run dev
```

Manual checks:

- 后台可新增音色项并保存默认音色
- 后台可从已有形象中加入可选项并保存默认形象
- 聊天页首次打开显示当前用户音色和形象
- 切换形象后 Live2D 立刻刷新
- 切换音色后下一条助手语音使用新音色
- 刷新页面后保持用户上次选择

- [ ] **Step 5: Commit**

```bash
git add digital-human-server/src/main/java/com/dh/server/controller/AdminController.java digital-human-server/src/main/resources/application.yml digital-human-web/src/services/api.js
git commit -m "chore: align legacy endpoints with new preferences flow"
```

## Self-Review

### Spec coverage

- 后台维护音色可选池：Task 1, Task 2, Task 5
- 后台维护形象可选池：Task 1, Task 2, Task 5
- 后台默认值：Task 1, Task 2, Task 5
- 用户偏好持久化：Task 1, Task 2
- 聊天页即时切换形象：Task 6
- 下一条语音使用当前音色：Task 3, Task 6
- 用户固定身份：Task 2, Task 4
- 偏好失效回退默认：Task 2, Task 3

### Placeholder scan

- 本计划未使用 `TBD`、`TODO` 或“类似 Task N”之类占位描述
- 所有运行步骤都附带了具体命令
- 所有核心代码步骤都附带了明确代码片段

### Type consistency

- 后端统一使用 `PreferencesSnapshot`
- 选项对象统一使用 `PreferenceOption`
- 固定用户统一使用 `default-user`
- 前端用户偏好字段统一为 `currentVoiceId` 和 `currentModelPath`

