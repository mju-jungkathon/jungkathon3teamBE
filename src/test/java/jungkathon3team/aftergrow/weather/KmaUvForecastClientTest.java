package jungkathon3team.aftergrow.weather;

import jungkathon3team.aftergrow.common.exception.BusinessException;
import jungkathon3team.aftergrow.weather.external.KmaUvForecastClient;
import jungkathon3team.aftergrow.weather.external.UvForecastClient;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.RequestMatcher;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.anything;

/**
 * 기상청 응답 → 2시간 격자 변환 단위 테스트.
 * <p>통합 테스트는 서비스키가 없어 MockUvForecastClient만 태우므로 이 변환 로직이 검증되지 않는다.
 * 여기서는 실제 네트워크 없이 응답 본문만 흉내 내 <b>보간과 오류 처리</b>를 고정한다.
 * <p><b>날짜를 하드코딩하지 않고 매번 "오늘"로 계산한다.</b> 발표시각 선택({@code latestAnnouncement()})이
 * 더 이상 요청 날짜가 아니라 실제 시계(now) 기준이라, 과거로 고정된 날짜를 쓰면 URI의 {@code time=} 값을
 * 예측할 수 없다. 자정~06시 사이에 테스트가 실행되면(전날 18시 발표로 분기) 아래 고정 fixture 값과 어긋날 수
 * 있지만 — 실제 개발·CI 환경에서 거의 발생하지 않는 경계라 감수한다.
 */
class KmaUvForecastClientTest {

    private static final LocalDate DATE = LocalDate.now();
    private static final LocalDateTime ANNOUNCED_AT = DATE.atTime(6, 0);
    private static final String ANNOUNCED_AT_PARAM = ANNOUNCED_AT.format(DateTimeFormatter.ofPattern("yyyyMMddHH"));

    /** 06시 발표 응답. h0=06시, h3=09시, h6=12시 … 3시간 간격이다. */
    private static final String MORNING_RESPONSE = """
            {"response":{"header":{"resultCode":"00","resultMsg":"NORMAL_SERVICE"},
             "body":{"items":{"item":[{"areaNo":"1100000000","date":"2026081606",
             "h0":"2","h3":"6","h6":"9","h9":"5","h12":"1","h15":"0","h18":"0",
             "h21":"0","h24":"2","h27":"6"}]}}}}
            """;

    private UvForecastClient clientReturning(String body) {
        return clientReturning(body, "test-key", anything());
    }

    private UvForecastClient clientReturning(String body, String authKey, RequestMatcher matcher) {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer.bindTo(builder).build()
                .expect(matcher)
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
        return new KmaUvForecastClient(builder, new ObjectMapper(), authKey);
    }

    /**
     * 인증키가 이중 인코딩되면 게이트웨이가 SERVICE_KEY_IS_NOT_REGISTERED_ERROR(403)를 준다.
     * uri(uriBuilder -> ...) 람다는 {@code build(false)}가 "인코딩 안 함"으로 동작하지 않아
     * 조용히 %2B를 %252B로 만든다 — 실제로 한 번 당했으므로 URI를 여기서 고정한다.
     */
    @Test
    void 인증키를_이중_인코딩하지_않는다() {
        // 인코딩 키(%2B 포함)를 넣어도 전송되는 건 %2B여야 한다(%252B가 아니라).
        UvForecastClient client = clientReturning(MORNING_RESPONSE, "abc%2Bdef%3D",
                request -> {
                    String uri = request.getURI().toString();
                    assertThat(uri).contains("serviceKey=abc%2Bdef%3D");
                    assertThat(uri).doesNotContain("%252B");
                });

        client.fetchDailyForecast("1100000000", DATE);
    }

    /** 디코딩 키(+, = 원문)를 넣어도 전송 시엔 인코딩돼야 한다. */
    @Test
    void 디코딩_키를_넣어도_인코딩해서_보낸다() {
        UvForecastClient client = clientReturning(MORNING_RESPONSE, "abc+def=",
                request -> {
                    String uri = request.getURI().toString();
                    assertThat(uri).contains("serviceKey=abc%2Bdef%3D");
                });

        client.fetchDailyForecast("1100000000", DATE);
    }

    @Test
    void 요청_URI는_V5_엔드포인트를_가리킨다() {
        UvForecastClient client = clientReturning(MORNING_RESPONSE, "k",
                request -> assertThat(request.getURI().toString())
                        .startsWith("https://apis.data.go.kr/1360000/LivingWthrIdxServiceV5/getUVIdxV5")
                        .contains("areaNo=1100000000")
                        .contains("time=" + ANNOUNCED_AT_PARAM));

        client.fetchDailyForecast("1100000000", DATE);
    }

    private Map<String, Integer> asMap(List<UvForecastClient.HourlyUv> hourly) {
        return hourly.stream().collect(Collectors.toMap(
                UvForecastClient.HourlyUv::hour, UvForecastClient.HourlyUv::uv));
    }

