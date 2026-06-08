package com.dh.server.emotion;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class EmotionResult {
    private EmotionLabel label;
    private float confidence;
    private String source;
}
