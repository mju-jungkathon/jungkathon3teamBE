package jungkathon3team.aftergrow.weather.external;

import jungkathon3team.aftergrow.common.exception.BusinessException;
import jungkathon3team.aftergrow.common.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 기상청 생활기상지수 자외선지수 API({@code LivingWthrIdxServiceV4/getUVIdxV4}) 연동.
 *
 * <p><b>공공데이터포털(apis.data.go.kr)을 쓴다. 기상청 API허브(apihub.kma.go.kr)로는 안 된다.</b>
 * API허브에도 기상청 API가 여럿 미러링돼 있지만 <b>자외선 예보는 없다</b> —
 * {@code LivingWthrIdxService*}는 어떤 조합으로 불러도 404("유효하지 않은 API")이고,
 * API허브의 자외선은 {@code typ01/url/kma_sfctm_uv.php}(지점별 실측 관측)뿐이라 미래 시간대를 못 준다.
 * 두 포털은 <b>키가 호환되지 않으므로</b>(각각 가입·발급) 여기 들어갈 값은 공공데이터포털 서비스키다.
 *
 * <p>경로 확인 요령: API허브는 경로가 틀리면 404, 경로는 맞고 활용신청이 없으면 403을 준다.
 *
 * <p>{@code kma.auth-key}가 비어 있으면 {@link UvForecastClientConfig}가 이 구현 대신
 * {@link MockUvForecastClient}를 등록한다
 * (CI에는 키가 없고, 외부 API를 때리는 테스트는 네트워크 상태에 따라 깨져 CI를 못 믿게 만든다).
 *
 * <p><b>응답 형태를 알고 있어야 이 코드가 읽힌다.</b> 기상청은 발표시각(하루 두 번, 06시·18시) 기준
 * <b>3시간 간격 상대 오프셋</b>으로 {@code h0}~{@code h75}를 준다. h0이 발표시각, h3이 +3시간이다.
 * 프론트가 원하는 건 오늘 00~22시의 <b>2시간 간격 12개</b>라 두 번 변환이 필요하다:
 * <ol>
 *   <li>상대 오프셋 → 절대 시각 (발표시각을 더한다)</li>
 *   <li>3시간 격자 → 2시간 격자 (사이값은 선형 보간)</li>
 * </ol>
 * 발표 이전 시간대는 응답에 아예 없다. 그 구간은 0으로 둔다 — 발표가 06시부터라 비는 건 새벽뿐이고,
 * 새벽 UV는 실제로 0이다.
 */
@Slf4j
public class KmaUvForecastClient implements UvForecastClient {

    private static final DateTimeFormatter ANNOUNCED_AT = DateTimeFormatter.ofPattern("yyyyMMddHH");
    private static final String RESULT_CODE_OK = "00";

    /** 기상청 발표 시각(하루 두 번). 요청한 날짜를 덮는 가장 이른 발표를 고르기 위해 필요하다. */
    private static final int[] ANNOUNCE_HOURS = {6, 18};

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String authKey;

    public KmaUvForecastClient(RestClient.Builder builder,
                               ObjectMapper objectMapper,
                               String authKey) {
        this.restClient = builder.build();
        this.objectMapper = objectMapper;
        this.authKey = authKey;
    }

    @Override
    public List<HourlyUv> fetchDailyForecast(String areaNo, LocalDate date) {
        LocalDateTime announcedAt = latestAnnouncementOnOrBefore(date);
        JsonNode item = requestItem(areaNo, announcedAt);
        return toTwoHourGrid(item, announcedAt, date);
    }

