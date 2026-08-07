package jungkathon3team.aftergrow.auth.dto;

import jungkathon3team.aftergrow.auth.entity.User;

import java.time.LocalDateTime;
import java.util.UUID;

public record SignupResponse(
        UUID userId,
        String email,
        String nickname,
        LocalDateTime createdAt
) {

    public static SignupResponse from(User user) {
        return new SignupResponse(
                user.getUserId(),
                user.getEmail(),
                user.getNickname(),
                user.getCreatedAt());
    }
}
