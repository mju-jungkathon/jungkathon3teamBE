package jungkathon3team.aftergrow.running.external;

import org.springframework.stereotype.Component;

/**
 * TODO: 카카오 로컬 API 등 실제 역지오코딩으로 교체하세요.
 */
@Component
public class MockLocationLabelResolver implements LocationLabelResolver {

    @Override
    public String resolve(double lat, double lng) {
        return "현재 위치";
    }
}
