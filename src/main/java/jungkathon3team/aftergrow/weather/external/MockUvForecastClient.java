package jungkathon3team.aftergrow.weather.external;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * {@code kma.auth-key}가 없을 때(주로 CI와 키를 넣지 않은 로컬) 대신 쓰이는 구현.
 * 실제 값이 아니라 <b>하루 UV 곡선의 모양만</b> 흉내 낸다 — 새벽 0, 정오 최대.
 * <p>어느 구현이 쓰일지는 {@link UvForecastClientConfig}가 키 설정 여부를 보고 정한다.
 */
public class MockUvForecastClient implements UvForecastClient {

    /** 00, 02, 04 … 22시의 대표적인 맑은 날 UV 곡선. */
    private static final int[] CURVE = {0, 0, 0, 1, 3, 6, 8, 6, 3, 1, 0, 0};

    @Override
    public List<HourlyUv> fetchDailyForecast(String areaNo, LocalDate date) {
        List<HourlyUv> hourly = new ArrayList<>(CURVE.length);
        for (int i = 0; i < CURVE.length; i++) {
            hourly.add(new HourlyUv("%02d".formatted(i * 2), CURVE[i]));
        }
        return hourly;
    }
}
