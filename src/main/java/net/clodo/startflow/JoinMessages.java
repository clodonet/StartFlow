package net.clodo.startflow;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Map;
import java.util.function.Supplier;

final class JoinMessages implements Listener {
    private final Messages messages;
    private final Supplier<GuideSettings> settings;

    JoinMessages(Messages messages, Supplier<GuideSettings> settings) {
        this.messages = messages;
        this.settings = settings;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        MessageFeatureSettings config = settings.get().joinMessages();
        if (!config.enabled()) return;
        Player player = event.getPlayer();
        String defaultLanguage = settings.get().defaultLanguage();
        Map<String, String> tokens = Map.of(
                "player", player.getName(),
                "online", String.valueOf(Bukkit.getOnlinePlayers().size())
        );
        if (config.firstJoinBroadcast() && !player.hasPlayedBefore()) {
            String firstKey = config.firstJoin();
            if (firstKey != null && !firstKey.isBlank() && messages.has(defaultLanguage, firstKey)) {
                event.joinMessage(messages.message(defaultLanguage, firstKey, tokens));
                return;
            }
        }
        String joinKey = config.join();
        if (joinKey != null && !joinKey.isBlank() && messages.has(defaultLanguage, joinKey)) {
            event.joinMessage(messages.message(defaultLanguage, joinKey, tokens));
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        MessageFeatureSettings config = settings.get().joinMessages();
        if (!config.enabled()) return;
        String quitKey = config.quit();
        if (quitKey == null || quitKey.isBlank()) return;
        String defaultLanguage = settings.get().defaultLanguage();
        if (!messages.has(defaultLanguage, quitKey)) return;
        Player player = event.getPlayer();
        Map<String, String> tokens = Map.of(
                "player", player.getName(),
                "online", String.valueOf(Math.max(0, Bukkit.getOnlinePlayers().size() - 1))
        );
        event.quitMessage(messages.message(defaultLanguage, quitKey, tokens));
    }
}
