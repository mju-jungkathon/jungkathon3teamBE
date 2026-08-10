package jungkathon3team.aftergrow.heartrate.entity;

import jungkathon3team.aftergrow.running.entity.RunningSession;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spring 컨텍스트 없이 도는 순수 단위 테스트.
 * POOR 측정값을 null로 지우는 규칙이 여기서 깨지면 R2 홈 대시보드의 주간 평균 bpm까지 오염된다.
 */
class HeartRateMeasurementTest {

    private static final LocalDateTime MEASURED_AT = LocalDateTime.of(2026, 8, 4, 7, 42);
    private final RunningSession session = RunningSession.start(
            null, LocalDateTime.of(2026, 8, 4, 7, 0), 37.5, 127.0, 5);

    @Test
    void 워치_측정은_항상_SUCCESS로_저장된다() {
        HeartRateMeasurement m = HeartRateMeasurement.watch(session, 152, 168, 42, MEASURED_AT);

        assertThat(m.getHeartRateSource()).isEqualTo(HeartRateSource.WATCH);
        assertThat(m.getSyncStatus()).isEqualTo(SyncStatus.SUCCESS);
        assertThat(m.getAvgBpm()).isEqualTo(152);
        assertThat(m.getMaxBpm()).isEqualTo(168);
        assertThat(m.getHrvMs()).isEqualTo(42);
        assertThat(m.getMeasuredAt()).isEqualTo(MEASURED_AT);
    }

    @Test
    void 워치_측정에는_신호_품질이_없다() {
        HeartRateMeasurement m = HeartRateMeasurement.watch(session, 152, 168, 42, MEASURED_AT);

        assertThat(m.getSignalQuality()).isNull();
    }

    @Test
    void 신호_품질이_GOOD인_rPPG는_값을_그대로_저장한다() {
        HeartRateMeasurement m = HeartRateMeasurement.rppg(
                session, 146, 158, 38, MEASURED_AT, SignalQuality.GOOD);

        assertThat(m.getHeartRateSource()).isEqualTo(HeartRateSource.RPPG);
        assertThat(m.getSyncStatus()).isEqualTo(SyncStatus.SUCCESS);
        assertThat(m.getSignalQuality()).isEqualTo(SignalQuality.GOOD);
        assertThat(m.getAvgBpm()).isEqualTo(146);
        assertThat(m.getMaxBpm()).isEqualTo(158);
        assertThat(m.getHrvMs()).isEqualTo(38);
    }

    /** 신뢰할 수 없는 값이 홈 대시보드 평균에 섞이지 않도록 버린다. */
    @Test
    void 신호_품질이_POOR인_rPPG는_FAILED로_저장하고_측정값을_버린다() {
        HeartRateMeasurement m = HeartRateMeasurement.rppg(
                session, 146, 158, 38, MEASURED_AT, SignalQuality.POOR);

        assertThat(m.getSyncStatus()).isEqualTo(SyncStatus.FAILED);
        assertThat(m.getSignalQuality()).isEqualTo(SignalQuality.POOR);
        assertThat(m.getAvgBpm()).isNull();
        assertThat(m.getMaxBpm()).isNull();
        assertThat(m.getHrvMs()).isNull();
    }

    /** 측정 시각은 실패해도 남아야 한다. 화면 8에 "언제 실패했는지"가 표시된다. */
    @Test
    void POOR이어도_측정_시각은_남는다() {
        HeartRateMeasurement m = HeartRateMeasurement.rppg(
                session, 146, 158, 38, MEASURED_AT, SignalQuality.POOR);

        assertThat(m.getMeasuredAt()).isEqualTo(MEASURED_AT);
        assertThat(m.getRunningSession()).isSameAs(session);
    }
}
