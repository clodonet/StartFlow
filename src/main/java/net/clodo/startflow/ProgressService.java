package net.clodo.startflow;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class ProgressService implements Listener {
    private final Storage storage;
    private final Supplier<Set<String>> stepIds;
    private final Consumer<Completion> completionListener;
    private final Map<UUID, Progress> cache = new ConcurrentHashMap<>();

    ProgressService(Storage storage, Supplier<Set<String>> stepIds, Consumer<Completion> completionListener) {
        this.storage = storage;
        this.stepIds = stepIds;
        this.completionListener = completionListener;
    }

    public Progress cached(UUID uuid) {
        return cache.get(uuid);
    }

    public CompletableFuture<Progress> load(UUID uuid) {
        return storage.getProgress(uuid).thenApply(progress -> {
            cache.put(uuid, progress);
            return progress;
        });
    }

    public void unload(UUID uuid) {
        cache.remove(uuid);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        load(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        unload(event.getPlayer().getUniqueId());
    }

    public CompletableFuture<Progress> getProgress(UUID uuid) {
        Progress existing = cache.get(uuid);
        if (existing != null) {
            return CompletableFuture.completedFuture(existing);
        }
        return load(uuid);
    }

    public CompletableFuture<Boolean> completeStep(UUID uuid, String stepId) {
        return completeStep(uuid, stepId, true);
    }

    public CompletableFuture<Boolean> completeStep(UUID uuid, String stepId, boolean admin) {
        if (stepId == null || stepId.isBlank()) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("A step id is required"));
        }
        return storage.completeStep(uuid, stepId, Set.copyOf(stepIds.get())).thenCompose(inserted -> {
            if (!inserted) return CompletableFuture.completedFuture(false);
            return storage.getProgress(uuid).thenApply(value -> {
                cache.put(uuid, value);
                int completed = (int) value.completedSteps().stream().filter(stepIds.get()::contains).count();
                boolean allDone = !stepIds.get().isEmpty() && completed >= stepIds.get().size();
                completionListener.accept(new Completion(uuid, stepId, completed, allDone, admin, value.skipped()));
                return true;
            });
        });
    }

    public CompletableFuture<Void> skip(UUID uuid) {
        return storage.skip(uuid).thenCompose(unused -> storage.getProgress(uuid).thenAccept(value -> cache.put(uuid, value)));
    }

    public CompletableFuture<Void> reset(UUID uuid) {
        return storage.reset(uuid).thenAccept(unused -> cache.put(uuid, new Progress(Set.of(), false)));
    }

    public CompletableFuture<Void> resetStep(UUID uuid, String stepId) {
        if (stepId == null || stepId.isBlank()) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("A step id is required"));
        }
        return storage.resetStep(uuid, stepId).thenCompose(unused -> storage.getProgress(uuid).thenAccept(value -> cache.put(uuid, value)));
    }

    public record Progress(Set<String> completedSteps, boolean skipped) {
    }

    record Completion(UUID uuid, String stepId, int completed, boolean allDone, boolean admin, boolean skipped) {
    }
}
