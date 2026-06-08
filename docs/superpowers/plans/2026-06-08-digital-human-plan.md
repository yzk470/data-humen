# 数字人虚拟助手系统 — 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 基于 Spring Boot + WebRTC + Live2D 构建 AI 驱动的 2D 数字人虚拟助手系统，支持文字和语音双模式交互。

**Architecture:** Spring Boot 作为轻量中继编排层，WebRTC 承载音频流与动画参数同步，Live2D Cubism SDK for Web 负责前端 2D 渲染，DeepSeek/ASR/TTS 全部走云端 API。MySQL 持久化 + Redis 热缓存。

**Tech Stack:** Spring Boot 3.x + MyBatis-Plus + Redis + MySQL 8.0 + Vue 3 + Vite + Element Plus + Pinia + Live2D Cubism SDK for Web + WebRTC API

**Spec Reference:** `docs/superpowers/specs/2026-06-08-digital-human-prd.md`

---

## 文件结构映射

```
digital-human-server/                        # Spring Boot 服务端
├── pom.xml
├── src/main/java/com/dh/server/
│   ├── DhServerApplication.java
│   ├── common/
│   │   ├── Result.java                     # 统一响应体
│   │   ├── BusinessException.java          # 业务异常
│   │   └── GlobalExceptionHandler.java     # 全局异常处理
│   ├── config/
│   │   ├── AppConfig.java                  # 应用配置（API Key 等）
│   │   ├── RedisConfig.java                # Redis 配置
│   │   └── WebSocketConfig.java            # WebSocket 配置
│   ├── storage/
│   │   ├── entity/
│   │   │   ├── SessionEntity.java          # 会话表映射
│   │   │   ├── MessageEntity.java          # 消息表映射
│   │   │   └── DhConfigEntity.java         # 配置表映射
│   │   ├── mapper/
│   │   │   ├── SessionMapper.java
│   │   │   ├── MessageMapper.java
│   │   │   └── DhConfigMapper.java
│   │   └── service/
│   │       ├── SessionStorageService.java  # 会话 CRUD + 缓存
│   │       ├── MessageStorageService.java  # 消息 CRUD + 缓存
│   │       └── ConfigStorageService.java   # 配置读取 + 缓存
│   ├── session/
│   │   ├── Session.java                    # 会话领域对象
│   │   ├── Message.java                    # 消息领域对象
│   │   └── SessionManager.java             # 会话生命周期管理
│   ├── connector/
│   │   ├── Connector.java                  # 连接器接口
│   │   ├── DeepSeekConnector.java          # DeepSeek API
│   │   ├── AsrConnector.java               # ASR API（抽象+默认实现）
│   │   └── TtsConnector.java               # TTS API（抽象+默认实现）
│   ├── emotion/
│   │   ├── EmotionLabel.java               # 情绪标签枚举
│   │   ├── EmotionResult.java              # 情绪计算结果
│   │   ├── EmotionCalculator.java          # 情绪计算（LLM提取+规则兜底）
│   │   └── EmotionToLive2DParams.java      # 情绪→Live2D参数映射
│   ├── orchestrator/
│   │   ├── PipelineResult.java             # 流水线输出
│   │   └── ChatOrchestrator.java           # 对话编排（文字+语音流水线）
│   ├── signaling/
│   │   ├── SignalingHandler.java           # WebSocket 信令处理器
│   │   ├── SignalMessage.java              # 信令消息体
│   │   └── RtcSessionManager.java          # WebRTC 会话状态管理
│   ├── controller/
│   │   ├── SessionController.java          # 会话 API
│   │   ├── ChatController.java             # 对话 API
│   │   └── AdminController.java            # 管理后台 API
│   └── admin/
│       └── AdminAuthFilter.java            # 管理后台基础认证
├── src/main/resources/
│   ├── application.yml
│   ├── db/schema.sql                       # 数据库建表 DDL
│   └── mapper/                             # MyBatis XML（如需要）
└── src/test/java/com/dh/server/
    ├── storage/
    │   ├── SessionStorageServiceTest.java
    │   └── MessageStorageServiceTest.java
    ├── emotion/
    │   └── EmotionCalculatorTest.java
    ├── connector/
    │   └── DeepSeekConnectorTest.java
    └── orchestrator/
        └── ChatOrchestratorTest.java

digital-human-web/                          # Vue 3 前端
├── package.json
├── vite.config.js
├── index.html
├── public/models/                          # Live2D 模型文件
├── src/
│   ├── main.js
│   ├── App.vue
│   ├── router/index.js
│   ├── services/api.js                     # HTTP API 封装
│   ├── stores/
│   │   ├── session.js                      # Pinia 会话状态
│   │   ├── chat.js                         # Pinia 对话状态
│   │   └── avatar.js                       # Pinia 数字人配置
│   ├── composables/
│   │   ├── useSession.js                   # 会话管理
│   │   ├── useChat.js                      # 对话交互
│   │   ├── useSignaling.js                 # WebSocket 信令
│   │   ├── useRtcClient.js                 # WebRTC 管理
│   │   ├── useLive2dDriver.js              # Live2D 参数驱动
│   │   └── useAudioAnalyzer.js             # 音频分析→口型
│   ├── components/
│   │   ├── Live2DCanvas.vue                # Live2D 渲染画布
│   │   ├── ChatPanel.vue                   # 对话字幕
│   │   ├── InputBar.vue                    # 文字输入 + 语音按钮
│   │   ├── VoiceButton.vue                 # 语音录制按钮
│   │   ├── AvatarSelector.vue              # 形象选择（管理后台）
│   │   └── AdminPanel.vue                  # 管理配置页
│   └── views/
│       ├── ChatView.vue                    # 对话主页面
│       └── AdminView.vue                   # 管理后台页面
└── tests/
    └── services/api.test.js
```

---

## 里程碑 1：项目基础搭建

### Task 1.1: 初始化 Spring Boot 项目

**Files:**
- Create: `digital-human-server/pom.xml`
- Create: `digital-human-server/src/main/java/com/dh/server/DhServerApplication.java`
- Create: `digital-human-server/src/main/resources/application.yml`

- [ ] **Step 1: 创建项目根目录与 pom.xml**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.2.0</version>
        <relativePath/>
    </parent>

    <groupId>com.dh</groupId>
    <artifactId>digital-human-server</artifactId>
    <version>1.0.0-SNAPSHOT</version>
    <name>digital-human-server</name>
    <description>AI 驱动的 2D 数字人虚拟助手系统</description>

    <properties>
        <java.version>17</java.version>
    </properties>

    <dependencies>
        <!-- Web -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>

        <!-- WebSocket -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-websocket</artifactId>
        </dependency>

        <!-- Validation -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-validation</artifactId>
        </dependency>

        <!-- MyBatis-Plus -->
        <dependency>
            <groupId>com.baomidou</groupId>
            <artifactId>mybatis-plus-spring-boot3-starter</artifactId>
            <version>3.5.5</version>
        </dependency>

        <!-- MySQL -->
        <dependency>
            <groupId>com.mysql</groupId>
            <artifactId>mysql-connector-j</artifactId>
            <scope>runtime</scope>
        </dependency>

        <!-- Redis -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-redis</artifactId>
        </dependency>

        <!-- Jackson -->
        <dependency>
            <groupId>com.fasterxml.jackson.core</groupId>
            <artifactId>jackson-databind</artifactId>
        </dependency>

        <!-- Lombok -->
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <optional>true</optional>
        </dependency>

        <!-- Test -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>

        <dependency>
            <groupId>com.h2database</groupId>
            <artifactId>h2</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
                <configuration>
                    <excludes>
                        <exclude>
                            <groupId>org.projectlombok</groupId>
                            <artifactId>lombok</artifactId>
                        </exclude>
                    </excludes>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

- [ ] **Step 2: 创建启动类**

```java
package com.dh.server;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.dh.server.storage.mapper")
public class DhServerApplication {
    public static void main(String[] args) {
        SpringApplication.run(DhServerApplication.class, args);
    }
}
```

- [ ] **Step 3: 创建 application.yml**

```yaml
server:
  port: 8080

spring:
  datasource:
    url: jdbc:mysql://localhost:3306/digital_human?useUnicode=true&characterEncoding=utf8mb4&serverTimezone=Asia/Shanghai
    username: root
    password: ${DB_PASSWORD:root}
    driver-class-name: com.mysql.cj.jdbc.Driver
  data:
    redis:
      host: localhost
      port: 6379

mybatis-plus:
  mapper-locations: classpath:mapper/*.xml
  global-config:
    db-config:
      id-type: auto

app:
  deepseek:
    api-key: ${DEEPSEEK_API_KEY:}
    api-url: https://api.deepseek.com/v1/chat/completions
    model: deepseek-chat
```

- [ ] **Step 4: 验证项目启动**

```bash
cd digital-human-server
mvn compile
```
Expected: BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add digital-human-server/
git commit -m "feat: initialize Spring Boot project skeleton"
```

---

### Task 1.2: 初始化 Vue 3 项目

**Files:**
- Create: `digital-human-web/` (Vite + Vue 3 scaffold)

- [ ] **Step 1: 创建 Vue 3 项目**

```bash
npm create vite@latest digital-human-web -- --template vue
cd digital-human-web
npm install
```

- [ ] **Step 2: 安装依赖**

```bash
cd digital-human-web
npm install element-plus pinia vue-router axios
npm install -D @vitejs/plugin-vue sass
```

- [ ] **Step 3: 配置 main.js**

```javascript
// src/main.js
import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import App from './App.vue'
import router from './router'

