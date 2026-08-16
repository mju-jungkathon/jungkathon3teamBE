package jungkathon3team.aftergrow.auth.dto;

import jungkathon3team.aftergrow.auth.entity.User;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 회원가입 응답.
 *
 * <p><b>토큰을 함께 내려준다.</b> 온보딩에서 곧바로 {@code PATCH /users/me/goal}(운동 목적·주간 횟수)을
 * 불러야 하는데, 토큰이 없으면 클라이언트가 {@code POST /auth/login}을 한 번 더 호출해야 했다.
 * 방금 비밀번호를 확인하고 만든 계정이라 다시 인증시킬 이유가 없다.
 *
 * <p>refresh 토큰은 로그인과 동일하게 Redis에도 저장되므로 로그아웃으로 즉시 무효화된다.
 */
public record SignupResponse(
        UUID userId,
        String email,
        String nickname,
        LocalDateTime createdAt,
        String accessToken,
        String refreshToken,
        long expiresIn
) {

    public static SignupResponse of(User user, String accessToken, String refreshToken, long expiresIn) {
        return new SignupResponse(
                user.getUserId(),
                user.getEmail(),
                user.getNickname(),
                user.getCreatedAt(),
                accessToken,
                refreshToken,
                expiresIn);
    }
}
