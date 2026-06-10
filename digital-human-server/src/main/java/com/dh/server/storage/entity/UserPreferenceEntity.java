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
