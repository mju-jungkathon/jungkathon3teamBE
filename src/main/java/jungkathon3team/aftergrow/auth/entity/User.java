package jungkathon3team.aftergrow.auth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "users")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "user_id")
    private UUID userId;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(length = 100)
    private String nickname;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** 필수 약관(이용약관) 동의 시각. 동의하지 않으면 애초에 가입이 되지 않으므로 항상 값이 있다. */
    @Column(name = "terms_agreed_at", nullable = false, updatable = false)
    private LocalDateTime termsAgreedAt;

    /** 필수 약관(개인정보처리방침) 동의 시각. */
    @Column(name = "privacy_agreed_at", nullable = false, updatable = false)
    private LocalDateTime privacyAgreedAt;

    /** 선택 약관(마케팅 수신) 동의 시각. null이면 동의하지 않은 것이다. */
    @Column(name = "marketing_agreed_at")
    private LocalDateTime marketingAgreedAt;

    /**
     * 회원가입으로 만들어지는 사용자. 필수 약관 동의 시각을 함께 남긴다.
     * <p>동의 <b>여부</b> 검증은 {@code SignupRequest}의 {@code @AssertTrue}가 담당한다 —
     * 여기까지 온 시점에는 이미 두 필수 약관에 동의한 상태다.
     */
    public static User signup(String email, String passwordHash, String nickname, boolean agreeMarketing) {
        LocalDateTime now = LocalDateTime.now();
        return User.builder()
                .email(email)
                .passwordHash(passwordHash)
                .nickname(nickname)
                .termsAgreedAt(now)
                .privacyAgreedAt(now)
                .marketingAgreedAt(agreeMarketing ? now : null)
                .build();
    }

    @PrePersist
    void prePersist() {
        createdAt = LocalDateTime.now();
        // 가입 경로(signup)로 만들어진 사용자는 이미 값이 차 있다.
        // 빌더로 직접 만든 객체(주로 테스트 픽스처)가 NOT NULL 제약에 걸리지 않도록 하는 폴백이며,
        // 실제 동의 강제는 SignupRequest의 @AssertTrue에 있다.
        if (termsAgreedAt == null) {
            termsAgreedAt = createdAt;
        }
        if (privacyAgreedAt == null) {
            privacyAgreedAt = createdAt;
        }
    }
}
