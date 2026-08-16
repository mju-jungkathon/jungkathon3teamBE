package jungkathon3team.aftergrow.auth.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 회원가입 요청.
 * <p>약관 동의 세 필드는 프론트 온보딩(Auth 화면)의 체크박스와 1:1로 대응한다.
 * 필수 두 개는 {@code @AssertTrue}라 false거나 생략되면 E4001로 거절된다 —
 * 동의를 강제하는 지점이 여기 하나뿐이므로 제거하면 미동의 가입이 뚫린다.
 */
public record SignupRequest(

        @NotBlank(message = "이메일은 필수입니다.")
        @Email(message = "이메일 형식이 올바르지 않습니다.")
        @Size(max = 255, message = "이메일은 255자를 넘을 수 없습니다.")
        String email,

        @NotBlank(message = "비밀번호는 필수입니다.")
        @Size(min = 8, max = 64, message = "비밀번호는 8자 이상 64자 이하여야 합니다.")
        String password,

        @NotBlank(message = "닉네임은 필수입니다.")
        @Size(max = 100, message = "닉네임은 100자를 넘을 수 없습니다.")
        String nickname,

        // @AssertTrue는 null을 "유효"로 보기 때문에 @NotNull이 없으면 필드를 생략하는 것만으로 뚫립니다.
        @NotNull(message = "이용약관 동의는 필수입니다.")
        @AssertTrue(message = "이용약관 동의는 필수입니다.")
        Boolean agreeTerms,

        @NotNull(message = "개인정보처리방침 동의는 필수입니다.")
        @AssertTrue(message = "개인정보처리방침 동의는 필수입니다.")
        Boolean agreePrivacy,

        // 선택 약관. 생략되면 동의하지 않은 것으로 본다.
        Boolean agreeMarketing
) {

    public boolean marketingAgreed() {
        return Boolean.TRUE.equals(agreeMarketing);
    }
}
