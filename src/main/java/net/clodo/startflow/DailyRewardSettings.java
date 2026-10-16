package net.clodo.startflow;

import java.util.List;
import java.util.Map;

record DailyRewardSettings(
        boolean enabled,
        int resetAfterDays,
        String messageKey,
        String milestoneMessageKey,
        List<String> commands,
        Map<Integer, List<String>> milestones
) {
    DailyRewardSettings() {
        this(false, 2, "daily.reward.message", "daily.milestone.message", List.of(), Map.of());
    }
}
