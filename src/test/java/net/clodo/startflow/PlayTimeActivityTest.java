package net.clodo.startflow;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerMoveEvent;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayTimeActivityTest {
    @Test
    void walkingWithLookingAroundCounts() {
        PlayTimeActivity activity = new PlayTimeActivity();
        UUID uuid = UUID.randomUUID();
        activity.register(uuid);

        activity.onMove(move(uuid, new Location(null, 0, 64, 0, 0, 0), new Location(null, 4, 64, 0, 30, 0)));

        assertTrue(activity.checkAndResetActive(uuid, 60));
    }

    @Test
    void driftingWithoutLookingDoesNotCount() {
        PlayTimeActivity activity = new PlayTimeActivity();
        UUID uuid = UUID.randomUUID();
        activity.register(uuid);

        activity.onMove(move(uuid, new Location(null, 0, 64, 0), new Location(null, 4, 64, 0)));

        assertFalse(activity.checkAndResetActive(uuid, 60));
    }

    @Test
    void rotationOnlyDoesNotCount() {
        PlayTimeActivity activity = new PlayTimeActivity();
        UUID uuid = UUID.randomUUID();
        activity.register(uuid);

        activity.onMove(move(uuid, new Location(null, 0, 64, 0, 0, 0), new Location(null, 0, 64, 0, 90, 0)));

        assertFalse(activity.checkAndResetActive(uuid, 60));
    }

    @Test
    void movementAfterTheAfkLimitDoesNotCount() throws InterruptedException {
        PlayTimeActivity activity = new PlayTimeActivity();
        UUID uuid = UUID.randomUUID();
        activity.register(uuid);

        activity.onMove(move(uuid, new Location(null, 0, 64, 0, 0, 0), new Location(null, 4, 64, 0, 30, 0)));
        Thread.sleep(5L);

        assertFalse(activity.checkAndResetActive(uuid, 0));
    }

    private PlayerMoveEvent move(UUID uuid, Location from, Location to) {
        Player player = (Player) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{Player.class}, (proxy, method, arguments) -> {
            if (method.getName().equals("getUniqueId")) {
                return uuid;
            }
            if (method.getName().equals("toString")) {
                return "test-player";
            }
            Class<?> type = method.getReturnType();
            if (!type.isPrimitive()) {
                return null;
            }
            if (type == boolean.class) {
                return false;
            }
            if (type == char.class) {
                return '\0';
            }
            return 0;
        });
        return new PlayerMoveEvent(player, from, to);
    }
}
