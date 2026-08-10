package jungkathon3team.aftergrow.home.service;

import jungkathon3team.aftergrow.auth.entity.User;
import jungkathon3team.aftergrow.auth.repository.UserRepository;
import jungkathon3team.aftergrow.common.exception.BusinessException;
import jungkathon3team.aftergrow.common.exception.ErrorCode;
import jungkathon3team.aftergrow.heartrate.entity.HeartRateMeasurement;
import jungkathon3team.aftergrow.heartrate.repository.HeartRateMeasurementRepository;
import jungkathon3team.aftergrow.home.dto.HomeResponse;
import jungkathon3team.aftergrow.profile.repository.UserGoalRepository;
import jungkathon3team.aftergrow.running.entity.RunningStatus;
import jungkathon3team.aftergrow.running.external.UvIndexClient;
import jungkathon3team.aftergrow.running.repository.RunningSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.UUID;

/**
 * R2 §2.1 GET /home 홈 대시보드 집계.
 * <p>주 기준은 월~일(이번 주), 주간 카운트/요약은 완료 세션(ENDED+COMPLETED)만 집계한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class HomeService {

    /** 이번 주 집계에 "완료"로 인정하는 상태. 러닝 종료(ENDED) 이후 리포트 확정(COMPLETED) 모두 포함. */
    private static final List<RunningStatus> COMPLETED_STATUSES =
            List.of(RunningStatus.ENDED, RunningStatus.COMPLETED);

    private static final String DEFAULT_NICKNAME = "러너";

    private final UserRepository userRepository;
    private final UserGoalRepository userGoalRepository;
    private final RunningSessionRepository runningSessionRepository;
    private final HeartRateMeasurementRepository heartRateMeasurementRepository;

    public HomeResponse getHome(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND)); // E4040

        LocalDateTime now = LocalDateTime.now();
        LocalDate today = now.toLocalDate();
        LocalDateTime weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).atStartOfDay();
        LocalDateTime todayStart = today.atStartOfDay();

        long weeklyRunCount = runningSessionRepository
                .countByUser_UserIdAndStatusInAndStartedAtBetween(userId, COMPLETED_STATUSES, weekStart, now);

        int weeklyGoalCount = userGoalRepository.findById(userId)
                .map(g -> g.getWeeklyRunGoal() == null ? 0 : g.getWeeklyRunGoal())
                .orElse(0);
        int remainingToGoal = Math.max(0, weeklyGoalCount - (int) weeklyRunCount);

        HomeResponse.LatestMeasurement latestMeasurement = heartRateMeasurementRepository
                .findTopByRunningSession_User_UserIdOrderByMeasuredAtDesc(userId)
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
                .existsByUser_UserIdAndStatusInAndStartedAtBetween(userId, COMPLETED_STATUSES, todayStart, now);
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
                .sumDistanceKmBetween(userId, COMPLETED_STATUSES, weekStart, now);

        Double avgBpmRaw = heartRateMeasurementRepository.avgBpmBetween(userId, weekStart, now);
        Integer avgBpm = avgBpmRaw == null ? null : (int) Math.round(avgBpmRaw);

        Double avgUvRaw = runningSessionRepository
                .avgUvIndexBetween(userId, COMPLETED_STATUSES, weekStart, now);
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
