package jungkathon3team.aftergrow.auth.dto;

public record LoginResponse(
        String accessToken,
        String refreshToken,
        /** access 토큰 만료까지 남은 시간(초). 명세서 예시의 3600에 대응합니다. */
        long expiresIn
) {
}
