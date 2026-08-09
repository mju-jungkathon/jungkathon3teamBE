package jungkathon3team.aftergrow.running.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jungkathon3team.aftergrow.common.response.ApiResponse;
import jungkathon3team.aftergrow.running.dto.StretchingSessionDto;
import jungkathon3team.aftergrow.running.service.RunningSessionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Tag(name = "러닝 준비", description = "스트레칭")
@RestController
@RequestMapping("/stretching-sessions")
@RequiredArgsConstructor
public class StretchingSessionController {

    private final RunningSessionService runningSessionService;

    /** 3.2 화면 3의 "스트레칭 시작하기" (선택) */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<StretchingSessionDto.Response> start(
            @AuthenticationPrincipal UUID userId,
            @Valid @RequestBody StretchingSessionDto.Request request
    ) {
        return ApiResponse.ok(runningSessionService.startStretching(userId, request));
    }
}
