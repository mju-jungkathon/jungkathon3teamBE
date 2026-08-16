package jungkathon3team.aftergrow.common.request;

import jungkathon3team.aftergrow.common.exception.BusinessException;
import jungkathon3team.aftergrow.common.exception.ErrorCode;

import java.time.LocalDateTime;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 목록 조회의 {@code range} 쿼리 파라미터("{일수}d")를 조회 하한 시각으로 바꾼다.
 * <p>측정 기록(R6.1)과 러닝 기록이 같은 규칙을 쓰므로 한 곳에 둔다 — 두 곳에서 따로 파싱하면
 * 한쪽만 고쳐져 같은 파라미터가 API마다 다르게 동작하게 된다.
 * <p>순수 함수라 DB 없이 테스트한다.
 */
public final class RangeParam {

    /** 생략하거나 공백일 때 쓰는 기본값. */
    public static final String DEFAULT_RANGE = "30d";

    private static final Pattern RANGE_PATTERN = Pattern.compile("(\\d+)d");

    private RangeParam() {
    }

    public static LocalDateTime since(String range, LocalDateTime now) {
        String value = (range == null || range.isBlank()) ? DEFAULT_RANGE : range.trim();

        Matcher matcher = RANGE_PATTERN.matcher(value);
        if (!matcher.matches()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST); // E4001
        }

        long days;
        try {
            days = Long.parseLong(matcher.group(1));
        } catch (NumberFormatException e) {
            // long을 넘는 자릿수. 그대로 두면 500이 되므로 잘못된 요청으로 돌린다.
            throw new BusinessException(ErrorCode.INVALID_REQUEST); // E4001
        }

        if (days <= 0) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST); // E4001
        }

        return now.minusDays(days);
    }
}
