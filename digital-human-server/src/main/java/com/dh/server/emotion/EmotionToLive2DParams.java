package com.dh.server.emotion;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class EmotionToLive2DParams {

    public Map<String, Double> mapToParams(EmotionLabel label) {
        Map<String, Double> params = createNeutralParams();

        switch (label) {
            case HAPPY:
                params.put("ParamMouthForm", 0.8);
                params.put("ParamEyeForm", 0.35);
                params.put("ParamBrowLY", 0.2);
                params.put("ParamBrowRY", 0.2);
                break;
            case PUZZLED:
                params.put("ParamBrowLY", 0.6);
                params.put("ParamBrowRY", 0.35);
                params.put("ParamBrowLAngle", 0.25);
                params.put("ParamBrowRAngle", -0.15);
                params.put("ParamMouthForm", -0.15);
                params.put("ParamEyeForm", -0.1);
                break;
            case SURPRISED:
                params.put("ParamMouthOpenY", 0.45);
                params.put("ParamEyeLOpen", 1.0);
                params.put("ParamEyeROpen", 1.0);
                params.put("ParamBrowLY", 0.7);
                params.put("ParamBrowRY", 0.7);
                params.put("ParamEyeForm", 0.2);
                break;
            case SORRY:
                params.put("ParamMouthForm", -0.65);
                params.put("ParamEyeForm", -0.45);
                params.put("ParamBrowLY", -0.45);
                params.put("ParamBrowRY", -0.45);
                params.put("ParamBrowLAngle", -0.2);
                params.put("ParamBrowRAngle", 0.2);
                break;
            case THINKING:
                params.put("ParamMouthForm", -0.25);
                params.put("ParamEyeBallX", 0.35);
                params.put("ParamBrowLY", 0.15);
                params.put("ParamBrowRY", 0.5);
                params.put("ParamBrowLAngle", -0.1);
                params.put("ParamBrowRAngle", 0.25);
                break;
            case NEUTRAL:
            default:
                break;
        }

        return params;
    }

    private Map<String, Double> createNeutralParams() {
        Map<String, Double> params = new HashMap<>();
        params.put("ParamMouthOpenY", 0.0);
        params.put("ParamMouthForm", 0.0);
        params.put("ParamEyeLOpen", 1.0);
        params.put("ParamEyeROpen", 1.0);
        params.put("ParamEyeForm", 0.0);
        params.put("ParamEyeBallX", 0.0);
        params.put("ParamEyeBallY", 0.0);
        params.put("ParamBrowLY", 0.0);
        params.put("ParamBrowRY", 0.0);
        params.put("ParamBrowLAngle", 0.0);
        params.put("ParamBrowRAngle", 0.0);
        return params;
    }
}
