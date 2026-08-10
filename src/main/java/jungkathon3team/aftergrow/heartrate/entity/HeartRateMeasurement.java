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
 * R2(홈 대시보드)에서는 최근 1건과 이번 주 평균 bpm 조회에만 사용한다. R4/R6에서 확장 예정.
 * <p>{@code sync_status}/{@code signal_quality}는 명세상 enum 후보지만 아직 사용처가 없어 String으로 둔다
 * (enum 확정은 실제로 쓰는 도메인에서).
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

    @Column(name = "sync_status", length = 20)
    private String syncStatus;

    @Column(name = "signal_quality", length = 20)
    private String signalQuality;
}
