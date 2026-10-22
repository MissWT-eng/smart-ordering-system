package com.luwei.ordering.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WeatherInfoDTO {
    /**
     *   城市名
     */
    private String cityName;

    /**
     *   天气描述
     */
    private String weatherDescription;

    /**
     *   摄氏温度
     */
    private String temperature;

    /**
     *   观测时间
     */
    private LocalDateTime obsTime;

    /**
     *   获取时间
     */
    private LocalDateTime obtainTime;
}
