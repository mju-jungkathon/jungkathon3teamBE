package jungkathon3team.aftergrow.profile.service;

import jungkathon3team.aftergrow.auth.entity.User;
import jungkathon3team.aftergrow.auth.repository.UserRepository;
import jungkathon3team.aftergrow.common.exception.BusinessException;
import jungkathon3team.aftergrow.common.exception.ErrorCode;
import jungkathon3team.aftergrow.profile.dto.GoalUpdateDto;
import jungkathon3team.aftergrow.profile.dto.IntegrationResponse;
import jungkathon3team.aftergrow.profile.dto.IntegrationUpdateDto;
import jungkathon3team.aftergrow.profile.dto.NotificationUpdateDto;
import jungkathon3team.aftergrow.profile.dto.ProfileResponse;
import jungkathon3team.aftergrow.profile.entity.IntegrationStatus;
import jungkathon3team.aftergrow.profile.entity.NotificationSetting;
import jungkathon3team.aftergrow.profile.entity.UserGoal;
import jungkathon3team.aftergrow.profile.repository.IntegrationStatusRepository;
import jungkathon3team.aftergrow.profile.repository.NotificationSettingRepository;
import jungkathon3team.aftergrow.profile.repository.UserGoalRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * R7 프로필 & 설정.
 * <p>설정 행이 없는 사용자: 조회는 기본값을 반환하고, 수정(PATCH)은 해당 테이블 행만 그때 생성한다(upsert).
 * PATCH는 부분 수정 — 요청에 담긴 필드만 바꾸고 생략된 필드는 기존값을 유지한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProfileService {

    private final UserRepository userRepository;
    private final UserGoalRepository userGoalRepository;
    private final NotificationSettingRepository notificationSettingRepository;
    private final IntegrationStatusRepository integrationStatusRepository;

    /** 7.1 GET /users/me/profile */
    public ProfileResponse getProfile(UUID userId) {
        User user = getUser(userId);
        return ProfileResponse.of(
                user.getNickname(),
                userGoalRepository.findById(userId).orElse(null),
                integrationStatusRepository.findById(userId).orElse(null),
                notificationSettingRepository.findById(userId).orElse(null)
        );
    }

    /** 7.2 GET /users/me/goal — 목표만 단독 조회. 설정 전이면 필드가 전부 null. */
    public GoalUpdateDto.Response getGoal(UUID userId) {
        requireUser(userId);
        return userGoalRepository.findById(userId)
                .map(GoalUpdateDto.Response::from)
                .orElseGet(() -> new GoalUpdateDto.Response(null, null, null));
    }

    /** 7.3 PATCH /users/me/goal */
    @Transactional
    public GoalUpdateDto.Response updateGoal(UUID userId, GoalUpdateDto.Request request) {
        requireUser(userId);
        UserGoal goal = userGoalRepository.findById(userId)
                .orElseGet(() -> UserGoal.create(userId));
        goal.updatePartial(request.goalType(), request.weeklyRunGoal());
        userGoalRepository.save(goal);
        return GoalUpdateDto.Response.from(goal);
    }

    /** 7.4 GET /users/me/integrations */
    public IntegrationResponse getIntegrations(UUID userId) {
        requireUser(userId);
        return integrationStatusRepository.findById(userId)
                .map(IntegrationResponse::from)
                .orElseGet(() -> IntegrationResponse.from(IntegrationStatus.defaults(userId)));
    }

    /**
     * PATCH /users/me/integrations — 브라우저 권한 상태 동기화.
     * <p>조회(7.4)만 있고 쓰기가 없어 cameraPermission/locationPermission을 갱신할 방법이 없던 공백을 메운다.
     */
    @Transactional
    public IntegrationResponse updateIntegrations(UUID userId, IntegrationUpdateDto.Request request) {
        requireUser(userId);
        IntegrationStatus status = integrationStatusRepository.findById(userId)
                .orElseGet(() -> IntegrationStatus.of(userId));
        status.updatePermissions(request.cameraPermission(), request.locationPermission());
        integrationStatusRepository.save(status);
        return IntegrationResponse.from(status);
    }

    /** 7.5 PATCH /users/me/notifications */
    @Transactional
    public NotificationUpdateDto.Response updateNotifications(UUID userId, NotificationUpdateDto.Request request) {
        requireUser(userId);
        NotificationSetting setting = notificationSettingRepository.findById(userId)
                .orElseGet(() -> NotificationSetting.create(userId));
        setting.updatePartial(request.runningReminderTime(), request.weeklyReportDay(), request.weeklyReportTime());
        notificationSettingRepository.save(setting);
        return NotificationUpdateDto.Response.from(setting);
    }

    private User getUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND)); // E4040
    }

    private void requireUser(UUID userId) {
        if (!userRepository.existsById(userId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND); // E4040
        }
    }
}
