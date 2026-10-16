package net.clodo.startflow;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class GuideCommands implements CommandExecutor, TabCompleter {
    private final StartFlowPlugin plugin;
    private final ProgressService progress;
    private final Messages messages;
    private final GuideSettingsLoader settings;
    private final GuideMenu menu;
    private final AdminMenu adminMenu;
    private final ReportMenu reportMenu;
    private final StepTriggers triggers;
    private final DailyReward dailyReward;
    private final PlayTime playTime;
    private final RulesGate rulesGate;
    private final StarterKit starterKit;
    private final Templates templates;
    private final Tasks tasks;

    GuideCommands(StartFlowPlugin plugin, ProgressService progress, Messages messages, GuideSettingsLoader settings, GuideMenu menu, AdminMenu adminMenu, ReportMenu reportMenu, StepTriggers triggers, DailyReward dailyReward, PlayTime playTime, RulesGate rulesGate, StarterKit starterKit, Templates templates, Tasks tasks) {
        this.plugin = plugin;
        this.progress = progress;
        this.messages = messages;
        this.settings = settings;
        this.menu = menu;
        this.adminMenu = adminMenu;
        this.reportMenu = reportMenu;
        this.triggers = triggers;
        this.dailyReward = dailyReward;
        this.playTime = playTime;
        this.rulesGate = rulesGate;
        this.starterKit = starterKit;
        this.templates = templates;
        this.tasks = tasks;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] arguments) {
        return isGuide(command) ? guide(sender, label, arguments) : startflow(sender, arguments);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] arguments) {
        if (isGuide(command)) {
            if (!sender.hasPermission("startflow.use")) return List.of();
            if (Words.command(label).equals("kurallar") && arguments.length == 1) return matching(Words.rulesActions(turkish(sender)), arguments[0]);
            if (arguments.length == 1) return matching(Words.guide(turkish(sender)), arguments[0]);
            if (arguments.length == 2 && Words.command(arguments[0]).equals("kurallar")) return matching(Words.rulesActions(turkish(sender)), arguments[1]);
            return List.of();
        }
        if (!sender.hasPermission("startflow.admin")) return List.of();
        if (arguments.length == 1) return matching(Words.startflow(turkish(sender)), arguments[0]);
        if (Words.command(arguments[0]).equals("rapor") && arguments.length == 2) return matching(List.of("csv"), arguments[1]);
        if (Words.command(arguments[0]).equals("sablon")) {
            if (arguments.length == 2) return matching(Templates.NAMES, arguments[1]);
            if (arguments.length == 3) return matching(turkish(sender) ? List.of("onayla") : List.of("confirm"), arguments[2]);
            return List.of();
        }
        if (!Words.command(arguments[0]).equals("oyuncu")) return List.of();
        if (arguments.length == 2) return playerNames(arguments[1]);
        if (arguments.length == 3) return matching(Words.playerActions(turkish(sender)), arguments[2]);
        if (arguments.length == 4) {
            String action = Words.command(arguments[2]);
            if (action.equals("adim") || action.equals("sifirla")) return matching(settings.settings().stepIds().stream().sorted().toList(), arguments[3]);
        }
        return List.of();
    }

    private boolean isGuide(Command command) {
        String name = command.getName();
        return !name.equalsIgnoreCase("startflow") && !name.equalsIgnoreCase("sf");
    }

    private boolean guide(CommandSender sender, String label, String[] arguments) {
        if (!(sender instanceof Player player)) return true;
        if (!player.hasPermission("startflow.use")) {
            messages.send(player, "admin-no-permission", Map.of());
            return true;
        }
        if (Words.command(label).equals("kurallar")) {
            if (arguments.length >= 1 && Words.command(arguments[0]).equals("kabul")) rulesGate.accept(player);
            else rulesGate.showRules(player);
            return true;
        }
        if (arguments.length == 0) { menu.open(player); return true; }
        if (arguments.length == 1 && Words.command(arguments[0]).equals("kurallar")) { rulesGate.showRules(player); return true; }
        if (arguments.length == 2 && Words.command(arguments[0]).equals("kurallar") && Words.command(arguments[1]).equals("kabul")) { rulesGate.accept(player); return true; }
        if (arguments.length != 1) { messages.send(player, "usage-rehber", Map.of()); return true; }
        switch (Words.command(arguments[0])) {
            case "sure" -> playTime.showStatus(player);
            case "gunluk" -> dailyReward.showStatus(player);
            case "atla" -> progress.skip(player.getUniqueId()).whenComplete((u, err) -> after(player, err, "guide-skip-success"));
            case "sifirla" -> progress.reset(player.getUniqueId()).whenComplete((u, err) -> { triggers.invalidate(player.getUniqueId()); after(player, err, "guide-reset-success"); });
            default -> messages.send(player, "usage-rehber", Map.of());
        }
        return true;
    }

    private boolean startflow(CommandSender sender, String[] arguments) {
        if (!sender.hasPermission("startflow.admin")) {
            messages.send(sender, "admin-no-permission", Map.of());
            return true;
        }
        if (arguments.length == 0 || (arguments.length == 1 && Words.command(arguments[0]).equals("menu"))) {
            if (sender instanceof Player player) adminMenu.open(player);
            else messages.send(sender, "usage-startflow", Map.of());
            return true;
        }
        if (arguments.length == 1 && Words.command(arguments[0]).equals("yenile")) {
            settings.reload(messages::reload).whenComplete((unused, error) -> tasks.global(() -> {
                if (error == null) triggers.refresh();
                String configError = settings.lastError();
                tasks.sender(sender, () -> {
                    if (error != null) messages.send(sender, "storage-error", Map.of());
                    else if (configError != null) messages.send(sender, "reload-config-error", Map.of("error", configError));
                    else messages.send(sender, "reload-success", Map.of());
                });
            }));
            return true;
        }
        if (arguments.length == 1 && Words.command(arguments[0]).equals("about")) {
            messages.send(sender, "about", Map.of("version", plugin.getPluginMeta().getVersion()));
            return true;
        }
        if (arguments.length >= 1 && Words.command(arguments[0]).equals("rapor")) return reportCommand(sender, arguments);
        if (arguments.length >= 1 && Words.command(arguments[0]).equals("sablon")) return templates.handle(sender, arguments);
        if (arguments.length >= 3 && Words.command(arguments[0]).equals("oyuncu")) return playerCommand(sender, arguments);
        messages.send(sender, "usage-startflow", Map.of());
        return true;
    }

    private boolean reportCommand(CommandSender sender, String[] arguments) {
        if (!sender.hasPermission("startflow.admin")) {
            messages.send(sender, "admin-no-permission", Map.of());
            return true;
        }
        if (arguments.length == 1) {
            reportMenu.sendSummary(sender);
            return true;
        }
        if (arguments.length == 2 && Words.command(arguments[1]).equals("csv")) {
            reportMenu.exportCsv(sender);
            return true;
        }
        messages.send(sender, "usage-startflow", Map.of());
        return true;
    }

    private boolean playerCommand(CommandSender sender, String[] arguments) {
        OfflinePlayer target = Bukkit.getOfflinePlayerIfCached(arguments[1]);
        if (target == null || target.getUniqueId() == null) {
            messages.send(sender, "player-not-found", Map.of("player", arguments[1]));
            return true;
        }
        String action = Words.command(arguments[2]);
        if (action.equals("adim") && arguments.length == 4) {
            Step step = settings.settings().step(arguments[3]);
            if (step == null) { messages.send(sender, "unknown-step", Map.of("step", arguments[3])); return true; }
            progress.completeStep(target.getUniqueId(), step.id()).whenComplete((in, err) -> sendAdmin(sender, err, "admin-step-complete", Map.of("player", name(target), "step", step.id())));
            return true;
        }
        if (action.equals("sifirla") && arguments.length == 3) {
            progress.reset(target.getUniqueId()).whenComplete((u, err) -> { triggers.invalidate(target.getUniqueId()); sendAdmin(sender, err, "admin-reset-success", Map.of("player", name(target))); });
            return true;
        }
        if (action.equals("sifirla") && arguments.length == 4) {
            Step step = settings.settings().step(arguments[3]);
            if (step == null) { messages.send(sender, "unknown-step", Map.of("step", arguments[3])); return true; }
            progress.resetStep(target.getUniqueId(), step.id()).whenComplete((u, err) -> { triggers.invalidate(target.getUniqueId()); sendAdmin(sender, err, "admin-step-reset", Map.of("player", name(target), "step", step.id())); });
            return true;
        }
        if (action.equals("bilgi") && arguments.length == 3) {
            progress.getProgress(target.getUniqueId()).whenComplete((value, error) -> sendAdmin(sender, error, "admin-info", error == null ? Map.of("player", name(target), "steps", String.join(", ", value.completedSteps())) : Map.of()));
            return true;
        }
        if (action.equals("gunluk-sifirla") && arguments.length == 3) {
            dailyReward.reset(target.getUniqueId()).whenComplete((unused, error) -> sendAdmin(sender, error, "admin-daily-reset-success", Map.of("player", name(target))));
            return true;
        }
        if (action.equals("sure-sifirla") && arguments.length == 3) {
            playTime.reset(target.getUniqueId()).whenComplete((unused, error) -> sendAdmin(sender, error, "admin-playtime-reset-success", Map.of("player", name(target))));
            return true;
        }
        if (action.equals("kural-sifirla") && arguments.length == 3) {
            rulesGate.reset(target.getUniqueId()).whenComplete((unused, error) -> sendAdmin(sender, error, "admin-rules-reset-success", Map.of("player", name(target))));
            return true;
        }
        if (action.equals("kit-sifirla") && arguments.length == 3) {
            starterKit.reset(target.getUniqueId()).whenComplete((unused, error) -> sendAdmin(sender, error, "admin-kit-reset-success", Map.of("player", name(target))));
            return true;
        }
        messages.send(sender, "usage-startflow", Map.of());
        return true;
    }

    private void sendAdmin(CommandSender sender, Throwable error, String key, Map<String, String> tokens) {
        tasks.sender(sender, () -> messages.send(sender, error == null ? key : "storage-error", tokens));
    }

    private void after(Player player, Throwable error, String key) {
        tasks.player(player, () -> messages.send(player, error == null ? key : "storage-error", Map.of()));
    }

    private List<String> playerNames(String typed) {
        String prefix = Words.fold(typed);
        List<String> results = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            String name = player.getName();
            if (Words.fold(name).startsWith(prefix) && seen.add(name)) {
                results.add(name);
                if (results.size() >= 50) return results;
            }
        }
        if (typed.length() < 2) return results;
        for (OfflinePlayer offline : Bukkit.getOfflinePlayers()) {
            String name = offline.getName();
            if (name != null && Words.fold(name).startsWith(prefix) && seen.add(name)) {
                results.add(name);
                if (results.size() >= 50) break;
            }
        }
        return results;
    }

    private List<String> matching(List<String> values, String typed) {
        String prefix = Words.fold(typed);
        return values.stream().filter(value -> Words.fold(value).startsWith(prefix)).toList();
    }

    private boolean turkish(CommandSender sender) {
        return messages.language(sender).equals("tr");
    }

    private String name(OfflinePlayer player) {
        return player.getName() == null ? player.getUniqueId().toString() : player.getName();
    }
}
