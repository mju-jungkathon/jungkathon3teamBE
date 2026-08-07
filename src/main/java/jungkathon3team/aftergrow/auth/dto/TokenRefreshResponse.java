package jungkathon3team.aftergrow.auth.dto;

public record TokenRefreshResponse(
        String accessToken,
        /** access 토큰 만료까지 남은 시간(초) */
        long expiresIn
) {
}
