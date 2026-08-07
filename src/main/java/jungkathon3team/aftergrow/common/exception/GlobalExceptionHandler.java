package jungkathon3team.aftergrow.common.exception;

import jungkathon3team.aftergrow.common.response.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException e) {
        ErrorCode errorCode = e.getErrorCode();
        return toResponse(errorCode, e.getMessage());
    }

    /** {@code @Valid} 검증 실패. 첫 번째 위반 필드의 메시지를 내려줍니다. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(FieldError::getDefaultMessage)
                .orElse(ErrorCode.INVALID_REQUEST.getMessage());
        return toResponse(ErrorCode.INVALID_REQUEST, message);
    }

    /**
     * 본문이 JSON으로 파싱되지 않는 경우(깨진 JSON, UTF-8이 아닌 바이트 등).
     * 클라이언트 잘못이므로 500이 아니라 400으로 응답해야 합니다.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnreadableBody(HttpMessageNotReadableException e) {
        // 파서 예외 메시지에는 본문 일부가 담길 수 있어 그대로 노출하지 않습니다.
        log.debug("요청 본문 파싱 실패", e);
        return toResponse(ErrorCode.INVALID_REQUEST,
                "요청 본문을 읽을 수 없습니다. JSON 형식과 UTF-8 인코딩을 확인하세요.");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception e) {
        log.error("처리되지 않은 예외", e);
        // 내부 예외 메시지는 노출하지 않고 고정 메시지로 대체
        return toResponse(ErrorCode.INTERNAL_ERROR, ErrorCode.INTERNAL_ERROR.getMessage());
    }

    private ResponseEntity<ApiResponse<Void>> toResponse(ErrorCode errorCode, String message) {
        return ResponseEntity.status(errorCode.getStatus())
                .body(ApiResponse.fail(errorCode.getCode(), message));
    }
}
