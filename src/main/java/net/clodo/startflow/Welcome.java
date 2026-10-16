package net.clodo.startflow;

import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.title.Title;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

final class Welcome implements Listener {
    private final StartFlowPlugin plugin;
    private final Storage storage;
    private final Messages messages;
    private final Supplier<GuideSettings> settings;
    private final CompletableFuture<Void> initialFiles;
    private final Tasks tasks;

    Welcome(StartFlowPlugin plugin, Storage storage, Messages messages, Supplier<GuideSettings> settings, CompletableFuture<Void> initialFiles, Tasks tasks) {
        this.plugin = plugin;
        this.storage = storage;
        this.messages = messages;
        this.settings = settings;
        this.initialFiles = initialFiles;
        this.tasks = tasks;
    }

    @EventHandler
    public void joined(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        initialFiles.handle((unused, ignored) -> settings.get().welcome())
                .thenCompose(enabled -> storage.recordJoinAndClaimWelcome(uuid, LocalDate.now(ZoneOffset.UTC).toString(), enabled))
                .whenComplete((welcome, error) -> {
                    if (error == null && welcome) {
                        tasks.playerLater(player, () -> show(player), 60L);
                    }
                    if (error != null) {
                        plugin.getLogger().warning("Could not record a player join: " + Errors.concise(error));
                    }
                });
    }

    private void show(Player player) {
        if (!player.isOnline()) return;
        player.showTitle(Title.title(messages.message(player, "welcome-title", Map.of()), messages.message(player, "welcome-subtitle", Map.of()), Title.Times.times(Duration.ofMillis(250), Duration.ofSeconds(2), Duration.ofMillis(400))));
        player.sendMessage(messages.message(player, "welcome-open", Map.of()).clickEvent(ClickEvent.runCommand("/basla")));
    }
}
