package com.luwei.ordering.service;

import com.luwei.ordering.dto.response.WeatherInfoDTO;

public interface WeatherService {
    String getCurrentWeather();
    WeatherInfoDTO obtainCurrentWeather();
}
