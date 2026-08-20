package jungkathon3team.aftergrow.recovery.dto;

import java.util.List;

/**
 * R5.4 응답. 연속된 낮은-UV 시간대를 구간 목록으로 담는다(예: 00~06시, 12~14시).
 * 추천 불가 시(위치 정보 없음 / UV 예보 실패 / 48시간 내 낮은 UV 시간대 없음)
 * {@code recommendedRanges}는 빈 배열이고 {@code reason}에 안내 메시지가 담긴다.
 */
public record NextRunSuggestionResponse(List<LowUvTimeRange> recommendedRanges, String reason) {}
