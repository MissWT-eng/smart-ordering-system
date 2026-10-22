package com.luwei.ordering.dto.response;

public record WeatherToolResult(
        Status status,
        WeatherInfoDTO weather,
        String message,
        String nextAction
) {
    public enum Status{
        SUCCESS,
        SERVICE_UNAVAILABLE
}
}
