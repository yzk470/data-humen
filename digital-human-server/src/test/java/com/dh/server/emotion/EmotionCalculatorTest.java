package com.dh.server.emotion;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EmotionCalculatorTest {

    private final EmotionCalculator calculator = new EmotionCalculator();

    @Test
    void shouldExtractEmotionFromLlmTag() {
        String reply = "你好呀！很高兴见到你 [EMOTION:happy]";
        EmotionResult result = calculator.calculate(reply);
        assertEquals(EmotionLabel.HAPPY, result.getLabel());
        assertEquals("LLM", result.getSource());
    }

    @Test
    void shouldFallbackToRuleWhenNoTag() {
        String reply = "非常抱歉，我暂时无法回答这个问题。";
        EmotionResult result = calculator.calculate(reply);
        assertEquals(EmotionLabel.SORRY, result.getLabel());
        assertEquals("RULE", result.getSource());
    }

    @Test
    void shouldDefaultToNeutral() {
        String reply = "今天是星期二。";
        EmotionResult result = calculator.calculate(reply);
        assertEquals(EmotionLabel.NEUTRAL, result.getLabel());
        assertEquals("DEFAULT", result.getSource());
    }

    @Test
    void shouldRemoveEmotionTagFromText() {
        String reply = "没问题 [EMOTION:happy]";
        String clean = calculator.removeEmotionTag(reply);
        assertEquals("没问题", clean);
    }

    @Test
    void shouldHandleCaseInsensitiveEmotionTag() {
        String reply = "Hello [EMOTION:HAPPY]";
        EmotionResult result = calculator.calculate(reply);
        assertEquals(EmotionLabel.HAPPY, result.getLabel());
    }

    @Test
    void shouldMapHappyToCorrectLive2DParams() {
        EmotionToLive2DParams mapper = new EmotionToLive2DParams();
        var params = mapper.mapToParams(EmotionLabel.HAPPY);
        assertEquals(0.9, params.get("ParamHappy"), 0.01);
        assertEquals(1.0, params.get("ParamEyeOpen"), 0.01);
    }
}
