package net.clodo.startflow;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

final class Placeholders extends PlaceholderExpansion {
    private final StartFlowPlugin plugin;
    private final ProgressService progress;
    private final DailyReward dailyReward;
    private final PlayTime playTime;
    private final Messages messages;
    private final GuideSettingsLoader settings;

    Placeholders(StartFlowPlugin plugin, ProgressService progress, DailyReward dailyReward, PlayTime playTime, Messages messages, GuideSettingsLoader settings) {
        this.plugin = plugin;
        this.progress = progress;
        this.dailyReward = dailyReward;
        this.playTime = playTime;
        this.messages = messages;
        this.settings = settings;
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public boolean canRegister() {
        return true;
    }

    @Override
    public String getIdentifier() {
        return "startflow";
    }

    @Override
    public String getAuthor() {
        return "Clodo";
    }

    @Override
    public String getVersion() {
        return plugin.getPluginMeta().getVersion();
    }

    @Override
    public String onRequest(OfflinePlayer offlinePlayer, String params) {
        if (offlinePlayer == null || params == null) {
            return "";
        }
        UUID uuid = offlinePlayer.getUniqueId();
        Player online = offlinePlayer.getPlayer();
        ProgressService.Progress cachedProgress = progress.cached(uuid);
        if (cachedProgress == null) {
            if (online != null) {
                progress.load(uuid);
            }
        }
        ProgressService.Progress playerProgress = cachedProgress != null ? cachedProgress : new ProgressService.Progress(Set.of(), false);

        List<Step> enabledSteps = settings.settings().steps().stream().filter(Step::enabled).toList();
        int total = enabledSteps.size();
        int completed = (int) enabledSteps.stream().filter(step -> playerProgress.completedSteps().contains(step.id())).count();
        int percent = total == 0 ? 0 : Math.min(100, Math.round((float) completed * 100 / total));
        boolean allDone = total > 0 && completed >= total;
        String language = messages.language(online);

        return switch (params.toLowerCase(Locale.ROOT)) {
            case "progress" -> completed + "/" + total;
            case "progress_percent" -> String.valueOf(percent);
            case "step" -> {
                if (allDone || playerProgress.skipped()) {
                    yield messages.text(language, "placeholder-step-completed");
                }
                Step next = null;
                for (Step step : enabledSteps) {
                    if (!playerProgress.completedSteps().contains(step.id())) {
                        next = step;
                        break;
                    }
                }
                if (next != null) {
                    yield messages.text(language, next.displayNameKey());
                }
                yield messages.text(language, "placeholder-step-completed");
            }
            case "completed" -> messages.text(language, allDone ? "placeholder-completed-yes" : "placeholder-completed-no");
            case "bar" -> {
                int barLength = 10;
                int filled = total == 0 ? 0 : Math.min(barLength, Math.round((float) completed * barLength / total));
                StringBuilder bar = new StringBuilder();
                bar.append("<green>");
                for (int i = 0; i < filled; i++) bar.append('▰');
                bar.append("</green><dark_gray>");
                for (int i = filled; i < barLength; i++) bar.append('▱');
                bar.append("</dark_gray>");
                yield bar.toString();
            }
            case "streak" -> {
                if (online != null && dailyReward.cached(uuid) == null) dailyReward.load(uuid);
                yield String.valueOf(dailyReward.streak(uuid));
            }
            case "best_streak" -> {
                if (online != null && dailyReward.cached(uuid) == null) dailyReward.load(uuid);
                yield String.valueOf(dailyReward.bestStreak(uuid));
            }
            case "playtime" -> {
                if (online != null && playTime.cached(uuid) == null) playTime.load(uuid);
                yield playTime.formatted(uuid, language);
            }
            case "playtime_hours" -> {
                if (online != null && playTime.cached(uuid) == null) playTime.load(uuid);
                yield playTime.formattedHours(uuid);
            }
            case "next_milestone" -> {
                if (online != null && playTime.cached(uuid) == null) playTime.load(uuid);
                yield playTime.nextMilestoneId(uuid, language);
            }
            default -> null;
        };
    }
}
