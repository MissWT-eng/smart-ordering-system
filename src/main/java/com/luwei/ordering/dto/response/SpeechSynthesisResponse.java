package com.luwei.ordering.dto.response;

public record SpeechSynthesisResponse(
        String audioUrl,
        String model,
        String voice
) {}
