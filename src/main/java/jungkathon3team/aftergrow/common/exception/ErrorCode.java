package jungkathon3team.aftergrow.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * API 명세서 §0 "공통 에러 코드" 표와 1:1로 대응합니다.
 * <p>
 * 코드는 대체로 {@code E{HTTP 상태코드}{일련번호}} 형태지만
 * {@code E5010}만 502를 가리켜 규칙에서 벗어납니다(명세서 기준). 새 코드를 만들 땐
 * 규칙을 유추하지 말고 명세서에 정의된 값을 쓰세요.
 */
@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    INVALID_REQUEST("E4001", HttpStatus.BAD_REQUEST, "요청 값이 유효하지 않습니다."),
    UNAUTHORIZED("E4010", HttpStatus.UNAUTHORIZED, "인증 토큰이 없거나 만료되었습니다."),
    FORBIDDEN("E4030", HttpStatus.FORBIDDEN, "권한이 없습니다."),
    NOT_FOUND("E4040", HttpStatus.NOT_FOUND, "요청한 리소스를 찾을 수 없습니다."),
    RUNNING_SESSION_ALREADY_IN_PROGRESS("E4090", HttpStatus.CONFLICT, "이미 진행 중인 러닝 세션이 있습니다."),
    INTERNAL_ERROR("E5000", HttpStatus.INTERNAL_SERVER_ERROR, "서버 오류가 발생했습니다."),
    APPLE_HEALTH_SYNC_FAILED("E5010", HttpStatus.BAD_GATEWAY, "애플 헬스 데이터를 가져오지 못했습니다.");

    private final String code;
    private final HttpStatus status;
    private final String message;
}
