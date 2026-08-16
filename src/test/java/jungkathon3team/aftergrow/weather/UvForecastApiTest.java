package jungkathon3team.aftergrow.weather;

import jungkathon3team.aftergrow.auth.entity.User;
import jungkathon3team.aftergrow.auth.jwt.JwtTokenProvider;
import jungkathon3team.aftergrow.auth.repository.UserRepository;
import jungkathon3team.aftergrow.weather.external.AreaCodeResolver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * GET /weather/uv-forecast 통합 테스트.
 * <p>CI에는 {@code kma.auth-key}가 없으므로 MockUvForecastClient가 응답한다 —
 * 값 자체가 아니라 <b>응답 형태와 캐시 동작</b>을 고정하는 테스트다.
 * 기상청 실연동 검증은 키를 넣은 로컬에서 수동으로 한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
// 로컬 개발자가 application-local.yml에 실제 키를 넣어 두면 이 테스트가 진짜 기상청을 호출하게 된다.
// 그러면 네트워크·발급 상태에 따라 결과가 달라져 테스트를 믿을 수 없으므로 여기서 강제로 비운다.
@TestPropertySource(properties = "kma.auth-key=")
class UvForecastApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private AreaCodeResolver areaCodeResolver;

    @Autowired
    private StringRedisTemplate redisTemplate;

    private String bearer;

    private static final double SEOUL_LAT = 37.5665;
    private static final double SEOUL_LNG = 126.9780;

    @BeforeEach
    void setUp() {
        User user = userRepository.save(User.builder()
                .email("uv-" + UUID.randomUUID() + "@example.com")
                .passwordHash("hashed")
                .nickname("김러너")
                .build());
        bearer = "Bearer " + jwtTokenProvider.createAccessToken(user.getUserId());
        clearCache();
    }

    /** Redis는 @Transactional로 롤백되지 않으므로 직접 지운다. */
    @AfterEach
    void tearDown() {
        clearCache();
    }

    private void clearCache() {
        redisTemplate.delete("uv:forecast:" + areaCodeResolver.resolve(SEOUL_LAT, SEOUL_LNG)
                + ":" + LocalDate.now());
    }

    @Test
    void UV_예보는_00시부터_2시간_간격_12개를_반환한다() throws Exception {
        mockMvc.perform(get("/weather/uv-forecast")
                        .header("Authorization", bearer)
                        .param("lat", String.valueOf(SEOUL_LAT))
                        .param("lng", String.valueOf(SEOUL_LNG)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.hourly.length()").value(12))
                .andExpect(jsonPath("$.data.hourly[0].hour").value("00"))
                .andExpect(jsonPath("$.data.hourly[11].hour").value("22"))
                .andExpect(jsonPath("$.data.hourly[0].uv").isNumber());
    }

    @Test
    void 조회하면_격자가_아니라_행정구역코드_기준으로_캐시에_저장된다() throws Exception {
        String cacheKey = "uv:forecast:" + areaCodeResolver.resolve(SEOUL_LAT, SEOUL_LNG) + ":" + LocalDate.now();
        assertThat(redisTemplate.hasKey(cacheKey)).isFalse();

        mockMvc.perform(get("/weather/uv-forecast")
                        .header("Authorization", bearer)
                        .param("lat", String.valueOf(SEOUL_LAT))
                        .param("lng", String.valueOf(SEOUL_LNG)))
                .andExpect(status().isOk());

        assertThat(redisTemplate.hasKey(cacheKey)).isTrue();
    }

    /** 캐시 키에 사용자 정보가 들어가지 않아, 가까운 사용자끼리 캐시를 공유해야 한다. */
    @Test
    void 같은_구_안의_다른_좌표는_같은_캐시를_쓴다() {
        String cityHall = areaCodeResolver.resolve(SEOUL_LAT, SEOUL_LNG);
        String nearby = areaCodeResolver.resolve(SEOUL_LAT + 0.002, SEOUL_LNG + 0.002);
        assertThat(nearby).isEqualTo(cityHall);
    }

    @Test
    void 먼_지역은_다른_행정구역코드로_떨어진다() {
        assertThat(areaCodeResolver.resolve(33.4996, 126.5312))   // 제주
                .isNotEqualTo(areaCodeResolver.resolve(SEOUL_LAT, SEOUL_LNG));
    }

    /**
     * 강원·전북은 특별자치도 전환으로 <b>시도 단위 코드가 기상청 구역 파일에 없다.</b>
     * 추측한 코드(5100000000 등)를 쓰면 조회가 실패하므로, 시군구 코드가 나오는지 고정한다.
     * 아래 값은 실제 API 호출로 NORMAL_SERVICE를 확인한 코드다.
     */
    @Test
    void 강원과_전북도_실재하는_시군구_코드로_해석된다() {
        assertThat(areaCodeResolver.resolve(37.8813, 127.7300)).isEqualTo("5111000000");   // 춘천시
        assertThat(areaCodeResolver.resolve(35.8091, 127.1480)).isEqualTo("5211300000");   // 전주시 덕진구
    }

    /** 표가 비어 있거나 파싱이 깨지면 전부 같은 코드로 몰릴 수 있어, 대표 지점 몇 개를 고정한다. */
    @Test
    void 대표_좌표가_기대한_행정구역코드로_해석된다() {
        assertThat(areaCodeResolver.resolve(37.4979, 127.0276)).isEqualTo("1165000000");   // 서울 서초구
        assertThat(areaCodeResolver.resolve(35.1796, 129.0756)).startsWith("26");          // 부산
        assertThat(areaCodeResolver.resolve(33.4996, 126.5312)).startsWith("50");          // 제주
    }

    @Test
    void 좌표가_없으면_400과_E4001을_반환한다() throws Exception {
        mockMvc.perform(get("/weather/uv-forecast").header("Authorization", bearer))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("E4001"));
    }

    @Test
    void 좌표_범위를_벗어나면_400과_E4001을_반환한다() throws Exception {
        mockMvc.perform(get("/weather/uv-forecast")
                        .header("Authorization", bearer)
                        .param("lat", "999")
                        .param("lng", "127.0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("E4001"));
    }

    @Test
    void 인증_없이는_401을_반환한다() throws Exception {
        mockMvc.perform(get("/weather/uv-forecast")
                        .param("lat", String.valueOf(SEOUL_LAT))
                        .param("lng", String.valueOf(SEOUL_LNG)))
                .andExpect(status().isUnauthorized());
    }
}
