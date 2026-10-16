package net.clodo.startflow;

import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.Location;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

final class PlayTimeActivity implements Listener {
    private static final double MIN_DISTANCE = 3.0;
    private static final double MAX_STEP = 5.0;

    private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();

    void register(UUID uuid) {
        sessions.put(uuid, new Session(System.currentTimeMillis()));
    }

    void unregister(UUID uuid) {
        sessions.remove(uuid);
    }

    boolean checkAndResetActive(UUID uuid, int afkAfterSeconds) {
        Session session = sessions.get(uuid);
        if (session == null) return false;

        long now = System.currentTimeMillis();
        boolean withinAfkLimit = now - session.lastValidActionTime <= afkAfterSeconds * 1000L;
        boolean walked = session.distance >= MIN_DISTANCE && session.rotated;
        boolean active = withinAfkLimit && (session.acted || walked);

        session.distance = 0.0;
        session.rotated = false;
        session.acted = false;
        return active;
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) return;
        double dx = to.getX() - from.getX();
        double dy = to.getY() - from.getY();
        double dz = to.getZ() - from.getZ();
        double moved = Math.sqrt(dx * dx + dy * dy + dz * dz);
        boolean rotationChanged = Math.abs(to.getYaw() - from.getYaw()) > 1.0f || Math.abs(to.getPitch() - from.getPitch()) > 1.0f;

        Session session = sessions.get(event.getPlayer().getUniqueId());
        if (session == null) return;

        if (moved > 0.0 && moved <= MAX_STEP) {
            session.distance += moved;
            session.lastValidActionTime = System.currentTimeMillis();
        }
        if (rotationChanged) {
            session.rotated = true;
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        recordAction(event.getPlayer().getUniqueId());
    }

    @EventHandler(ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        recordAction(event.getPlayer().getUniqueId());
    }

    @EventHandler(ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        recordAction(event.getPlayer().getUniqueId());
    }

    private void recordAction(UUID uuid) {
        Session session = sessions.get(uuid);
        if (session != null) {
            session.acted = true;
            session.lastValidActionTime = System.currentTimeMillis();
        }
    }

    private static final class Session {
        private long lastValidActionTime;
        private volatile double distance;
        private volatile boolean rotated;
        private volatile boolean acted;

        private Session(long now) {
            this.lastValidActionTime = now;
            this.distance = 0.0;
            this.rotated = false;
            this.acted = false;
        }
    }
}
