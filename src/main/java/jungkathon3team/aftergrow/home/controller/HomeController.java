package jungkathon3team.aftergrow.home.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jungkathon3team.aftergrow.common.response.ApiResponse;
import jungkathon3team.aftergrow.home.dto.HomeResponse;
import jungkathon3team.aftergrow.home.service.HomeService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Tag(name = "홈", description = "홈 대시보드 요약")
@RestController
@RequestMapping("/home")
@RequiredArgsConstructor
public class HomeController {

    private final HomeService homeService;

    @Operation(summary = "홈 요약 조회",
            description = "화면 2 진입 시 인사말·주간 목표·최근 측정·오늘 러닝 상태·주간 요약을 반환합니다.")
    @GetMapping
    public ApiResponse<HomeResponse> home(@AuthenticationPrincipal UUID userId) {
        return ApiResponse.ok(homeService.getHome(userId));
    }
}
