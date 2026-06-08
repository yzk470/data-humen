package com.dh.server.emotion;

import org.springframework.stereotype.Component;
import java.util.HashMap;
import java.util.Map;

@Component
public class EmotionToLive2DParams {

    public Map<String, Double> mapToParams(EmotionLabel label) {
        Map<String, Double> params = new HashMap<>();

        params.put("ParamMouthOpenY", 0.0);
        params.put("ParamMouthForm", 0.0);
        params.put("ParamEyeOpen", 1.0);
        params.put("ParamBrowY", 0.0);
        params.put("ParamAngry", 0.0);
        params.put("ParamHappy", 0.5);
        params.put("ParamSad", 0.0);
        params.put("ParamSurprise", 0.0);

        switch (label) {
            case HAPPY:
                params.put("ParamHappy", 0.9);
                params.put("ParamEyeOpen", 1.0);
                break;
            case PUZZLED:
                params.put("ParamBrowY", 0.6);
                params.put("ParamEyeOpen", 0.7);
                params.put("ParamSurprise", 0.3);
                break;
            case SURPRISED:
                params.put("ParamSurprise", 0.9);
                params.put("ParamEyeOpen", 1.0);
                params.put("ParamMouthOpenY", 0.4);
                break;
            case SORRY:
                params.put("ParamSad", 0.6);
                params.put("ParamBrowY", -0.3);
                params.put("ParamHappy", 0.2);
                break;
            case THINKING:
                params.put("ParamBrowY", 0.4);
                params.put("ParamEyeOpen", 0.5);
                params.put("ParamHappy", 0.3);
                break;
            case NEUTRAL:
            default:
                params.put("ParamHappy", 0.5);
                break;
        }
        return params;
    }
}
