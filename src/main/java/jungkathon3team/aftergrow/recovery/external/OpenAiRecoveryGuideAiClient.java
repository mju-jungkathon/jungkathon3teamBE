package jungkathon3team.aftergrow.recovery.external;

import jungkathon3team.aftergrow.recovery.entity.RecoveryActionType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * R5.1 실제 LLM 연동. OpenAI Chat Completions(response_format=json_schema)로
 * summaryMessage/actions/cooldownTimerSec을 구조화된 JSON으로 받는다.
 * <p>{@code openai.api-key}가 비어있거나 호출이 실패하면(네트워크 오류, 타임아웃, 5xx,
 * 파싱 실패 등) {@link MockRecoveryGuideAiClient}로 조용히 폴백한다 —
 * AI 응답 하나 때문에 화면 7 진입 자체가 막히면 안 되기 때문.
 * <p>{@link RecoveryGuideAiClient} 인터페이스만 구현하면 되므로, 다른 프로바이더로 바꾸고
 * 싶으면 이 클래스만 교체하면 된다 (서비스/컨트롤러는 인터페이스만 안다).
 */
@Slf4j
@Component
@Primary
public class OpenAiRecoveryGuideAiClient implements RecoveryGuideAiClient {

    private static final String CHAT_COMPLETIONS_PATH = "/chat/completions";
    private static final int MIN_COOLDOWN_SEC = 60;
    private static final int MAX_COOLDOWN_SEC = 900;
    private static final int DEFAULT_COOLDOWN_SEC = 300;

    private static final String SYSTEM_PROMPT = """
            너는 러닝 앱의 회복 코치야. 사용자가 방금 마친 러닝 데이터를 보고
            1~2문장의 한국어 요약 메시지와, 회복에 도움이 되는 액션 2~3개를 제안해.
            actions의 type은 HYDRATION, COOLDOWN_STRETCH, UV_CAUTION 중에서만 골라.
            UV_CAUTION은 UV 지수가 높을 때만 포함하고, 그렇지 않으면 빼.
            cooldownTimerSec은 120~600 사이 정수로, 강도가 높거나 거리가 길수록 크게 잡아.
            반드시 지정된 JSON 스키마 형식으로만 응답하고 다른 설명은 붙이지 마.
            """;

    private static final Map<String, Object> RESPONSE_FORMAT = buildResponseFormat();

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final MockRecoveryGuideAiClient fallback;
    private final String apiKey;
    private final String model;

    public OpenAiRecoveryGuideAiClient(RestClient.Builder restClientBuilder,
                                       ObjectMapper objectMapper,
                                       MockRecoveryGuideAiClient fallback,
                                       @Value("${openai.api-key:}") String apiKey,
                                       @Value("${openai.model:gpt-4o-mini}") String model,
                                       @Value("${openai.timeout-ms:8000}") int timeoutMs) {
        this.objectMapper = objectMapper;
        this.fallback = fallback;
        this.apiKey = apiKey;
        this.model = model;

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(timeoutMs);
        requestFactory.setReadTimeout(timeoutMs);

        this.restClient = restClientBuilder
                .baseUrl("https://api.openai.com/v1")
                .requestFactory(requestFactory)
                .build();
    }

    @Override
    public Guide generate(Context ctx) {
        if (apiKey == null || apiKey.isBlank()) {
            log.debug("openai.api-key가 설정되지 않아 규칙 기반 가이드로 대체합니다.");
            return fallback.generate(ctx);
        }
        try {
            return toGuide(requestGuide(ctx));
        } catch (Exception e) {
            log.warn("OpenAI 회복 가이드 생성 실패, 규칙 기반으로 대체합니다: {}", e.toString());
            return fallback.generate(ctx);
        }
    }