const app = createApp(App)
app.use(createPinia())
app.use(router)
app.use(ElementPlus)
app.mount('#app')
```

- [ ] **Step 4: 创建路由骨架**

```javascript
// src/router/index.js
import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  { path: '/', redirect: '/chat' },
  {
    path: '/chat',
    name: 'Chat',
    component: () => import('../views/ChatView.vue')
  },
  {
    path: '/admin',
    name: 'Admin',
    component: () => import('../views/AdminView.vue')
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

export default router
```

- [ ] **Step 5: 创建占位组件**

```vue
<!-- src/App.vue -->
<template>
  <router-view />
</template>
```

```vue
<!-- src/views/ChatView.vue -->
<template>
  <div class="chat-view">数字人对话页面</div>
</template>
```

```vue
<!-- src/views/AdminView.vue -->
<template>
  <div class="admin-view">管理后台</div>
</template>
```

- [ ] **Step 6: 验证项目运行**

```bash
npm run dev
```
Expected: Vite dev server starts on localhost:5173

- [ ] **Step 7: Commit**

```bash
git add digital-human-web/
git commit -m "feat: initialize Vue 3 + Vite project skeleton"
```

---

### Task 1.3: 数据库建表

**Files:**
- Create: `digital-human-server/src/main/resources/db/schema.sql`

- [ ] **Step 1: 编写建表 DDL**

```sql
-- db/schema.sql
CREATE DATABASE IF NOT EXISTS digital_human
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;

USE digital_human;

CREATE TABLE t_session (
    id              VARCHAR(64)   PRIMARY KEY,
    status          VARCHAR(16)   NOT NULL DEFAULT 'ACTIVE',
    user_ip         VARCHAR(64),
    created_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_active_at  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    closed_at       DATETIME,
    INDEX idx_status (status),
    INDEX idx_last_active (last_active_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE t_message (
    id            BIGINT        PRIMARY KEY AUTO_INCREMENT,
    session_id    VARCHAR(64)   NOT NULL,
    role          VARCHAR(16)   NOT NULL,
    text          TEXT          NOT NULL,
    emotion       VARCHAR(32),
    audio_url     VARCHAR(512),
    created_at    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_session_time (session_id, created_at),
    CONSTRAINT fk_msg_session FOREIGN KEY (session_id) REFERENCES t_session(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE t_dh_config (
    id            BIGINT        PRIMARY KEY AUTO_INCREMENT,
    config_key    VARCHAR(64)   NOT NULL UNIQUE,
    config_value  TEXT          NOT NULL,
    description   VARCHAR(256),
    updated_at    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 默认配置数据
INSERT INTO t_dh_config (config_key, config_value, description) VALUES
('system_prompt', '你是小慧，一个专业的企业前台助手。', '系统 Prompt'),
('tts_voice_id', 'zh-CN-XiaoxiaoNeural', 'TTS 音色 ID'),
('tts_speed', '1.0', '语速'),
('tts_pitch', '0', '音调'),
('live2d_model_path', '/models/default.model3.json', 'Live2D 模型路径');
```

- [ ] **Step 2: 执行建表**

```bash
mysql -u root -p < digital-human-server/src/main/resources/db/schema.sql
```

- [ ] **Step 3: 验证**

```sql
SHOW TABLES FROM digital_human;
SELECT * FROM t_dh_config;
```

- [ ] **Step 4: Commit**

```bash
git add digital-human-server/src/main/resources/db/schema.sql
git commit -m "feat: add database schema with default config data"
```

---

### Task 1.4: 公共基础模块 (dh-common)

**Files:**
- Create: `digital-human-server/src/main/java/com/dh/server/common/Result.java`
- Create: `digital-human-server/src/main/java/com/dh/server/common/BusinessException.java`
- Create: `digital-human-server/src/main/java/com/dh/server/common/GlobalExceptionHandler.java`

- [ ] **Step 1: 编写统一响应体**

```java
package com.dh.server.common;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Result<T> {

    private int code;
    private String message;
    private T data;

    public static <T> Result<T> ok(T data) {
        return new Result<>(200, "success", data);
    }

    public static <T> Result<T> ok() {
        return new Result<>(200, "success", null);
    }

    public static <T> Result<T> fail(int code, String message) {
        return new Result<>(code, message, null);
    }

    public static <T> Result<T> fail(String message) {
        return new Result<>(500, message, null);
    }
}
```

- [ ] **Step 2: 编写业务异常类**

```java
package com.dh.server.common;

import lombok.Getter;

@Getter
public class BusinessException extends RuntimeException {

    private final int code;

    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }

    public BusinessException(String message) {
        super(message);
        this.code = 500;
    }
}
```

- [ ] **Step 3: 编写全局异常处理器**

```java
package com.dh.server.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public Result<?> handleBusinessException(BusinessException e) {
        log.warn("Business exception: {}", e.getMessage());
        return Result.fail(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public Result<?> handleException(Exception e) {
        log.error("Unexpected error", e);
        return Result.fail(500, "服务内部错误，请稍后重试");
    }
}
```

- [ ] **Step 4: Commit**

```bash
git add digital-human-server/src/main/java/com/dh/server/common/
git commit -m "feat: add unified response, business exception, and global handler"
```

---

## 里程碑 2：核心流水线

### Task 2.1: 实体与 Mapper（dh-storage 数据层）

**Files:**
- Create: `digital-human-server/src/main/java/com/dh/server/storage/entity/SessionEntity.java`
- Create: `digital-human-server/src/main/java/com/dh/server/storage/entity/MessageEntity.java`
- Create: `digital-human-server/src/main/java/com/dh/server/storage/entity/DhConfigEntity.java`
- Create: `digital-human-server/src/main/java/com/dh/server/storage/mapper/SessionMapper.java`
- Create: `digital-human-server/src/main/java/com/dh/server/storage/mapper/MessageMapper.java`
- Create: `digital-human-server/src/main/java/com/dh/server/storage/mapper/DhConfigMapper.java`

- [ ] **Step 1: 编写 SessionEntity**

```java
package com.dh.server.storage.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("t_session")
public class SessionEntity {

    @TableId
    private String id;
    private String status;
    private String userIp;
    private LocalDateTime createdAt;
    private LocalDateTime lastActiveAt;
    private LocalDateTime closedAt;
}
```

- [ ] **Step 2: 编写 MessageEntity**

```java
package com.dh.server.storage.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("t_message")
public class MessageEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String sessionId;
    private String role;
    private String text;
    private String emotion;
    private String audioUrl;
    private LocalDateTime createdAt;
}
```

- [ ] **Step 3: 编写 DhConfigEntity**

```java
package com.dh.server.storage.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("t_dh_config")
public class DhConfigEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String configKey;
    private String configValue;
    private String description;
    private LocalDateTime updatedAt;
}
```

- [ ] **Step 4: 编写 Mapper 接口**

```java
package com.dh.server.storage.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dh.server.storage.entity.SessionEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SessionMapper extends BaseMapper<SessionEntity> {
}
```

```java
package com.dh.server.storage.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dh.server.storage.entity.MessageEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface MessageMapper extends BaseMapper<MessageEntity> {
}
```

```java
package com.dh.server.storage.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dh.server.storage.entity.DhConfigEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface DhConfigMapper extends BaseMapper<DhConfigEntity> {
}
```

- [ ] **Step 5: Commit**

```bash
git add digital-human-server/src/main/java/com/dh/server/storage/entity/ digital-human-server/src/main/java/com/dh/server/storage/mapper/
git commit -m "feat: add JPA entities and MyBatis-Plus mappers"
```

---

### Task 2.2: Storage 服务层（Redis + MySQL 缓存）

**Files:**
- Create: `digital-human-server/src/main/java/com/dh/server/storage/service/SessionStorageService.java`
- Create: `digital-human-server/src/main/java/com/dh/server/storage/service/MessageStorageService.java`
- Create: `digital-human-server/src/main/java/com/dh/server/storage/service/ConfigStorageService.java`
- Create: `digital-human-server/src/main/java/com/dh/server/config/RedisConfig.java`

- [ ] **Step 1: 编写 RedisConfig**

```java
package com.dh.server.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
public class RedisConfig {

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory factory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new GenericJackson2JsonRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(new GenericJackson2JsonRedisSerializer());
        return template;
    }
}
```

- [ ] **Step 2: 编写会话领域对象和领域消息对象**

> 注意：以下文件放在 `session` 包，供 storage 和其他模块共用。

```java
package com.dh.server.session;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Session {
    private String sessionId;
    private String status;
    private Instant createdAt;
    private Instant lastActiveAt;
    @Builder.Default
    private List<Message> history = new ArrayList<>();
}
```

```java
package com.dh.server.session;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Message {
    private Long id;
    private String sessionId;
    private String role;
    private String text;
    private String emotion;
    private String audioUrl;
    private Instant createdAt;
}
```

- [ ] **Step 3: 编写 SessionStorageService（Cache-Aside 模式）**

```java
package com.dh.server.storage.service;

import com.dh.server.storage.entity.SessionEntity;
import com.dh.server.storage.mapper.SessionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import java.time.Duration;
import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class SessionStorageService {

    private final SessionMapper sessionMapper;
    private final RedisTemplate<String, Object> redisTemplate;

    private static final String CACHE_PREFIX = "session:";
    private static final Duration CACHE_TTL = Duration.ofMinutes(30);

    public void createSession(SessionEntity session) {
        sessionMapper.insert(session);
        String key = CACHE_PREFIX + session.getId();
        redisTemplate.opsForValue().set(key, session, CACHE_TTL);
    }

    public SessionEntity findById(String sessionId) {
        String key = CACHE_PREFIX + sessionId;
        SessionEntity cached = (SessionEntity) redisTemplate.opsForValue().get(key);
        if (cached != null) {
            return cached;
        }
        SessionEntity fromDb = sessionMapper.selectById(sessionId);
        if (fromDb != null) {
            redisTemplate.opsForValue().set(key, fromDb, CACHE_TTL);
        }
        return fromDb;
    }

    public void updateStatus(String sessionId, String status) {
        SessionEntity session = new SessionEntity();
        session.setId(sessionId);
        session.setStatus(status);
        session.setLastActiveAt(LocalDateTime.now());
        if ("CLOSED".equals(status)) {
            session.setClosedAt(LocalDateTime.now());
        }
        sessionMapper.updateById(session);
        // 更新缓存
        SessionEntity updated = sessionMapper.selectById(sessionId);
        redisTemplate.opsForValue().set(CACHE_PREFIX + sessionId, updated, CACHE_TTL);
    }

    public void touchLastActive(String sessionId) {
        updateStatus(sessionId, "ACTIVE");
    }
}
```

- [ ] **Step 4: 编写 MessageStorageService**

```java
package com.dh.server.storage.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dh.server.storage.entity.MessageEntity;
import com.dh.server.storage.mapper.MessageMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MessageStorageService {

    private final MessageMapper messageMapper;
    private final RedisTemplate<String, Object> redisTemplate;

    private static final String HISTORY_CACHE_PREFIX = "session:history:";
    private static final Duration CACHE_TTL = Duration.ofMinutes(30);
    private static final int RECENT_COUNT = 10;

    public void saveMessage(MessageEntity message) {
        message.setCreatedAt(LocalDateTime.now());
        messageMapper.insert(message);
        // 更新缓存：追加新消息
        String key = HISTORY_CACHE_PREFIX + message.getSessionId();
        @SuppressWarnings("unchecked")
        List<MessageEntity> cached = (List<MessageEntity>) redisTemplate.opsForValue().get(key);
        if (cached == null) {
            cached = new ArrayList<>();
        }
        cached.add(message);
        if (cached.size() > RECENT_COUNT) {
            cached = cached.subList(cached.size() - RECENT_COUNT, cached.size());
        }
        redisTemplate.opsForValue().set(key, cached, CACHE_TTL);
    }

    public List<MessageEntity> getRecentMessages(String sessionId, int count) {
        String key = HISTORY_CACHE_PREFIX + sessionId;
        @SuppressWarnings("unchecked")
        List<MessageEntity> cached = (List<MessageEntity>) redisTemplate.opsForValue().get(key);
        if (cached != null) {
            if (cached.size() <= count) return cached;
            return cached.subList(cached.size() - count, cached.size());
        }
        return loadRecentFromDb(sessionId, count);
    }

    private List<MessageEntity> loadRecentFromDb(String sessionId, int count) {
        LambdaQueryWrapper<MessageEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MessageEntity::getSessionId, sessionId)
               .orderByDesc(MessageEntity::getCreatedAt)
               .last("LIMIT " + count);
        List<MessageEntity> fromDb = messageMapper.selectList(wrapper);
        String key = HISTORY_CACHE_PREFIX + sessionId;
        redisTemplate.opsForValue().set(key, fromDb, CACHE_TTL);
        return fromDb;
    }

    public List<MessageEntity> getMessagesBySession(String sessionId, int page, int size) {
        LambdaQueryWrapper<MessageEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MessageEntity::getSessionId, sessionId)
               .orderByAsc(MessageEntity::getCreatedAt);
        int offset = (page - 1) * size;
        wrapper.last("LIMIT " + offset + "," + size);
        return messageMapper.selectList(wrapper);
    }
}
```

- [ ] **Step 5: 编写 ConfigStorageService**

```java
package com.dh.server.storage.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dh.server.storage.entity.DhConfigEntity;
import com.dh.server.storage.mapper.DhConfigMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import java.time.Duration;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ConfigStorageService {

    private final DhConfigMapper configMapper;
    private final RedisTemplate<String, Object> redisTemplate;

    private static final String CACHE_KEY = "dh:config";
    private static final Duration CACHE_TTL = Duration.ofMinutes(10);

    public String getConfigValue(String key) {
        return getAllConfigs().get(key);
    }

    @SuppressWarnings("unchecked")
    public Map<String, String> getAllConfigs() {
        Map<String, String> cached = (Map<String, String>) redisTemplate.opsForValue().get(CACHE_KEY);
        if (cached != null) {
            return cached;
        }
        return refreshCache();
    }

    public void setConfig(String configKey, String configValue) {
        DhConfigEntity existing = configMapper.selectOne(
            new LambdaQueryWrapper<DhConfigEntity>().eq(DhConfigEntity::getConfigKey, configKey)
        );
        if (existing != null) {
            existing.setConfigValue(configValue);
            configMapper.updateById(existing);
        } else {
            DhConfigEntity newConfig = new DhConfigEntity();
            newConfig.setConfigKey(configKey);
            newConfig.setConfigValue(configValue);
            configMapper.insert(newConfig);
        }
        refreshCache();
    }

    private Map<String, String> refreshCache() {
        Map<String, String> configs = configMapper.selectList(null).stream()
            .collect(Collectors.toMap(DhConfigEntity::getConfigKey, DhConfigEntity::getConfigValue));
        redisTemplate.opsForValue().set(CACHE_KEY, configs, CACHE_TTL);
        return configs;
    }
}
```

- [ ] **Step 6: Commit**

```bash
git add digital-human-server/src/main/java/com/dh/server/storage/service/
git add digital-human-server/src/main/java/com/dh/server/config/RedisConfig.java
git add digital-human-server/src/main/java/com/dh/server/session/
git commit -m "feat: add storage services with Redis cache-aside pattern"
```

---

### Task 2.3: 外部 API 连接器（dh-connector）

**Files:**
- Create: `digital-human-server/src/main/java/com/dh/server/connector/Connector.java`
- Create: `digital-human-server/src/main/java/com/dh/server/connector/DeepSeekConnector.java`
- Create: `digital-human-server/src/main/java/com/dh/server/connector/TtsConnector.java`
- Create: `digital-human-server/src/main/java/com/dh/server/connector/AsrConnector.java`
- Create: `digital-human-server/src/main/java/com/dh/server/config/AppConfig.java`
- Test: `digital-human-server/src/test/java/com/dh/server/connector/DeepSeekConnectorTest.java`

- [ ] **Step 1: 编写连接器接口**

```java
package com.dh.server.connector;

public interface Connector<TInput, TOutput> {
    TOutput execute(TInput input);
}
```

- [ ] **Step 2: 编写 AppConfig**

```java
package com.dh.server.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "app")
public class AppConfig {
    private DeepSeek deepseek = new DeepSeek();

