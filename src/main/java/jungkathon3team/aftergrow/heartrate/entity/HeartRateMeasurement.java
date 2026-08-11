package jungkathon3team.aftergrow.heartrate.entity;

import jakarta.persistence.*;
import jungkathon3team.aftergrow.running.entity.RunningSession;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * heart_rate_measurements 테이블 매핑.
 * <p>워치 실패 시 rPPG 재측정으로 한 러닝 세션에 여러 건이 남을 수 있어 RunningSession : HeartRateMeasurement = 1:N.
 * <p>R2(홈 대시보드)는 최근 1건과 이번 주 평균 bpm을, R4/R6는 측정 기록 생성과 목록 조회에 사용한다.
 * <p>행은 R4.2(워치 업로드) 또는 R4.6(rPPG 결과 제출)에서만 만들어진다.
 * rPPG 측정 중 상태는 Redis({@code RppgSessionStore})가 들고 있어 미완성 행이 남지 않는다.
 */
@Entity
@Table(name = "heart_rate_measurements")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class HeartRateMeasurement {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "heart_rate_measurement_id")
    private UUID heartRateMeasurementId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "running_session_id", nullable = false)
    private RunningSession runningSession;

    @Enumerated(EnumType.STRING)
    @Column(name = "heart_rate_source", length = 30)
    private HeartRateSource heartRateSource;

    @Column(name = "avg_bpm")
    private Integer avgBpm;

    @Column(name = "max_bpm")
    private Integer maxBpm;

    @Column(name = "hrv_ms")
    private Integer hrvMs;

    @Column(name = "measured_at", nullable = false)
    private LocalDateTime measuredAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "sync_status", length = 20)
    private SyncStatus syncStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "signal_quality", length = 20)
    private SignalQuality signalQuality;

    /**
     * R4.2 워치(애플 헬스) 측정. 앱이 HealthKit 읽기에 성공했을 때만 업로드하므로 항상 SUCCESS다.
     * 신호 품질은 rPPG에만 있는 개념이라 null로 둔다.
     */
    public static HeartRateMeasurement watch(RunningSession runningSession,
                                             Integer avgBpm,
                                             Integer maxBpm,
                                             Integer hrvMs,
                                             LocalDateTime measuredAt) {
        return HeartRateMeasurement.builder()
                .runningSession(runningSession)
                .heartRateSource(HeartRateSource.WATCH)
                .avgBpm(avgBpm)
                .maxBpm(maxBpm)
                .hrvMs(hrvMs)
                .measuredAt(measuredAt)
                .syncStatus(SyncStatus.SUCCESS)
                .build();
    }

    /**
     * R4.6 rPPG 측정 결과.
     * <p>신호 품질이 POOR이면 FAILED로 저장하고 bpm/hrv를 버린다.
     * 값을 그대로 두면 {@code HeartRateMeasurementRepository.avgBpmBetween}이
     * sync_status를 거르지 않으므로 R2 홈 대시보드의 주간 평균 bpm이 오염된다.
     */
    public static HeartRateMeasurement rppg(RunningSession runningSession,
                                            Integer avgBpm,
                                            Integer maxBpm,
                                            Integer hrvMs,
                                            LocalDateTime measuredAt,
                                            SignalQuality signalQuality) {
        boolean usable = signalQuality != SignalQuality.POOR;
        return HeartRateMeasurement.builder()
                .runningSession(runningSession)
                .heartRateSource(HeartRateSource.RPPG)
                .avgBpm(usable ? avgBpm : null)
                .maxBpm(usable ? maxBpm : null)
                .hrvMs(usable ? hrvMs : null)
                .measuredAt(measuredAt)
                .syncStatus(usable ? SyncStatus.SUCCESS : SyncStatus.FAILED)
                .signalQuality(signalQuality)
                .build();
    }
}
