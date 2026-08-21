package jungkathon3team.aftergrow.recovery.service;

import jungkathon3team.aftergrow.recovery.dto.LowUvTimeRange;
import jungkathon3team.aftergrow.recovery.service.RecoveryGuideService.UvSlot;
import jungkathon3team.aftergrow.running.entity.Intensity;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link RecoveryGuideService#groupLowUvRanges}만 떼어 검증한다 — Spring 컨텍스트·벽시계 시각과 무관하게
 * "연속된 낮음(≤2) 시간대를 하나의 구간으로 묶는다"는 그룹핑 규칙 자체가 맞는지 확인하기 위함.
 */
class RecoveryGuideServiceTest {

    private static final LocalDate DATE = LocalDate.of(2026, 8, 21);

    @Test
    void 연속된_낮음_구간_두_개로_나뉜다() {
        // UV: 0, 0, 1, 2, 4, 6, 2, 1 (00,02,04,06,08,10,12,14시) → 00~06시, 12~14시 두 구간
        List<UvSlot> slots = slotsAt(0, 0, 1, 2, 4, 6, 2, 1);

        List<LowUvTimeRange> ranges = RecoveryGuideService.groupLowUvRanges(slots, DATE.atStartOfDay());

        assertThat(ranges).containsExactly(
                new LowUvTimeRange(DATE.atTime(0, 0), DATE.atTime(6, 0), 2),
                new LowUvTimeRange(DATE.atTime(12, 0), DATE.atTime(14, 0), 2)
        );
    }

    @Test
    void after_이전_슬롯은_구간_계산에서_제외된다() {
        // 00~06시가 낮음이어도 after=08시면 무시되고, 12~14시 구간만 남는다
        List<UvSlot> slots = slotsAt(0, 0, 1, 2, 4, 6, 2, 1);

        List<LowUvTimeRange> ranges = RecoveryGuideService.groupLowUvRanges(slots, DATE.atTime(8, 0));

        assertThat(ranges).containsExactly(new LowUvTimeRange(DATE.atTime(12, 0), DATE.atTime(14, 0), 2));
    }

    @Test
    void 낮음_구간이_없으면_빈_리스트다() {
        List<UvSlot> slots = slotsAt(4, 6, 8, 6);

        List<LowUvTimeRange> ranges = RecoveryGuideService.groupLowUvRanges(slots, DATE.atStartOfDay());

        assertThat(ranges).isEmpty();
    }

    @Test
    void 강도별_최소_휴식일은_LOW_1일_MODERATE_2일_HIGH_3일이다() {
        assertThat(RecoveryGuideService.restDaysFor(Intensity.LOW)).isEqualTo(1);
        assertThat(RecoveryGuideService.restDaysFor(Intensity.MODERATE)).isEqualTo(2);
        assertThat(RecoveryGuideService.restDaysFor(Intensity.HIGH)).isEqualTo(3);
    }

    private List<UvSlot> slotsAt(int... uvByTwoHourStep) {
        return java.util.stream.IntStream.range(0, uvByTwoHourStep.length)
                .mapToObj(i -> new UvSlot(DATE.atTime(i * 2, 0), uvByTwoHourStep[i]))
                .toList();
    }
}
