package net.clodo.startflow;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

final class PlayTime implements Listener {
    private final Storage storage;
    private final Messages messages;
    private final Supplier<GuideSettings> settings;
    private final Tasks tasks;
    private final PlayTimeActivity activity;
    private final PlayTimeRewards rewards;
    private final Map<UUID, Long> cache = new ConcurrentHashMap<>();
    private final Map<UUID, Long> cacheGenerations = new ConcurrentHashMap<>();
    private final Map<UUID, Tasks.TaskRef> playerTasks = new ConcurrentHashMap<>();
    private final Tasks.TaskRef flushTask;

    PlayTime(StartFlowPlugin plugin, Storage storage, Messages messages, Supplier<GuideSettings> settings, Tasks tasks) {
        this.storage = storage;
        this.messages = messages;
        this.settings = settings;
        this.tasks = tasks;
        this.activity = new PlayTimeActivity();
        this.rewards = new PlayTimeRewards(plugin, storage, messages, settings, tasks);
        this.flushTask = tasks.asyncRepeating(this::flush, 300L, 300L, TimeUnit.SECONDS);
    }

    PlayTimeActivity activity() {
        return activity;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        start(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        stop(event.getPlayer());
    }

    void start(Player player) {
        UUID uuid = player.getUniqueId();
        activity.register(uuid);
        load(uuid);
        Tasks.TaskRef existing = playerTasks.remove(uuid);
        if (existing != null) {
            existing.cancel();
        }
        Tasks.TaskRef ref = tasks.playerRepeating(player, () -> tick(player), 600L, 600L);
        playerTasks.put(uuid, ref);
    }

    void stop(Player player) {
        UUID uuid = player.getUniqueId();
        cacheGenerations.remove(uuid);
        Tasks.TaskRef ref = playerTasks.remove(uuid);
        if (ref != null) {
            ref.cancel();
        }
        activity.unregister(uuid);
        Long seconds = cache.remove(uuid);
        if (seconds != null) {
            storage.savePlayTime(uuid, seconds);
        }
    }

    void load(UUID uuid) {
        long generation = cacheGeneration(uuid);
        storage.getPlayTime(uuid).thenAccept(seconds -> {
            if (seconds != null) {
                storeLoadedSeconds(uuid, generation, seconds);
            }
        });
    }

    Long cached(UUID uuid) {
        return cache.get(uuid);
    }

    long seconds(UUID uuid) {
        Long value = cache.get(uuid);
        return value != null ? value : 0L;
    }

    CompletableFuture<Void> reset(UUID uuid) {
        advanceCacheGeneration(uuid);
        cache.remove(uuid);
        Player player = Bukkit.getPlayer(uuid);
        if (player == null || !player.isOnline()) {
            cacheGenerations.remove(uuid);
        }
        return storage.resetPlayTime(uuid);
    }

    void close() {
        flushTask.cancel();
        playerTasks.values().forEach(Tasks.TaskRef::cancel);
        playerTasks.clear();
        if (!cache.isEmpty()) {
            try {
                storage.saveAllPlayTime(Map.copyOf(cache)).join();
            } catch (Exception ignored) {
            }
            cache.clear();
        }
    }

    private void flush() {
        if (!cache.isEmpty()) {
            storage.saveAllPlayTime(Map.copyOf(cache));
        }
    }

    private void tick(Player player) {
        if (!player.isOnline()) return;
        UUID uuid = player.getUniqueId();
        PlayTimeSettings config = settings.get().playTime();
        boolean active = activity.checkAndResetActive(uuid, config.afkAfterSeconds());
        if (!active) return;

        long updated = cache.compute(uuid, (id, current) -> (current == null ? 0L : current) + 30L);
        if (config.enabled()) {
            rewards.checkMilestones(player, updated, config);
        }
    }

    void showStatus(Player player) {
        UUID uuid = player.getUniqueId();
        Long cached = cache.get(uuid);
        if (cached != null) {
            sendStatus(player, cached);
            return;
        }
        long generation = cacheGeneration(uuid);
        storage.getPlayTime(uuid).thenAccept(sec -> {
            long seconds = sec != null ? sec : 0L;
            if (storeLoadedSeconds(uuid, generation, seconds)) {
                tasks.player(player, () -> sendStatus(player, seconds));
            }
        });
    }

    private void sendStatus(Player player, long seconds) {
        if (!player.isOnline()) return;
        PlayTimeSettings config = settings.get().playTime();
        List<PlayTimeSettings.Milestone> milestones = config.milestones();
        PlayTimeSettings.Milestone next = null;
        long prevSeconds = 0L;
        for (PlayTimeSettings.Milestone milestone : milestones) {
            if (milestone.requiredSeconds() > seconds) {
                next = milestone;
                break;
            }
            prevSeconds = milestone.requiredSeconds();
        }

        int percent;
        int filled;
        if (next == null) {
            percent = milestones.isEmpty() ? 0 : 100;
            filled = milestones.isEmpty() ? 0 : 10;
        } else {
            long span = next.requiredSeconds() - prevSeconds;
            long progress = Math.max(0L, seconds - prevSeconds);
            percent = span <= 0 ? 100 : (int) Math.min(100L, Math.round((double) progress * 100.0 / span));
            filled = Math.min(10, Math.max(0, (int) Math.round((double) progress * 10.0 / span)));
        }

        StringBuilder bar = new StringBuilder("<green>");
        for (int i = 0; i < filled; i++) bar.append('▰');
        bar.append("</green><dark_gray>");
        for (int i = filled; i < 10; i++) bar.append('▱');
        bar.append("</dark_gray>");

        String formatted = formatted(player.getUniqueId(), messages.language(player));
        String nextText = next != null ? next.id() : messages.text(player, "playtime-milestone-none");
        String nextHours = next != null ? PlayTimeRewards.formatHours(next.hours()) : "-";
        messages.send(player, "playtime-info", Map.of(
                "time", formatted,
                "hours", formattedHours(player.getUniqueId()),
                "next", nextText,
                "next_hours", nextHours,
                "percent", String.valueOf(percent)
        ), Map.of("bar", bar.toString()));
    }

    String formatted(UUID uuid, String language) {
        return PlayTimeRewards.formatDuration(messages.text(language, "playtime-format"), seconds(uuid) / 60);
    }

    String formattedHours(UUID uuid) {
        long sec = seconds(uuid);
        double hours = (double) sec / 3600.0;
        return String.format(Locale.ROOT, "%.1f", hours);
    }

    String nextMilestoneId(UUID uuid, String language) {
        long sec = seconds(uuid);
        for (PlayTimeSettings.Milestone milestone : settings.get().playTime().milestones()) {
            if (milestone.requiredSeconds() > sec) {
                return milestone.id();
            }
        }
        return messages.text(language, "playtime-milestone-none");
    }

    private long cacheGeneration(UUID uuid) {
        return cacheGenerations.compute(uuid, (id, generation) -> generation == null ? 1L : generation + 1L);
    }

    private void advanceCacheGeneration(UUID uuid) {
        cacheGenerations.compute(uuid, (id, generation) -> generation == null ? 1L : generation + 1L);
    }

    private boolean storeLoadedSeconds(UUID uuid, long generation, long seconds) {
        AtomicBoolean stored = new AtomicBoolean();
        cacheGenerations.computeIfPresent(uuid, (id, current) -> {
            if (current == generation) {
                cache.putIfAbsent(uuid, seconds);
                stored.set(true);
            }
            return current;
        });
        return stored.get();
    }
}
