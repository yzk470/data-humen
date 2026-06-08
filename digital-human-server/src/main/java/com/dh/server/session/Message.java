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
