package jungkathon3team.aftergrow.weather.dto;

import jungkathon3team.aftergrow.weather.external.UvForecastClient;

import java.util.List;

/**
 * GET /weather/uv-forecast 응답.
 * <p>{@code hourly}는 00시부터 2시간 간격 12개로 항상 같은 길이다 — 프론트가 길이를 검사하지 않고
 * 그대로 그래프에 넣을 수 있도록.
 */
public record UvForecastResponse(List<UvForecastClient.HourlyUv> hourly) {
}
