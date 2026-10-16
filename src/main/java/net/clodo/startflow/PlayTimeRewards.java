package net.clodo.startflow;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

final class PlayTimeRewards {
    private final StartFlowPlugin plugin;
    private final Storage storage;
    private final Messages messages;
    private final Supplier<GuideSettings> settings;
    private final Tasks tasks;

    PlayTimeRewards(StartFlowPlugin plugin, Storage storage, Messages messages, Supplier<GuideSettings> settings, Tasks tasks) {
        this.plugin = plugin;
        this.storage = storage;
        this.messages = messages;
        this.settings = settings;
        this.tasks = tasks;
    }

    void checkMilestones(Player player, long seconds, PlayTimeSettings config) {
        UUID uuid = player.getUniqueId();
        CompletableFuture<Void> chain = CompletableFuture.completedFuture(null);
        for (PlayTimeSettings.Milestone milestone : config.milestones()) {
            if (seconds >= milestone.requiredSeconds()) {
                chain = chain.thenCompose(v -> storage.claimReward(uuid, "playtime:" + milestone.id()).thenAccept(granted -> {
                    if (granted) {
                        grant(player, milestone);
                    }
                }));
            }
        }
    }

    private void grant(Player player, PlayTimeSettings.Milestone milestone) {
        UUID uuid = player.getUniqueId();
        String playerName = player.getName();
        tasks.global(() -> {
            executeCommands(milestone.commands(), playerName, uuid, milestone.hours());
            Player online = Bukkit.getPlayer(uuid);
            if (online != null && online.isOnline()) {
                tasks.player(online, () -> {
                    String messageKey = milestone.messageKey() != null ? milestone.messageKey() : "playtime.reward.message";
                    String hoursText = formatHours(milestone.hours());
                    Map<String, String> tokens = Map.of(
                            "player", online.getName(),
                            "hours", hoursText,
                            "duration", formatDuration(messages.text(online, "playtime-format"), Math.round(milestone.hours() * 60.0)),
                            "id", milestone.id()
                    );
                    messages.send(online, messageKey, tokens);
                    if (settings.get().effects()) {
                        Component title = messages.message(online, "playtime-title", tokens);
                        Component subtitle = messages.message(online, "playtime-subtitle", tokens);
                        online.showTitle(Title.title(
                                title,
                                subtitle,
                                Title.Times.times(Duration.ofMillis(200), Duration.ofSeconds(2), Duration.ofMillis(300))
                        ));
                        online.playSound(online.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
                    }
                });
            }
        });
    }

    private void executeCommands(List<String> commands, String playerName, UUID uuid, double hours) {
        if (commands == null || commands.isEmpty()) return;
        String hoursText = formatHours(hours);
        for (String command : commands) {
            String formatted = command
                    .replace("<player>", playerName)
                    .replace("<uuid>", uuid.toString())
                    .replace("<hours>", hoursText);
            String cmd = formatted.startsWith("/") ? formatted.substring(1).trim() : formatted.trim();
            try {
                if (!Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd)) {
                    plugin.getLogger().warning("Playtime reward command failed for " + playerName + ": " + command);
                }
            } catch (Exception error) {
                plugin.getLogger().warning("Playtime reward command failed for " + playerName + ": " + command);
            }
        }
    }

    static String formatDuration(String template, long totalMinutes) {
        long hours = totalMinutes / 60;
        long minutes = totalMinutes % 60;
        int minutesAt = template.indexOf("<minutes>");
        String pattern = template;
        if (minutesAt >= 0) {
            if (hours == 0) pattern = template.substring(minutesAt);
            else if (minutes == 0) pattern = template.substring(0, minutesAt).trim();
        }
        return pattern.replace("<hours>", String.valueOf(hours)).replace("<minutes>", String.valueOf(minutes));
    }

    static String formatHours(double hours) {
        if (hours == (long) hours) {
            return String.valueOf((long) hours);
        }
        return String.format(Locale.ROOT, "%.1f", hours);
    }
}
