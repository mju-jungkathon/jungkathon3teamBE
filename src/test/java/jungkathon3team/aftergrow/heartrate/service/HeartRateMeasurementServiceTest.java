package jungkathon3team.aftergrow.heartrate.service;

import jungkathon3team.aftergrow.common.exception.BusinessException;
import jungkathon3team.aftergrow.common.exception.ErrorCode;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * R4/R6 서비스 로직 테스트.
 * <p>Task 5에서는 range 파싱만 덮는다. sourceRatio 집계와 기본 source 파생은 이후 태스크에서 추가된다.
 */
class HeartRateMeasurementServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 10, 12, 0);

    // --- range 파싱 ---

    @Test
    void range가_30d면_30일_전부터_조회한다() {
        assertThat(HeartRateMeasurementService.sinceOf("30d", NOW))
                .isEqualTo(LocalDateTime.of(2026, 7, 11, 12, 0));
    }

    @Test
    void range가_7d면_7일_전부터_조회한다() {
        assertThat(HeartRateMeasurementService.sinceOf("7d", NOW))
                .isEqualTo(LocalDateTime.of(2026, 8, 3, 12, 0));
    }

    @Test
    void range를_생략하면_기본_30일이다() {
        assertThat(HeartRateMeasurementService.sinceOf(null, NOW))
                .isEqualTo(HeartRateMeasurementService.sinceOf("30d", NOW));
    }

    @Test
    void range가_빈_문자열이면_기본_30일이다() {
        assertThat(HeartRateMeasurementService.sinceOf("  ", NOW))
                .isEqualTo(HeartRateMeasurementService.sinceOf("30d", NOW));
    }

    @Test
    void 형식이_어긋난_range는_E4001이다() {
        assertThatThrownBy(() -> HeartRateMeasurementService.sinceOf("abc", NOW))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_REQUEST);
    }

    @Test
    void 단위가_없는_range는_E4001이다() {
        assertThatThrownBy(() -> HeartRateMeasurementService.sinceOf("30", NOW))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void 주_단위처럼_지원하지_않는_단위는_E4001이다() {
        assertThatThrownBy(() -> HeartRateMeasurementService.sinceOf("4w", NOW))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void _0일_조회는_E4001이다() {
        assertThatThrownBy(() -> HeartRateMeasurementService.sinceOf("0d", NOW))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void 음수_range는_E4001이다() {
        assertThatThrownBy(() -> HeartRateMeasurementService.sinceOf("-5d", NOW))
                .isInstanceOf(BusinessException.class);
    }

    /** long 범위를 넘는 숫자에 NumberFormatException이 새어 나가면 500이 된다. */
    @Test
    void 지나치게_큰_숫자도_E4001이다() {
        assertThatThrownBy(() -> HeartRateMeasurementService.sinceOf("99999999999999999999d", NOW))
                .isInstanceOf(BusinessException.class);
    }
}
