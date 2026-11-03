package com.luwei.ordering.service;

import com.luwei.ordering.dto.response.SpeechRecognitionResponse;

public interface SpeechRecognitionService {
    SpeechRecognitionResponse recognize(byte[] audio, String mimeType);
}
