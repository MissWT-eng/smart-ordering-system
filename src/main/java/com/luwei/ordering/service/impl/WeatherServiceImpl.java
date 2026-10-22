package com.luwei.ordering.service.impl;

import com.luwei.ordering.common.exception.BusinessException;
import com.luwei.ordering.common.exception.ErrorCode;
import com.luwei.ordering.dto.response.WeatherInfoDTO;
import com.luwei.ordering.service.WeatherService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.zip.GZIPInputStream;

@Service
@Slf4j
public class WeatherServiceImpl implements WeatherService {
    private final String baseUrl;
    private final String apiKey;
    private final String cityId;
    private final String weatherCityName;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final StringRedisTemplate redisTemplate;
    private final long cacheTtlSeconds;

    public WeatherServiceImpl(@Value("${weather.base-url}") String baseUrl,
                              @Value("${weather.api-key}") String apiKey,
                              @Value("${weather.city-id}") String cityId,
                              @Value("${weather.city-name}") String weatherCityName,
                              @Value("${weather.cache-ttl-seconds}") long cacheTtlSeconds,
                              StringRedisTemplate redisTemplate,
                              ObjectMapper objectMapper) {
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.cityId = cityId;
        this.weatherCityName = weatherCityName;
        this.redisTemplate = redisTemplate;
        this.cacheTtlSeconds = cacheTtlSeconds;
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
            WeatherInfoDTO weather = obtainCurrentWeather();

            return weather.getWeatherDescription()
                    + "，"
                    + weather.getTemperature()
                    + "°C";
        } catch (BusinessException e) {
            log.warn("天气不可用，本次推荐跳过天气：{}", e.getMessage());
            return null;
        }
    }


    @Override
    public WeatherInfoDTO obtainCurrentWeather() {
        String cacheKey = "weather:current:" + cityId;

        // 第一段：读取、解析和检查缓存
        try {
            String cachedJson = redisTemplate.opsForValue().get(cacheKey);

            if (cachedJson != null && !cachedJson.isBlank()) {
                WeatherInfoDTO cached =
                        objectMapper.readValue(cachedJson, WeatherInfoDTO.class);

                if (cached != null
                        && cached.getCityName() != null
                        && !cached.getCityName().isBlank()
                        && cached.getWeatherDescription() != null
                        && !cached.getWeatherDescription().isBlank()
                        && cached.getTemperature() != null
                        && !cached.getTemperature().isBlank()
                        && cached.getObsTime() != null
                        && cached.getObtainTime() != null) {
                    return cached;
                }
            }
        } catch (Exception e) {
            log.warn("天气缓存读取失败，继续查询天气，异常类型：{}",
                    e.getClass().getSimpleName());
        }

        // 第二段：请求真实天气，保留它原有的异常处理
        WeatherInfoDTO weather = fetchCurrentWeather();

        // 第三段：尝试写入缓存
        try {
            redisTemplate.opsForValue().set(
                    cacheKey,
                    objectMapper.writeValueAsString(weather),
                    Duration.ofSeconds(cacheTtlSeconds)
            );
        } catch (Exception e) {
            log.warn("天气缓存写入失败，仍返回查询结果，异常类型：{}",
                    e.getClass().getSimpleName());
        }

        return weather;
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


    /**
     * 解析和风 obsTime，如 "2024-06-01T10:30+08:00"
     */
    private LocalDateTime parseObsTime(String obsTime) {
        if (obsTime == null || obsTime.isBlank()) {
            return null;
        }
        try {
            return java.time.OffsetDateTime.parse(obsTime).toLocalDateTime();
        } catch (Exception e) {
            log.warn("观测时间解析失败: {}", obsTime);
            return null;
        }
    }

    private WeatherInfoDTO fetchCurrentWeather(){
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
                throw new BusinessException(ErrorCode.WEATHER_SERVICE_UNAVAILABLE);
            }

            JsonNode now = root.path("now");
            if(! now.isObject()){
                throw new BusinessException(
                        ErrorCode.WEATHER_SERVICE_UNAVAILABLE,
                        "天气服务接口返回信息不完整");
            }
            String description = now.path("text").asText(null);
            String temperature = now.path("temp").asText(null);
            LocalDateTime obsTime =
                    parseObsTime(now.path("obsTime").asText(null));

            if (description == null || description.isBlank()
                    || temperature == null || temperature.isBlank()
                    || obsTime == null) {
                throw new BusinessException(
                        ErrorCode.WEATHER_SERVICE_UNAVAILABLE,
                        "天气服务返回的数据不完整"
                );
            }

            return new WeatherInfoDTO(
                    weatherCityName,
                    description,
                    temperature,
                    obsTime,
                    LocalDateTime.now()
            );
        }
        catch (BusinessException e) {
            // 上面已经判断出的业务失败，原样向外抛
            throw e;
        } catch (Exception e) {
            // 网络、解压、JSON 解析等异常，统一转换
            log.warn("天气查询失败，异常类型：{}",
                    e.getClass().getSimpleName());

            throw new BusinessException(
                    ErrorCode.WEATHER_SERVICE_UNAVAILABLE,
                    "暂时无法获取天气，请稍后重试"
            );
        }
    }
}
