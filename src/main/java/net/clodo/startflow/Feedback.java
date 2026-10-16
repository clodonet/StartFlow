package net.clodo.startflow;

import net.kyori.adventure.title.Title;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.FireworkEffect;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.inventory.meta.FireworkMeta;

import java.time.Duration;
import java.util.Map;
import java.util.function.Supplier;

final class Feedback {
    private final StartFlowPlugin plugin;
    private final Messages messages;
    private final Supplier<GuideSettings> settings;
    private final Tasks tasks;

    Feedback(StartFlowPlugin plugin, Messages messages, Supplier<GuideSettings> settings, Tasks tasks) {
        this.plugin = plugin;
        this.messages = messages;
        this.settings = settings;
        this.tasks = tasks;
    }

    void completed(ProgressService.Completion completion) {
        Player player = Bukkit.getPlayer(completion.uuid());
        if (player == null || !player.isOnline()) return;
        tasks.player(player, () -> {
            if (!player.isOnline()) return;
            Step step = settings.get().step(completion.stepId());
            if (step == null) return;
            int total = (int) settings.get().steps().stream().filter(Step::enabled).count();
            int completed = completion.completed();
            if (!settings.get().effects()) {
                messages.send(player, "step-complete-chat", Map.of("step", messages.text(player, step.displayNameKey()), "current", Integer.toString(completed), "total", Integer.toString(total)));
                return;
            }
            if (completed >= total) finish(player, step, completed, total);
            else advance(player, step, completed, total);
        });
    }

    private void advance(Player player, Step step, int completed, int total) {
        player.showTitle(Title.title(messages.message(player, "step-complete-title", Map.of("step", messages.text(player, step.displayNameKey()))), messages.message(player, "step-complete-subtitle", Map.of("current", Integer.toString(completed), "total", Integer.toString(total))), Title.Times.times(Duration.ofMillis(250), Duration.ofSeconds(2), Duration.ofMillis(400))));
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.9f, 1.2f);
        burst(player.getLocation(), 18);
        bar(player, completed, total);
    }

    private void finish(Player player, Step step, int completed, int total) {
        player.showTitle(Title.title(messages.message(player, "guide-complete-title", Map.of()), messages.message(player, "guide-complete-subtitle", Map.of("step", messages.text(player, step.displayNameKey()), "current", Integer.toString(completed), "total", Integer.toString(total))), Title.Times.times(Duration.ofMillis(250), Duration.ofSeconds(3), Duration.ofMillis(600))));
        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
        burst(player.getLocation(), 35);
        Firework firework = player.getWorld().spawn(player.getLocation(), Firework.class);
        FireworkMeta meta = firework.getFireworkMeta();
        meta.addEffect(FireworkEffect.builder().withColor(org.bukkit.Color.LIME).with(FireworkEffect.Type.BALL_LARGE).build());
        meta.setPower(1);
        firework.setFireworkMeta(meta);
        bar(player, completed, total);
    }

    private void burst(Location location, int count) {
        location.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, location.clone().add(0, 1, 0), count, 0.6, 0.8, 0.6, 0.1);
    }

    private void bar(Player player, int completed, int total) {
        BossBar bar = Bukkit.createBossBar(PlainTextComponentSerializer.plainText().serialize(messages.message(player, "boss-progress", Map.of("current", Integer.toString(completed), "total", Integer.toString(total)))), BarColor.GREEN, BarStyle.SOLID);
        bar.setProgress(total == 0 ? 1 : Math.min(1, (double) completed / total));
        bar.addPlayer(player);
        tasks.playerLater(player, bar::removeAll, 100L);
    }
}
