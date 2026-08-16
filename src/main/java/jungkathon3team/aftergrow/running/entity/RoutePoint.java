package jungkathon3team.aftergrow.running.entity;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * 러닝 경로를 이루는 GPS 점 하나. {@code running_sessions.route_path}(JSONB) 배열의 원소이며,
 * 요청 DTO와 엔티티가 같은 타입을 공유한다(직렬화 형태가 곧 저장 형태다).
 *
 * @param lat 위도
 * @param lng 경도
 * @param t   러닝 시작 시점부터의 경과 초. 점 사이 간격이 일정하지 않으므로(프론트가 스로틀링한다)
 *            인덱스가 아니라 값으로 갖고 있어야 구간 속도를 나중에 계산할 수 있다.
 */
public record RoutePoint(
        @NotNull @Min(-90) @Max(90) Double lat,
        @NotNull @Min(-180) @Max(180) Double lng,
        @NotNull @PositiveOrZero Integer t
) {
}