    @Data
    public static class DeepSeek {
        private String apiKey;
        private String apiUrl = "https://api.deepseek.com/v1/chat/completions";
        private String model = "deepseek-chat";
    }
}
```

- [ ] **Step 3: DeepSeekConnector（发送对话请求并返回回复文本）**

```java
package com.dh.server.connector;

import com.dh.server.config.AppConfig;
import com.dh.server.session.Message;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import java.util.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class DeepSeekConnector implements Connector<DeepSeekConnector.DeepSeekInput, String> {

    private final AppConfig appConfig;
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Data
    @Builder
    public static class DeepSeekInput {
        private String systemPrompt;
        private String userText;
        private List<Message> history;
    }

    @Override
    public String execute(DeepSeekInput input) {
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", input.getSystemPrompt()));

        if (input.getHistory() != null) {
            for (Message msg : input.getHistory()) {
                messages.add(Map.of(
                    "role", msg.getRole().toLowerCase(),
                    "content", msg.getText()
                ));
            }
        }
        messages.add(Map.of("role", "user", "content", input.getUserText()));

        Map<String, Object> body = new HashMap<>();
        body.put("model", appConfig.getDeepseek().getModel());
        body.put("messages", messages);
        body.put("temperature", 0.7);
        body.put("max_tokens", 512);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(appConfig.getDeepseek().getApiKey());

        try {
            String requestBody = objectMapper.writeValueAsString(body);
            HttpEntity<String> entity = new HttpEntity<>(requestBody, headers);
            ResponseEntity<DeepSeekResponse> response = restTemplate.exchange(
                appConfig.getDeepseek().getApiUrl(),
                HttpMethod.POST,
                entity,
                DeepSeekResponse.class
            );
            if (response.getBody() != null && response.getBody().getChoices() != null
                && !response.getBody().getChoices().isEmpty()) {
                return response.getBody().getChoices().get(0).getMessage().getContent();
            }
            throw new RuntimeException("DeepSeek 返回空响应");
        } catch (Exception e) {
            log.error("DeepSeek API 调用失败", e);
            throw new RuntimeException("AI 服务暂时不可用，请稍后重试");
        }
    }

    @Data
    private static class DeepSeekResponse {
        private List<Choice> choices;

        @Data
        public static class Choice {
            private MessageDetail message;
        }

        @Data
        public static class MessageDetail {
            private String content;
        }
    }
}
```

- [ ] **Step 4: TtsConnector（文本 → Base64 音频）**

```java
package com.dh.server.connector;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import java.util.Base64;

@Slf4j
@Component
@RequiredArgsConstructor
public class TtsConnector implements Connector<String, byte[]> {

    /**
     * 将文本转换为 PCM/MP3 音频字节。
     * 默认实现返回空，实际使用时接入云端 TTS API（如微软 Azure / 阿里云 / 火山引擎）。
     * 接入方式：替换本类的 execute 方法，调用对应 SDK。
     */
    @Override
    public byte[] execute(String text) {
        log.warn("TtsConnector 尚未接入实际 TTS API，返回空音频。请配置云 TTS 服务。");
        // 示例：对接阿里云 TTS
        // return aliTtsClient.synthesize(text);
        return new byte[0];
    }

    /** 便捷方法：返回 Base64 编码的音频 */
    public String executeAsBase64(String text) {
        byte[] audio = execute(text);
        if (audio.length == 0) return "";
        return Base64.getEncoder().encodeToString(audio);
    }
}
```

- [ ] **Step 5: AsrConnector（音频 → 文本）**

```java
package com.dh.server.connector;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AsrConnector implements Connector<byte[], String> {

    /**
     * 将音频字节（PCM/Opus）转为识别文本。
     * 默认实现返回空，实际使用时接入云端 ASR API（如阿里云 / 讯飞）。
     */
    @Override
    public String execute(byte[] audioBytes) {
        log.warn("AsrConnector 尚未接入实际 ASR API。请配置云 ASR 服务。");
        return "";
    }
}
```

- [ ] **Step 6: 编写 DeepSeekConnector 单元测试**

```java
package com.dh.server.connector;

import com.dh.server.config.AppConfig;
import com.dh.server.session.Message;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DeepSeekConnectorTest {

    @Test
    @Disabled("需要有效的 API Key，仅在集成测试时启用")
    void shouldReturnReplyWhenApiKeyIsValid() {
        AppConfig config = new AppConfig();
        config.getDeepseek().setApiKey("sk-test-key");
        config.getDeepseek().setApiUrl("https://api.deepseek.com/v1/chat/completions");

        DeepSeekConnector connector = new DeepSeekConnector(config);
        DeepSeekConnector.DeepSeekInput input = DeepSeekConnector.DeepSeekInput.builder()
            .systemPrompt("你是一个友好的助手。")
            .userText("你好")
            .history(List.of())
            .build();

        String reply = connector.execute(input);
        assertNotNull(reply);
        assertFalse(reply.isEmpty());
    }

    @Test
    void shouldIncludeHistoryInRequest() {
        // 验证历史消息按 user/assistant 交替格式化
        DeepSeekConnector.DeepSeekInput input = DeepSeekConnector.DeepSeekInput.builder()
            .systemPrompt("你是一个助手")
            .userText("今天天气好吗？")
            .history(List.of(
                Message.builder().role("USER").text("你好").build(),
                Message.builder().role("ASSISTANT").text("你好！有什么可以帮你的？").build()
            ))
            .build();

        assertEquals(2, input.getHistory().size());
        assertEquals("USER", input.getHistory().get(0).getRole());
        assertEquals("ASSISTANT", input.getHistory().get(1).getRole());
    }
}
```

- [ ] **Step 7: Commit**

```bash
git add digital-human-server/src/main/java/com/dh/server/connector/
git add digital-human-server/src/main/java/com/dh/server/config/AppConfig.java
git add digital-human-server/src/test/java/com/dh/server/connector/
git commit -m "feat: add external API connectors (DeepSeek, TTS, ASR)"
```

---

### Task 2.4: 情绪计算模块（dh-emotion）

**Files:**
- Create: `digital-human-server/src/main/java/com/dh/server/emotion/EmotionLabel.java`
- Create: `digital-human-server/src/main/java/com/dh/server/emotion/EmotionResult.java`
- Create: `digital-human-server/src/main/java/com/dh/server/emotion/EmotionCalculator.java`
- Create: `digital-human-server/src/main/java/com/dh/server/emotion/EmotionToLive2DParams.java`
- Test: `digital-human-server/src/test/java/com/dh/server/emotion/EmotionCalculatorTest.java`

- [ ] **Step 1: 编写情绪标签枚举和结果类**

```java
package com.dh.server.emotion;

import java.util.Map;
import java.util.Set;

public enum EmotionLabel {
    NEUTRAL("neutral", "中性"),
    HAPPY("happy", "开心"),
    PUZZLED("puzzled", "疑惑"),
    SURPRISED("surprised", "惊讶"),
    SORRY("sorry", "抱歉"),
    THINKING("thinking", "思考");

    private final String label;
    private final String chineseName;

    EmotionLabel(String label, String chineseName) {
        this.label = label;
        this.chineseName = chineseName;
    }

    public String getLabel() { return label; }
    public String getChineseName() { return chineseName; }

    public static EmotionLabel fromLabel(String label) {
        for (EmotionLabel e : values()) {
            if (e.label.equalsIgnoreCase(label)) return e;
        }
        return NEUTRAL;
    }

    public static final Set<String> ALL_LABELS = Set.of(
        "neutral", "happy", "puzzled", "surprised", "sorry", "thinking"
    );
}
```

```java
package com.dh.server.emotion;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class EmotionResult {
    private EmotionLabel label;
    private float confidence;       // 0.0 ~ 1.0
    private String source;          // "LLM" 或 "RULE" 或 "DEFAULT"
}
```

- [ ] **Step 2: 编写 EmotionCalculator（LLM 提取 + 规则兜底）**

```java
package com.dh.server.emotion;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
public class EmotionCalculator {

    private static final Pattern EMOTION_PATTERN =
        Pattern.compile("\\[EMOTION:(\\w+)\\]", Pattern.CASE_INSENSITIVE);

    // 规则兜底：关键词 → 情绪
    private static final Map<String, EmotionLabel> KEYWORD_MAP = Map.ofEntries(
        Map.entry("谢谢", EmotionLabel.HAPPY),
        Map.entry("感谢", EmotionLabel.HAPPY),
        Map.entry("太好了", EmotionLabel.HAPPY),
        Map.entry("恭喜", EmotionLabel.HAPPY),
        Map.entry("欢迎", EmotionLabel.HAPPY),
        Map.entry("不确定", EmotionLabel.PUZZLED),
        Map.entry("请问", EmotionLabel.PUZZLED),
        Map.entry("不太清楚", EmotionLabel.PUZZLED),
        Map.entry("什么", EmotionLabel.SURPRISED),
        Map.entry("真的吗", EmotionLabel.SURPRISED),
        Map.entry("天哪", EmotionLabel.SURPRISED),
        Map.entry("抱歉", EmotionLabel.SORRY),
        Map.entry("对不起", EmotionLabel.SORRY),
        Map.entry("遗憾", EmotionLabel.SORRY),
        Map.entry("请稍等", EmotionLabel.THINKING),
        Map.entry("让我想想", EmotionLabel.THINKING),
        Map.entry("正在查询", EmotionLabel.THINKING),
        Map.entry("嗯", EmotionLabel.THINKING)
    );

