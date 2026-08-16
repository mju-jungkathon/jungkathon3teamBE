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
            너는 러닝 앱의 피부 회복 코치야. UVB는 표피를 손상시켜 색소침착을,
            UVA는 진피의 콜라겐을 파괴해 광노화를 일으키고, 심박수가 오르면 말초혈류가
            늘어 피부 온도가 올라 홍조·열감이 생기고, 땀을 많이 흘릴수록 피지막이 씻겨나가
            피부 장벽이 자극에 약해진다는 걸 근거로 판단해.

            사용자 메시지의 운동 데이터(강도·거리·시간·UV 노출량 등급·심박수)를 보고
            다음 순서로 판단해:
            1. UV 노출량 등급과 운동 강도를 함께 고려해 종합 피부 위험도를 정해.
            2. 위험도가 높을수록 액션 개수(3~5개)와 description의 구체성(순서·시점)을 늘려.
            3. 아래 6개 type 중에서만 골라 actions를 구성해:
               HYDRATION(수분 보충), COOLDOWN(심박을 진정시킨 뒤 스킨케어 착수),
               CLEANSING(세안), SOOTHING(홍조·열감 진정), UV_CARE(자외선 손상 케어),
               MOISTURIZING(보습)
               - HYDRATION, CLEANSING, MOISTURIZING은 항상 포함해.
               - UV 노출량 등급이 '높음' 이상이면 UV_CARE를 반드시 포함해.
               - 운동 강도가 높거나 장거리를 뛰었으면 COOLDOWN을 반드시 포함해.
               - 강도는 높은데 UV 노출은 낮으면 SOOTHING을 포함해 열감을 따로 다뤄.
                 UV_CARE에 이미 진정 내용을 담았다면 SOOTHING은 생략해도 돼.
            4. description은 왜 필요한지와 구체적 방법·시점(예: "귀가 후 10분 이내")을
               1~2문장으로 적어.
            5. 의약품 추천, 질환명 언급, 진단은 하지 말고, 근거 없는 과장(예: "피부암 위험")도
               하지 마. 특정 브랜드명도 쓰지 마.
            6. cooldownTimerSec은 120~600 사이 정수로, 강도가 높거나 거리가 길수록 크게 잡아.
            반드시 지정된 JSON 스키마 형식으로만 응답하고 다른 설명은 붙이지 마.
            """;

    private static final Map<String, Object> RESPONSE_FORMAT = buildResponseFormat();
    private static final List<Map<String, Object>> FEW_SHOT_MESSAGES = buildFewShotMessages();

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
        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", SYSTEM_PROMPT));
        messages.addAll(FEW_SHOT_MESSAGES);
        messages.add(Map.of("role", "user", "content", userPrompt(ctx)));

        Map<String, Object> requestBody = Map.of(
                "model", model,
                "messages", messages,
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
                운동 시간: %s 분
                시작 시점 UV 지수: %s
                UV 노출량 등급(지수×시간 기준): %s
                측정된 평균 심박수: %s bpm
                """.formatted(
                ctx.intensity() != null ? ctx.intensity() : "알수없음",
                ctx.distanceKm() != null ? ctx.distanceKm() : "알수없음",
                ctx.durationSec() != null ? ctx.durationSec() / 60 : "알수없음",
                ctx.uvIndexAtStart() != null ? ctx.uvIndexAtStart() : "알수없음",
                RecoveryGuideAiClient.uvDoseTier(ctx.uvIndexAtStart(), ctx.durationSec()).label(),
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
                                "enum", List.of("HYDRATION", "COOLDOWN", "CLEANSING", "SOOTHING", "UV_CARE", "MOISTURIZING")),
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

    /**
     * few-shot 예시 3쌍. system 지시만으로는 모델이 문체·구체성을 알아서 정해 편차가 컸는데,
     * 원하는 톤·분량의 예시를 보여주면 출력이 그쪽으로 수렴한다. 세 축을 모두 보여준다:
     * 고UV+고강도(UV_CARE), 저UV+저강도(기본 3종), 고강도+저UV(SOOTHING) — SOOTHING은
     * 예시가 없으면 모델이 거의 안 골라서 따로 넣었다.
     * <p>user/assistant 메시지 쌍이라 {@link #userPrompt}와 같은 포맷을 써야 하고,
     * assistant 쪽은 {@link #RESPONSE_FORMAT} 스키마를 그대로 만족해야 한다(강제 검증은 안 되므로 직접 맞춘다).
     */
    private static List<Map<String, Object>> buildFewShotMessages() {
        String highUvUser = """
                강도: HIGH
                거리: 8.0 km
                운동 시간: 55 분
                시작 시점 UV 지수: 9
                UV 노출량 등급(지수×시간 기준): 매우 높음
                측정된 평균 심박수: 168 bpm
                """;
        String highUvAssistant = """
                {"summaryMessage":"높은 강도로 8km를 55분간 달려 자외선 노출과 열감이 모두 컸어요. 진정 케어와 수분 보충을 서둘러 챙기세요.",\
                "cooldownTimerSec":540,"actions":[\
                {"type":"HYDRATION","title":"수분 보충","description":"물 600ml 이상을 15분 내로 나눠 마셔 전해질 균형을 맞춰주세요"},\
                {"type":"COOLDOWN","title":"심박 안정화","description":"그늘에서 5~10분간 걸으며 심박수를 낮춘 뒤 스킨케어를 시작하세요"},\
                {"type":"CLEANSING","title":"세안","description":"귀가 후 15분 이내 약산성 클렌저로 땀과 피지를 씻어내세요"},\
                {"type":"UV_CARE","title":"자외선 진정 케어","description":"시원한 물로 홍반을 가라앉힌 뒤 자외선 차단제를 다시 발라주세요"},\
                {"type":"MOISTURIZING","title":"보습","description":"세안 직후 무자극 수분크림으로 마무리해 손상된 장벽을 보호하세요"}]}\
                """;

        String lowLoadUser = """
                강도: LOW
                거리: 2.0 km
                운동 시간: 15 분
                시작 시점 UV 지수: 2
                UV 노출량 등급(지수×시간 기준): 낮음
                측정된 평균 심박수: 110 bpm
                """;
        String lowLoadAssistant = """
                {"summaryMessage":"가볍게 2km를 15분간 달렸어요. 부담 없이 기본 케어만 챙기면 충분해요.",\
                "cooldownTimerSec":180,"actions":[\
                {"type":"HYDRATION","title":"수분 보충","description":"물 250~300ml 정도를 천천히 나눠 마셔주세요"},\
                {"type":"CLEANSING","title":"세안","description":"미온수로 가볍게 세안해주세요"},\
                {"type":"MOISTURIZING","title":"보습","description":"세안 후 수분 크림으로 마무리해주세요"}]}\
                """;

        String highLoadLowUvUser = """
                강도: HIGH
                거리: 7.0 km
                운동 시간: 50 분
                시작 시점 UV 지수: 2
                UV 노출량 등급(지수×시간 기준): 낮음
                측정된 평균 심박수: 172 bpm
                """;
        String highLoadLowUvAssistant = """
                {"summaryMessage":"흐린 날씨에 7km를 50분간 고강도로 달렸어요. 자외선보다는 열감과 땀 관리가 필요해요.",\
                "cooldownTimerSec":480,"actions":[\
                {"type":"HYDRATION","title":"수분 보충","description":"물 500ml 이상을 20분 내로 나눠 마셔주세요"},\
                {"type":"COOLDOWN","title":"심박 안정화","description":"실내에서 5~10분간 가볍게 걸으며 심박수를 낮춘 뒤 스킨케어를 시작하세요"},\
                {"type":"CLEANSING","title":"세안","description":"귀가 후 30분 이내 약산성 클렌저로 땀과 피지를 씻어내세요"},\
                {"type":"SOOTHING","title":"열감 진정","description":"붉어진 얼굴에 차가운 수건을 5분간 올려 열을 가라앉혀 주세요"},\
                {"type":"MOISTURIZING","title":"보습","description":"세안 직후 수분 크림으로 마무리해 피부 장벽을 보호하세요"}]}\
                """;

        return List.of(
                Map.of("role", "user", "content", highUvUser),
                Map.of("role", "assistant", "content", highUvAssistant),
                Map.of("role", "user", "content", lowLoadUser),
                Map.of("role", "assistant", "content", lowLoadAssistant),
                Map.of("role", "user", "content", highLoadLowUvUser),
                Map.of("role", "assistant", "content", highLoadLowUvAssistant)
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