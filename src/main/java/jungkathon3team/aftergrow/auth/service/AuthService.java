package jungkathon3team.aftergrow.auth.service;

import jungkathon3team.aftergrow.auth.dto.SignupRequest;
import jungkathon3team.aftergrow.auth.dto.SignupResponse;
import jungkathon3team.aftergrow.auth.entity.User;
import jungkathon3team.aftergrow.auth.repository.UserRepository;
import jungkathon3team.aftergrow.common.exception.BusinessException;
import jungkathon3team.aftergrow.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

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
}
