package com.luwei.ordering.service;

import com.luwei.ordering.dto.response.SpeechSynthesisResponse;

public interface SpeechSynthesisService {
    SpeechSynthesisResponse synthesize(String text);
}
