package jungkathon3team.aftergrow.recovery.dto;

import java.time.LocalDateTime;

/**
 * 연속된 "낮음"(UV≤2) 시간대 구간 하나. {@code expectedUvIndex}는 구간 내 최댓값(최악의 경우)이다.
 */
public record LowUvTimeRange(LocalDateTime startTime, LocalDateTime endTime, Integer expectedUvIndex) {}