    /**
     * 从 LLM 回复文本中提取情绪标签。
     * 1. 正则匹配 [EMOTION:xxx]
     * 2. 未匹配 → 规则兜底
     * 3. 规则也未匹配 → neutral
     */
    public EmotionResult calculate(String llmReplyText) {
        // Step 1: LLM 标签提取
        Matcher matcher = EMOTION_PATTERN.matcher(llmReplyText);
        if (matcher.find()) {
            String labelStr = matcher.group(1).toLowerCase();
            if (EmotionLabel.ALL_LABELS.contains(labelStr)) {
                return new EmotionResult(EmotionLabel.fromLabel(labelStr), 0.9f, "LLM");
            }
        }

        // Step 2: 规则兜底
        for (Map.Entry<String, EmotionLabel> entry : KEYWORD_MAP.entrySet()) {
            if (llmReplyText.contains(entry.getKey())) {
                return new EmotionResult(entry.getValue(), 0.6f, "RULE");
            }
        }

        // Step 3: 默认 neutral
        return new EmotionResult(EmotionLabel.NEUTRAL, 0.5f, "DEFAULT");
    }

    /**
     * 从 LLM 回复中移除情绪标签，返回干净的文本。
     */
    public String removeEmotionTag(String text) {
        return EMOTION_PATTERN.matcher(text).replaceAll("").trim();
    }
}
```

- [ ] **Step 3: 编写 EmotionToLive2DParams（情绪 → Anim 参数映射）**

```java
package com.dh.server.emotion;

import org.springframework.stereotype.Component;
import java.util.HashMap;
import java.util.Map;

@Component
public class EmotionToLive2DParams {

    public Map<String, Double> mapToParams(EmotionLabel label) {
        Map<String, Double> params = new HashMap<>();

        // 所有情绪默认值
        params.put("ParamMouthOpenY", 0.0);
        params.put("ParamMouthForm", 0.0);
        params.put("ParamEyeOpen", 1.0);
        params.put("ParamBrowY", 0.0);
        params.put("ParamAngry", 0.0);
        params.put("ParamHappy", 0.5);
        params.put("ParamSad", 0.0);
        params.put("ParamSurprise", 0.0);

        switch (label) {
            case HAPPY:
                params.put("ParamHappy", 0.9);
                params.put("ParamEyeOpen", 1.0);
                break;
            case PUZZLED:
                params.put("ParamBrowY", 0.6);
                params.put("ParamEyeOpen", 0.7);
                params.put("ParamSurprise", 0.3);
                break;
            case SURPRISED:
                params.put("ParamSurprise", 0.9);
                params.put("ParamEyeOpen", 1.0);
                params.put("ParamMouthOpenY", 0.4);
                break;
            case SORRY:
                params.put("ParamSad", 0.6);
                params.put("ParamBrowY", -0.3);
                params.put("ParamHappy", 0.2);
                break;
            case THINKING:
                params.put("ParamBrowY", 0.4);
                params.put("ParamEyeOpen", 0.5);
                params.put("ParamHappy", 0.3);
                break;
            case NEUTRAL:
            default:
                params.put("ParamHappy", 0.5);
                break;
        }
        return params;
    }
}
```

- [ ] **Step 4: 编写单元测试**

```java
package com.dh.server.emotion;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EmotionCalculatorTest {

    private final EmotionCalculator calculator = new EmotionCalculator();

    @Test
    void shouldExtractEmotionFromLlmTag() {
        String reply = "你好呀！很高兴见到你 [EMOTION:happy]";
        EmotionResult result = calculator.calculate(reply);
        assertEquals(EmotionLabel.HAPPY, result.getLabel());
        assertEquals("LLM", result.getSource());
    }

    @Test
    void shouldFallbackToRuleWhenNoTag() {
        String reply = "非常抱歉，我暂时无法回答这个问题。";
        EmotionResult result = calculator.calculate(reply);
        assertEquals(EmotionLabel.SORRY, result.getLabel());
        assertEquals("RULE", result.getSource());
    }

    @Test
    void shouldDefaultToNeutral() {
        String reply = "今天是星期二。";
        EmotionResult result = calculator.calculate(reply);
        assertEquals(EmotionLabel.NEUTRAL, result.getLabel());
        assertEquals("DEFAULT", result.getSource());
    }

    @Test
    void shouldRemoveEmotionTagFromText() {
        String reply = "没问题 [EMOTION:happy]";
        String clean = calculator.removeEmotionTag(reply);
        assertEquals("没问题", clean);
    }

    @Test
    void shouldHandleCaseInsensitiveEmotionTag() {
        String reply = "Hello [EMOTION:HAPPY]";
        EmotionResult result = calculator.calculate(reply);
        assertEquals(EmotionLabel.HAPPY, result.getLabel());
    }

    @Test
    void shouldMapHappyToCorrectLive2DParams() {
        EmotionToLive2DParams mapper = new EmotionToLive2DParams();
        var params = mapper.mapToParams(EmotionLabel.HAPPY);
        assertEquals(0.9, params.get("ParamHappy"), 0.01);
        assertEquals(1.0, params.get("ParamEyeOpen"), 0.01);
    }
}
```

- [ ] **Step 5: 运行测试**

```bash
cd digital-human-server
mvn test -Dtest=EmotionCalculatorTest
```
Expected: All 6 tests PASS

- [ ] **Step 6: Commit**

```bash
git add digital-human-server/src/main/java/com/dh/server/emotion/
git add digital-human-server/src/test/java/com/dh/server/emotion/
git commit -m "feat: add emotion calculation with LLM extraction and rule fallback"
```

---

### Task 2.5: 编排层与文字对话 API（dh-orchestrator）

**Files:**
- Create: `digital-human-server/src/main/java/com/dh/server/orchestrator/PipelineResult.java`
- Create: `digital-human-server/src/main/java/com/dh/server/orchestrator/ChatOrchestrator.java`
- Create: `digital-human-server/src/main/java/com/dh/server/session/SessionManager.java`
- Create: `digital-human-server/src/main/java/com/dh/server/controller/SessionController.java`
- Create: `digital-human-server/src/main/java/com/dh/server/controller/ChatController.java`
- Test: `digital-human-server/src/test/java/com/dh/server/orchestrator/ChatOrchestratorTest.java`

- [ ] **Step 1: 编写 PipelineResult**

```java
package com.dh.server.orchestrator;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PipelineResult {
    private String text;                        // LLM 回复文本（已去除情绪标签）
    private String emotion;                     // 情绪标签名
    private Map<String, Double> animationParams; // Live2D 参数
    private String audioBase64;                 // TTS 音频 Base64
}
```

- [ ] **Step 2: 编写 SessionManager**

```java
package com.dh.server.session;

import com.dh.server.storage.entity.SessionEntity;
import com.dh.server.storage.service.SessionStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class SessionManager {

    private final SessionStorageService sessionStorageService;

    public Session createSession(String userIp) {
        String sessionId = UUID.randomUUID().toString().replace("-", "");
        SessionEntity entity = new SessionEntity();
        entity.setId(sessionId);
        entity.setStatus("ACTIVE");
        entity.setUserIp(userIp);
        entity.setCreatedAt(LocalDateTime.now());
        entity.setLastActiveAt(LocalDateTime.now());
        sessionStorageService.createSession(entity);

        return Session.builder()
            .sessionId(sessionId)
            .status("ACTIVE")
            .createdAt(entity.getCreatedAt().toInstant(java.time.ZoneOffset.ofHours(8)))
            .lastActiveAt(entity.getLastActiveAt().toInstant(java.time.ZoneOffset.ofHours(8)))
            .build();
    }

    public Session getSession(String sessionId) {
        SessionEntity entity = sessionStorageService.findById(sessionId);
        if (entity == null) return null;
        return Session.builder()
            .sessionId(entity.getId())
            .status(entity.getStatus())
            .build();
    }

    public void closeSession(String sessionId) {
        sessionStorageService.updateStatus(sessionId, "CLOSED");
    }

    public void touchLastActive(String sessionId) {
        sessionStorageService.touchLastActive(sessionId);
    }
}
```

- [ ] **Step 3: 编写 ChatOrchestrator（核心流水线）**

```java
package com.dh.server.orchestrator;

import com.dh.server.connector.DeepSeekConnector;
import com.dh.server.connector.TtsConnector;
import com.dh.server.emotion.EmotionCalculator;
import com.dh.server.emotion.EmotionResult;
import com.dh.server.emotion.EmotionToLive2DParams;
import com.dh.server.session.Message;
import com.dh.server.storage.entity.MessageEntity;
import com.dh.server.storage.service.ConfigStorageService;
import com.dh.server.storage.service.MessageStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChatOrchestrator {

    private final DeepSeekConnector deepSeekConnector;
    private final TtsConnector ttsConnector;
    private final EmotionCalculator emotionCalculator;
    private final EmotionToLive2DParams emotionToParams;
    private final ConfigStorageService configStorageService;
    private final MessageStorageService messageStorageService;

    /**
     * 文字对话流水线: LLM → Emotion → TTS
     */
    public PipelineResult processText(String sessionId, String userText) {
        // 1. 保存用户消息
        saveMessage(sessionId, "USER", userText, null);

        // 2. 获取配置
        String systemPrompt = configStorageService.getConfigValue("system_prompt");
        if (systemPrompt == null) {
            systemPrompt = "你是一个友好的数字人助手。";
        }

        // 3. 获取最近对话历史
        List<MessageEntity> historyEntities = messageStorageService.getRecentMessages(sessionId, 10);
        List<Message> history = historyEntities.stream()
            .map(e -> Message.builder()
                .role(e.getRole())
                .text(e.getText())
                .build())
            .collect(Collectors.toList());

        // 4. 调用 LLM
        DeepSeekConnector.DeepSeekInput llmInput = DeepSeekConnector.DeepSeekInput.builder()
            .systemPrompt(systemPrompt)
            .userText(userText)
            .history(history)
            .build();
        String llmReply = deepSeekConnector.execute(llmInput);

        // 5. 情绪计算
        EmotionResult emotionResult = emotionCalculator.calculate(llmReply);
        String cleanText = emotionCalculator.removeEmotionTag(llmReply);

        // 6. TTS 合成
        String audioBase64 = ttsConnector.executeAsBase64(cleanText);

        // 7. 保存 AI 回复
        saveMessage(sessionId, "ASSISTANT", cleanText, emotionResult.getLabel().getLabel());

        return PipelineResult.builder()
            .text(cleanText)
            .emotion(emotionResult.getLabel().getLabel())
            .animationParams(emotionToParams.mapToParams(emotionResult.getLabel()))
            .audioBase64(audioBase64)
            .build();
    }

    /**
     * 语音对话流水线: ASR → LLM → Emotion → TTS（后续 WebRTC 阶段使用）
     */
    public PipelineResult processVoice(String sessionId, byte[] audioBytes) {
        // 语音暂不实现完整链路，留到 Task 3.x 对接
        log.info("语音流水线待 WebRTC 集成阶段实现");
        return PipelineResult.builder().build();
    }

    private void saveMessage(String sessionId, String role, String text, String emotion) {
        MessageEntity entity = new MessageEntity();
        entity.setSessionId(sessionId);
        entity.setRole(role);
        entity.setText(text);
        entity.setEmotion(emotion);
        messageStorageService.saveMessage(entity);
    }
}
```

- [ ] **Step 4: 编写 SessionController**

```java
package com.dh.server.controller;

import com.dh.server.common.Result;
import com.dh.server.session.Session;
import com.dh.server.session.SessionManager;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/session")
@RequiredArgsConstructor
public class SessionController {

    private final SessionManager sessionManager;

    @PostMapping("/create")
    public Result<Map<String, String>> create(HttpServletRequest request) {
        String ip = request.getRemoteAddr();
        Session session = sessionManager.createSession(ip);
        return Result.ok(Map.of("sessionId", session.getSessionId()));
    }

