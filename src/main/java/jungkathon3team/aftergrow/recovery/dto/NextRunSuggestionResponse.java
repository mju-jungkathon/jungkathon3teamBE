package jungkathon3team.aftergrow.recovery.dto;

import java.time.LocalDateTime;

/**
 * R5.4 응답. 추천 불가 시(위치 정보 없음 / UV 예보 실패 / 48시간 내 낮은 UV 시간대 없음)
 * {@code recommendedTime}·{@code expectedUvIndex}는 null이고 {@code reason}에 안내 메시지가 담긴다.
 */
public record NextRunSuggestionResponse(LocalDateTime recommendedTime, String reason, Integer expectedUvIndex) {}
