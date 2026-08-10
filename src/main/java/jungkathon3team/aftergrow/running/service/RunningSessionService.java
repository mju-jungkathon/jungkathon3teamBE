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

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RunningSessionService {

    private static final int GOOD_TIME_UV_THRESHOLD = 7;

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
            session.end(request.endedAt(), request.durationSec(), request.distanceKm(), request.intensity());
        }

        return new RunningEndDto.Response(
                session.getRunningSessionId(),
                session.getStatus(),
                RunningEndDto.Response.NEXT_STEP_HEART_RATE_CHECK,
                // 화면 5의 기본 선택지. 러닝 종료 → 화면 5 진입이 유일한 경로라 여기서 함께 내려준다.
                heartRateMeasurementService.defaultSourceFor(userId)
        );
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