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
