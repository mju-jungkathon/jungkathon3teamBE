package jungkathon3team.aftergrow.common.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/**
 * API 명세서 §0 "공통 에러 코드" 표를 그대로 고정합니다.
 * 명세서가 바뀌면 이 테스트부터 깨져야 합니다.
 */
class ErrorCodeTest {

    @Test
    void 명세서_공통_에러코드_표와_일치한다() {
        assertThat(ErrorCode.values())
                .extracting(ErrorCode::getCode, ErrorCode::getStatus)
                .containsExactlyInAnyOrder(
                        tuple("E4001", HttpStatus.BAD_REQUEST),
                        tuple("E4010", HttpStatus.UNAUTHORIZED),
                        tuple("E4011", HttpStatus.UNAUTHORIZED),
                        tuple("E4030", HttpStatus.FORBIDDEN),
                        tuple("E4040", HttpStatus.NOT_FOUND),
                        tuple("E4090", HttpStatus.CONFLICT),
                        tuple("E4091", HttpStatus.CONFLICT),
                        tuple("E5000", HttpStatus.INTERNAL_SERVER_ERROR),
                        // 명세서상 502. E5020이 아닌 점은 명세서를 따른 것이며 오타 여부 확인 필요
                        tuple("E5010", HttpStatus.BAD_GATEWAY),
                        // UV 예보(기상청) 연동 실패. E5010의 번호 규칙을 그대로 이어받았다
                        tuple("E5011", HttpStatus.BAD_GATEWAY));
    }
}
