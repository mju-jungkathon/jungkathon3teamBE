package jungkathon3team.aftergrow.profile.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jungkathon3team.aftergrow.auth.dto.WithdrawRequest;
import jungkathon3team.aftergrow.auth.service.AuthService;
import jungkathon3team.aftergrow.common.response.ApiResponse;
import jungkathon3team.aftergrow.profile.dto.GoalUpdateDto;
import jungkathon3team.aftergrow.profile.dto.IntegrationResponse;
import jungkathon3team.aftergrow.profile.dto.IntegrationUpdateDto;
import jungkathon3team.aftergrow.profile.dto.NotificationUpdateDto;
import jungkathon3team.aftergrow.profile.dto.ProfileResponse;
import jungkathon3team.aftergrow.profile.service.ProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Tag(name = "프로필 & 설정", description = "프로필 조회 / 목표·알림 수정 / 연동 상태 조회")
@RestController
@RequestMapping("/users/me")
@RequiredArgsConstructor
public class ProfileController {

    private final ProfileService profileService;

    /** 탈퇴는 계정 조작이라 인증 도메인이 담당한다(비밀번호 확인·refresh 토큰 정리). */
    private final AuthService authService;

    @Operation(summary = "프로필 조회", description = "닉네임·목표·연동상태·알림설정을 한 번에 반환합니다.")
    @GetMapping("/profile")
    public ApiResponse<ProfileResponse> profile(@AuthenticationPrincipal UUID userId) {
        return ApiResponse.ok(profileService.getProfile(userId));
    }

    @Operation(summary = "목표 조회", description = "설정한 적 없으면 필드가 전부 null입니다.")
    @GetMapping("/goal")
    public ApiResponse<GoalUpdateDto.Response> getGoal(@AuthenticationPrincipal UUID userId) {
        return ApiResponse.ok(profileService.getGoal(userId));
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

    @Operation(summary = "연동/권한 상태 갱신",
            description = "부분 수정 — 보낸 필드만 변경됩니다. 브라우저에서 실제로 권한을 요청한 결과를 "
                    + "서버에 동기화하는 용도이며, 저장된 값은 표시용 캐시일 뿐 권한 검증 수단이 아닙니다.")
    @PatchMapping("/integrations")
    public ApiResponse<IntegrationResponse> updateIntegrations(
            @AuthenticationPrincipal UUID userId,
            @Valid @RequestBody IntegrationUpdateDto.Request request
    ) {
        return ApiResponse.ok(profileService.updateIntegrations(userId, request));
    }

    @Operation(summary = "회원 탈퇴",
            description = "계정과 모든 러닝·측정·회복 기록이 삭제됩니다. 되돌릴 수 없습니다. "
                    + "탈취된 토큰만으로 실행되지 않도록 현재 비밀번호를 함께 보내야 합니다.")
    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void withdraw(
            @AuthenticationPrincipal UUID userId,
            @Valid @RequestBody WithdrawRequest request
    ) {
        authService.withdraw(userId, request);
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
