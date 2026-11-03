package com.luwei.ordering.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SpeechSynthesisRequest {
    //需要朗读的文本内容
    @NotBlank
    @Size(max = 1000)
    private String text;
}
