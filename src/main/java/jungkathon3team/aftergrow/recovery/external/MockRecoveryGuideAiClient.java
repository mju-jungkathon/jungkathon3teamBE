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
 * <p>강도×UV 노출량 조합 규칙은 {@code docs/피부회복가이드_프롬프트.md}의 위험도 표와 같다.
 */
@Component
public class MockRecoveryGuideAiClient implements RecoveryGuideAiClient {

    private static final int DEFAULT_COOLDOWN_SEC = 300;
    private static final int EXTENDED_COOLDOWN_SEC = 420;
    private static final double HIGH_DISTANCE_KM_THRESHOLD = 5.0;

    @Override
    public Guide generate(Context ctx) {
        boolean highLoad = ctx.intensity() == Intensity.HIGH
                || (ctx.distanceKm() != null && ctx.distanceKm() >= HIGH_DISTANCE_KM_THRESHOLD);
        boolean highUv = RecoveryGuideAiClient.uvDoseTier(ctx.uvIndexAtStart(), ctx.durationSec()).isHighOrAbove();

        List<ActionDraft> actions = new ArrayList<>();
        actions.add(new ActionDraft(RecoveryActionType.HYDRATION,
                "수분 보충", "500ml 물 또는 이온음료로 손실된 수분과 전해질을 채워주세요"));
        actions.add(new ActionDraft(RecoveryActionType.CLEANSING,
                "세안", highLoad
                        ? "땀과 피지가 모공에 남지 않도록 30분 이내에 약산성 클렌저로 세안하세요"
                        : "미온수로 가볍게 세안하세요"));
        if (highLoad) {
            actions.add(new ActionDraft(RecoveryActionType.COOLDOWN,
                    "심박 안정화", "그늘에서 5~10분간 걸으며 심박수를 낮춘 뒤 스킨케어를 시작하세요"));
        }
        if (highUv) {
            actions.add(new ActionDraft(RecoveryActionType.UV_CARE,
                    "자외선 진정 케어", "귀가 후 10분 이내에 시원한 물로 노출 부위를 식히고 자외선 차단제를 다시 발라주세요"));
        } else if (highLoad) {
            actions.add(new ActionDraft(RecoveryActionType.SOOTHING,
                    "열감 진정", "붉어진 부위에 차가운 수건이나 쿨링 시트를 5분간 올려 열을 가라앉혀 주세요"));
        }
        actions.add(new ActionDraft(RecoveryActionType.MOISTURIZING,
                "보습", "세안 직후 수분 크림으로 마무리해 피부 장벽을 보호하세요"));

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
            sb.append("에 자외선 노출까지 겹쳤어요");
        } else {
            sb.append("을 완주했어요");
        }
        sb.append(". ");
        sb.append(highLoad
                ? "수분 보충과 피부 진정 케어로 마무리하는 걸 추천해요."
                : "가볍게 세안하고 보습하며 마무리해보세요.");
        return sb.toString();
    }
}
