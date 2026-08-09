package jungkathon3team.aftergrow.running.dto;

import jakarta.validation.constraints.NotNull;
import jungkathon3team.aftergrow.running.entity.StretchingType;

import java.time.LocalDateTime;
import java.util.UUID;

public class StretchingSessionDto {

    public record Request(
            @NotNull StretchingType type
    ) {}

    public record Response(
            UUID stretchingSessionId,
            LocalDateTime startedAt
    ) {}
}