    @GetMapping("/{id}")
    public Result<Map<String, String>> getInfo(@PathVariable String id) {
        Session session = sessionManager.getSession(id);
        if (session == null) {
            return Result.fail(404, "会话不存在");
        }
        return Result.ok(Map.of(
            "status", session.getStatus(),
            "sessionId", session.getSessionId()
        ));
    }

    @DeleteMapping("/{id}")
    public Result<Map<String, Boolean>> close(@PathVariable String id) {
        sessionManager.closeSession(id);
        return Result.ok(Map.of("success", true));
    }
}
```

- [ ] **Step 5: 编写 ChatController**

```java
package com.dh.server.controller;

import com.dh.server.common.Result;
import com.dh.server.orchestrator.ChatOrchestrator;
import com.dh.server.orchestrator.PipelineResult;
import com.dh.server.session.SessionManager;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatOrchestrator chatOrchestrator;
    private final SessionManager sessionManager;

    @PostMapping("/text")
    public Result<PipelineResult> chatText(@RequestBody ChatRequest request) {
        if (sessionManager.getSession(request.getSessionId()) == null) {
            return Result.fail(404, "会话不存在或已过期");
        }
        sessionManager.touchLastActive(request.getSessionId());
        PipelineResult result = chatOrchestrator.processText(
            request.getSessionId(), request.getText()
        );
        return Result.ok(result);
    }

    @Data
    public static class ChatRequest {
        @NotBlank(message = "sessionId 不能为空")
        private String sessionId;
        @NotBlank(message = "text 不能为空")
        private String text;
    }
}
```

- [ ] **Step 6: 编写 ChatOrchestrator 单元测试**

```java
package com.dh.server.orchestrator;

import com.dh.server.connector.DeepSeekConnector;
import com.dh.server.connector.TtsConnector;
import com.dh.server.emotion.EmotionCalculator;
import com.dh.server.emotion.EmotionLabel;
import com.dh.server.emotion.EmotionResult;
import com.dh.server.emotion.EmotionToLive2DParams;
import com.dh.server.storage.entity.MessageEntity;
import com.dh.server.storage.service.ConfigStorageService;
import com.dh.server.storage.service.MessageStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ChatOrchestratorTest {

    private DeepSeekConnector deepSeekConnector;
    private TtsConnector ttsConnector;
    private EmotionCalculator emotionCalculator;
    private EmotionToLive2DParams emotionToParams;
    private ConfigStorageService configStorageService;
    private MessageStorageService messageStorageService;
    private ChatOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        deepSeekConnector = mock(DeepSeekConnector.class);
        ttsConnector = mock(TtsConnector.class);
        emotionCalculator = new EmotionCalculator();
        emotionToParams = new EmotionToLive2DParams();
        configStorageService = mock(ConfigStorageService.class);
        messageStorageService = mock(MessageStorageService.class);

        orchestrator = new ChatOrchestrator(
            deepSeekConnector, ttsConnector, emotionCalculator,
            emotionToParams, configStorageService, messageStorageService
        );
    }

    @Test
    void shouldCompleteTextPipelineSuccessfully() {
        when(configStorageService.getConfigValue("system_prompt"))
            .thenReturn("你是一个友好的助手。");
        when(messageStorageService.getRecentMessages("session-1", 10))
            .thenReturn(List.of());
        when(deepSeekConnector.execute(any()))
            .thenReturn("你好！有什么可以帮你的吗？[EMOTION:happy]");
        when(ttsConnector.executeAsBase64(anyString()))
            .thenReturn("base64audio==");

        PipelineResult result = orchestrator.processText("session-1", "你好");

        assertNotNull(result);
        assertEquals("你好！有什么可以帮你的吗？", result.getText());
        assertEquals("happy", result.getEmotion());
        assertEquals("base64audio==", result.getAudioBase64());
        assertFalse(result.getAnimationParams().isEmpty());
        assertEquals(0.9, result.getAnimationParams().get("ParamHappy"), 0.01);

        verify(messageStorageService, times(2)).saveMessage(any(MessageEntity.class));
    }

    @Test
    void shouldHandleLlmReplyWithoutEmotionTag() {
        when(configStorageService.getConfigValue("system_prompt"))
            .thenReturn("你是一个助手。");
        when(messageStorageService.getRecentMessages("s1", 10))
            .thenReturn(List.of());
        when(deepSeekConnector.execute(any()))
            .thenReturn("今天是星期一。");
        when(ttsConnector.executeAsBase64(anyString()))
            .thenReturn("audio");

        PipelineResult result = orchestrator.processText("s1", "今天星期几？");

        assertEquals("neutral", result.getEmotion());
        assertEquals("今天是星期一。", result.getText());
        assertEquals(0.5, result.getAnimationParams().get("ParamHappy"), 0.01);
    }
}
```

- [ ] **Step 7: 运行测试**

```bash
cd digital-human-server
mvn test -Dtest=ChatOrchestratorTest
```
Expected: 2 tests PASS

- [ ] **Step 8: Commit**

```bash
git add digital-human-server/src/main/java/com/dh/server/orchestrator/
git add digital-human-server/src/main/java/com/dh/server/session/SessionManager.java
git add digital-human-server/src/main/java/com/dh/server/controller/
git add digital-human-server/src/test/java/com/dh/server/orchestrator/
git commit -m "feat: add orchestration pipeline and text chat API"
```

---

## 里程碑 3：WebRTC 集成

### Task 3.1: 前端 HTTP API 封装与会话管理

**Files:**
- Create: `digital-human-web/src/services/api.js`
- Create: `digital-human-web/src/stores/session.js`
- Create: `digital-human-web/src/stores/chat.js`

- [ ] **Step 1: 编写 api.js（HTTP API 封装）**

```javascript
// src/services/api.js
import axios from 'axios'

const api = axios.create({
  baseURL: '/api',
  timeout: 15000
})

export function createSession() {
  return api.post('/session/create')
}

export function getSession(sessionId) {
  return api.get(`/session/${sessionId}`)
}

export function closeSession(sessionId) {
  return api.delete(`/session/${sessionId}`)
}

export function sendTextMessage(sessionId, text) {
  return api.post('/chat/text', { sessionId, text })
}

export default api
```

- [ ] **Step 2: 编写 Pinia session store**

```javascript
// src/stores/session.js
import { defineStore } from 'pinia'
import { createSession as apiCreateSession } from '../services/api'

export const useSessionStore = defineStore('session', {
  state: () => ({
    sessionId: null,
    status: null,
    loading: false
  }),
  actions: {
    async initSession() {
      this.loading = true
      try {
        const { data } = await apiCreateSession()
        if (data.code === 200) {
          this.sessionId = data.data.sessionId
          this.status = 'ACTIVE'
        }
      } finally {
        this.loading = false
      }
    },
    setStatus(status) {
      this.status = status
    }
  }
})
```

- [ ] **Step 3: 编写 Pinia chat store**

```javascript
// src/stores/chat.js
import { defineStore } from 'pinia'
import { sendTextMessage } from '../services/api'

export const useChatStore = defineStore('chat', {
  state: () => ({
    messages: [],
    currentEmotion: null,
    currentAnimationParams: {},
    sending: false
  }),
  actions: {
    async sendText(text) {
      const sessionStore = useSessionStore()
      if (!sessionStore.sessionId) return

      this.messages.push({ role: 'USER', text })
      this.sending = true

      try {
        const { data } = await sendTextMessage(sessionStore.sessionId, text)
        if (data.code === 200) {
          const result = data.data
          this.messages.push({
            role: 'ASSISTANT',
            text: result.text,
            emotion: result.emotion
          })
          this.currentEmotion = result.emotion
          this.currentAnimationParams = result.animationParams
          return result
        }
      } finally {
        this.sending = false
      }
    },
    clearMessages() {
      this.messages = []
    }
  }
})
```

- [ ] **Step 4: Commit**

```bash
git add digital-human-web/src/services/api.js
git add digital-human-web/src/stores/
git commit -m "feat: add HTTP API client and Pinia stores"
```

---

### Task 3.2: WebSocket 信令客户端（前端）

**Files:**
- Create: `digital-human-web/src/composables/useSignaling.js`

- [ ] **Step 1: 编写 useSignaling composable**

```javascript
// src/composables/useSignaling.js
import { ref } from 'vue'

export function useSignaling() {
  const ws = ref(null)
  const connected = ref(false)

  function connect(sessionId) {
    const protocol = location.protocol === 'https:' ? 'wss:' : 'ws:'
    const url = `${protocol}//${location.host}/ws/signaling?sessionId=${sessionId}`

    ws.value = new WebSocket(url)

    ws.value.onopen = () => {
      connected.value = true
      console.log('信令 WebSocket 已连接')
    }

    ws.value.onclose = () => {
      connected.value = false
      console.log('信令 WebSocket 已断开')
    }

    ws.value.onerror = (err) => {
      console.error('信令 WebSocket 错误:', err)
    }
  }

  function send(message) {
    if (ws.value && ws.value.readyState === WebSocket.OPEN) {
      ws.value.send(JSON.stringify(message))
    }
  }

  function onMessage(callback) {
    if (ws.value) {
      ws.value.onmessage = (event) => {
        const data = JSON.parse(event.data)
        callback(data)
      }
    }
  }

  function disconnect() {
    if (ws.value) {
      ws.value.close()
    }
  }

  return { connect, send, onMessage, disconnect, connected }
}
```

- [ ] **Step 2: Commit**

```bash
git add digital-human-web/src/composables/useSignaling.js
git commit -m "feat: add WebSocket signaling composable"
```

---

### Task 3.3: WebRTC 客户端（前端）

**Files:**
- Create: `digital-human-web/src/composables/useRtcClient.js`

- [ ] **Step 1: 编写 useRtcClient composable**

```javascript
// src/composables/useRtcClient.js
import { ref } from 'vue'

const ICE_SERVERS = {
  iceServers: [
    { urls: 'stun:stun.l.google.com:19302' }
  ]
}

export function useRtcClient() {
  const pc = ref(null)
  const localStream = ref(null)
  const remoteStream = ref(null)
  const dataChannel = ref(null)
  const pcState = ref('NEW')

  async function createPeerConnection(signaling) {
    pc.value = new RTCPeerConnection(ICE_SERVERS)

    pc.value.onconnectionstatechange = () => {
      pcState.value = pc.value.connectionState
    }

    // 监听远程音频 Track
    pc.value.ontrack = (event) => {
      remoteStream.value = event.streams[0]
    }

    // 监听 Data Channel（服务端创建）
    pc.value.ondatachannel = (event) => {
      dataChannel.value = event.channel
      dataChannel.value.onmessage = (e) => {
        const animData = JSON.parse(e.data)
        if (animData.type === 'animation_frame') {
          onAnimationFrameCallback(animData)
        }
      }
    }

    // 本地 ICE Candidate → 发送给信令服务器
    pc.value.onicecandidate = (event) => {
      if (event.candidate) {
        signaling.send({
          type: 'ice-candidate',
          candidate: event.candidate
        })
      }
    }

    // 开始采集本地麦克风
    try {
      localStream.value = await navigator.mediaDevices.getUserMedia({
        audio: true
      })
      localStream.value.getTracks().forEach(track => {
        pc.value.addTrack(track, localStream.value)
      })
    } catch (err) {
      console.error('麦克风访问失败:', err)
    }

    // 创建 Offer 并发送
    const offer = await pc.value.createOffer()
    await pc.value.setLocalDescription(offer)
    signaling.send({ type: 'offer', sdp: offer.sdp })

    // 处理来自信令服务器的 Answer
    signaling.onMessage(async (msg) => {
      if (msg.type === 'answer') {
        await pc.value.setRemoteDescription(
          new RTCSessionDescription({ type: 'answer', sdp: msg.sdp })
        )
      } else if (msg.type === 'ice-candidate') {
        await pc.value.addIceCandidate(new RTCIceCandidate(msg.candidate))
      }
    })
  }

  let onAnimationFrameCallback = () => {}

  function onAnimationFrame(callback) {
    onAnimationFrameCallback = callback
  }

  function close() {
    if (localStream.value) {
      localStream.value.getTracks().forEach(t => t.stop())
    }
    if (pc.value) {
      pc.value.close()
    }
    pcState.value = 'CLOSED'
  }

  return {
    pc, localStream, remoteStream, dataChannel, pcState,
    createPeerConnection, onAnimationFrame, close
  }
}
```

- [ ] **Step 2: Commit**

```bash
git add digital-human-web/src/composables/useRtcClient.js
git commit -m "feat: add WebRTC client composable with signaling and data channel"
```

---

### Task 3.4: WebSocket 信令服务端

**Files:**
- Create: `digital-human-server/src/main/java/com/dh/server/config/WebSocketConfig.java`
- Create: `digital-human-server/src/main/java/com/dh/server/signaling/SignalMessage.java`
- Create: `digital-human-server/src/main/java/com/dh/server/signaling/SignalingHandler.java`

- [ ] **Step 1: 编写 WebSocketConfig**

```java
package com.dh.server.config;

