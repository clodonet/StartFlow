package net.clodo.startflow;

import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

final class GuideMenu implements Listener {
    private final StartFlowPlugin plugin;
    private final ProgressService progress;
    private final Messages messages;
    private final Supplier<GuideSettings> settings;
    private final Tasks tasks;
    private final Map<UUID, Long> stepClicks = new ConcurrentHashMap<>();
    private final Map<UUID, Long> skipConfirmations = new ConcurrentHashMap<>();

    GuideMenu(StartFlowPlugin plugin, ProgressService progress, Messages messages, Supplier<GuideSettings> settings, Tasks tasks) {
        this.plugin = plugin;
        this.progress = progress;
        this.messages = messages;
        this.settings = settings;
        this.tasks = tasks;
    }

    void open(Player player) {
        progress.getProgress(player.getUniqueId()).whenComplete((value, error) -> tasks.player(player, () -> {
            if (error != null) {
                messages.send(player, "storage-error", Map.of());
                return;
            }
            if (value.skipped()) {
                messages.send(player, "guide-skipped", Map.of());
                return;
            }
            open(player, value);
        }));
    }

    @EventHandler
    public void click(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof GuideMenuView view)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || !view.player().equals(player.getUniqueId()) || event.getRawSlot() < 0 || event.getRawSlot() >= 45) return;

        if (event.getRawSlot() == 36) {
            skip(player);
            return;
        }
        if (event.getRawSlot() == 44) {
            player.closeInventory();
            return;
        }
        if (event.getRawSlot() == 4 || event.getRawSlot() == 40) {
            return;
        }

        Step step = view.stepAt(event.getRawSlot());
        if (step != null) {
            long now = System.currentTimeMillis();
            Long last = stepClicks.get(player.getUniqueId());
            if (last != null && now - last < 1500) return;
            stepClicks.put(player.getUniqueId(), now);
            messages.send(player, "menu-step-description", Map.of(
                    "step", messages.text(player, step.displayNameKey()),
                    "description", messages.text(player, step.descriptionKey())
            ));
            if (settings.get().effects()) {
                if (view.isCurrent(step)) {
                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.7f, 1.6f);
                } else {
                    player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.0f);
                }
            }
        }
    }

    @EventHandler
    public void drag(InventoryDragEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof GuideMenuView)) return;
        for (int slot : event.getRawSlots()) {
            if (slot < 45) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        stepClicks.remove(uuid);
        skipConfirmations.remove(uuid);
    }

    private void open(Player player, ProgressService.Progress progressValue) {
        GuideSettings current = settings.get();
        GuideMenuView view = new GuideMenuView(player.getUniqueId());
        view.build(player, current, progressValue, messages);
        player.openInventory(view.getInventory());
        if (current.effects()) {
            player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.7f, 1.0f);
        }
    }

    private void skip(Player player) {
        long now = System.currentTimeMillis();
        Long previous = skipConfirmations.put(player.getUniqueId(), now);
        if (previous == null || now - previous > 5000) {
            messages.send(player, "menu-skip-confirm", Map.of());
            return;
        }
        skipConfirmations.remove(player.getUniqueId());
        progress.skip(player.getUniqueId()).whenComplete((unused, error) -> tasks.player(player, () -> {
            messages.send(player, error == null ? "guide-skip-success" : "storage-error", Map.of());
            if (error == null) player.closeInventory();
        }));
    }
}
