package jungkathon3team.aftergrow.weather.service;

import jungkathon3team.aftergrow.weather.dto.UvForecastResponse;
import jungkathon3team.aftergrow.weather.external.AreaCodeResolver;
import jungkathon3team.aftergrow.weather.external.UvForecastClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

/**
 * 시간대별 UV 예보 조회 + Redis 캐싱.
 *
 * <p><b>왜 테이블이 아니라 캐시인가:</b> UV 예보는 시간 × 지역으로 계속 변하는 "지리적 사실"이라
 * 사용자별로 저장할 이유가 없고, 기상청이 이미 갖고 있는 원본을 우리 DB에 복제하는 셈이 된다.
 * 사라져도 다시 만들 수 있으므로 팀 원칙대로 Redis에 둔다.
 *
 * <p><b>캐시 키에 사용자 정보가 들어가지 않는다.</b> 행정구역코드는 순수 지리 정보라, 같은 시도의
 * 모든 사용자가 캐시 한 벌을 공유한다. 시간대별로 쪼개지 않고 하루치를 배열 하나로 담는 이유도 같다 —
 * 기상청이 애초에 하루 전체를 한 번에 주므로 쪼개면 외부 호출만 늘어난다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UvForecastService {

    private static final String CACHE_KEY_PREFIX = "uv:forecast:";

    /**
     * 기상청 발표가 하루 두 번(06시·18시)이라 그보다 짧게 잡을 이유가 없고,
     * 자정을 넘기면 키의 날짜 부분이 바뀌어 어차피 새로 받는다.
     */
    private static final Duration CACHE_TTL = Duration.ofHours(6);

    private final UvForecastClient uvForecastClient;
    private final AreaCodeResolver areaCodeResolver;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public UvForecastResponse getTodayForecast(double lat, double lng) {
        return getForecast(lat, lng, LocalDate.now());
    }

    /** 회복 가이드의 다음 러닝 추천 시점 계산처럼, 오늘이 아닌 날짜의 예보가 필요할 때 쓴다. */
    public UvForecastResponse getForecast(double lat, double lng, LocalDate date) {
        String areaNo = areaCodeResolver.resolve(lat, lng);
        String cacheKey = CACHE_KEY_PREFIX + areaNo + ":" + date;

        List<UvForecastClient.HourlyUv> cached = readCache(cacheKey);
        if (cached != null) {
            return new UvForecastResponse(cached);
        }

        List<UvForecastClient.HourlyUv> hourly = uvForecastClient.fetchDailyForecast(areaNo, date);
        writeCache(cacheKey, hourly);
        return new UvForecastResponse(hourly);
    }

    /**
     * Redis가 죽어도 UV 조회까지 같이 죽지는 않게 한다 — 캐시는 외부 호출을 줄이는 장치일 뿐이라
     * 없으면 매번 기상청에 물어보면 된다. (refresh token과 달리 정확성에 관여하지 않는다.)
     */
    private List<UvForecastClient.HourlyUv> readCache(String cacheKey) {
        try {
            String json = redisTemplate.opsForValue().get(cacheKey);
            if (json == null) {
                return null;
            }
            return objectMapper.readValue(json, new TypeReference<List<UvForecastClient.HourlyUv>>() {});
        } catch (Exception e) {
            log.warn("UV 예보 캐시를 읽지 못해 기상청에 직접 조회합니다. key={}", cacheKey, e);
            return null;
        }
    }

    private void writeCache(String cacheKey, List<UvForecastClient.HourlyUv> hourly) {
        try {
            redisTemplate.opsForValue().set(cacheKey, objectMapper.writeValueAsString(hourly), CACHE_TTL);
        } catch (Exception e) {
            log.warn("UV 예보 캐시를 저장하지 못했습니다. key={}", cacheKey, e);
        }
    }
}
