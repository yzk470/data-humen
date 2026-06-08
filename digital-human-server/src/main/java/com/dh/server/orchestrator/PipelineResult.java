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
    private String text;
    private String emotion;
    private Map<String, Double> animationParams;
    private String audioBase64;
}
