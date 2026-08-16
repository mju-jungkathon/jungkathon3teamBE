package jungkathon3team.aftergrow.running.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jungkathon3team.aftergrow.running.entity.RunningStatus;

import java.time.LocalDateTime;
import java.util.UUID;

public class RunningStartDto {

    @Schema(name = "RunningStartRequest", description = "3.3 러닝 시작 요청. 진행 중인 세션이 이미 있으면 409 E4090입니다.")
    public record Request(

            @Schema(description = "러닝을 시작한 시각. 오프셋 없는 ISO 로컬 시각(KST)입니다.",
                    example = "2026-08-16T07:00:00", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull LocalDateTime startedAt,

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull @Valid Location location,

            @Schema(description = "출발 시점의 자외선 지수. 3.1 러닝 준비 응답의 uvIndex를 그대로 보냅니다.",
                    example = "5", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull Integer uvIndexAtStart
    ) {

        @Schema(name = "RunningStartLocation", description = "출발 지점 좌표. 브라우저 navigator.geolocation에서 얻은 값입니다.")
        public record Location(

                @Schema(description = "위도(남북). 한국은 대략 33~38입니다.", example = "37.5665")
                @NotNull Double lat,

                @Schema(description = "경도(동서). 한국은 대략 125~130입니다.", example = "126.9780")
                @NotNull Double lng
        ) {}
    }

    @Schema(name = "RunningStartResponse")
    public record Response(

            @Schema(example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
            UUID runningSessionId,

            @Schema(description = "생성 직후에는 항상 IN_PROGRESS", example = "IN_PROGRESS")
            RunningStatus status
    ) {}
}
