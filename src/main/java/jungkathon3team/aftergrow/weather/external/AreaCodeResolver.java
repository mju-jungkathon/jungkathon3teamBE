package jungkathon3team.aftergrow.weather.external;

import org.springframework.stereotype.Component;

/**
 * 위경도 → 기상청 행정구역코드({@code areaNo}) 변환.
 *
 * <p><b>기상청 자외선지수 API는 격자좌표(nx/ny)가 아니라 행정구역코드를 받는다.</b>
 * 격자좌표를 쓰는 건 초단기·단기예보이고 거기엔 UV 항목이 없다. 그래서 LCC 투영 변환이 아니라
 * 행정구역 매핑이 필요하다.
 *
 * <p>17개 광역시·도의 대표 지점 중 <b>가장 가까운 곳</b>을 고른다. 경계 다각형이 아니라 최근접점이라
 * 도 경계 근처에서는 옆 시도로 떨어질 수 있지만, UV 지수는 수십 km 단위로 거의 균일해서 실용상 문제없다.
 * 오히려 같은 시도 사용자가 캐시를 널리 공유하게 되는 이점이 있다.
 */
@Component
public class AreaCodeResolver {

    /**
     * @param areaNo 기상청 행정구역코드(10자리)
     * @param lat    시도청 소재지 기준 대표 위도
     * @param lng    시도청 소재지 기준 대표 경도
     */
    private record Area(String areaNo, double lat, double lng) {
    }

    // ponytail: 시도 단위 17개로 시작한다. 시군구 단위 정밀도가 필요해지면
    // 기상청 지역코드 목록(공공데이터포털 첨부 엑셀)을 리소스로 넣고 이 배열만 늘리면 된다.
    private static final Area[] AREAS = {
            new Area("1100000000", 37.5665, 126.9780), // 서울
            new Area("2600000000", 35.1796, 129.0756), // 부산
            new Area("2700000000", 35.8714, 128.6014), // 대구
            new Area("2800000000", 37.4563, 126.7052), // 인천
            new Area("2900000000", 35.1595, 126.8526), // 광주
            new Area("3000000000", 36.3504, 127.3845), // 대전
            new Area("3100000000", 35.5384, 129.3114), // 울산
            new Area("3600000000", 36.4800, 127.2890), // 세종
            new Area("4100000000", 37.2636, 127.0286), // 경기(수원)
            new Area("4300000000", 36.6424, 127.4890), // 충북(청주)
            new Area("4400000000", 36.6009, 126.6650), // 충남(홍성)
            new Area("4600000000", 34.8161, 126.4630), // 전남(무안)
            new Area("4700000000", 36.5684, 128.7294), // 경북(안동)
            new Area("4800000000", 35.2280, 128.6811), // 경남(창원)
            new Area("5000000000", 33.4996, 126.5312), // 제주
            // 강원·전북은 특별자치도 전환으로 코드가 바뀌었다(42→51, 45→52).
            // 기상청이 아직 구 코드를 쓴다면 이 두 줄만 42/45로 되돌리면 된다.
            new Area("5100000000", 37.8813, 127.7300), // 강원(춘천)
            new Area("5200000000", 35.8242, 127.1480), // 전북(전주)
    };

    /** 가장 가까운 광역시·도의 행정구역코드. 한국 범위에서만 의미가 있다. */
    public String resolve(double lat, double lng) {
        Area nearest = AREAS[0];
        double nearestDistance = Double.MAX_VALUE;

        for (Area area : AREAS) {
            // 실제 거리가 아니라 순위만 필요하므로 제곱합 그대로 비교한다(sqrt 불필요).
            // 위도 1도와 경도 1도의 실제 길이가 달라 약간 왜곡되지만, 시도를 고르는 데는 충분하다.
            double dLat = area.lat() - lat;
            double dLng = area.lng() - lng;
            double distance = dLat * dLat + dLng * dLng;
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearest = area;
            }
        }
        return nearest.areaNo();
    }
}