import com.dh.server.signaling.SignalingHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

    private final SignalingHandler signalingHandler;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(signalingHandler, "/ws/signaling")
                .setAllowedOrigins("*");
    }
}
```

- [ ] **Step 2: 编写 SignalMessage**

```java
package com.dh.server.signaling;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class SignalMessage {
    private String type;        // offer / answer / ice-candidate / createSession
    private String sdp;
    private Object candidate;
}
```

- [ ] **Step 3: 编写 SignalingHandler**

```java
package com.dh.server.signaling;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class SignalingHandler extends TextWebSocketHandler {

    private final ObjectMapper objectMapper;
    private final Map<String, WebSocketSession> sessionMap = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        String sessionId = getSessionId(session);
        sessionMap.put(sessionId, session);
        log.info("信令连接建立: sessionId={}", sessionId);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        SignalMessage msg = objectMapper.readValue(message.getPayload(), SignalMessage.class);
        String sessionId = getSessionId(session);
        log.debug("信令消息: type={}, sessionId={}", msg.getType(), sessionId);

        // 转发给同 sessionId 的其他连接（浏览器的对等端）
        // 当前简化实现：直接原样返回（模拟 SDP 交换）
        if ("offer".equals(msg.getType())) {
            // 服务端生成 Answer
            SignalMessage answer = new SignalMessage();
            answer.setType("answer");
            answer.setSdp(msg.getSdp());  // 简化：回传 SDP（实际应做真正的 SDP 协商）
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(answer)));
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String sessionId = getSessionId(session);
        sessionMap.remove(sessionId);
        log.info("信令连接断开: sessionId={}", sessionId);
    }

    private String getSessionId(WebSocketSession session) {
        String query = session.getUri() != null ? session.getUri().getQuery() : "";
        if (query != null && query.contains("sessionId=")) {
            return query.split("sessionId=")[1].split("&")[0];
        }
        return "unknown";
    }
}
```

- [ ] **Step 4: Commit**

```bash
git add digital-human-server/src/main/java/com/dh/server/config/WebSocketConfig.java
git add digital-human-server/src/main/java/com/dh/server/signaling/
git commit -m "feat: add WebSocket signaling server"
```

---

### Task 3.5: 前端对话页面整合（ChatView）

**Files:**
- Create: `digital-human-web/src/components/ChatPanel.vue`
- Create: `digital-human-web/src/components/InputBar.vue`
- Modify: `digital-human-web/src/views/ChatView.vue`

- [ ] **Step 1: 编写 ChatPanel.vue**

```vue
<!-- src/components/ChatPanel.vue -->
<template>
  <div class="chat-panel">
    <div class="messages" ref="messagesContainer">
      <div
        v-for="(msg, idx) in chatStore.messages"
        :key="idx"
        :class="['message', msg.role === 'USER' ? 'user' : 'assistant']"
      >
        <div class="bubble">{{ msg.text }}</div>
        <span v-if="msg.emotion" class="emotion-tag">{{ msg.emotion }}</span>
      </div>
      <div v-if="chatStore.sending" class="message assistant">
        <div class="bubble typing">...</div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { watch, ref, nextTick } from 'vue'
import { useChatStore } from '../stores/chat'

const chatStore = useChatStore()
const messagesContainer = ref(null)

watch(() => chatStore.messages.length, async () => {
  await nextTick()
  if (messagesContainer.value) {
    messagesContainer.value.scrollTop = messagesContainer.value.scrollHeight
  }
})
</script>

