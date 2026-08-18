package jungkathon3team.aftergrow.running.service;

import jungkathon3team.aftergrow.auth.entity.User;
import jungkathon3team.aftergrow.auth.repository.UserRepository;
import jungkathon3team.aftergrow.common.exception.BusinessException;
import jungkathon3team.aftergrow.common.exception.ErrorCode;
import jungkathon3team.aftergrow.heartrate.service.HeartRateMeasurementService;
import jungkathon3team.aftergrow.running.dto.*;
import jungkathon3team.aftergrow.running.entity.Intensity;
import jungkathon3team.aftergrow.running.entity.RunningSession;
import jungkathon3team.aftergrow.running.entity.RunningStatus;
import jungkathon3team.aftergrow.running.entity.StretchingSession;
import jungkathon3team.aftergrow.running.external.LocationLabelResolver;
import jungkathon3team.aftergrow.running.external.UvIndexClient;
import jungkathon3team.aftergrow.running.repository.RunningSessionRepository;
import jungkathon3team.aftergrow.running.repository.StretchingSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jungkathon3team.aftergrow.common.request.RangeParam;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RunningSessionService {

    private static final int GOOD_TIME_UV_THRESHOLD = 7;

    /**
     * "이 러닝 직전의 스트레칭"으로 인정하는 시간 범위.
     * <p>화면 흐름상 스트레칭 직후 바로 러닝을 시작하므로 넉넉히 잡아도 오탐이 적다.
     * ponytail: 시각 근접 추정이다. 정확히 묶어야 하면 stretching_sessions에 running_session_id를
     * 추가하고 러닝 시작 시 연결하는 편이 맞다(스키마 변경 필요).
     */
    private static final Duration PRE_RUN_STRETCHING_WINDOW = Duration.ofMinutes(60);

    private final RunningSessionRepository runningSessionRepository;
    private final StretchingSessionRepository stretchingSessionRepository;
    private final UserRepository userRepository;
    private final UvIndexClient uvIndexClient;
    private final LocationLabelResolver locationLabelResolver;
    private final HeartRateMeasurementService heartRateMeasurementService;

    /** 3.1 GET /running-sessions/prepare */
    public RunningPrepareResponse prepare(double lat, double lng) {
        UvIndexClient.UvIndexResult uv = uvIndexClient.fetchCurrentUvIndex(lat, lng);
        String locationLabel = locationLabelResolver.resolve(lat, lng);
        boolean goodTimeToRun = uv.uvIndex() < GOOD_TIME_UV_THRESHOLD;

        return new RunningPrepareResponse(
                locationLabel,
                uv.uvIndex(),
                uv.uvLevel(),
                goodTimeToRun,
                RunningPrepareResponse.StretchingInfo.preRunDefault()
        );
    }

    /** 3.2 POST /stretching-sessions */
    @Transactional
    public StretchingSessionDto.Response startStretching(UUID userId, StretchingSessionDto.Request request) {
        User user = getUser(userId);
        StretchingSession session = StretchingSession.start(user, request.type());
        stretchingSessionRepository.save(session);
        return new StretchingSessionDto.Response(session.getStretchingSessionId(), session.getStartedAt());
    }

    /** 3.3 POST /running-sessions */
    @Transactional
    public RunningStartDto.Response startRunning(UUID userId, RunningStartDto.Request request) {
        if (runningSessionRepository.existsByUser_UserIdAndStatus(userId, RunningStatus.IN_PROGRESS)) {
            throw new BusinessException(ErrorCode.RUNNING_SESSION_ALREADY_IN_PROGRESS); // E4090
        }

        User user = getUser(userId);
        RunningSession session = RunningSession.start(
                user,
                request.startedAt(),
                request.location().lat(),
                request.location().lng(),
                request.uvIndexAtStart()
        );
        runningSessionRepository.save(session);

        return new RunningStartDto.Response(session.getRunningSessionId(), session.getStatus());
    }

    /**
     * 3.4 GET /running-sessions/{id}/live
     * distanceKm/intensity가 함께 오면(클라이언트가 주기적으로 로컬 트래킹값을 실어 보내는 방식) 스냅샷을 갱신 후 반환한다.
     */
    @Transactional
    public RunningLiveResponse getLive(UUID userId, UUID sessionId, Double distanceKm, Intensity intensity) {
        RunningSession session = getOwnedSession(userId, sessionId);
        session.updateLiveSnapshot(distanceKm, intensity);

        long elapsedSec = Duration.between(session.getStartedAt(), LocalDateTime.now()).getSeconds();
        UvIndexClient.UvIndexResult uv = uvIndexClient.fetchCurrentUvIndex(session.getLat(), session.getLng());

        return new RunningLiveResponse(
                session.getRunningSessionId(),
                Math.max(elapsedSec, 0),
                session.getIntensity(),
                session.getDistanceKm(),
                RunningLiveResponse.HEART_RATE_STATUS_PENDING,
                RunningLiveResponse.STRESS_STATUS_PENDING,
                uv.uvIndex(),
                uv.uvLevel()
        );
    }

    /**
     * 3.5 POST /running-sessions/{id}/end
     * 이미 종료된 세션에 다시 호출되면(중복 클릭·네트워크 재시도 등) 명세서에 정의된 전용 에러 코드가 없으므로
     * 에러를 던지는 대신 현재 상태를 그대로 반환한다(idempotent).
     */
    @Transactional
    public RunningEndDto.Response endRunning(UUID userId, UUID sessionId, RunningEndDto.Request request) {
        RunningSession session = getOwnedSession(userId, sessionId);

        if (session.isInProgress()) {
            session.end(request.endedAt(), request.durationSec(), request.distanceKm(), request.intensity(),
                    request.routePath());
        }

        return new RunningEndDto.Response(
                session.getRunningSessionId(),
                session.getStatus(),
                RunningEndDto.Response.NEXT_STEP_HEART_RATE_CHECK,
                // 화면 5의 기본 선택지. 러닝 종료 → 화면 5 진입이 유일한 경로라 여기서 함께 내려준다.
                heartRateMeasurementService.defaultSourceFor(userId)
        );
    }

    /**
     * GET /running-sessions?range=30d — 러닝 기록 목록.
     * <p>세션마다 심박수를 따로 조회하면 N+1이 되므로, range 내 측정을 한 번에 읽어
     * 세션별 최신 1건으로 접어 쓴다(측정은 최신순으로 오므로 먼저 담긴 것이 최신이다).
     */
    public RunningRecordsResponse getRecords(UUID userId, String range) {
        LocalDateTime since = RangeParam.since(range, LocalDateTime.now());

        List<RunningSession> sessions = runningSessionRepository
                .findByUser_UserIdAndStartedAtGreaterThanEqualOrderByStartedAtDesc(userId, since);

        Map<UUID, Integer> avgBpmBySession = heartRateMeasurementService.avgBpmBySessionSince(userId, since);

        List<RunningRecordsResponse.Item> items = sessions.stream()
                .map(s -> RunningRecordsResponse.Item.of(s, avgBpmBySession.get(s.getRunningSessionId())))
                .toList();

        return new RunningRecordsResponse(items, summaryOf(sessions));
    }

    private RunningRecordsResponse.Summary summaryOf(List<RunningSession> sessions) {
        double totalDistanceKm = sessions.stream()
                .filter(s -> s.getDistanceKm() != null)
                .mapToDouble(RunningSession::getDistanceKm)
                .sum();
        int totalDurationSec = sessions.stream()
                .filter(s -> s.getDurationSec() != null)
                .mapToInt(RunningSession::getDurationSec)
                .sum();
        // 부동소수 누적 오차가 응답에 그대로 드러나지 않도록 소수 둘째 자리에서 끊는다.
        return new RunningRecordsResponse.Summary(
                sessions.size(),
                Math.round(totalDistanceKm * 100) / 100.0,
                totalDurationSec);
    }

    /**
     * 3.8 GET /running-sessions/weekly-count — 월~일 완료(ENDED+COMPLETED) 러닝 횟수.
     * <p>{@code date}가 속한 주(월요일 시작)를 집계한다. 생략하면 오늘 기준 이번 주.
     */
    public WeeklyRunCountResponse getWeeklyRunCount(UUID userId, LocalDate date) {
        LocalDate base = date == null ? LocalDate.now() : date;
        LocalDate weekStart = base.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate weekEnd = weekStart.plusDays(6);

        long count = runningSessionRepository.countByUser_UserIdAndStatusInAndStartedAtBetween(
                userId, RunningStatus.COMPLETED_STATUSES, weekStart.atStartOfDay(), weekEnd.plusDays(1).atStartOfDay());

        return new WeeklyRunCountResponse(weekStart, weekEnd, count);
    }

    /** GET /running-sessions/{id} — 상세. routePath(지도용 좌표 배열)는 여기에만 실린다. */
    public RunningSessionDetailResponse getDetail(UUID userId, UUID sessionId) {
        RunningSession session = getOwnedSession(userId, sessionId);
        return RunningSessionDetailResponse.of(
                session,
                heartRateMeasurementService.latestSuccessfulMeasurement(sessionId).orElse(null),
                preRunStretchingOf(userId, session.getStartedAt()));
    }

    /**
     * 이 러닝 직전에 한 스트레칭을 찾는다.
     * <p>스트레칭 세션에는 러닝 세션 FK가 없어(화면 흐름상 러닝보다 먼저 만들어진다) 시각 근접도로 고른다.
     */
    private StretchingSession preRunStretchingOf(UUID userId, LocalDateTime runStartedAt) {
        return stretchingSessionRepository
                .findTopByUser_UserIdAndStartedAtBetweenOrderByStartedAtDesc(
                        userId, runStartedAt.minus(PRE_RUN_STRETCHING_WINDOW), runStartedAt)
                .orElse(null);
    }

    private RunningSession getOwnedSession(UUID userId, UUID sessionId) {
        RunningSession session = runningSessionRepository.findById(sessionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND)); // E4040
        if (!session.isOwnedBy(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN); // E4030
        }
        return session;
    }

    private User getUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND)); // E4040
    }
}