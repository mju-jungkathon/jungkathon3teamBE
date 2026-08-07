package jungkathon3team.aftergrow.common.exception;

import jungkathon3team.aftergrow.common.response.ApiResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void BusinessException은_ErrorCode의_상태코드와_코드로_변환된다() {
        ResponseEntity<ApiResponse<Void>> response =
                handler.handleBusiness(new BusinessException(ErrorCode.UNAUTHORIZED));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody().success()).isFalse();
        assertThat(response.getBody().data()).isNull();
        assertThat(response.getBody().error().code()).isEqualTo("E4010");
    }

    @Test
    void BusinessException에_직접_넘긴_메시지가_응답에_담긴다() {
        ResponseEntity<ApiResponse<Void>> response =
                handler.handleBusiness(new BusinessException(ErrorCode.INVALID_REQUEST, "이메일 형식이 아닙니다."));

        assertThat(response.getBody().error().message()).isEqualTo("이메일 형식이 아닙니다.");
    }

    @Test
    void 예상하지_못한_예외는_내부_메시지를_노출하지_않는다() {
        ResponseEntity<ApiResponse<Void>> response =
                handler.handleUnexpected(new IllegalStateException("DB 커넥션 문자열 유출됨"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().error().code()).isEqualTo("E5000");
        assertThat(response.getBody().error().message()).doesNotContain("커넥션");
    }
}
