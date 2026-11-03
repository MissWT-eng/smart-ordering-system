package com.luwei.ordering.controller;

import com.luwei.ordering.common.exception.BusinessException;
import com.luwei.ordering.common.exception.ErrorCode;
import com.luwei.ordering.dto.request.SpeechSynthesisRequest;
import com.luwei.ordering.dto.response.ApiResponse;
import com.luwei.ordering.dto.response.SpeechRecognitionResponse;
import com.luwei.ordering.dto.response.SpeechSynthesisResponse;
import com.luwei.ordering.service.SpeechRecognitionService;
import com.luwei.ordering.service.SpeechSynthesisService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/speech")
@RequiredArgsConstructor

public class SpeechRecognitionController {
    private final SpeechRecognitionService speechRecognitionService;
    private final SpeechSynthesisService speechSynthesisService;

    @PostMapping(
            value = "/recognize",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ApiResponse<SpeechRecognitionResponse> recognize(@RequestPart("audio") MultipartFile audio){
        try{
            SpeechRecognitionResponse result = speechRecognitionService.recognize(audio.getBytes(), audio.getContentType());
            return ApiResponse.success(result);
        }
        catch (IOException e){
            throw new BusinessException(
                    ErrorCode.SYSTEM_ERROR,
                    "录音文件读取失败，请重试");
        }
    }

    @PostMapping(value = "/synthesize",
                consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<SpeechSynthesisResponse> synthesize(
            @RequestBody @Valid SpeechSynthesisRequest req){
            SpeechSynthesisResponse result = speechSynthesisService.synthesize(req.getText());
            return ApiResponse.success(result);
        }
    }
