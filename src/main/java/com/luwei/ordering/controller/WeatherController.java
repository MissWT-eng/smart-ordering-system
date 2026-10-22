package com.luwei.ordering.controller;

import com.luwei.ordering.dto.response.ApiResponse;
import com.luwei.ordering.dto.response.WeatherInfoDTO;
import com.luwei.ordering.service.WeatherService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/weather")
@RequiredArgsConstructor
public class WeatherController {
    private final WeatherService weatherService;

    @GetMapping
    public ApiResponse<WeatherInfoDTO> getWeatherInfo(){
        return ApiResponse.success(weatherService.obtainCurrentWeather());
    }

}