    private AiGuidePayload requestGuide(Context ctx) {
        Map<String, Object> requestBody = Map.of(
                "model", model,
                "messages", List.of(
                        Map.of("role", "system", "content", SYSTEM_PROMPT),
                        Map.of("role", "user", "content", userPrompt(ctx))
                ),
                "response_format", RESPONSE_FORMAT,
                "temperature", 0.7
        );

        OpenAiChatResponse response = restClient.post()
                .uri(CHAT_COMPLETIONS_PATH)
                .header("Authorization", "Bearer " + apiKey)
                .body(requestBody)
                .retrieve()
                .body(OpenAiChatResponse.class);

        if (response == null || response.choices() == null || response.choices().isEmpty()) {
            throw new IllegalStateException("OpenAI 응답이 비어 있습니다.");
        }
        String content = response.choices().get(0).message().content();
        return objectMapper.readValue(content, AiGuidePayload.class);
    }

    private String userPrompt(Context ctx) {
        return """
                강도: %s
                거리: %s km
                시작 시점 UV 지수: %s
                측정된 평균 심박수: %s bpm
                """.formatted(
                ctx.intensity() != null ? ctx.intensity() : "알수없음",
                ctx.distanceKm() != null ? ctx.distanceKm() : "알수없음",
                ctx.uvIndexAtStart() != null ? ctx.uvIndexAtStart() : "알수없음",
                ctx.measuredBpm() != null ? ctx.measuredBpm() : "측정안됨"
        );
    }

    /** OpenAI가 스키마대로 응답해도 값(빈 배열, 범위 밖 숫자 등)은 방어적으로 다시 검증한다. */
    private Guide toGuide(AiGuidePayload payload) {
        List<ActionDraft> actions = new ArrayList<>();
        if (payload.actions() != null) {
            for (AiActionPayload a : payload.actions()) {
                RecoveryActionType type = parseType(a.type());
                if (type == null || isBlank(a.title()) || isBlank(a.description())) {
                    continue; // 형식이 이상한 항목은 건너뛴다
                }
                actions.add(new ActionDraft(type, a.title(), a.description()));
            }
        }
        if (actions.isEmpty() || isBlank(payload.summaryMessage())) {
            throw new IllegalStateException("OpenAI 응답에 유효한 내용이 없습니다.");
        }

        int cooldownSec = payload.cooldownTimerSec() != null
                ? Math.min(Math.max(payload.cooldownTimerSec(), MIN_COOLDOWN_SEC), MAX_COOLDOWN_SEC)
                : DEFAULT_COOLDOWN_SEC;

        return new Guide(payload.summaryMessage(), actions, cooldownSec);
    }

    private RecoveryActionType parseType(String raw) {
        if (raw == null) {
            return null;
        }
        try {
            return RecoveryActionType.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static Map<String, Object> buildResponseFormat() {
        Map<String, Object> actionItemSchema = mapOf(
                "type", "object",
                "properties", mapOf(
                        "type", mapOf("type", "string",
                                "enum", List.of("HYDRATION", "COOLDOWN_STRETCH", "UV_CAUTION")),
                        "title", mapOf("type", "string"),
                        "description", mapOf("type", "string")
                ),
                "required", List.of("type", "title", "description"),
                "additionalProperties", false
        );

        Map<String, Object> guideSchema = mapOf(
                "type", "object",
                "properties", mapOf(
                        "summaryMessage", mapOf("type", "string"),
                        "cooldownTimerSec", mapOf("type", "integer"),
                        "actions", mapOf("type", "array", "items", actionItemSchema)
                ),
                "required", List.of("summaryMessage", "cooldownTimerSec", "actions"),
                "additionalProperties", false
        );

        return mapOf(
                "type", "json_schema",
                "json_schema", mapOf(
                        "name", "recovery_guide",
                        "strict", true,
                        "schema", guideSchema
                )
        );
    }

    private static Map<String, Object> mapOf(Object... kv) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            map.put((String) kv[i], kv[i + 1]);
        }
        return map;
    }

    /** OpenAI Chat Completions 응답 봉투. 필요한 필드만 골라 받는다. */
    private record OpenAiChatResponse(List<Choice> choices) {
        private record Choice(Message message) {}

        private record Message(String content) {}
    }

    /** SYSTEM_PROMPT + RESPONSE_FORMAT이 강제하는 실제 페이로드 형태. */
    private record AiGuidePayload(String summaryMessage, Integer cooldownTimerSec, List<AiActionPayload> actions) {}

    private record AiActionPayload(String type, String title, String description) {}
}