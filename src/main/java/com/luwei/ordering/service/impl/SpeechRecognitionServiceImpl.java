package com.luwei.ordering.service.impl;

import org.springframework.util.StringUtils;
import tools.jackson.core.JacksonException;
import com.luwei.ordering.common.exception.BusinessException;
import com.luwei.ordering.common.exception.ErrorCode;
import com.luwei.ordering.dto.response.SpeechRecognitionResponse;
import com.luwei.ordering.service.SpeechRecognitionService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import java.util.List;
import java.time.Duration;
import java.util.Base64;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class SpeechRecognitionServiceImpl implements SpeechRecognitionService {
    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String model;
    private final String baseUrl;

    public SpeechRecognitionServiceImpl(@Value("${speech.base-url}")String baseUrl,
                                        @Value("${speech.api-key}")String apiKey,
                                        @Value("${speech.asr.model}")String model,
                                        ObjectMapper objectMapper){
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.model = model;
        this.objectMapper = objectMapper;

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(2));
        factory.setReadTimeout(Duration.ofSeconds(30));

        this.restClient = RestClient.builder()
                                    .baseUrl(baseUrl)
                                    .requestFactory(factory)
                                    .build();
    }

    @Override
    public SpeechRecognitionResponse recognize(byte[] audio, String mimeType) {
        //开头先检查录音是否存在
        if(audio == null || audio.length == 0){
            throw new BusinessException(ErrorCode.PARAM_ERROR,"录音不能为空");
        }

        //首版限制录音最大5MB
        if(audio.length > 5 * 1024 * 1024){
            throw new BusinessException(ErrorCode.PARAM_ERROR,"录音大小不能超过5MB");
        }

        if(mimeType == null || mimeType.isBlank()){
            throw new BusinessException(ErrorCode.PARAM_ERROR,"音频类型不能为空");
        }

        String normalizedMimeType = mimeType.split(";", 2)[0]
                                            .trim()
                                            .toLowerCase(Locale.ROOT);

        Set<String> supportedTypes = Set.of("audio/webm",
                "audio/wav",
                "audio/mpeg",
                "audio/ogg");

        if(! supportedTypes.contains(normalizedMimeType)){
            throw new BusinessException(ErrorCode.PARAM_ERROR,"不支持的音频格式");
        }

        if (!StringUtils.hasText(baseUrl)
                || !StringUtils.hasText(apiKey)
                || !StringUtils.hasText(model)) {
            throw new BusinessException(ErrorCode.SPEECH_SERVICE_UNAVAILABLE);
        }

        String audioBase64 = Base64.getEncoder().encodeToString(audio);
        String audioDataUrl =
                "data:" + normalizedMimeType + ";base64," + audioBase64;

        //组装阿里云的请求体
        //1.音频数据
        Map<String,String> inputAudio = Map.of("data",audioDataUrl);

        //2.一条音频内容
        Map<String,Object> audioContent = Map.of("type","input_audio",
                                                  "input_audio",inputAudio);

        //3.用户消息，content是数组
        Map<String,Object> userMessage = Map.of("role","user",
                                                "content",List.of(audioContent));

        //4.组装完整请求体
        Map<String,Object> requestBody = Map.of("model",model,
                                                "messages",List.of(userMessage),
                                                "stream",false
                );
        try {
            String responseJson = restClient.post()
                    .uri("/compatible-mode/v1/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .headers(headers -> headers.setBearerAuth(apiKey))
                    .body(requestBody)
                    .retrieve()
                    .body(String.class);

            if(responseJson == null || responseJson.isBlank()){
                throw new BusinessException(ErrorCode.SPEECH_SERVICE_UNAVAILABLE);
            }

            JsonNode root = objectMapper.readTree(responseJson);
            if(root == null || ! root.isObject()){
                throw new BusinessException(ErrorCode.SPEECH_SERVICE_UNAVAILABLE);
            }

            JsonNode content = root.path("choices")
                    .path(0)
                    .path("message")
                    .path("content");

            if (!content.isString()) {
                throw new BusinessException(ErrorCode.SPEECH_SERVICE_UNAVAILABLE);
            }

            String text = content.asString().strip();

            if (text.isBlank()) {
                throw new BusinessException(
                        ErrorCode.PARAM_ERROR,
                        "未识别到有效语音，请重新录音"
                );
            }

            return new SpeechRecognitionResponse(text);
        }

        catch (RestClientException | JacksonException e) {
            throw new BusinessException(ErrorCode.SPEECH_SERVICE_UNAVAILABLE);
        }
    }

}
