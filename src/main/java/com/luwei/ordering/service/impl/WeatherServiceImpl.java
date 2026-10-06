package com.luwei.ordering.service.impl;

import com.luwei.ordering.service.WeatherService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.zip.GZIPInputStream;

@Service
@Slf4j
public class WeatherServiceImpl implements WeatherService {
    private final String baseUrl;
    private final String apiKey;
    private final String cityId;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public WeatherServiceImpl(@Value("${weather.base-url}") String baseUrl,
                              @Value("${weather.api-key}") String apiKey,
                              @Value("${weather.city-id}") String cityId,
                              ObjectMapper objectMapper) {
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.cityId = cityId;
        this.objectMapper = objectMapper;

        // 在这里建 RestClient(配超时工厂)
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(2));//两秒连接不上就超时
        requestFactory.setReadTimeout(Duration.ofSeconds(3));//三秒读取不到内容也超时

        this.restClient = RestClient.builder()
                                    .baseUrl(baseUrl)
                                    .requestFactory(requestFactory)
                                    .build();
    }

    //{ "code": "200", "now": { "text": "晴", "temp": "34", "humidity": "60", ... } }
    //和风的响应数据 即这里的json数据格式

    @Override
    public String getCurrentWeather() {
        try {
            byte[] body = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/v7/weather/now")
                            .queryParam("location", cityId)
                            .queryParam("key", apiKey)
                            .build())
                    .retrieve()
                    .body(byte[].class);

            //和风的响应是 gzip 压缩的,先还原成 JSON 字符串
            String json = decompressIfGzip(body);

            JsonNode root = objectMapper.readTree(json);

            String code = root.path("code").asText();
            if (!"200".equals(code)) {
                log.error("和风接口返回异常, code:{}", code);
                return null;
            }

            JsonNode now = root.path("now");
            String text = now.path("text").asText();
            String temp = now.path("temp").asText();
            return text + "，" + temp + "°C";
        }
        catch (Exception e) {log.error("当前天气推荐模块异常,跳过天气推荐",e);}

        return null;
    }

    /**
     * gzip 魔数是 1F 8B,匹配才解压;否则按普通 UTF-8 文本处理
     */
    private String decompressIfGzip(byte[] body) throws Exception {
        boolean isGzip = body.length > 2
                && body[0] == (byte) 0x1F
                && body[1] == (byte) 0x8B;
        if (!isGzip) {
            return new String(body, StandardCharsets.UTF_8);
        }
        try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(body))) {
            return new String(gzip.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
