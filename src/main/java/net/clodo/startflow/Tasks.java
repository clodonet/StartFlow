package net.clodo.startflow;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.concurrent.TimeUnit;

final class Tasks {
    private static final boolean FOLIA;

    static {
        boolean folia;
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            folia = true;
        } catch (ClassNotFoundException error) {
            folia = false;
        }
        FOLIA = folia;
    }

    private final StartFlowPlugin plugin;

    Tasks(StartFlowPlugin plugin) {
        this.plugin = plugin;
    }

    static boolean isFolia() {
        return FOLIA;
    }

    void global(Runnable runnable) {
        if (FOLIA) {
            Bukkit.getGlobalRegionScheduler().run(plugin, task -> runnable.run());
        } else {
            Bukkit.getScheduler().runTask(plugin, runnable);
        }
    }

    void globalLater(Runnable runnable, long delayTicks) {
        long ticks = Math.max(1L, delayTicks);
        if (FOLIA) {
            Bukkit.getGlobalRegionScheduler().runDelayed(plugin, task -> runnable.run(), ticks);
        } else {
            Bukkit.getScheduler().runTaskLater(plugin, runnable, ticks);
        }
    }

    TaskRef globalRepeating(Runnable runnable, long initialDelayTicks, long periodTicks) {
        long delay = Math.max(1L, initialDelayTicks);
        long period = Math.max(1L, periodTicks);
        if (FOLIA) {
            io.papermc.paper.threadedregions.scheduler.ScheduledTask task =
                    Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, t -> runnable.run(), delay, period);
            return task::cancel;
        } else {
            BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, runnable, delay, period);
            return task::cancel;
        }
    }

    void player(Player player, Runnable runnable) {
        if (player == null || !player.isOnline()) return;
        if (FOLIA) {
            player.getScheduler().run(plugin, task -> {
                if (player.isOnline()) runnable.run();
            }, () -> {});
        } else {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (player.isOnline()) runnable.run();
            });
        }
    }

    void playerLater(Player player, Runnable runnable, long delayTicks) {
        if (player == null || !player.isOnline()) return;
        long ticks = Math.max(1L, delayTicks);
        if (FOLIA) {
            player.getScheduler().runDelayed(plugin, task -> {
                if (player.isOnline()) runnable.run();
            }, () -> {}, ticks);
        } else {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline()) runnable.run();
            }, ticks);
        }
    }

    TaskRef playerRepeating(Player player, Runnable runnable, long initialDelayTicks, long periodTicks) {
        if (player == null) return () -> {};
        long delay = Math.max(1L, initialDelayTicks);
        long period = Math.max(1L, periodTicks);
        if (FOLIA) {
            io.papermc.paper.threadedregions.scheduler.ScheduledTask task =
                    player.getScheduler().runAtFixedRate(plugin, t -> {
                        if (player.isOnline()) runnable.run();
                    }, () -> {}, delay, period);
            return task != null ? task::cancel : () -> {};
        } else {
            BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
                if (player.isOnline()) runnable.run();
            }, delay, period);
            return task::cancel;
        }
    }

    void region(Location location, Runnable runnable) {
        if (location == null || location.getWorld() == null) return;
        if (FOLIA) {
            Bukkit.getRegionScheduler().run(plugin, location, task -> runnable.run());
        } else {
            Bukkit.getScheduler().runTask(plugin, runnable);
        }
    }

    void sender(CommandSender sender, Runnable runnable) {
        if (sender instanceof Player player) {
            player(player, runnable);
        } else {
            global(runnable);
        }
    }

    void async(Runnable runnable) {
        if (FOLIA) {
            Bukkit.getAsyncScheduler().runNow(plugin, task -> runnable.run());
        } else {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, runnable);
        }
    }

    TaskRef asyncRepeating(Runnable runnable, long initialDelay, long period, TimeUnit unit) {
        if (FOLIA) {
            io.papermc.paper.threadedregions.scheduler.ScheduledTask task =
                    Bukkit.getAsyncScheduler().runAtFixedRate(plugin, t -> runnable.run(), Math.max(1L, initialDelay), Math.max(1L, period), unit);
            return task::cancel;
        } else {
            long delayTicks = Math.max(1L, unit.toSeconds(initialDelay) * 20L);
            long periodTicks = Math.max(1L, unit.toSeconds(period) * 20L);
            BukkitTask task = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, runnable, delayTicks, periodTicks);
            return task::cancel;
        }
    }

    @FunctionalInterface
    interface TaskRef {
        void cancel();
    }
}