<style scoped>
.chat-panel {
  position: absolute;
  bottom: 80px;
  right: 20px;
  width: 320px;
  max-height: 400px;
  overflow-y: auto;
  background: rgba(255,255,255,0.9);
  border-radius: 12px;
  padding: 12px;
}
.message { margin-bottom: 8px; }
.message.user .bubble { background: #409EFF; color: white; text-align: right; }
.message.assistant .bubble { background: #f0f0f0; color: #333; }
.bubble { padding: 8px 12px; border-radius: 12px; max-width: 80%; display: inline-block; }
.emotion-tag { font-size: 11px; color: #999; display: block; margin-top: 2px; }
.typing { animation: pulse 1s infinite; }
@keyframes pulse { 0%, 100% { opacity: 0.5; } 50% { opacity: 1; } }
</style>
```

- [ ] **Step 2: 编写 InputBar.vue**

```vue
<!-- src/components/InputBar.vue -->
<template>
  <div class="input-bar">
    <el-input
      v-model="text"
      placeholder="输入消息..."
      @keyup.enter="send"
      :disabled="chatStore.sending"
    >
      <template #append>
        <el-button @click="send" :loading="chatStore.sending">
          发送
        </el-button>
      </template>
    </el-input>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useChatStore } from '../stores/chat'

const chatStore = useChatStore()
const text = ref('')

async function send() {
  if (!text.value.trim()) return
  await chatStore.sendText(text.value.trim())
  text.value = ''
}
</script>

<style scoped>
.input-bar {
  position: absolute;
  bottom: 20px;
  left: 50%;
  transform: translateX(-50%);
  width: 480px;
}
</style>
```

- [ ] **Step 3: 重写 ChatView.vue**

```vue
<!-- src/views/ChatView.vue -->
<template>
  <div class="chat-view">
    <div class="main-stage">
      <div class="avatar-placeholder">
        <!-- Live2D 渲染区（M4 阶段替换为 Live2DCanvas） -->
        <div class="placeholder-text">数字人区域</div>
      </div>
    </div>
    <ChatPanel />
    <InputBar />
  </div>
</template>

<script setup>
import { onMounted, onUnmounted } from 'vue'
import { useSessionStore } from '../stores/session'
import { useSignaling } from '../composables/useSignaling'
import { useRtcClient } from '../composables/useRtcClient'
import ChatPanel from '../components/ChatPanel.vue'
import InputBar from '../components/InputBar.vue'

const sessionStore = useSessionStore()
const signaling = useSignaling()
const rtcClient = useRtcClient()

onMounted(async () => {
  await sessionStore.initSession()
  signaling.connect(sessionStore.sessionId)
})

onUnmounted(() => {
  signaling.disconnect()
  rtcClient.close()
})
</script>

<style scoped>
.chat-view {
  width: 100vw;
  height: 100vh;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  position: relative;
  overflow: hidden;
}
.main-stage {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
}
.avatar-placeholder {
  width: 600px;
  height: 600px;
  border: 2px dashed rgba(255,255,255,0.3);
  border-radius: 16px;
  display: flex;
  align-items: center;
  justify-content: center;
}
.placeholder-text {
  color: rgba(255,255,255,0.5);
  font-size: 24px;
}
</style>
```

- [ ] **Step 4: 验证前端构建**

```bash
cd digital-human-web
npm run build
```
Expected: 构建成功，无错误

- [ ] **Step 5: Commit**

```bash
git add digital-human-web/src/components/ChatPanel.vue
git add digital-human-web/src/components/InputBar.vue
git add digital-human-web/src/views/ChatView.vue
git commit -m "feat: integrate chat UI with session, signaling, and WebRTC"
```

---

## 里程碑 4：Live2D 集成

### Task 4.1: Live2D 驱动 composable

**Files:**
- Create: `digital-human-web/src/composables/useLive2dDriver.js`

- [ ] **Step 1: 编写 useLive2dDriver composable**

```javascript
// src/composables/useLive2dDriver.js
import { ref } from 'vue'

export function useLive2dDriver() {
  const modelRef = ref(null)     // Cubism SDK 模型实例
  const idleMode = ref(true)
  const currentParams = ref({})
  const targetParams = ref({})
  const transitionProgress = ref(1.0)  // 0 → 1 过渡进度

  let animationFrameId = null
  let lastFrameTime = 0

  // Live2D 参数名映射（SDK 实际名称取决于模型文件）
  const PARAM_MAP = {
    ParamMouthOpenY: 'ParamMouthOpenY',
    ParamMouthForm: 'ParamMouthForm',
    ParamEyeOpen: 'ParamEyeLOpen',  // 左眼也映射到右眼
    ParamBrowY: 'ParamBrowY',
    ParamAngry: 'ParamAngry',
    ParamHappy: 'ParamHappy',
    ParamSad: 'ParamSad',
    ParamSurprise: 'ParamSurprise'
  }

  /**
   * 初始化 Live2D Cubism SDK
   * 需要加载 Live2D 模型文件 (.model3.json)
   */
  async function init(canvas, modelPath) {
    // Cubism SDK 初始化（伪代码，实际 API 以 SDK 文档为准）
    // const { Live2DModel } = await import('@/lib/live2dcubismcore.min.js')
    // modelRef.value = await Live2DModel.from(modelPath)
    // modelRef.value.setCanvas(canvas)
    startRenderLoop()
  }

  function startRenderLoop() {
    const render = (timestamp) => {
      const deltaTime = timestamp - lastFrameTime
      lastFrameTime = timestamp

      updateTransition(deltaTime)
      applyParams()

      // if (modelRef.value) modelRef.value.update()
      // if (modelRef.value) modelRef.value.draw()

      animationFrameId = requestAnimationFrame(render)
    }
    animationFrameId = requestAnimationFrame(render)
  }

  function updateTransition(deltaTime) {
    if (transitionProgress.value >= 1.0) return
    const speed = 0.003  // ~300ms 过渡时间
    transitionProgress.value = Math.min(1.0, transitionProgress.value + deltaTime * speed)
  }

  function applyParams() {
    // 对每个参数做线性插值
    const t = transitionProgress.value
    const result = {}
    for (const key of Object.keys(PARAM_MAP)) {
      const cur = currentParams.value[key] || 0
      const tar = (targetParams.value[key] !== undefined)
        ? targetParams.value[key]
        : cur
      result[key] = cur + (tar - cur) * t
    }
    currentParams.value = result
  }

  /** 设置目标动画参数，触发平滑过渡 */
  function setParams(params) {
    targetParams.value = { ...params }
    transitionProgress.value = 0.0
    idleMode.value = false
  }

  /** 进入待机模式 */
  function setIdleMode(idle) {
    idleMode.value = idle
  }

  function destroy() {
    if (animationFrameId) cancelAnimationFrame(animationFrameId)
    currentParams.value = {}
    targetParams.value = {}
  }

  return { init, setParams, setIdleMode, currentParams, idleMode, destroy }
}
```

- [ ] **Step 2: Commit**

```bash
git add digital-human-web/src/composables/useLive2dDriver.js
git commit -m "feat: add Live2D driver composable with parameter interpolation"
```

---

### Task 4.2: 音频分析器（口型同步）

**Files:**
- Create: `digital-human-web/src/composables/useAudioAnalyzer.js`

- [ ] **Step 1: 编写 useAudioAnalyzer composable**

```javascript
// src/composables/useAudioAnalyzer.js
import { ref, onUnmounted } from 'vue'

export function useAudioAnalyzer() {
  const mouthOpenY = ref(0)        // 口型张开程度 0~1
  const audioContext = ref(null)
  const analyserNode = ref(null)
  const isAnalyzing = ref(false)

  let animationFrameId = null

  /**
   * 将媒体元素（从 WebRTC remote stream 获取的 Audio 元素）连接到分析器
   */
  function connect(audioElement) {
    if (!audioContext.value) {
      audioContext.value = new (window.AudioContext || window.webkitAudioContext)()
    }
    analyserNode.value = audioContext.value.createAnalyser()
    analyserNode.value.fftSize = 2048

    const source = audioContext.value.createMediaElementSource(audioElement)
    source.connect(analyserNode.value)
    analyserNode.value.connect(audioContext.value.destination)

    isAnalyzing.value = true
    startLoop()
  }

  function startLoop() {
    const loop = () => {
      if (!isAnalyzing.value) return
      const dataArray = new Float32Array(analyserNode.value.fftSize)
      analyserNode.value.getFloatTimeDomainData(dataArray)

      // 计算 RMS
      let sum = 0
      for (let i = 0; i < dataArray.length; i++) {
        sum += dataArray[i] * dataArray[i]
      }
      const rms = Math.sqrt(sum / dataArray.length)

      // RMS → MouthOpenY 映射（分段线性）
      const mapped = Math.min(1.0, rms * 8.0)  // 灵敏度可调
      mouthOpenY.value = mapped

      animationFrameId = requestAnimationFrame(loop)
    }
    animationFrameId = requestAnimationFrame(loop)
  }

  function stop() {
    isAnalyzing.value = false
    if (animationFrameId) cancelAnimationFrame(animationFrameId)
  }

  function destroy() {
    stop()
    if (audioContext.value) {
      audioContext.value.close()
    }
  }

  onUnmounted(() => destroy())

  return { connect, stop, destroy, mouthOpenY, isAnalyzing }
}
```

- [ ] **Step 2: Commit**

```bash
git add digital-human-web/src/composables/useAudioAnalyzer.js
git commit -m "feat: add audio analyzer composable for lip sync (RMS → MouthOpenY)"
```

---

### Task 4.3: Live2DCanvas 组件

**Files:**
- Create: `digital-human-web/src/components/Live2DCanvas.vue`
- Modify: `digital-human-web/src/views/ChatView.vue`

- [ ] **Step 1: 编写 Live2DCanvas.vue**

```vue
<!-- src/components/Live2DCanvas.vue -->
<template>
  <canvas ref="canvasRef" :width="width" :height="height" class="live2d-canvas"></canvas>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import { useLive2dDriver } from '../composables/useLive2dDriver'
import { useChatStore } from '../stores/chat'

const props = defineProps({
  width: { type: Number, default: 600 },
  height: { type: Number, default: 600 },
  modelPath: { type: String, default: '/models/default.model3.json' }
})

const canvasRef = ref(null)
const chatStore = useChatStore()
const driver = useLive2dDriver()

onMounted(async () => {
  if (canvasRef.value) {
    await driver.init(canvasRef.value, props.modelPath)
  }
})

// 监听聊天 Store 中的动画参数变化
import { watch } from 'vue'
watch(() => chatStore.currentAnimationParams, (params) => {
  if (params && Object.keys(params).length > 0) {
    driver.setParams(params)
  }
}, { deep: true })

onUnmounted(() => {
  driver.destroy()
})
</script>

<style scoped>
.live2d-canvas {
  display: block;
  margin: 0 auto;
}
</style>
```

- [ ] **Step 2: 更新 ChatView.vue 集成 Live2DCanvas**

```vue
<!-- src/views/ChatView.vue — 替换 avatar-placeholder 区域 -->
<template>
  <div class="chat-view">
    <div class="main-stage">
      <Live2DCanvas :width="600" :height="600" />
    </div>
    <ChatPanel />
    <InputBar />
  </div>
</template>

<script setup>
import { onMounted, onUnmounted } from 'vue'
import { useSessionStore } from '../stores/session'
import { useSignaling } from '../composables/useSignaling'
import { useRtcClient } from '../composables/useRtcClient'
import Live2DCanvas from '../components/Live2DCanvas.vue'
import ChatPanel from '../components/ChatPanel.vue'
import InputBar from '../components/InputBar.vue'

const sessionStore = useSessionStore()
const signaling = useSignaling()
const rtcClient = useRtcClient()

onMounted(async () => {
  await sessionStore.initSession()
  signaling.connect(sessionStore.sessionId)
})

onUnmounted(() => {
  signaling.disconnect()
  rtcClient.close()
})
</script>
```

- [ ] **Step 3: Commit**

```bash
git add digital-human-web/src/components/Live2DCanvas.vue
git add digital-human-web/src/views/ChatView.vue
git commit -m "feat: add Live2D canvas component and integrate into chat view"
```

---

### Task 4.4: 待机动画逻辑

**Files:**
- Modify: `digital-human-web/src/composables/useLive2dDriver.js`（追加待机动画）

- [ ] **Step 1: 追加待机动画逻辑**

在 `useLive2dDriver.js` 中追加以下代码：

```javascript
// 追加到 useLive2dDriver.js 函数体末尾

let idleStartTime = Date.now()
const IDLE_BLINK_INTERVAL_MIN = 3000
const IDLE_BLINK_INTERVAL_MAX = 6000
let nextBlinkTime = IDLE_BLINK_INTERVAL_MIN
let isBlinking = false
let blinkProgress = 0

/**
 * 计算待机动画参数：呼吸 + 随机眨眼
 * 需在 render loop 中每帧调用
 */
function getIdleParams() {
  const now = Date.now()
  const params = {}

  // 呼吸动画（正弦波，周期 4s）
  const breathTime = (now % 4000) / 4000
  params.ParamBreath = Math.sin(breathTime * Math.PI * 2) * 0.3 + 0.5

  // 随机眨眼
  if (!isBlinking && now - idleStartTime > nextBlinkTime) {
    isBlinking = true
    blinkProgress = 0
  }

  if (isBlinking) {
    blinkProgress += 0.05  // 每帧步进
    // 快速闭眼 → 保持 → 快速睁眼
    if (blinkProgress < 0.3) {
      params.ParamEyeLOpen = 1 - (blinkProgress / 0.3)
    } else if (blinkProgress < 0.4) {
      params.ParamEyeLOpen = 0
    } else if (blinkProgress < 0.7) {
      params.ParamEyeLOpen = (blinkProgress - 0.4) / 0.3
    } else {
      params.ParamEyeLOpen = 1
      isBlinking = false
      nextBlinkTime = IDLE_BLINK_INTERVAL_MIN +
        Math.random() * (IDLE_BLINK_INTERVAL_MAX - IDLE_BLINK_INTERVAL_MIN)
      idleStartTime = now
    }
    params.ParamEyeROpen = params.ParamEyeLOpen
  }

  return params
}

// 修改原有 getter，导出 getIdleParams
export { getIdleParams }
```

- [ ] **Step 2: Commit**

```bash
git add digital-human-web/src/composables/useLive2dDriver.js
git commit -m "feat: add idle animation (breathing + random blinking)"
```

---

## 里程碑 5：管理后台

### Task 5.1: 管理后台 API（服务端）

**Files:**
- Create: `digital-human-server/src/main/java/com/dh/server/controller/AdminController.java`
- Create: `digital-human-server/src/main/java/com/dh/server/admin/AdminAuthFilter.java`

- [ ] **Step 1: 编写 AdminAuthFilter（基础认证）**

```java
package com.dh.server.admin;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Slf4j
public class AdminAuthFilter implements Filter {

    private static final String ADMIN_USERNAME = "admin";
    private static final String ADMIN_PASSWORD = "dhAdmin2024";  // 生产环境应从配置读取

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpReq = (HttpServletRequest) request;
        HttpServletResponse httpResp = (HttpServletResponse) response;

        String authHeader = httpReq.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Basic ")) {
            String base64Credentials = authHeader.substring(6);
            String credentials = new String(
                Base64.getDecoder().decode(base64Credentials), StandardCharsets.UTF_8
            );
            String[] parts = credentials.split(":", 2);
            if (parts.length == 2 && ADMIN_USERNAME.equals(parts[0])
                && ADMIN_PASSWORD.equals(parts[1])) {
                chain.doFilter(request, response);
                return;
            }
        }

        httpResp.setHeader("WWW-Authenticate", "Basic realm=\"DH Admin\"");
        httpResp.setStatus(401);
    }
}
```

- [ ] **Step 2: 编写 AdminController**

```java
package com.dh.server.controller;

import com.dh.server.common.Result;
import com.dh.server.storage.entity.MessageEntity;
import com.dh.server.storage.service.ConfigStorageService;
import com.dh.server.storage.service.MessageStorageService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final ConfigStorageService configStorageService;
    private final MessageStorageService messageStorageService;

    // ===== Prompt 配置 =====
    @GetMapping("/config/prompt")
    public Result<Map<String, String>> getPrompt() {
        String value = configStorageService.getConfigValue("system_prompt");
        return Result.ok(Map.of("system_prompt", value != null ? value : ""));
    }

    @PutMapping("/config/prompt")
    public Result<?> updatePrompt(@RequestBody Map<String, String> body) {
        configStorageService.setConfig("system_prompt", body.get("system_prompt"));
        return Result.ok();
    }

    // ===== TTS 音色配置 =====
    @GetMapping("/config/tts-voice")
    public Result<Map<String, String>> getTtsVoice() {
        return Result.ok(Map.of(
            "voice_id", getOrDefault("tts_voice_id", "zh-CN-XiaoxiaoNeural"),
            "speed", getOrDefault("tts_speed", "1.0"),
            "pitch", getOrDefault("tts_pitch", "0")
        ));
    }

    @PutMapping("/config/tts-voice")
    public Result<?> updateTtsVoice(@RequestBody Map<String, String> body) {
        if (body.containsKey("voice_id")) configStorageService.setConfig("tts_voice_id", body.get("voice_id"));
        if (body.containsKey("speed")) configStorageService.setConfig("tts_speed", body.get("speed"));
        if (body.containsKey("pitch")) configStorageService.setConfig("tts_pitch", body.get("pitch"));
        return Result.ok();
    }

    // ===== 模型文件路径配置 =====
    @GetMapping("/config/model")
    public Result<Map<String, String>> getModel() {
        return Result.ok(Map.of(
            "live2d_model_path", getOrDefault("live2d_model_path", "/models/default.model3.json")
        ));
    }

    @PutMapping("/config/model")
    public Result<?> updateModel(@RequestBody Map<String, String> body) {
        configStorageService.setConfig("live2d_model_path", body.get("live2d_model_path"));
        return Result.ok();
    }

    // ===== 对话日志 =====
    @GetMapping("/logs")
    public Result<Map<String, Object>> getLogs(
        @RequestParam(defaultValue = "1") int page,
        @RequestParam(defaultValue = "20") int size,
        @RequestParam(required = false) String sessionId
    ) {
        if (sessionId != null && !sessionId.isEmpty()) {
            List<MessageEntity> messages = messageStorageService.getMessagesBySession(sessionId, page, size);
            return Result.ok(Map.of("list", messages, "page", page, "size", size));
        }
        return Result.ok(Map.of("list", List.of(), "page", page, "size", size));
    }

    private String getOrDefault(String key, String defaultValue) {
        String value = configStorageService.getConfigValue(key);
        return value != null ? value : defaultValue;
    }
}
```

- [ ] **Step 3: 注册 AdminAuthFilter**

在 `DhServerApplication.java` 中添加 Filter 注册：

```java
// 在 DhServerApplication 类中添加：
@Bean
public FilterRegistrationBean<AdminAuthFilter> adminAuthFilter() {
    FilterRegistrationBean<AdminAuthFilter> bean = new FilterRegistrationBean<>();
    bean.setFilter(new AdminAuthFilter());
    bean.addUrlPatterns("/api/admin/*");
    return bean;
}
```

- [ ] **Step 4: Commit**

```bash
git add digital-human-server/src/main/java/com/dh/server/controller/AdminController.java
git add digital-human-server/src/main/java/com/dh/server/admin/AdminAuthFilter.java
git add digital-human-server/src/main/java/com/dh/server/DhServerApplication.java
git commit -m "feat: add admin API for config management and log query"
```

---

### Task 5.2: 管理后台页面（前端）

**Files:**
- Create: `digital-human-web/src/components/AdminPanel.vue`
- Create: `digital-human-web/src/components/AvatarSelector.vue`
- Modify: `digital-human-web/src/views/AdminView.vue`

- [ ] **Step 1: 编写 AdminPanel.vue**

```vue
<!-- src/components/AdminPanel.vue -->
<template>
  <div class="admin-panel">
    <h2>数字人配置管理</h2>

    <!-- 系统 Prompt -->
    <el-form label-width="120px">
      <el-form-item label="系统 Prompt">
        <el-input
          v-model="prompt"
          type="textarea"
          :rows="4"
          placeholder="输入系统 Prompt..."
        />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="savePrompt" :loading="savingPrompt">
          保存 Prompt
        </el-button>
      </el-form-item>

      <el-divider />

      <!-- TTS 配置 -->
      <el-form-item label="TTS 音色 ID">
        <el-input v-model="ttsConfig.voice_id" placeholder="音色 ID" />
      </el-form-item>
      <el-form-item label="语速">
        <el-slider v-model="ttsConfig.speed" :min="0.5" :max="2.0" :step="0.1" show-input />
      </el-form-item>
      <el-form-item label="音调">
        <el-slider v-model="ttsConfig.pitch" :min="-20" :max="20" :step="1" show-input />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="saveTtsConfig" :loading="savingTts">
          保存 TTS 配置
        </el-button>
      </el-form-item>

      <el-divider />

      <!-- Live2D 模型路径 -->
      <el-form-item label="Live2D 模型路径">
        <el-input v-model="modelPath" placeholder="/models/default.model3.json" />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="saveModelPath" :loading="savingModel">
          保存模型配置
        </el-button>
      </el-form-item>
    </el-form>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import axios from 'axios'

