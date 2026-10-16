package net.clodo.startflow;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

record GuideSettings(
        String defaultLanguage,
        boolean effects,
        boolean welcome,
        DailyRewardSettings dailyReward,
        PlayTimeSettings playTime,
        RulesSettings rules,
        StarterKitSettings starterKit,
        MessageFeatureSettings joinMessages,
        List<String> completionRewards,
        String completionRewardMessageKey,
        List<Step> steps
) {
    GuideSettings(String defaultLanguage, boolean effects, boolean welcome, DailyRewardSettings dailyReward, PlayTimeSettings playTime, List<String> completionRewards, String completionRewardMessageKey, List<Step> steps) {
        this(defaultLanguage, effects, welcome, dailyReward, playTime, new RulesSettings(), new StarterKitSettings(), new MessageFeatureSettings(), completionRewards, completionRewardMessageKey, steps);
    }

    GuideSettings(String defaultLanguage, boolean effects, boolean welcome, DailyRewardSettings dailyReward, List<String> completionRewards, String completionRewardMessageKey, List<Step> steps) {
        this(defaultLanguage, effects, welcome, dailyReward, new PlayTimeSettings(), new RulesSettings(), new StarterKitSettings(), new MessageFeatureSettings(), completionRewards, completionRewardMessageKey, steps);
    }

    GuideSettings(String defaultLanguage, boolean effects, boolean welcome, List<String> completionRewards, String completionRewardMessageKey, List<Step> steps) {
        this(defaultLanguage, effects, welcome, new DailyRewardSettings(), new PlayTimeSettings(), new RulesSettings(), new StarterKitSettings(), new MessageFeatureSettings(), completionRewards, completionRewardMessageKey, steps);
    }

    GuideSettings(String defaultLanguage, boolean effects, boolean welcome, List<String> completionRewards, List<Step> steps) {
        this(defaultLanguage, effects, welcome, new DailyRewardSettings(), new PlayTimeSettings(), new RulesSettings(), new StarterKitSettings(), new MessageFeatureSettings(), completionRewards, "reward.completion.message", steps);
    }

    GuideSettings(String defaultLanguage, boolean effects, boolean welcome, List<Step> steps) {
        this(defaultLanguage, effects, welcome, new DailyRewardSettings(), new PlayTimeSettings(), new RulesSettings(), new StarterKitSettings(), new MessageFeatureSettings(), List.of(), "reward.completion.message", steps);
    }

    Step firstIncomplete(Set<String> completedSteps) {
        return steps.stream()
                .filter(Step::enabled)
                .filter(step -> !completedSteps.contains(step.id()))
                .findFirst()
                .orElse(null);
    }

    Set<String> stepIds() {
        return steps.stream().map(Step::id).collect(Collectors.toUnmodifiableSet());
    }

    Set<String> enabledStepIds() {
        return steps.stream().filter(Step::enabled).map(Step::id).collect(Collectors.toUnmodifiableSet());
    }

    Step step(String id) {
        return steps.stream().filter(step -> step.id().equals(id)).findFirst().orElse(null);
    }
}
