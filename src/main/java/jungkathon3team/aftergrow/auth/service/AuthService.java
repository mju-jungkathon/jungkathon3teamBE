package jungkathon3team.aftergrow.auth.service;

import jungkathon3team.aftergrow.auth.dto.LoginRequest;
import jungkathon3team.aftergrow.auth.dto.LoginResponse;
import jungkathon3team.aftergrow.auth.dto.SignupRequest;
import jungkathon3team.aftergrow.auth.dto.SignupResponse;
import jungkathon3team.aftergrow.auth.dto.TokenRefreshRequest;
import jungkathon3team.aftergrow.auth.dto.TokenRefreshResponse;
import jungkathon3team.aftergrow.auth.entity.User;
import jungkathon3team.aftergrow.auth.jwt.JwtTokenProvider;
import jungkathon3team.aftergrow.auth.repository.RefreshTokenStore;
import jungkathon3team.aftergrow.auth.repository.UserRepository;
import jungkathon3team.aftergrow.common.exception.BusinessException;
import jungkathon3team.aftergrow.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenStore refreshTokenStore;

    @Transactional
    public SignupResponse signup(SignupRequest request) {
        // 중복 검사와 저장 사이에 동시 요청이 끼면 users.email UNIQUE 제약에 걸려 500이 됩니다.
        // 실사용 빈도가 낮아 우선 이대로 두고, 문제가 되면 DataIntegrityViolationException을
        // E4091로 변환하는 처리를 추가하세요.
        if (userRepository.existsByEmail(request.email())) {
            throw new BusinessException(ErrorCode.DUPLICATE_EMAIL);
        }

        User user = userRepository.save(User.signup(
                request.email(),
                passwordEncoder.encode(request.password()),
                request.nickname(),
                request.marketingAgreed()));

        // 가입 직후 온보딩(목표 저장)이 바로 이어지므로 로그인과 같은 방식으로 토큰을 발급한다.
        // refresh를 Redis에 남기는 것까지 로그인과 동일해야 로그아웃이 똑같이 동작한다.
        UUID userId = user.getUserId();
        String refreshToken = jwtTokenProvider.createRefreshToken(userId);
        refreshTokenStore.save(userId, refreshToken, jwtTokenProvider.getRefreshTokenTtl());

        return SignupResponse.of(
                user,
                jwtTokenProvider.createAccessToken(userId),
                refreshToken,
                jwtTokenProvider.getAccessTokenTtl().toSeconds());
    }

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        // 이메일이 없는 경우와 비밀번호가 틀린 경우를 같은 예외로 처리합니다.
        // 구분하면 응답만 보고 가입된 이메일인지 알아낼 수 있습니다.
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_CREDENTIALS));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }

        UUID userId = user.getUserId();
        String refreshToken = jwtTokenProvider.createRefreshToken(userId);
        refreshTokenStore.save(userId, refreshToken, jwtTokenProvider.getRefreshTokenTtl());

        return new LoginResponse(
                jwtTokenProvider.createAccessToken(userId),
                refreshToken,
                jwtTokenProvider.getAccessTokenTtl().toSeconds());
    }

    /**
     * refresh 토큰으로 access 토큰을 재발급합니다.
     * <p>
     * 서명 검증만으로는 부족합니다. 로그아웃했거나 다른 기기에서 재로그인한 토큰도 서명은 유효하기 때문에,
     * Redis에 저장된 값과 일치하는지까지 확인해야 실제로 살아 있는 토큰인지 알 수 있습니다.
     */
    public TokenRefreshResponse refresh(TokenRefreshRequest request) {
        // 서명·만료·타입(REFRESH) 검증. 실패하면 여기서 E4010.
        UUID userId = jwtTokenProvider.parseRefreshToken(request.refreshToken());

        if (!refreshTokenStore.matches(userId, request.refreshToken())) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        return new TokenRefreshResponse(
                jwtTokenProvider.createAccessToken(userId),
                jwtTokenProvider.getAccessTokenTtl().toSeconds());
    }

    /**
     * 저장된 refresh 토큰을 지워 재발급을 막습니다.
     * <p>
     * 이미 발급된 access 토큰은 만료 전까지 유효합니다. JWT는 취소할 수 없기 때문이며,
     * access 수명을 1시간으로 짧게 잡은 이유가 이 창을 좁히기 위함입니다.
     */
    public void logout(UUID userId) {
        refreshTokenStore.delete(userId);
    }
}
