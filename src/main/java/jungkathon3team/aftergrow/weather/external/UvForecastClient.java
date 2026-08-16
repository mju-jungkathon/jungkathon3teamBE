package jungkathon3team.aftergrow.weather.external;

import java.time.LocalDate;
import java.util.List;

/**
 * 하루치 시간대별 UV 예보 공급자.
 * <p>구현체는 {@link KmaUvForecastClient}(기상청 실연동)와 {@link MockUvForecastClient}(키 미설정 시)가 있고,
 * 어느 쪽이 뜨는지는 {@code kma.service-key} 설정 여부로 갈린다.
 * <p>러닝 준비 화면의 "지금 UV"는 {@code running.external.UvIndexClient}가 따로 담당한다 —
 * 이쪽은 하루 전체 그래프용이다.
 */
public interface UvForecastClient {

    /**
     * 지정한 날짜의 시간대별 UV 예보를 조회한다.
     *
     * @param areaNo 기상청 행정구역코드(10자리). 위경도는 {@link AreaCodeResolver}가 먼저 변환한다.
     * @return 00시부터 2시간 간격 12개({@code 00, 02, ... 22}). 순서가 곧 시간대다.
     */
    List<HourlyUv> fetchDailyForecast(String areaNo, LocalDate date);

    /**
     * @param hour 2자리 시각 문자열("00", "02", … "22"). 프론트 그래프의 x축 라벨로 그대로 쓰인다.
     * @param uv   해당 시간대 UV 지수
     */
    record HourlyUv(String hour, int uv) {
    }
}