    /**
     * 요청 날짜 06시 발표를 쓴다. 그 시각이 아직 오지 않았다면(오늘 새벽) 전날 18시 발표로 물러난다 —
     * 아직 발표되지 않은 시각으로 조회하면 빈 응답이 온다.
     */
    private LocalDateTime latestAnnouncementOnOrBefore(LocalDate date) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime morning = date.atTime(ANNOUNCE_HOURS[0], 0);
        if (!morning.isAfter(now)) {
            return morning;
        }
        return date.minusDays(1).atTime(ANNOUNCE_HOURS[1], 0);
    }

    private JsonNode requestItem(String areaNo, LocalDateTime announcedAt) {
        String body;
        try {
            body = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            // 인증키에 +/= 같은 문자가 들어갈 수 있고 이미 인코딩된 형태로 발급되기도 한다.
                            // build(false)로 재인코딩을 막아 발급받은 문자열이 그대로 전달되게 한다
                            // (재인코딩하면 %2B가 %252B가 되어 인증 실패한다).
                            .scheme("https").host("apis.data.go.kr")
                            .path("/1360000/LivingWthrIdxServiceV4/getUVIdxV4")
                            .queryParam("serviceKey", authKey)
                            .queryParam("areaNo", areaNo)
                            .queryParam("time", announcedAt.format(ANNOUNCED_AT))
                            .queryParam("dataType", "JSON")
                            .queryParam("numOfRows", 10)
                            .queryParam("pageNo", 1)
                            .build(false))
                    .retrieve()
                    .body(String.class);
        } catch (Exception e) {
            log.warn("기상청 자외선지수 호출 실패 areaNo={} time={}", areaNo, announcedAt, e);
            throw new BusinessException(ErrorCode.UV_FORECAST_FAILED); // E5011
        }

        return parseItem(body, areaNo);
    }

    /**
     * 기상청은 인증 실패·잘못된 지역코드도 HTTP 200에 담아 주기 때문에 상태코드만으로는 성공을 판단할 수 없다.
     * 본문의 {@code resultCode}까지 봐야 한다. 키가 잘못된 경우 JSON이 아니라 XML 오류 문서가 오기도 한다.
     */
    private JsonNode parseItem(String body, String areaNo) {
        JsonNode root;
        try {
            root = objectMapper.readTree(body);
        } catch (Exception e) {
            log.warn("기상청 응답을 JSON으로 읽지 못했습니다. 인증키가 잘못됐을 수 있습니다. body={}",
                    abbreviate(body), e);
            throw new BusinessException(ErrorCode.UV_FORECAST_FAILED); // E5011
        }

        String resultCode = root.path("response").path("header").path("resultCode").asString("");
        if (!RESULT_CODE_OK.equals(resultCode)) {
            log.warn("기상청 자외선지수 오류 resultCode={} resultMsg={} areaNo={}",
                    resultCode,
                    root.path("response").path("header").path("resultMsg").asString(""),
                    areaNo);
            throw new BusinessException(ErrorCode.UV_FORECAST_FAILED); // E5011
        }

        JsonNode item = root.path("response").path("body").path("items").path("item");
        // 지역코드가 유효해도 해당 발표에 자료가 없으면 빈 배열이 온다.
        JsonNode first = item.isArray() ? item.path(0) : item;
        if (first.isMissingNode() || first.isNull() || first.isEmpty()) {
            log.warn("기상청 자외선지수 응답에 항목이 없습니다. areaNo={}", areaNo);
            throw new BusinessException(ErrorCode.UV_FORECAST_FAILED); // E5011
        }
        return first;
    }

    /**
     * {@code h0}~{@code h75}(발표시각 기준 3시간 간격)를 요청 날짜의 00~22시 2시간 격자로 옮긴다.
     * 3시간 격자에 없는 시각(02, 04, 08, …)은 양옆 값으로 선형 보간한다.
     */
    private List<HourlyUv> toTwoHourGrid(JsonNode item, LocalDateTime announcedAt, LocalDate date) {
        // 절대 시각(0~23) → UV. 발표가 덮지 않는 시각은 비워 둔다.
        Integer[] byHour = new Integer[24];
        for (int offset = 0; offset <= 75; offset += 3) {
            JsonNode value = item.path("h" + offset);
            if (value.isMissingNode() || value.asString("").isBlank()) {
                continue;
            }
            LocalDateTime at = announcedAt.plusHours(offset);
            if (!at.toLocalDate().equals(date)) {
                continue; // 요청한 날짜 밖(전날 밤 / 다음날)은 버린다
            }
            byHour[at.getHour()] = value.asInt(0);
        }

        List<HourlyUv> hourly = new ArrayList<>(12);
        for (int hour = 0; hour < 24; hour += 2) {
            hourly.add(new HourlyUv("%02d".formatted(hour), uvAt(byHour, hour)));
        }
        return hourly;
    }

    /**
     * 값이 있으면 그대로, 없으면 <b>양옆의 가장 가까운 격자값 사이를 거리로 가중해</b> 보간한다.
     * <p>바로 옆 시각만 보면 안 된다 — 3시간 격자라 이웃이 1시간 옆에 없는 경우가 대부분이고,
     * 그러면 08시(06시=2와 09시=6 사이)가 6이 되어 곡선이 계단처럼 튄다.
     * <p><b>알려진 구간 밖으로는 외삽하지 않고 0을 쓴다.</b> 발표가 덮지 않는 건 새벽·심야뿐이고
     * 그 시간대 UV는 실제로 0이다 — 첫 값(06시)을 00시까지 끌어오면 한밤중에 UV가 있다고 답하게 된다.
     */
    private int uvAt(Integer[] byHour, int hour) {
        if (byHour[hour] != null) {
            return byHour[hour];
        }

        int beforeHour = -1;
        for (int h = hour - 1; h >= 0; h--) {
            if (byHour[h] != null) {
                beforeHour = h;
                break;
            }
        }
        int afterHour = -1;
        for (int h = hour + 1; h < 24; h++) {
            if (byHour[h] != null) {
                afterHour = h;
                break;
            }
        }

        if (beforeHour < 0 || afterHour < 0) {
            return 0;
        }
        double weight = (double) (hour - beforeHour) / (afterHour - beforeHour);
        return (int) Math.round(byHour[beforeHour] + weight * (byHour[afterHour] - byHour[beforeHour]));
    }

    private String abbreviate(String body) {
        if (body == null) {
            return "";
        }
        return body.length() <= 200 ? body : body.substring(0, 200) + "...";
    }
}
