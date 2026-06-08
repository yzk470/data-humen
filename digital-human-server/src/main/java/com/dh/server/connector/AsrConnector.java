package com.dh.server.connector;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class AsrConnector implements Connector<byte[], String> {

    @Override
    public String execute(byte[] audioBytes) {
        log.warn("AsrConnector 尚未接入实际 ASR API。请配置云 ASR 服务。");
        return "";
    }
}
