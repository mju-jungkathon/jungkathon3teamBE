package jungkathon3team.aftergrow.running.external;

import org.springframework.stereotype.Component;

import java.time.LocalTime;

/**
 * TODO: 실제 UV API(기상청 초단기예보, Open-Meteo 등) 연동 후 이 클래스를 대체하고
 * @Component 를 제거하세요. 지금은 하루 중 시간대에 따라 그럴듯한 값만 리턴합니다.
 */
@Component
public class MockUvIndexClient implements UvIndexClient {

    @Override
    public UvIndexResult fetchCurrentUvIndex(double lat, double lng) {
        int hour = LocalTime.now().getHour();
        int uvIndex;
        if (hour < 7 || hour >= 18) {
            uvIndex = 1;
        } else if (hour < 10 || hour >= 16) {
            uvIndex = 4;
        } else {
            uvIndex = 7;
        }
        return UvIndexResult.of(uvIndex);
    }
}
