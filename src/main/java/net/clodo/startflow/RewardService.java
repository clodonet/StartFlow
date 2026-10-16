package net.clodo.startflow;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

final class RewardService {
    private final StartFlowPlugin plugin;
    private final Storage storage;
    private final Messages messages;
    private final Supplier<GuideSettings> settings;
    private final Tasks tasks;

    RewardService(StartFlowPlugin plugin, Storage storage, Messages messages, Supplier<GuideSettings> settings, Tasks tasks) {
        this.plugin = plugin;
        this.storage = storage;
        this.messages = messages;
        this.settings = settings;
        this.tasks = tasks;
    }

    void completed(ProgressService.Completion completion) {
        if (completion.skipped() && !completion.admin()) return;
        GuideSettings guideSettings = settings.get();
        Step step = guideSettings.step(completion.stepId());
        int total = (int) guideSettings.steps().stream().filter(Step::enabled).count();
        String totalProgress = completion.completed() + "/" + total;
        if (step != null && !step.rewards().isEmpty()) {
            grant(completion.uuid(), step.id(), step.rewards(), step.displayNameKey(), step.rewardMessageKey(), totalProgress);
        }
        if (completion.allDone() && !guideSettings.completionRewards().isEmpty()) {
            grant(completion.uuid(), "__completion__", guideSettings.completionRewards(), "reward-completion", guideSettings.completionRewardMessageKey(), totalProgress);
        }
    }

    private void grant(UUID uuid, String key, List<String> commands, String displayKey, String rewardMessageKey, String totalProgress) {
        String playerName = resolveName(uuid);
        if (playerName == null || playerName.isBlank()) {
            plugin.getLogger().warning("Cannot grant reward for " + key + ": player name is unknown for " + uuid);
            return;
        }
        storage.claimReward(uuid, key).thenAccept(claimed -> {
            if (!claimed) return;
            tasks.global(() -> {
                int executed = 0;
                for (String command : commands) {
                    String formatted = command
                            .replace("<player>", playerName)
                            .replace("<uuid>", uuid.toString())
                            .replace("<step>", key);
                    String cmd = formatted.startsWith("/") ? formatted.substring(1).trim() : formatted.trim();
                    try {
                        if (Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd)) {
                            executed++;
                        } else {
                            plugin.getLogger().warning("Reward command failed for " + key + ": " + command);
                        }
                    } catch (Exception error) {
                        plugin.getLogger().warning("Reward command failed for " + key + ": " + command);
                    }
                }
                if (executed > 0) {
                    Player online = Bukkit.getPlayer(uuid);
                    if (online != null && online.isOnline()) {
                        tasks.player(online, () -> {
                            String name = messages.text(online, displayKey);
                            if (rewardMessageKey != null && messages.has(online, rewardMessageKey)) {
                                messages.send(online, rewardMessageKey, Map.of(
                                        "player", online.getName(),
                                        "step", name,
                                        "total", totalProgress
                                ));
                            } else {
                                messages.send(online, "reward-given", Map.of("step", name));
                            }
                            if (settings.get().effects()) {
                                online.showTitle(Title.title(
                                        MiniMessage.miniMessage().deserialize(name),
                                        Component.empty(),
                                        Title.Times.times(Duration.ofMillis(200), Duration.ofSeconds(2), Duration.ofMillis(300))
                                ));
                                online.playSound(online.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
                            }
                        });
                    }
                }
            });
        });
    }

    private String resolveName(UUID uuid) {
        Player online = Bukkit.getPlayer(uuid);
        if (online != null && online.isOnline()) return online.getName();
        OfflinePlayer offline = Bukkit.getOfflinePlayer(uuid);
        return offline.getName();
    }
}
