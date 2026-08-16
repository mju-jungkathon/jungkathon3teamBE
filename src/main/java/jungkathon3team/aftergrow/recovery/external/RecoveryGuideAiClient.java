package jungkathon3team.aftergrow.recovery.external;

import jungkathon3team.aftergrow.recovery.entity.RecoveryActionType;
import jungkathon3team.aftergrow.running.entity.Intensity;

import java.util.List;

/**
 * R5.1 회복 가이드 생성을 담당하는 외부(AI) 클라이언트 경계.
 * <p>{@link jungkathon3team.aftergrow.running.external.UvIndexClient}와 같은 패턴 —
 * 서비스 계층은 이 인터페이스만 알고, 실제 LLM 연동은 구현체를 갈아끼우는 걸로 끝난다.
 * 지금은 {@link MockRecoveryGuideAiClient}(규칙 기반)만 있고, 실제 LLM(OpenAI/Claude 등) 연동 시
 * 이 인터페이스를 구현하는 새 @Component를 추가하고 Mock의 @Component를 지우면 된다.
 */
public interface RecoveryGuideAiClient {

    Guide generate(Context context);

    /**
     * 가이드 생성에 필요한 입력값. 세션 종료 시점의 운동 데이터 + 측정된 심박수.
     * <p>스트레스 지수는 아직 계산되지 않아(HRV 미구현, {@code stressStatus=PENDING_HRV_CALCULATION})
     * 여기에 없다. 계산되면 필드를 추가하고 프롬프트에 한 줄 얹으면 된다.
     */
    record Context(Intensity intensity, Double distanceKm, Integer durationSec,
                   Integer uvIndexAtStart, Integer measuredBpm) {}

    /** 생성 결과. actions는 저장 전 초안(draft)이라 엔티티가 아니다. */
    record Guide(String summaryMessage, List<ActionDraft> actions, int cooldownTimerSec) {}

    record ActionDraft(RecoveryActionType type, String title, String description) {}
}
