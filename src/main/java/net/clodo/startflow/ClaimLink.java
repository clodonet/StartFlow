package net.clodo.startflow;

import org.bukkit.Bukkit;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.EventExecutor;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.UUID;
import java.util.function.Consumer;

final class ClaimLink implements Listener {
    private final StartFlowPlugin plugin;
    private Consumer<UUID> creationListener;
    private Method claimGetter;
    private Field ownerField;
    private Method ownerMethod;
    private Field parentField;
    private Method parentMethod;
    private boolean available;

    ClaimLink(StartFlowPlugin plugin) {
        this.plugin = plugin;
        initialize();
    }

    void retry() {
        if (!available) {
            initialize();
        }
    }

    boolean isAvailable() {
        return available;
    }

    void onClaimCreated(Consumer<UUID> listener) {
        this.creationListener = listener;
    }

    private void initialize() {
        if (!Bukkit.getPluginManager().isPluginEnabled("GriefPrevention")) {
            plugin.getLogger().info("GriefPrevention not found; claim trigger is disabled.");
            return;
        }
        try {
            Class<?> eventClass = Class.forName("me.ryanhamshire.GriefPrevention.events.ClaimCreatedEvent");
            if (!Event.class.isAssignableFrom(eventClass)) {
                plugin.getLogger().warning("ClaimCreatedEvent is not a Bukkit event; claim trigger is disabled.");
                return;
            }
            claimGetter = eventClass.getMethod("getClaim");
            Class<?> claimClass = claimGetter.getReturnType();
            try {
                ownerField = claimClass.getField("ownerID");
            } catch (NoSuchFieldException ignored) {
                try {
                    ownerMethod = claimClass.getMethod("getOwnerID");
                } catch (NoSuchMethodException ignoredMethod) {
                }
            }
            try {
                parentField = claimClass.getField("parent");
            } catch (NoSuchFieldException ignored) {
                try {
                    parentMethod = claimClass.getMethod("getParent");
                } catch (NoSuchMethodException ignoredMethod) {
                }
            }
            @SuppressWarnings("unchecked")
            Class<? extends Event> bukkitEventClass = (Class<? extends Event>) eventClass;
            EventExecutor executor = (listener, event) -> handleEvent(event);
            Bukkit.getPluginManager().registerEvent(bukkitEventClass, this, EventPriority.MONITOR, executor, plugin, true);
            available = true;
        } catch (Exception error) {
            plugin.getLogger().warning("Could not hook into GriefPrevention: " + Errors.concise(error) + "; claim trigger is disabled.");
            available = false;
        }
    }

    private void handleEvent(Object event) {
        if (creationListener == null || claimGetter == null) {
            return;
        }
        try {
            Object claim = claimGetter.invoke(event);
            if (claim == null) {
                return;
            }
            if (isSubdivision(claim)) {
                return;
            }
            UUID owner = extractOwner(claim);
            if (owner != null) {
                creationListener.accept(owner);
            }
        } catch (Exception error) {
            plugin.getLogger().warning("Error handling claim creation: " + Errors.concise(error));
        }
    }

    private boolean isSubdivision(Object claim) {
        try {
            if (parentField != null && parentField.get(claim) != null) {
                return true;
            }
            if (parentMethod != null && parentMethod.invoke(claim) != null) {
                return true;
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    private UUID extractOwner(Object claim) {
        try {
            if (ownerField != null) {
                Object value = ownerField.get(claim);
                if (value instanceof UUID uuid) {
                    return uuid;
                }
            }
            if (ownerMethod != null) {
                Object value = ownerMethod.invoke(claim);
                if (value instanceof UUID uuid) {
                    return uuid;
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }
}
