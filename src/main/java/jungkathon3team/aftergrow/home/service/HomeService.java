package jungkathon3team.aftergrow.home.service;

import jungkathon3team.aftergrow.auth.entity.User;
import jungkathon3team.aftergrow.auth.repository.UserRepository;
import jungkathon3team.aftergrow.common.exception.BusinessException;
import jungkathon3team.aftergrow.common.exception.ErrorCode;
import jungkathon3team.aftergrow.heartrate.entity.HeartRateMeasurement;
import jungkathon3team.aftergrow.heartrate.entity.SyncStatus;
import jungkathon3team.aftergrow.heartrate.repository.HeartRateMeasurementRepository;
import jungkathon3team.aftergrow.home.dto.HomeResponse;
import jungkathon3team.aftergrow.profile.service.ProfileService;
import jungkathon3team.aftergrow.running.entity.RunningStatus;
import jungkathon3team.aftergrow.running.external.UvIndexClient;
import jungkathon3team.aftergrow.running.repository.RunningSessionRepository;
import jungkathon3team.aftergrow.running.service.RunningSessionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.UUID;

/**
 * R2 §2.1 GET /home 홈 대시보드 집계.
 * <p>주 기준은 월~일(이번 주), 주간 카운트/요약은 완료 세션(ENDED+COMPLETED)만 집계한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class HomeService {

    private static final String DEFAULT_NICKNAME = "러너";

    private final UserRepository userRepository;
    private final RunningSessionRepository runningSessionRepository;
    private final HeartRateMeasurementRepository heartRateMeasurementRepository;
    private final RunningSessionService runningSessionService;
    private final ProfileService profileService;

    public HomeResponse getHome(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND)); // E4040

        LocalDateTime now = LocalDateTime.now();
        LocalDate today = now.toLocalDate();
        LocalDateTime weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).atStartOfDay();
        LocalDateTime todayStart = today.atStartOfDay();

        // 주간 횟수/목표는 각각 러닝·프로필 도메인이 소유한 로직(weekly-count 엔드포인트, 목표 조회)을 그대로 재사용한다.
        long weeklyRunCount = runningSessionService.getWeeklyRunCount(userId, today).count();

        Integer weeklyRunGoal = profileService.getGoal(userId).weeklyRunGoal();
        int weeklyGoalCount = weeklyRunGoal == null ? 0 : weeklyRunGoal;
        int remainingToGoal = Math.max(0, weeklyGoalCount - (int) weeklyRunCount);

        // 실패한 측정(avgBpm=null)이 최신이어도 건너뛰고, 그 이전의 성공한 측정으로 폴백한다.
        // 필터 없이 조회하면 "측정 없음"과 "측정 실패"를 클라이언트가 구분할 수 없다.
        HomeResponse.LatestMeasurement latestMeasurement = heartRateMeasurementRepository
                .findTopByRunningSession_User_UserIdAndSyncStatusOrderByMeasuredAtDesc(userId, SyncStatus.SUCCESS)
                .map(HomeService::toLatestMeasurement)
                .orElse(null);

        HomeResponse.TodayRunningStatus todayRunningStatus =
                resolveTodayStatus(userId, todayStart, now);

        HomeResponse.WeeklySummary weeklySummary =
                buildWeeklySummary(userId, weekStart, now);

        String greeting = "안녕하세요, "
                + (user.getNickname() == null || user.getNickname().isBlank() ? DEFAULT_NICKNAME : user.getNickname())
                + "님";

        return new HomeResponse(
                greeting,
                weeklyRunCount,
                weeklyGoalCount,
                remainingToGoal,
                latestMeasurement,
                todayRunningStatus,
                weeklySummary
        );
    }

    private HomeResponse.TodayRunningStatus resolveTodayStatus(UUID userId, LocalDateTime todayStart, LocalDateTime now) {
        boolean completedToday = runningSessionRepository
                .existsByUser_UserIdAndStatusInAndStartedAtBetween(userId, RunningStatus.COMPLETED_STATUSES, todayStart, now);
        if (completedToday) {
            return HomeResponse.TodayRunningStatus.COMPLETED;
        }
        boolean inProgressToday = runningSessionRepository
                .existsByUser_UserIdAndStatusAndStartedAtBetween(userId, RunningStatus.IN_PROGRESS, todayStart, now);
        if (inProgressToday) {
            return HomeResponse.TodayRunningStatus.IN_PROGRESS;
        }
        return HomeResponse.TodayRunningStatus.NOT_STARTED;
    }

    private HomeResponse.WeeklySummary buildWeeklySummary(UUID userId, LocalDateTime weekStart, LocalDateTime now) {
        double totalDistanceKm = runningSessionRepository
                .sumDistanceKmBetween(userId, RunningStatus.COMPLETED_STATUSES, weekStart, now);

        Double avgBpmRaw = heartRateMeasurementRepository.avgBpmBetween(userId, weekStart, now);
        Integer avgBpm = avgBpmRaw == null ? null : (int) Math.round(avgBpmRaw);

        Double avgUvRaw = runningSessionRepository
                .avgUvIndexBetween(userId, RunningStatus.COMPLETED_STATUSES, weekStart, now);
        String cumulativeUvLevel = avgUvRaw == null
                ? null
                : UvIndexClient.UvIndexResult.of((int) Math.round(avgUvRaw)).uvLevel();

        return new HomeResponse.WeeklySummary(totalDistanceKm, avgBpm, cumulativeUvLevel);
    }

    private static HomeResponse.LatestMeasurement toLatestMeasurement(HeartRateMeasurement m) {
        return new HomeResponse.LatestMeasurement(
                m.getHeartRateSource() == null ? null : m.getHeartRateSource().name(),
                m.getAvgBpm(),
                m.getMeasuredAt()
        );
    }
}
