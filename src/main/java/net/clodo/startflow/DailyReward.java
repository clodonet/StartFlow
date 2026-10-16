package net.clodo.startflow;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

final class DailyReward implements Listener {
    private final StartFlowPlugin plugin;
    private final Storage storage;
    private final Messages messages;
    private final Supplier<GuideSettings> settings;
    private final Tasks tasks;
    private final Map<UUID, Storage.DailyRecord> cache = new ConcurrentHashMap<>();

    DailyReward(StartFlowPlugin plugin, Storage storage, Messages messages, Supplier<GuideSettings> settings, Tasks tasks) {
        this.plugin = plugin;
        this.storage = storage;
        this.messages = messages;
        this.settings = settings;
        this.tasks = tasks;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        tasks.playerLater(player, () -> processJoin(player), 60L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        cache.remove(event.getPlayer().getUniqueId());
    }

    void load(UUID uuid) {
        storage.getDaily(uuid).thenAccept(record -> {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline() && record != null) {
                cache.put(uuid, record);
            }
        });
    }

    Storage.DailyRecord cached(UUID uuid) {
        return cache.get(uuid);
    }

    int streak(UUID uuid) {
        Storage.DailyRecord record = cache.get(uuid);
        return record != null ? record.streak() : 0;
    }

    int bestStreak(UUID uuid) {
        Storage.DailyRecord record = cache.get(uuid);
        return record != null ? record.best() : 0;
    }

    CompletableFuture<Void> reset(UUID uuid) {
        cache.remove(uuid);
        return storage.resetDaily(uuid);
    }

    void showStatus(Player player) {
        UUID uuid = player.getUniqueId();
        Storage.DailyRecord cachedRecord = cache.get(uuid);
        if (cachedRecord != null) {
            sendStatus(player, cachedRecord);
            return;
        }
        storage.getDaily(uuid).thenAccept(record -> {
            if (!player.isOnline()) return;
            if (record != null) {
                cache.put(uuid, record);
            }
            tasks.player(player, () -> sendStatus(player, record));
        });
    }

    private void sendStatus(Player player, Storage.DailyRecord record) {
        if (!player.isOnline()) return;
        DailyRewardSettings config = settings.get().dailyReward();
        LocalDate today = LocalDate.now(ZoneId.systemDefault());
        boolean claimed = record != null && record.lastDay() != null && today.toString().equals(record.lastDay());
        String statusText;
        if (!config.enabled()) {
            statusText = messages.text(player, "daily-status-disabled");
        } else if (claimed) {
            statusText = messages.text(player, "daily-status-claimed");
        } else {
            statusText = messages.text(player, "daily-status-available");
        }
        ZoneId zone = ZoneId.systemDefault();
        ZonedDateTime now = ZonedDateTime.now(zone);
        ZonedDateTime nextDay = now.toLocalDate().plusDays(1).atStartOfDay(zone);
        Duration remaining = Duration.between(now, nextDay);
        long hours = remaining.toHours();
        long minutes = remaining.toMinutesPart();
        int streak = record != null ? record.streak() : 0;
        int best = record != null ? record.best() : 0;
        messages.send(player, "daily-info", Map.of(
                "streak", String.valueOf(streak),
                "best", String.valueOf(best),
                "hours", String.valueOf(hours),
                "minutes", String.valueOf(minutes)
        ), Map.of("status", statusText));
    }

    private void processJoin(Player player) {
        if (!player.isOnline()) return;
        DailyRewardSettings config = settings.get().dailyReward();
        String today = LocalDate.now(ZoneId.systemDefault()).toString();
        if (!config.enabled()) {
            load(player.getUniqueId());
            return;
        }
        UUID uuid = player.getUniqueId();
        storage.claimDaily(uuid, today, config.resetAfterDays()).thenAccept(claim -> {
            tasks.global(() -> {
            Player online = Bukkit.getPlayer(uuid);
            if (online == null || !online.isOnline()) {
                if (claim.granted()) {
                    storage.rollbackDailyClaim(uuid, today, claim);
                }
                return;
            }
            cache.put(uuid, new Storage.DailyRecord(claim.streak(), claim.best(), today));
            if (!claim.granted()) return;
            grant(online, claim.streak(), claim.best(), config);
            });
        }).exceptionally(error -> {
            plugin.getLogger().warning("Could not process daily reward for " + player.getName() + ": " + Errors.concise(error));
            return null;
        });
    }

    private void grant(Player player, int streak, int best, DailyRewardSettings config) {
        UUID uuid = player.getUniqueId();
        String playerName = player.getName();
        executeCommands(config.commands(), playerName, uuid, streak);
        List<String> milestoneCommands = config.milestones().get(streak);
        boolean milestone = milestoneCommands != null && !milestoneCommands.isEmpty();
        if (milestone) {
            executeCommands(milestoneCommands, playerName, uuid, streak);
        }
        tasks.player(player, () -> {
            String messageKey = milestone ? config.milestoneMessageKey() : config.messageKey();
            Map<String, String> tokens = Map.of(
                    "player", player.getName(),
                    "streak", String.valueOf(streak),
                    "best", String.valueOf(best)
            );
            messages.send(player, messageKey, tokens);
            if (settings.get().effects()) {
                Component title = messages.message(player, "daily-title", tokens);
                Component subtitle = messages.message(player, "daily-subtitle", tokens);
                player.showTitle(Title.title(
                        title,
                        subtitle,
                        Title.Times.times(Duration.ofMillis(200), Duration.ofSeconds(2), Duration.ofMillis(300))
                ));
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
            }
        });
    }

    private void executeCommands(List<String> commands, String playerName, UUID uuid, int streak) {
        if (commands == null || commands.isEmpty()) return;
        for (String command : commands) {
            String formatted = command
                    .replace("<player>", playerName)
                    .replace("<uuid>", uuid.toString())
                    .replace("<streak>", String.valueOf(streak));
            String cmd = formatted.startsWith("/") ? formatted.substring(1).trim() : formatted.trim();
            try {
                if (!Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd)) {
                    plugin.getLogger().warning("Daily reward command failed for " + playerName + ": " + command);
                }
            } catch (Exception error) {
                plugin.getLogger().warning("Daily reward command failed for " + playerName + ": " + command);
            }
        }
    }
}
