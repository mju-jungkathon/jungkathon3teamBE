package jungkathon3team.aftergrow.recovery.external;

import jungkathon3team.aftergrow.recovery.entity.RecoveryActionType;
import jungkathon3team.aftergrow.running.entity.Intensity;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 규칙 기반 회복 가이드 생성기.
 * <p>{@link OpenAiRecoveryGuideAiClient}의 폴백으로 쓰인다 — {@code openai.api-key}가
 * 비어있거나 OpenAI 호출이 실패했을 때 강도·거리·UV·심박수를 규칙으로 조합해
 * 화면 7이 항상 뭔가는 보여줄 수 있게 한다. 직접 주입받고 싶다면 인터페이스가 아니라
 * 이 클래스를 명시적으로 의존해야 한다({@code @Primary}는 OpenAI 쪽에 있다).
 */
@Component
public class MockRecoveryGuideAiClient implements RecoveryGuideAiClient {

    private static final int DEFAULT_COOLDOWN_SEC = 300;
    private static final int EXTENDED_COOLDOWN_SEC = 420;
    private static final double HIGH_DISTANCE_KM_THRESHOLD = 5.0;
    private static final int HIGH_UV_THRESHOLD = 6;

    @Override
    public Guide generate(Context ctx) {
        boolean highLoad = ctx.intensity() == Intensity.HIGH
                || (ctx.distanceKm() != null && ctx.distanceKm() >= HIGH_DISTANCE_KM_THRESHOLD);
        boolean highUv = ctx.uvIndexAtStart() != null && ctx.uvIndexAtStart() >= HIGH_UV_THRESHOLD;

        List<ActionDraft> actions = new ArrayList<>();
        actions.add(new ActionDraft(RecoveryActionType.HYDRATION,
                "수분 보충", "500ml 물 또는 이온음료로 회복을 도와요"));
        actions.add(highLoad
                ? new ActionDraft(RecoveryActionType.COOLDOWN_STRETCH, "쿨다운 스트레칭", "종아리·햄스트링 위주 5분")
                : new ActionDraft(RecoveryActionType.COOLDOWN_STRETCH, "가벼운 스트레칭", "종아리·발목 위주 3분"));
        if (highUv) {
            actions.add(new ActionDraft(RecoveryActionType.UV_CAUTION,
                    "자외선 진정 케어", "직사광선을 피해 그늘에서 휴식하고 노출 부위를 시원하게 식혀주세요"));
        }

        return new Guide(
                buildSummary(ctx, highLoad, highUv),
                actions,
                highLoad ? EXTENDED_COOLDOWN_SEC : DEFAULT_COOLDOWN_SEC
        );
    }

    private String buildSummary(Context ctx, boolean highLoad, boolean highUv) {
        StringBuilder sb = new StringBuilder("오늘 ");
        sb.append(highLoad ? "강도 높은 " : "가벼운 ");
        if (ctx.distanceKm() != null) {
            sb.append(String.format("%.1fkm ", ctx.distanceKm()));
        }
        sb.append("러닝");
        if (highUv) {
            sb.append("에 UV 지수 ").append(ctx.uvIndexAtStart()).append("까지 겹쳤어요");
        } else {
            sb.append("을 완주했어요");
        }
        sb.append(". ");
        sb.append(highLoad
                ? "수분 보충과 스트레칭으로 마무리하는 걸 추천해요."
                : "가볍게 몸을 풀어주며 마무리해보세요.");
        return sb.toString();
    }
}
