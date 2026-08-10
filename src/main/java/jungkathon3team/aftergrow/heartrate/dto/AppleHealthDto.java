package jungkathon3team.aftergrow.heartrate.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * R4.2 워치 데이터 업로드 · R4.3 애플 헬스 연동 기록.
 * <p>명세는 두 엔드포인트를 GET으로 적었지만 HealthKit은 온디바이스 API라 서버가 직접 읽을 수 없다.
 * 앱이 읽은 값과 권한 동의 결과를 서버로 올리는 POST 구조로 바꿨다.
 */
public class AppleHealthDto {

    public record HeartRateRequest(
            @NotNull UUID runningSessionId,
            @NotNull @Positive Integer avgBpm,
            @NotNull @Positive Integer maxBpm,
            Integer hrvMs,
            @NotNull LocalDateTime syncedAt
    ) {}

    public record LinkRequest(
            @NotNull Boolean linked
    ) {}

    public record LinkResponse(
            boolean appleHealthLinked
    ) {}
}
