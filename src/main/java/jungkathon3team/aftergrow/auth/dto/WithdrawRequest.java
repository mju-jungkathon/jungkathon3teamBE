package jungkathon3team.aftergrow.auth.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 회원 탈퇴 요청.
 *
 * <p><b>현재 비밀번호를 함께 받는다.</b> 되돌릴 수 없는 삭제라 access 토큰만으로 실행되면 안 된다 —
 * 토큰이 유출되면 계정과 모든 러닝 기록이 그대로 날아간다. 로그인과 같은 확인 절차를 한 번 더 거친다.
 */
public record WithdrawRequest(

        @NotBlank(message = "비밀번호는 필수입니다.")
        String password
) {
}
