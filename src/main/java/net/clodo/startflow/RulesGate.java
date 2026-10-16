package net.clodo.startflow;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.inventory.Book;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.vehicle.VehicleEnterEvent;
import org.bukkit.event.vehicle.VehicleMoveEvent;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

final class RulesGate implements Listener {
    private final StartFlowPlugin plugin;
    private final Storage storage;
    private final Messages messages;
    private final StarterKit starterKit;
    private final Supplier<GuideSettings> settings;
    private final Tasks tasks;

    private final Map<UUID, Boolean> acceptedCache = new ConcurrentHashMap<>();
    private final Map<UUID, Long> cacheGenerations = new ConcurrentHashMap<>();
    private final Map<UUID, Location> joinLocations = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastReminderTimes = new ConcurrentHashMap<>();

    RulesGate(StartFlowPlugin plugin, Storage storage, Messages messages, StarterKit starterKit, Supplier<GuideSettings> settings, Tasks tasks) {
        this.plugin = plugin;
        this.storage = storage;
        this.messages = messages;
        this.starterKit = starterKit;
        this.settings = settings;
        this.tasks = tasks;
    }

    void load(UUID uuid) {
        long generation = cacheGeneration(uuid);
        storage.hasAcceptedRules(uuid).thenAccept(accepted -> storeLoadedAcceptance(uuid, generation, accepted));
    }

    boolean isAccepted(UUID uuid) {
        if (!settings.get().rules().enabled()) return true;
        Boolean value = acceptedCache.get(uuid);
        return value != null && value;
    }

