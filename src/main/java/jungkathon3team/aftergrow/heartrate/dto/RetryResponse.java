package jungkathon3team.aftergrow.heartrate.dto;

import java.util.UUID;

/**
 * R6.2 POST /heart-rate-measurements/{id}/retry
 * <p>앱은 이 응답을 받고 R4.4~4.6 rPPG 흐름을 다시 탄다. 실패 기록 자체는 삭제하지 않는다.
 */
public record RetryResponse(
        String retryFlow,
        UUID runningSessionId
) {
    public static final String RETRY_FLOW_RPPG_GUIDE = "RPPG_GUIDE";
}
