-- digital_human database schema
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

-- Default config data
INSERT INTO t_dh_config (config_key, config_value, description) VALUES
('system_prompt', '你是小慧，一个专业的企业前台助手。', '系统 Prompt'),
('tts_voice_id', 'zh-CN-XiaoxiaoNeural', 'TTS 音色 ID'),
('tts_speed', '1.0', '语速'),
('tts_pitch', '0', '音调'),
('live2d_model_path', '/models/generated/avatar_default/Haru.model3.json', 'Live2D 模型路径');
