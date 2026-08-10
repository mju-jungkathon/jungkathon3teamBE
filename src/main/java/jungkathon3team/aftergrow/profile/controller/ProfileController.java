package jungkathon3team.aftergrow.profile.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jungkathon3team.aftergrow.common.response.ApiResponse;
import jungkathon3team.aftergrow.profile.dto.GoalUpdateDto;
import jungkathon3team.aftergrow.profile.dto.IntegrationResponse;
import jungkathon3team.aftergrow.profile.dto.NotificationUpdateDto;
import jungkathon3team.aftergrow.profile.dto.ProfileResponse;
import jungkathon3team.aftergrow.profile.service.ProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Tag(name = "프로필 & 설정", description = "프로필 조회 / 목표·알림 수정 / 연동 상태 조회")
@RestController
@RequestMapping("/users/me")
@RequiredArgsConstructor
public class ProfileController {

    private final ProfileService profileService;

    @Operation(summary = "프로필 조회", description = "닉네임·목표·연동상태·알림설정을 한 번에 반환합니다.")
    @GetMapping("/profile")
    public ApiResponse<ProfileResponse> profile(@AuthenticationPrincipal UUID userId) {
        return ApiResponse.ok(profileService.getProfile(userId));
    }

    @Operation(summary = "목표 수정", description = "부분 수정 — 보낸 필드만 변경됩니다.")
    @PatchMapping("/goal")
    public ApiResponse<GoalUpdateDto.Response> updateGoal(
            @AuthenticationPrincipal UUID userId,
            @Valid @RequestBody GoalUpdateDto.Request request
    ) {
        return ApiResponse.ok(profileService.updateGoal(userId, request));
    }

    @Operation(summary = "연동/권한 상태 조회")
    @GetMapping("/integrations")
    public ApiResponse<IntegrationResponse> integrations(@AuthenticationPrincipal UUID userId) {
        return ApiResponse.ok(profileService.getIntegrations(userId));
    }

    @Operation(summary = "알림 설정 변경", description = "부분 수정 — 보낸 필드만 변경됩니다.")
    @PatchMapping("/notifications")
    public ApiResponse<NotificationUpdateDto.Response> updateNotifications(
            @AuthenticationPrincipal UUID userId,
            @Valid @RequestBody NotificationUpdateDto.Request request
    ) {
        return ApiResponse.ok(profileService.updateNotifications(userId, request));
    }
}
