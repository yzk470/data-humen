package com.dh.server.connector;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import java.util.Base64;

@Slf4j
@Component
@RequiredArgsConstructor
public class TtsConnector implements Connector<String, byte[]> {

    @Override
    public byte[] execute(String text) {
        log.warn("TtsConnector 尚未接入实际 TTS API，返回空音频。请配置云 TTS 服务。");
        return new byte[0];
    }

    public String executeAsBase64(String text) {
        byte[] audio = execute(text);
        if (audio.length == 0) return "";
        return Base64.getEncoder().encodeToString(audio);
    }
}
