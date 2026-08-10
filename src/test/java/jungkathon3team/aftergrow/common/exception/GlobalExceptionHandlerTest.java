package jungkathon3team.aftergrow.common.exception;

import jungkathon3team.aftergrow.common.response.ApiResponse;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.UUID;

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

    /** 경로 변수에 UUID 대신 "notauuid" 같은 값이 오면 500이 아니라 400/E4001이어야 한다. */
    @Test
    void 경로_변수_타입이_맞지_않으면_400과_E4001을_반환한다() {
        MethodArgumentTypeMismatchException e = new MethodArgumentTypeMismatchException(
                "notauuid", UUID.class, "id", (MethodParameter) null, new IllegalArgumentException());

        ResponseEntity<ApiResponse<Void>> response = handler.handleTypeMismatch(e);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().success()).isFalse();
        assertThat(response.getBody().error().code()).isEqualTo("E4001");
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