    @Test
    void 응답을_00시부터_2시간_간격_12개로_변환한다() {
        List<UvForecastClient.HourlyUv> hourly =
                clientReturning(MORNING_RESPONSE).fetchDailyForecast("1100000000", DATE);

        assertThat(hourly).hasSize(12);
        assertThat(hourly).extracting(UvForecastClient.HourlyUv::hour)
                .containsExactly("00", "02", "04", "06", "08", "10",
                        "12", "14", "16", "18", "20", "22");
    }

    @Test
    void 발표시각과_겹치는_시간대는_기상청_값을_그대로_쓴다() {
        Map<String, Integer> uv = asMap(clientReturning(MORNING_RESPONSE)
                .fetchDailyForecast("1100000000", DATE));

        assertThat(uv.get("06")).isEqualTo(2);  // h0
        assertThat(uv.get("12")).isEqualTo(9);  // h6
        assertThat(uv.get("18")).isEqualTo(1);  // h12
    }

    /**
     * 3시간 격자에 없는 짝수 시각(08, 10 …)은 양옆 격자값 사이를 거리로 가중해 보간한다.
     * 바로 옆 시각만 보면 08시가 09시 값을 그대로 받아 곡선이 계단처럼 튄다.
     */
    @Test
    void 격자에_없는_시간대는_양옆_격자값_사이를_선형_보간한다() {
        Map<String, Integer> uv = asMap(clientReturning(MORNING_RESPONSE)
                .fetchDailyForecast("1100000000", DATE));

        // 06시=2, 09시=6 → 08시는 2/3 지점: 2 + 2/3*4 = 4.67 → 5
        assertThat(uv.get("08")).isEqualTo(5);
        // 09시=6, 12시=9 → 10시는 1/3 지점: 6 + 1/3*3 = 7
        assertThat(uv.get("10")).isEqualTo(7);
        // 12시=9, 15시=5 → 14시는 2/3 지점: 9 - 2/3*4 = 6.33 → 6
        assertThat(uv.get("14")).isEqualTo(6);
    }

    /**
     * 발표(06시) 이전 새벽은 응답에 아예 없다. 첫 값을 끌어오는 외삽을 하면 한밤중에 UV가 있다고
     * 답하게 되므로, 알려진 구간 밖은 0으로 둔다.
     */
    @Test
    void 알려진_구간_밖은_외삽하지_않고_0으로_둔다() {
        Map<String, Integer> uv = asMap(clientReturning(MORNING_RESPONSE)
                .fetchDailyForecast("1100000000", DATE));

        // 06시(=2) 이전 — 2가 새어 나오면 안 된다
        assertThat(uv.get("00")).isZero();
        assertThat(uv.get("02")).isZero();
        assertThat(uv.get("04")).isZero();
    }

    /** 다음 날 값(h24 이후)이 오늘 배열에 섞이면 안 된다. */
    @Test
    void 요청한_날짜_밖의_값은_버린다() {
        Map<String, Integer> uv = asMap(clientReturning(MORNING_RESPONSE)
                .fetchDailyForecast("1100000000", DATE));

        // h24(다음날 06시)=2, h27(다음날 09시)=6 이 오늘 22시로 새지 않아야 한다
        assertThat(uv.get("22")).isZero();
    }

    /** 기상청은 인증 실패도 HTTP 200에 담아 주므로 resultCode를 봐야 한다. */
    @Test
    void resultCode가_00이_아니면_E5011() {
        UvForecastClient client = clientReturning("""
                {"response":{"header":{"resultCode":"30","resultMsg":"SERVICE_KEY_IS_NOT_REGISTERED_ERROR"}}}
                """);

        assertThatThrownBy(() -> client.fetchDailyForecast("1100000000", DATE))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("자외선 예보");
    }

    /** 서비스키가 잘못되면 JSON이 아니라 XML 오류 문서가 오기도 한다. */
    @Test
    void JSON이_아닌_응답이면_E5011() {
        UvForecastClient client = clientReturning("<OpenAPI_ServiceResponse><cmmMsgHeader/></OpenAPI_ServiceResponse>");

        assertThatThrownBy(() -> client.fetchDailyForecast("1100000000", DATE))
                .isInstanceOf(BusinessException.class);
    }

    /**
     * 게이트웨이 거절은 정상 응답과 봉투가 다르다. 이걸 따로 보지 않으면 로그에 빈 값만 찍혀
     * 활용신청 문제인지 경로 문제인지 알 수 없다.
     */
    @Test
    void 게이트웨이_거절_봉투도_E5011로_처리한다() {
        UvForecastClient client = clientReturning("""
                {"OpenAPI_ServiceResponse":{"cmmMsgHeader":{
                  "errMsg":"NO_OPENAPI_SERVICE_ERROR",
                  "returnAuthMsg":"해당 오픈API 서비스가 없거나 폐기됨",
                  "returnReasonCode":"12"}}}
                """);

        assertThatThrownBy(() -> client.fetchDailyForecast("1100000000", DATE))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void 항목이_비어_있으면_E5011() {
        UvForecastClient client = clientReturning("""
                {"response":{"header":{"resultCode":"00","resultMsg":"NORMAL_SERVICE"},
                 "body":{"items":{"item":[]}}}}
                """);

        assertThatThrownBy(() -> client.fetchDailyForecast("1100000000", DATE))
                .isInstanceOf(BusinessException.class);
    }
}
