package com.dh.server.signaling;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class SignalMessage {
    private String type;
    private String sdp;
    private Object candidate;
}
