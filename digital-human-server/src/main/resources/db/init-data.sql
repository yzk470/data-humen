SET NAMES utf8mb4;
INSERT IGNORE INTO t_dh_config (config_key, config_value, description) VALUES ('system_prompt', 'You are a friendly digital human assistant.', 'System Prompt');
INSERT IGNORE INTO t_dh_config (config_key, config_value, description) VALUES ('tts_voice_id', 'zh-CN-XiaoxiaoNeural', 'TTS Voice ID');
INSERT IGNORE INTO t_dh_config (config_key, config_value, description) VALUES ('tts_speed', '1.0', 'Speech Speed');
INSERT IGNORE INTO t_dh_config (config_key, config_value, description) VALUES ('tts_pitch', '0', 'Pitch');
INSERT IGNORE INTO t_dh_config (config_key, config_value, description) VALUES ('live2d_model_path', '/models/generated/avatar_default/Haru.model3.json', 'Live2D Model Path');
