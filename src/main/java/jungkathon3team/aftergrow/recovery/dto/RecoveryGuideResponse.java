package jungkathon3team.aftergrow.recovery.dto;

import jungkathon3team.aftergrow.recovery.entity.RecoveryAction;
import jungkathon3team.aftergrow.recovery.entity.RecoveryActionType;
import jungkathon3team.aftergrow.recovery.entity.RecoveryGuide;

import java.util.List;
import java.util.UUID;

public record RecoveryGuideResponse(
        UUID recoveryGuideId,
        Integer measuredBpm,
        String summaryMessage,
        List<ActionItem> actions,
        Integer cooldownTimerSec
) {
    public record ActionItem(RecoveryActionType type, String title, String description) {
        static ActionItem from(RecoveryAction action) {
            return new ActionItem(action.getType(), action.getTitle(), action.getDescription());
        }
    }

    public static RecoveryGuideResponse from(RecoveryGuide guide) {
        return new RecoveryGuideResponse(
                guide.getRecoveryGuideId(),
                guide.getMeasuredBpm(),
                guide.getSummaryMessage(),
                guide.getActions().stream().map(ActionItem::from).toList(),
                guide.getCooldownTimerSec()
        );
    }
}
