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
