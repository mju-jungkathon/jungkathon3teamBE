package jungkathon3team.aftergrow.weather.external;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 위경도 → 기상청 행정구역코드({@code areaNo}) 변환.
 *
 * <p><b>기상청 자외선지수 API는 격자좌표(nx/ny)가 아니라 행정구역코드를 받는다.</b>
 * 격자좌표를 쓰는 건 초단기·단기예보이고 거기엔 UV 항목이 없다. 그래서 LCC 투영 변환이 아니라
 * 행정구역 매핑이 필요하다.
 *
 * <p>표는 {@code kma-area-codes.csv}에 있고, 기상청이 배포하는 동네예보 구역코드 엑셀에서
 * <b>시군구 단위 248개</b>를 뽑은 것이다. 손으로 적은 값이 아니라 기상청 파일에서 유래했으므로
 * 코드가 실재함이 보장된다 — 특히 강원·전북은 특별자치도 전환으로 시도 단위 코드가
 * 그 파일에 없어서, 추측한 코드를 쓰면 조회가 실패한다.
 *
 * <p>구역 경계 다각형이 아니라 <b>대표 지점 최근접</b>으로 고른다. 경계 근처에서는 옆 시군구로
 * 떨어질 수 있지만 UV 지수는 수십 km 단위로 거의 균일해 실용상 문제없고, 같은 시군구 사용자가
 * 캐시를 공유하게 되는 이점이 있다.
 */
@Slf4j
@Component
public class AreaCodeResolver {

    private static final String RESOURCE = "kma-area-codes.csv";

    private record Area(String areaNo, double lat, double lng, String name) {
    }

    private List<Area> areas;

    @PostConstruct
    void load() {
        List<Area> loaded = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new ClassPathResource(RESOURCE).getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank() || line.startsWith("#")) {
                    continue;
                }
                String[] parts = line.split(",", 4);
                loaded.add(new Area(parts[0], Double.parseDouble(parts[1]), Double.parseDouble(parts[2]),
                        parts.length > 3 ? parts[3] : ""));
            }
        } catch (IOException e) {
            // 표가 없으면 UV 예보 자체가 불가능하므로 기동 시점에 실패시킨다.
            // 런타임에 조용히 틀린 지역을 조회하는 것보다 낫다.
            throw new IllegalStateException(RESOURCE + " 를 읽을 수 없습니다.", e);
        }
        if (loaded.isEmpty()) {
            throw new IllegalStateException(RESOURCE + " 에 유효한 행이 없습니다.");
        }
        this.areas = List.copyOf(loaded);
        log.info("기상청 행정구역코드 {}건 로드", areas.size());
    }

    /** 가장 가까운 시군구의 행정구역코드. 한국 범위 좌표에서만 의미가 있다. */
    public String resolve(double lat, double lng) {
        Area nearest = areas.get(0);
        double nearestDistance = Double.MAX_VALUE;

        for (Area area : areas) {
            // 실제 거리가 아니라 순위만 필요하므로 제곱합 그대로 비교한다(sqrt 불필요).
            // 위도 1도와 경도 1도의 실제 길이가 달라 약간 왜곡되지만, 구역을 고르는 데는 충분하다.
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
