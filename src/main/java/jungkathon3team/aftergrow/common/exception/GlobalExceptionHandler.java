package jungkathon3team.aftergrow.common.exception;

import jungkathon3team.aftergrow.common.response.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

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

    /**
     * 경로 변수 타입이 맞지 않는 경우(예: UUID 자리에 "notauuid"). 파싱 실패는 클라이언트 잘못이라
     * 500이 아니라 400이어야 합니다.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        log.debug("경로/파라미터 타입 불일치: {}", e.getName());
        return toResponse(ErrorCode.INVALID_REQUEST, ErrorCode.INVALID_REQUEST.getMessage());
    }

    /** 매핑된 핸들러가 없는 경로. 존재하지 않는 URL은 서버 오류가 아니라 404입니다. */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoResource(NoResourceFoundException e) {
        log.debug("존재하지 않는 경로: {}", e.getResourcePath());
        return toResponse(ErrorCode.NOT_FOUND, ErrorCode.NOT_FOUND.getMessage());
    }

    /** 경로는 있지만 HTTP 메서드가 다른 경우. 이것도 클라이언트 잘못이라 4xx입니다. */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        log.debug("지원하지 않는 메서드: {}", e.getMethod());
        return toResponse(ErrorCode.NOT_FOUND, "요청한 경로 또는 메서드를 찾을 수 없습니다.");
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
