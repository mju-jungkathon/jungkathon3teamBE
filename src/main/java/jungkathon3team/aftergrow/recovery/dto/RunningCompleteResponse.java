package jungkathon3team.aftergrow.recovery.dto;

import jungkathon3team.aftergrow.running.entity.RunningStatus;

import java.util.UUID;

/**
 * R5.3 응답.
 * <p>reportId: 명세엔 있지만 ERD에 별도 Report 엔티티가 없어 recoveryGuideId를 그대로 쓴다 —
 * 리포트 화면이 결국 회복 가이드 내용을 보여주는 것이라 같은 리소스를 가리켜도 무방하다는 판단.
 */
public record RunningCompleteResponse(UUID runningSessionId, RunningStatus status, UUID reportId) {}
