package jungkathon3team.aftergrow.recovery.dto;

import java.time.LocalDateTime;

/** R5.2 응답. cooldownTimerSec은 저장된 값을 그대로 돌려주고, startedAt은 호출 시점이다. */
public record CooldownTimerStartResponse(Integer cooldownTimerSec, LocalDateTime startedAt) {}

