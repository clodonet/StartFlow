package net.clodo.startflow;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

final class StarterKit implements Listener {
    private final StartFlowPlugin plugin;
    private final Storage storage;
    private final Messages messages;
    private final Supplier<GuideSettings> settings;
    private final Tasks tasks;
    private final Set<UUID> pendingGrants = ConcurrentHashMap.newKeySet();

    StarterKit(StartFlowPlugin plugin, Storage storage, Messages messages, Supplier<GuideSettings> settings, Tasks tasks) {
        this.plugin = plugin;
        this.storage = storage;
        this.messages = messages;
        this.settings = settings;
        this.tasks = tasks;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        StarterKitSettings config = settings.get().starterKit();
        if (!config.enabled() || !config.giveOn().equalsIgnoreCase("first-join")) {
            return;
        }
        giveIfEligible(player);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        if (pendingGrants.remove(uuid)) {
            storage.resetKit(uuid);
        }
    }

    void onRulesAccepted(Player player) {
        StarterKitSettings config = settings.get().starterKit();
        if (!config.enabled() || !config.giveOn().equalsIgnoreCase("rules-accept")) {
            return;
        }
        giveIfEligible(player);
    }

    void giveIfEligible(Player player) {
        if (!player.isOnline()) return;
        UUID uuid = player.getUniqueId();
        storage.claimReward(uuid, "kit").thenAccept(granted -> {
            if (granted) {
                pendingGrants.add(uuid);
                tasks.global(() -> {
                    Player online = Bukkit.getPlayer(uuid);
                    if (online == null || !online.isOnline()) {
                        if (pendingGrants.remove(uuid)) {
                            storage.resetKit(uuid);
                        }
                        return;
                    }
                    tasks.player(online, () -> {
                        if (!grant(online)) {
                            if (pendingGrants.remove(uuid)) {
                                storage.resetKit(uuid);
                            }
                            return;
                        }
                        pendingGrants.remove(uuid);
                    });
                });
            }
        });
    }

    CompletableFuture<Void> reset(UUID uuid) {
        return storage.resetKit(uuid).thenAccept(unused -> {
            Player online = Bukkit.getPlayer(uuid);
            if (online != null && online.isOnline() && settings.get().starterKit().enabled()) {
                String giveOn = settings.get().starterKit().giveOn();
                if (giveOn.equalsIgnoreCase("first-join")) {
                    giveIfEligible(online);
                }
            }
        });
    }

    private boolean grant(Player player) {
        if (!player.isOnline()) return false;
        StarterKitSettings config = settings.get().starterKit();
        List<StarterKitSettings.KitItem> items = config.items();
        if (items.isEmpty()) return true;
        int count = Math.min(items.size(), 27);
        for (int i = 0; i < count; i++) {
            StarterKitSettings.KitItem item = items.get(i);
            Material mat = Material.matchMaterial(item.material());
            if (mat == null || mat.isAir()) {
                plugin.getLogger().warning("Invalid starter kit material: " + item.material());
                continue;
            }
            int amount = Math.max(1, Math.min(item.amount(), mat.getMaxStackSize() * 64));
            ItemStack stack = new ItemStack(mat, amount);
            ItemMeta meta = stack.getItemMeta();
            if (meta != null) {
                boolean changed = false;
                String language = messages.language(player);
                String fallbackLanguage = settings.get().defaultLanguage();
                String name = item.name(language, fallbackLanguage);
                List<String> loreLines = item.lore(language, fallbackLanguage);
                if (name != null && !name.isBlank()) {
                    meta.displayName(MiniMessage.miniMessage().deserialize(name).decoration(TextDecoration.ITALIC, false));
                    changed = true;
                }
                if (!loreLines.isEmpty()) {
                    List<Component> lore = new ArrayList<>();
                    for (String line : loreLines) {
                        lore.add(MiniMessage.miniMessage().deserialize(line).decoration(TextDecoration.ITALIC, false));
                    }
                    meta.lore(lore);
                    changed = true;
                }
                if (changed) {
                    stack.setItemMeta(meta);
                }
            }
            Map<Integer, ItemStack> overflow = player.getInventory().addItem(stack);
            if (!overflow.isEmpty()) {
                for (ItemStack dropped : overflow.values()) {
                    player.getWorld().dropItemNaturally(player.getLocation(), dropped);
                }
            }
        }
        messages.send(player, config.messageKey(), Map.of("player", player.getName()));
        return true;
    }
}
