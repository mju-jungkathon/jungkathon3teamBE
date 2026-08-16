package jungkathon3team.aftergrow.running.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jungkathon3team.aftergrow.running.entity.StretchingType;

import java.time.LocalDateTime;
import java.util.UUID;

public class StretchingSessionDto {

    @Schema(name = "StretchingStartRequest", description = "3.2 스트레칭 시작 요청. 러닝 준비 화면에서 '스트레칭 시작'을 누를 때 보냅니다.")
    public record Request(

            @Schema(description = "스트레칭 종류. 현재는 러닝 전 스트레칭(PRE_RUN)만 지원합니다.",
                    example = "PRE_RUN", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull StretchingType type
    ) {}

    @Schema(name = "StretchingStartResponse")
    public record Response(

            @Schema(example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
            UUID stretchingSessionId,

            @Schema(description = "서버가 기록한 스트레칭 시작 시각", example = "2026-08-16T06:52:00")
            LocalDateTime startedAt
    ) {}
}
