package com.dh.server.emotion;

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
