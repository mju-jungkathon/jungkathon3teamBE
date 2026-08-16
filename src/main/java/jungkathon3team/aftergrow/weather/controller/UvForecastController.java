package jungkathon3team.aftergrow.weather.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jungkathon3team.aftergrow.common.response.ApiResponse;
import jungkathon3team.aftergrow.weather.dto.UvForecastResponse;
import jungkathon3team.aftergrow.weather.service.UvForecastService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "날씨", description = "시간대별 UV 예보")
@RestController
@RequestMapping("/weather")
@RequiredArgsConstructor
public class UvForecastController {

    private final UvForecastService uvForecastService;

    /**
     * 홈 화면의 UV 그래프용. 러닝 세션과 무관하게 "이 위치의 오늘 하루 UV"를 돌려준다.
     * <p>"지금 UV"와 "UV가 낮은 추천 시간대"도 이 배열 하나에서 계산할 수 있어 별도 API를 두지 않았다.
     */
    @Operation(summary = "시간대별 UV 예보",
            description = "오늘 00시부터 2시간 간격 12개를 반환합니다. 같은 시도의 사용자끼리 캐시를 공유합니다.")
    @GetMapping("/uv-forecast")
    public ApiResponse<UvForecastResponse> uvForecast(
            @RequestParam @Min(-90) @Max(90) double lat,
            @RequestParam @Min(-180) @Max(180) double lng
    ) {
        return ApiResponse.ok(uvForecastService.getTodayForecast(lat, lng));
    }
}
