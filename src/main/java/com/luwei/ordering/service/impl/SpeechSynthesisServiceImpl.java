package com.luwei.ordering.service.impl;

import com.luwei.ordering.common.exception.BusinessException;
import com.luwei.ordering.common.exception.ErrorCode;
import com.luwei.ordering.dto.response.SpeechSynthesisResponse;
import com.luwei.ordering.service.SpeechSynthesisService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.time.Duration;
import java.util.Map;

@Service
@Slf4j
public class SpeechSynthesisServiceImpl implements SpeechSynthesisService {
    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String model;
    private final String baseUrl;
    private final String voice;

    public SpeechSynthesisServiceImpl(@Value("${speech.base-url}")String baseUrl,
                                        @Value("${speech.api-key}")String apiKey,
                                        @Value("${speech.tts.model}")String model,
                                        @Value("${speech.tts.voice}")String voice,
                                        ObjectMapper objectMapper){
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.model = model;
        this.voice = voice;
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
    public SpeechSynthesisResponse synthesize(String text) {
        //检查text是否合法
        if (text == null || text.isBlank()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "朗读文字不能为空");
        }

        if (text.length() > 1000) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "朗读文字不能超过1000字符");
        }
        //检查配置字段是否合法
        if (!StringUtils.hasText(baseUrl)
                || !StringUtils.hasText(apiKey)
                || !StringUtils.hasText(model)
                || !StringUtils.hasText(voice)) {
            throw new BusinessException(ErrorCode.SPEECH_SYNTHESIS_UNAVAILABLE);
        }

        //组装请求体
        Map<String,Object> input = Map.of(
                "text", text.strip(),
                "voice", voice,
                "instruction", "你说话的角色是温和客服，你说话的情感是neutral。",
                "format", "mp3",
                "enable_ssml", false
        );
        Map<String,Object> requestBody = Map.of(
                "model", model,
                "input", input
        );

        try{
            String responseJson = restClient.post()
                    .uri("/api/v1/services/audio/tts/SpeechSynthesizer")
                    .contentType(MediaType.APPLICATION_JSON)
                    .headers(headers -> headers.setBearerAuth(apiKey))
                    .body(requestBody)
                    .retrieve()
                    .body(String.class);
            if(responseJson == null || responseJson.isBlank()){
                throw new BusinessException(ErrorCode.SPEECH_SYNTHESIS_UNAVAILABLE);
            }

            JsonNode root = objectMapper.readTree(responseJson);
            if(root == null || !root.isObject()
                    || root.hasNonNull("code")
                    || !"stop".equals(root.path("output").path("finish_reason").asString(""))){
                throw new BusinessException(ErrorCode.SPEECH_SYNTHESIS_UNAVAILABLE);
            }

            JsonNode urlNode = root.path("output").path("audio").path("url");
            if(!urlNode.isString() || !StringUtils.hasText(urlNode.asString())){
                throw new BusinessException(ErrorCode.SPEECH_SYNTHESIS_UNAVAILABLE);
            }

            String audioUrl = normalizeAudioUrl(urlNode.asString());
            return new SpeechSynthesisResponse(audioUrl, model, voice);
        }
        catch (RestClientResponseException e){
            log.warn("语音合成上游请求失败: status={}", e.getStatusCode().value());
            throw new BusinessException(ErrorCode.SPEECH_SYNTHESIS_UNAVAILABLE);
        }
        catch (RestClientException | JacksonException | IllegalArgumentException e){
            log.warn("语音合成请求或响应异常: type={}", e.getClass().getSimpleName());
            throw new BusinessException(ErrorCode.SPEECH_SYNTHESIS_UNAVAILABLE);
        }
    }

    private String normalizeAudioUrl(String audioUrl) {
        URI uri;
        try {
            uri = URI.create(audioUrl.strip());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.SPEECH_SYNTHESIS_UNAVAILABLE);
        }

        if (!StringUtils.hasText(uri.getHost())
                || !StringUtils.hasText(uri.getRawPath())
                || "/".equals(uri.getRawPath())
                || uri.getRawUserInfo() != null
                || uri.getRawFragment() != null) {
            throw new BusinessException(ErrorCode.SPEECH_SYNTHESIS_UNAVAILABLE);
        }

        if ("https".equalsIgnoreCase(uri.getScheme())
                && (uri.getPort() == -1 || uri.getPort() == 443)) {
            return uri.toString();
        }

        // The documented Beijing OSS URL uses HTTP. Preserve the signed path and query when upgrading it.
        if ("http".equalsIgnoreCase(uri.getScheme()) && uri.getPort() == -1
                && "dashscope-result-bj.oss-cn-beijing.aliyuncs.com".equalsIgnoreCase(uri.getHost())) {
            return "https" + uri.toString().substring(uri.getScheme().length());
        }

        throw new BusinessException(ErrorCode.SPEECH_SYNTHESIS_UNAVAILABLE);
    }
}
