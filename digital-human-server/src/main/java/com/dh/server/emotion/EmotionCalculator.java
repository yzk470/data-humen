package com.dh.server.emotion;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
public class EmotionCalculator {

    private static final Pattern EMOTION_PATTERN =
        Pattern.compile("\\[EMOTION:(\\w+)\\]", Pattern.CASE_INSENSITIVE);

    private static final Map<String, EmotionLabel> KEYWORD_MAP = Map.ofEntries(
        Map.entry("谢谢", EmotionLabel.HAPPY),
        Map.entry("感谢", EmotionLabel.HAPPY),
        Map.entry("太好了", EmotionLabel.HAPPY),
        Map.entry("恭喜", EmotionLabel.HAPPY),
        Map.entry("欢迎", EmotionLabel.HAPPY),
        Map.entry("不确定", EmotionLabel.PUZZLED),
        Map.entry("请问", EmotionLabel.PUZZLED),
        Map.entry("不太清楚", EmotionLabel.PUZZLED),
        Map.entry("什么", EmotionLabel.SURPRISED),
        Map.entry("真的吗", EmotionLabel.SURPRISED),
        Map.entry("天哪", EmotionLabel.SURPRISED),
        Map.entry("抱歉", EmotionLabel.SORRY),
        Map.entry("对不起", EmotionLabel.SORRY),
        Map.entry("遗憾", EmotionLabel.SORRY),
        Map.entry("请稍等", EmotionLabel.THINKING),
        Map.entry("让我想想", EmotionLabel.THINKING),
        Map.entry("正在查询", EmotionLabel.THINKING),
        Map.entry("嗯", EmotionLabel.THINKING)
    );

    public EmotionResult calculate(String llmReplyText) {
        Matcher matcher = EMOTION_PATTERN.matcher(llmReplyText);
        if (matcher.find()) {
            String labelStr = matcher.group(1).toLowerCase();
            if (EmotionLabel.ALL_LABELS.contains(labelStr)) {
                return new EmotionResult(EmotionLabel.fromLabel(labelStr), 0.9f, "LLM");
            }
        }

        for (Map.Entry<String, EmotionLabel> entry : KEYWORD_MAP.entrySet()) {
            if (llmReplyText.contains(entry.getKey())) {
                return new EmotionResult(entry.getValue(), 0.6f, "RULE");
            }
        }

        return new EmotionResult(EmotionLabel.NEUTRAL, 0.5f, "DEFAULT");
    }

    public String removeEmotionTag(String text) {
        return EMOTION_PATTERN.matcher(text).replaceAll("").trim();
    }
}