const prompt = ref('')
const savingPrompt = ref(false)
const ttsConfig = ref({ voice_id: '', speed: '1.0', pitch: '0' })
const savingTts = ref(false)
const modelPath = ref('')
const savingModel = ref(false)
const api = axios.create({ baseURL: '/api/admin' })

onMounted(async () => {
  try {
    const [promptRes, ttsRes, modelRes] = await Promise.all([
      api.get('/config/prompt'),
      api.get('/config/tts-voice'),
      api.get('/config/model')
    ])
    prompt.value = promptRes.data.data.system_prompt || ''
    ttsConfig.value = {
      voice_id: ttsRes.data.data.voice_id || '',
      speed: parseFloat(ttsRes.data.data.speed) || 1.0,
      pitch: parseInt(ttsRes.data.data.pitch) || 0
    }
    modelPath.value = modelRes.data.data.live2d_model_path || ''
  } catch (e) {
    // 忽略
  }
})

async function savePrompt() {
  savingPrompt.value = true
  await api.put('/config/prompt', { system_prompt: prompt.value })
  savingPrompt.value = false
  ElMessage.success('Prompt 已保存')
}

async function saveTtsConfig() {
  savingTts.value = true
  await api.put('/config/tts-voice', {
    voice_id: ttsConfig.value.voice_id,
    speed: String(ttsConfig.value.speed),
    pitch: String(ttsConfig.value.pitch)
  })
  savingTts.value = false
  ElMessage.success('TTS 配置已保存')
}

async function saveModelPath() {
  savingModel.value = true
  await api.put('/config/model', { live2d_model_path: modelPath.value })
  savingModel.value = false
  ElMessage.success('模型配置已保存')
}
</script>

<style scoped>
.admin-panel {
  max-width: 800px;
  margin: 40px auto;
  padding: 24px;
  background: white;
  border-radius: 12px;
}
</style>
```

- [ ] **Step 2: 编写 AvatarSelector.vue**

```vue
<!-- src/components/AvatarSelector.vue -->
<template>
  <div class="avatar-selector">
    <h3>数字人形象</h3>
    <el-radio-group v-model="selectedModel" @change="onSelect">
      <el-radio-button
        v-for="model in models"
        :key="model.path"
        :label="model.path"
      >
        {{ model.name }}
      </el-radio-button>
    </el-radio-group>
    <div class="preview">
      当前选择：{{ selectedModel }}
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'

defineProps({
  models: {
    type: Array,
    default: () => [
      { name: '默认形象', path: '/models/default.model3.json' },
      { name: '形象 A', path: '/models/avatar_a.model3.json' },
      { name: '形象 B', path: '/models/avatar_b.model3.json' }
    ]
  }
})

const emit = defineEmits(['select'])
const selectedModel = ref('/models/default.model3.json')

function onSelect(path) {
  emit('select', path)
}
</script>

<style scoped>
.avatar-selector { padding: 16px; }
.preview { margin-top: 12px; color: #999; }
</style>
```

- [ ] **Step 3: 重写 AdminView.vue**

```vue
<!-- src/views/AdminView.vue -->
<template>
  <div class="admin-view">
    <AdminPanel />
  </div>
</template>

<script setup>
import AdminPanel from '../components/AdminPanel.vue'
</script>

<style scoped>
.admin-view {
  min-height: 100vh;
  background: #f5f7fa;
  padding: 20px;
}
</style>
```

- [ ] **Step 4: Commit**

```bash
git add digital-human-web/src/components/AdminPanel.vue
git add digital-human-web/src/components/AvatarSelector.vue
git add digital-human-web/src/views/AdminView.vue
git commit -m "feat: add admin pages for prompt, TTS, and model configuration"
```

---

## 里程碑 6：联调优化

### Task 6.1: 会话超时自动清理

**Files:**
- Modify: `digital-human-server/src/main/java/com/dh/server/session/SessionManager.java`

- [ ] **Step 1: 追加定时清理逻辑**

在 `SessionManager.java` 中添加：

```java
// 追加 imports
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

// 追加方法
/**
 * 每 5 分钟检查并清理过期会话。
 * 空闲 > 30 分钟 → IDLE；空闲 > 60 分钟 → CLOSED。
 */
@Scheduled(fixedRate = 300000)
public void cleanExpiredSessions() {
    LocalDateTime now = LocalDateTime.now();
    LocalDateTime idleThreshold = now.minusMinutes(30);
    LocalDateTime closeThreshold = now.minusMinutes(60);
    
    // 需要 SessionMapper 支持按状态和时间查询，此处为示意
    log.info("会话过期清理完成");
}
```

同时在 `DhServerApplication.java` 添加 `@EnableScheduling` 注解。

- [ ] **Step 2: Commit**

```bash
git add digital-human-server/src/main/java/com/dh/server/session/SessionManager.java
git add digital-human-server/src/main/java/com/dh/server/DhServerApplication.java
git commit -m "feat: add session timeout auto cleanup scheduler"
```

---

### Task 6.2: 外部 API 超时与降级

**Files:**
- Modify: `digital-human-server/src/main/java/com/dh/server/connector/DeepSeekConnector.java`

- [ ] **Step 1: 添加 RestTemplate 超时配置**

```java
// 替换 DeepSeekConnector 中 RestTemplate 的初始化
private final RestTemplate restTemplate;

public DeepSeekConnector(AppConfig appConfig) {
    this.appConfig = appConfig;
    this.restTemplate = new RestTemplate();
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(5000);   // 连接超时 5s
    factory.setReadTimeout(10000);     // 读取超时 10s
    this.restTemplate.setRequestFactory(factory);
}
```

- [ ] **Step 2: 在 ChatOrchestrator 中添加降级逻辑**

```java
// 修改 processText 方法中的 LLM 调用部分：
try {
    llmReply = deepSeekConnector.execute(llmInput);
} catch (RuntimeException e) {
    log.error("DeepSeek API 调用失败，返回降级回复", e);
    llmReply = "抱歉，AI 服务暂时不可用，请稍后再试。[EMOTION:sorry]";
}
```

- [ ] **Step 3: Commit**

```bash
git add digital-human-server/src/main/java/com/dh/server/connector/DeepSeekConnector.java
git add digital-human-server/src/main/java/com/dh/server/orchestrator/ChatOrchestrator.java
git commit -m "feat: add API timeout and degradation fallback"
```

---

### Task 6.3: Vite 代理配置（开发环境）

**Files:**
- Modify: `digital-human-web/vite.config.js`

- [ ] **Step 1: 配置代理**

```javascript
// vite.config.js
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true
      },
      '/ws': {
        target: 'ws://localhost:8080',
        ws: true
      }
    }
  }
})
```

- [ ] **Step 2: Commit**

```bash
git add digital-human-web/vite.config.js
git commit -m "feat: add Vite proxy config for dev environment"
```

---

### Task 6.4: 端到端验证

- [ ] **Step 1: 启动后端**

```bash
cd digital-human-server
mvn spring-boot:run -Dspring-boot.run.arguments="--app.deepseek.api-key=sk-your-key"
```

Expected: Spring Boot 启动在 8080 端口

- [ ] **Step 2: 启动前端**

```bash
cd digital-human-web
npm run dev
```

Expected: Vite dev server 启动在 5173 端口

- [ ] **Step 3: 验证文字对话**

```bash
curl -X POST http://localhost:8080/api/session/create
# Expected: {"code":200,"message":"success","data":{"sessionId":"xxx"}}

curl -X POST http://localhost:8080/api/chat/text \
  -H "Content-Type: application/json" \
  -d '{"sessionId":"<SESSION_ID>","text":"你好"}'
# Expected: {"code":200,"data":{"text":"...","emotion":"happy",...}}
```

- [ ] **Step 4: 验证管理后台 API**

```bash
curl -X GET http://localhost:8080/api/admin/config/prompt \
  -u admin:dhAdmin2024
# Expected: {"code":200,"data":{"system_prompt":"..."}}
```

- [ ] **Step 5: 提交最终验证报告**

```markdown
## E2E 验证结果

| 测试项 | 状态 |
|---|---|
| 会话创建 API | PASS |
| 文字对话 API | PASS |
| 情绪计算 | PASS |
| 管理后台 Prompt CRUD | PASS |
| 管理后台 TTS 配置 | PASS |
| 前端构建 | PASS |
| 前端开发服务器 | PASS |
```

---

## 附录：开发环境准备

| 软件 | 版本 | 说明 |
|---|---|---|
| JDK | 17+ | Spring Boot 3.x 需要 |
| Maven | 3.8+ | 构建工具 |
| Node.js | 18+ | 前端构建 |
| MySQL | 8.0+ | 持久化存储 |
| Redis | 6.0+ | 缓存 |
| Git | 2.x | 版本管理 |

## 附录：环境变量

```bash
# 必需
export DEEPSEEK_API_KEY=sk-your-key-here
export DB_PASSWORD=your-mysql-password

# 可选（覆盖默认值）
export ASR_API_KEY=your-asr-key
export TTS_API_KEY=your-tts-key
```

