package jungkathon3team.aftergrow.weather.external;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

/**
 * UV 예보 공급자를 서비스키 설정 여부로 고른다.
 *
 * <p>조건 애노테이션(@ConditionalOnProperty / @ConditionalOnMissingBean)을 쓰지 않은 이유:
 * 전자는 <b>빈 문자열도 "설정됨"으로 취급</b>하고(기본값 {@code ${KMA_SERVICE_KEY:}}가 정확히 그 경우다),
 * 후자는 컴포넌트 스캔 순서에 따라 결과가 달라진다. 여기서 명시적으로 고르는 편이 확실하다.
 */
@Slf4j
@Configuration
public class UvForecastClientConfig {

    @Bean
    public UvForecastClient uvForecastClient(RestClient.Builder builder,
                                             ObjectMapper objectMapper,
                                             @Value("${kma.service-key:}") String serviceKey) {
        if (serviceKey == null || serviceKey.isBlank()) {
            log.warn("kma.service-key가 설정되지 않아 UV 예보를 모의값으로 응답합니다. "
                    + "실제 데이터가 필요하면 application-local.yml(로컬) 또는 .env(배포)에 키를 넣으세요.");
            return new MockUvForecastClient();
        }
        return new KmaUvForecastClient(builder, objectMapper, serviceKey);
    }
}
