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
