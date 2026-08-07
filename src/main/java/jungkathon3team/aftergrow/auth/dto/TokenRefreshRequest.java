package jungkathon3team.aftergrow.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record TokenRefreshRequest(

        @NotBlank(message = "refreshToken은 필수입니다.")
        String refreshToken
) {
}