    private boolean isRestricted(Player player) {
        return player != null && settings.get().rules().enabled() && settings.get().rules().restrict() && !isAccepted(player.getUniqueId());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        joinLocations.put(uuid, player.getLocation());
        long generation = cacheGeneration(uuid);
        storage.hasAcceptedRules(uuid).thenAccept(accepted -> {
            if (storeLoadedAcceptance(uuid, generation, accepted) && !accepted && settings.get().rules().enabled()) {
                tasks.playerLater(player, () -> {
                    if (player.isOnline() && !isAccepted(uuid)) showRules(player);
                }, 140L);
            }
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        cacheGenerations.remove(uuid);
        acceptedCache.remove(uuid);
        joinLocations.remove(uuid);
        lastReminderTimes.remove(uuid);
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (!isRestricted(player)) return;
        Location joinLoc = joinLocations.computeIfAbsent(player.getUniqueId(), k -> player.getLocation());
        Location to = event.getTo();
        if (to == null) return;
        if (!to.getWorld().equals(joinLoc.getWorld()) || to.distanceSquared(joinLoc) > 9.0) {
            event.setTo(joinLoc);
            remind(player);
        }
    }

    @EventHandler
    public void onBreak(BlockBreakEvent e) { if (isRestricted(e.getPlayer())) { e.setCancelled(true); remind(e.getPlayer()); } }

    @EventHandler
    public void onPlace(BlockPlaceEvent e) { if (isRestricted(e.getPlayer())) { e.setCancelled(true); remind(e.getPlayer()); } }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) { if (isRestricted(e.getPlayer())) { e.setCancelled(true); remind(e.getPlayer()); } }

    @EventHandler
    public void onInteractEntity(PlayerInteractEntityEvent e) { if (isRestricted(e.getPlayer())) { e.setCancelled(true); remind(e.getPlayer()); } }

    @EventHandler
    public void onInteractAtEntity(PlayerInteractAtEntityEvent e) { if (isRestricted(e.getPlayer())) { e.setCancelled(true); remind(e.getPlayer()); } }

    @EventHandler
    public void onDamage(EntityDamageByEntityEvent event) {
        Player player = event.getDamager() instanceof Player damager ? damager : (event.getDamager() instanceof Projectile proj && proj.getShooter() instanceof Player shooter ? shooter : null);
        if (isRestricted(player)) { event.setCancelled(true); remind(player); }
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent e) { if (isRestricted(e.getPlayer())) { e.setCancelled(true); remind(e.getPlayer()); } }

    @EventHandler
    public void onPickup(EntityPickupItemEvent e) { if (e.getEntity() instanceof Player p && isRestricted(p)) { e.setCancelled(true); remind(p); } }

    @EventHandler
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (event.getPlayer() instanceof Player player && isRestricted(player) && !(event.getView().getTopInventory().getHolder() instanceof GuideMenuView)) {
            event.setCancelled(true);
            remind(player);
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player) || !isRestricted(player)) return;
        if (event.getView().getTopInventory().getHolder() instanceof GuideMenuView) {
            if (event.getClickedInventory() == player.getInventory() || event.getRawSlot() >= 45) {
                event.setCancelled(true);
                remind(player);
            }
            return;
        }
        event.setCancelled(true);
        remind(player);
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent e) { if (e.getWhoClicked() instanceof Player p && isRestricted(p)) { e.setCancelled(true); remind(p); } }

    @EventHandler
    public void onVehicleEnter(VehicleEnterEvent e) { if (e.getEntered() instanceof Player p && isRestricted(p)) { e.setCancelled(true); remind(p); } }

    @EventHandler
    public void onVehicleMove(VehicleMoveEvent event) {
        if (!settings.get().rules().enabled() || !settings.get().rules().restrict()) return;
        for (Entity passenger : event.getVehicle().getPassengers()) {
            if (passenger instanceof Player player && !isAccepted(player.getUniqueId())) {
                Location joinLoc = joinLocations.computeIfAbsent(player.getUniqueId(), k -> player.getLocation());
                Location to = event.getTo();
                if (!to.getWorld().equals(joinLoc.getWorld()) || to.distanceSquared(joinLoc) > 9.0) {
                    event.getVehicle().teleport(joinLoc);
                    remind(player);
                }
                break;
            }
        }
    }

    @EventHandler
    public void onChat(AsyncChatEvent e) { if (isRestricted(e.getPlayer())) { e.setCancelled(true); remind(e.getPlayer()); } }

    @EventHandler
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (!isRestricted(player)) return;
        String raw = event.getMessage().trim().toLowerCase(Locale.ROOT);
        if (raw.startsWith("/")) raw = raw.substring(1);
        String cmd = raw.split("\\s+")[0];
        if (cmd.equals("rehber") || cmd.equals("guide") || cmd.equals("basla") || cmd.equals("rules") || cmd.equals("kurallar")) return;
        event.setCancelled(true);
        remind(player);
    }

    private void remind(Player player) {
        long now = System.currentTimeMillis();
        Long last = lastReminderTimes.get(player.getUniqueId());
        if (last == null || now - last >= 10_000L) {
            lastReminderTimes.put(player.getUniqueId(), now);
            tasks.player(player, () -> messages.send(player, "rules-restricted-reminder", Map.of()));
        }
    }

    void showRules(Player player) {
        if (!player.isOnline()) return;
        RulesSettings config = settings.get().rules();
        List<Component> pages = new ArrayList<>();
        String[] header = plain(messages.text(player, "rules-header")).split("\n", 2);
        Component page = Component.text(header[0], NamedTextColor.DARK_RED, TextDecoration.BOLD).append(Component.newline());
        int used = bookLines(header[0]);
        if (header.length > 1 && !header[1].isBlank()) {
            page = page.append(Component.text(header[1].strip(), NamedTextColor.DARK_GRAY)).append(Component.newline());
            used += bookLines(header[1]);
        }
        page = page.append(Component.text("▬▬▬▬▬▬▬▬▬▬▬", NamedTextColor.GOLD)).append(Component.newline()).append(Component.newline());
        used += 2;
        List<String> texts = config.linesByLanguage().get(messages.language(player));
        if (texts == null) texts = config.lines();
        if (texts.isEmpty()) texts = config.linesKeys().stream().map(key -> messages.text(player, key)).toList();
        for (String text : texts) {
            String line = plain(text);
            int needed = bookLines(line) + 1;
            if (used + needed > BOOK_PAGE_LINES) {
                pages.add(page);
                page = Component.empty();
                used = 0;
            }
            page = page.append(ruleLine(line)).append(Component.newline()).append(Component.newline());
            used += needed;
        }
        if (used + 3 > BOOK_PAGE_LINES) {
            pages.add(page);
            page = Component.empty();
        }
        boolean accepted = isAccepted(player.getUniqueId());
        Component ending = accepted
                ? Component.text(plain(messages.text(player, "rules-already-accepted")), NamedTextColor.DARK_GREEN)
                : Component.text(plain(messages.text(player, "rules-accept-prompt")), NamedTextColor.DARK_GREEN, TextDecoration.BOLD).clickEvent(ClickEvent.runCommand("/rehber kurallar kabul"));
        pages.add(page.append(ending));
        player.openBook(Book.book(Component.text("StartFlow"), Component.text("StartFlow"), pages));
    }

    private static final Pattern NUMBERED_RULE = Pattern.compile("^(\\d+[.)])\\s*(.*)$", Pattern.DOTALL);
    private static final int BOOK_PAGE_LINES = 14;
    private static final int BOOK_LINE_CHARS = 19;

    private int bookLines(String text) {
        return Math.max(1, (text.strip().length() + BOOK_LINE_CHARS - 1) / BOOK_LINE_CHARS);
    }

    private Component ruleLine(String line) {
        Matcher matcher = NUMBERED_RULE.matcher(line);
        if (!matcher.matches()) return Component.text(line, NamedTextColor.BLACK);
        return Component.text(matcher.group(1) + " ", NamedTextColor.DARK_RED, TextDecoration.BOLD)
                .append(Component.text(matcher.group(2), NamedTextColor.BLACK));
    }

    private String plain(String text) {
        String raw = text.replace("<prefix>", "");
        return PlainTextComponentSerializer.plainText().serialize(MiniMessage.miniMessage().deserialize(raw)).strip();
    }

    void accept(Player player) {
        UUID uuid = player.getUniqueId();
        if (isAccepted(uuid)) {
            messages.send(player, "rules-already-accepted", Map.of());
            return;
        }
        storage.acceptRules(uuid).thenAccept(saved -> {
            acceptedCache.put(uuid, true);
            joinLocations.remove(uuid);
            starterKit.onRulesAccepted(player);
            storage.claimReward(uuid, "rules").thenAccept(rewardGranted -> {
                if (rewardGranted) executeRewardCommands(player);
            });
            tasks.player(player, () -> {
                messages.send(player, "rules-accepted-message", Map.of("player", player.getName()));
                if (settings.get().effects()) {
                    Component title = messages.message(player, "rules-accepted-title", Map.of("player", player.getName()));
                    Component subtitle = messages.message(player, "rules-accepted-subtitle", Map.of("player", player.getName()));
                    player.showTitle(Title.title(title, subtitle, Title.Times.times(Duration.ofMillis(200), Duration.ofSeconds(2), Duration.ofMillis(300))));
                    player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
                }
            });
        });
    }

    CompletableFuture<Void> reset(UUID uuid) {
        advanceCacheGeneration(uuid);
        acceptedCache.remove(uuid);
        Player player = Bukkit.getPlayer(uuid);
        if (player != null && player.isOnline()) joinLocations.put(uuid, player.getLocation());
        else cacheGenerations.remove(uuid);
        return storage.resetRules(uuid);
    }

    private long cacheGeneration(UUID uuid) {
        return cacheGenerations.compute(uuid, (id, generation) -> generation == null ? 1L : generation + 1L);
    }

    private void advanceCacheGeneration(UUID uuid) {
        cacheGenerations.compute(uuid, (id, generation) -> generation == null ? 1L : generation + 1L);
    }

    private boolean storeLoadedAcceptance(UUID uuid, long generation, boolean accepted) {
        AtomicBoolean stored = new AtomicBoolean();
        cacheGenerations.computeIfPresent(uuid, (id, current) -> {
            if (current == generation) {
                acceptedCache.put(uuid, accepted);
                stored.set(true);
            }
            return current;
        });
        return stored.get();
    }

    private void executeRewardCommands(Player player) {
        List<String> commands = settings.get().rules().acceptRewardCommands();
        if (commands == null || commands.isEmpty()) return;
        tasks.global(() -> {
            for (String command : commands) {
                String formatted = command.replace("<player>", player.getName()).replace("<uuid>", player.getUniqueId().toString());
                String cmd = formatted.startsWith("/") ? formatted.substring(1).trim() : formatted.trim();
                try {
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
                } catch (Exception error) {
                    plugin.getLogger().warning("Rules reward command failed for " + player.getName() + ": " + command);
                }
            }
        });
    }
}
