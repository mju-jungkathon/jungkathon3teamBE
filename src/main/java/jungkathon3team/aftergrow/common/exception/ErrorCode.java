package jungkathon3team.aftergrow.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * 코드 형식은 {@code E{HTTP 상태코드}{일련번호}} 입니다. (예: E4001 = 400번대 1번)
 * 도메인별 에러는 필요해질 때 해당 상태코드 자리에 추가하세요.
 */
@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    INVALID_REQUEST("E4001", HttpStatus.BAD_REQUEST, "잘못된 요청입니다."),
    UNAUTHORIZED("E4010", HttpStatus.UNAUTHORIZED, "인증이 필요합니다."),
    FORBIDDEN("E4030", HttpStatus.FORBIDDEN, "접근 권한이 없습니다."),
    INTERNAL_ERROR("E5000", HttpStatus.INTERNAL_SERVER_ERROR, "서버 오류가 발생했습니다.");

    private final String code;
    private final HttpStatus status;
    private final String message;
}
