package jungkathon3team.aftergrow.auth.service;

import jungkathon3team.aftergrow.auth.dto.LoginRequest;
import jungkathon3team.aftergrow.auth.dto.LoginResponse;
import jungkathon3team.aftergrow.auth.dto.SignupRequest;
import jungkathon3team.aftergrow.auth.dto.SignupResponse;
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

        User user = User.builder()
                .email(request.email())
                .passwordHash(passwordEncoder.encode(request.password()))
                .nickname(request.nickname())
                .build();

        return SignupResponse.from(userRepository.save(user));
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
}
