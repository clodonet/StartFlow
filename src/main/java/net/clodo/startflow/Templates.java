package net.clodo.startflow;

import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;

final class Templates {
    static final List<String> NAMES = List.of("survival", "skyblock", "anarsi");
    private static final DateTimeFormatter BACKUP_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS");

    private final StartFlowPlugin plugin;
    private final ExecutorService executor;
    private final GuideSettingsLoader settings;
    private final Messages messages;
    private final StepTriggers triggers;
    private final Tasks tasks;
    private final Map<String, Confirmation> pending = new ConcurrentHashMap<>();

    Templates(StartFlowPlugin plugin, ExecutorService executor, GuideSettingsLoader settings, Messages messages, StepTriggers triggers, Tasks tasks) {
        this.plugin = plugin;
        this.executor = executor;
        this.settings = settings;
        this.messages = messages;
        this.triggers = triggers;
        this.tasks = tasks;
    }

    boolean handle(CommandSender sender, String[] arguments) {
        if (!sender.hasPermission("startflow.admin")) {
            messages.send(sender, "admin-no-permission", Map.of());
            return true;
        }
        if (arguments.length == 1) {
            list(sender);
            return true;
        }
        String templateName = Words.fold(arguments[1]);
        if (!isTemplate(templateName)) {
            messages.send(sender, "template-unknown", Map.of("name", arguments[1]));
            return true;
        }
        boolean explicitConfirm = arguments.length >= 3 && Words.command(arguments[2]).equals("onayla");
        if (explicitConfirm || isConfirmed(sender, templateName)) {
            apply(sender, templateName);
            return true;
        }
        requestConfirmation(sender, templateName);
        messages.send(sender, "template-confirm-warning", Map.of("name", templateName), Map.of("template", templateName));
        return true;
    }

    boolean isTemplate(String name) {
        return NAMES.contains(Words.fold(name));
    }

    void list(CommandSender sender) {
        messages.send(sender, "template-list", Map.of());
    }

    boolean isConfirmed(CommandSender sender, String template) {
        String key = senderKey(sender);
        Confirmation confirmation = pending.get(key);
        if (confirmation != null && confirmation.matches(template)) {
            pending.remove(key);
            return true;
        }
        return false;
    }

    void requestConfirmation(CommandSender sender, String template) {
        pending.put(senderKey(sender), new Confirmation(template, System.currentTimeMillis() + 30_000L));
    }

    void apply(CommandSender sender, String templateName) {
        CompletableFuture.runAsync(() -> {
            try {
                File dataFolder = plugin.getDataFolder();
                File configFile = new File(dataFolder, "config.yml");
                File backupDir = new File(dataFolder, "backups");
                if (!backupDir.exists()) {
                    backupDir.mkdirs();
                }
                String stamp = BACKUP_FORMAT.format(LocalDateTime.now());
                File chosenBackup = new File(backupDir, "config-" + stamp + ".yml");
                if (configFile.exists()) {
                    int suffix = 1;
                    while (true) {
                        try {
                            Files.copy(configFile.toPath(), chosenBackup.toPath());
                            break;
                        } catch (java.nio.file.FileAlreadyExistsException ignored) {
                            chosenBackup = new File(backupDir, "config-" + stamp + "-" + suffix++ + ".yml");
                        }
                    }
                }
                final File backupFile = chosenBackup;
                YamlConfiguration templateConfig = loadTemplate(templateName);
                if (templateConfig == null) {
                    throw new IllegalStateException("Template not found: " + templateName);
                }
                List<Map<?, ?>> steps = templateConfig.getMapList("steps");
                int count = steps.size();

                YamlConfiguration config = YamlConfiguration.loadConfiguration(configFile);
                config.set("steps", templateConfig.get("steps"));
                config.save(configFile);

                settings.reload(messages::reload).whenComplete((unused, error) -> tasks.global(() -> {
                    if (error == null) {
                        triggers.refresh();
                        tasks.sender(sender, () -> messages.send(sender, "template-apply-" + templateName, Map.of(
                                "count", String.valueOf(count),
                                "backup", "plugins/StartFlow/backups/" + backupFile.getName()
                        )));
                    } else {
                        tasks.sender(sender, () -> messages.send(sender, "template-error", Map.of(
                                "error", error.getMessage() != null ? error.getMessage() : "reload failed"
                        )));
                    }
                }));
            } catch (Exception error) {
                tasks.sender(sender, () -> messages.send(sender, "template-error", Map.of(
                        "error", error.getMessage() != null ? error.getMessage() : "unknown"
                )));
            }
        }, executor);
    }

    private YamlConfiguration loadTemplate(String name) throws Exception {
        File custom = new File(plugin.getDataFolder(), "templates/" + name + ".yml");
        if (custom.exists()) {
            return YamlConfiguration.loadConfiguration(custom);
        }
        InputStream stream = plugin.getResource("templates/" + name + ".yml");
        if (stream == null) {
            return null;
        }
        try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            return YamlConfiguration.loadConfiguration(reader);
        }
    }

    private String senderKey(CommandSender sender) {
        return sender instanceof Player player ? player.getUniqueId().toString() : "console";
    }

    private record Confirmation(String template, long expiresAt) {
        boolean matches(String target) {
            return template.equalsIgnoreCase(target) && System.currentTimeMillis() <= expiresAt;
        }
    }
}
