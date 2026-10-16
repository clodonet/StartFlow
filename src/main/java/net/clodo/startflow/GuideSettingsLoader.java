package net.clodo.startflow;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

final class GuideSettingsLoader {
    private final StartFlowPlugin plugin;
    private final ExecutorService executor;
    private final GuideSettingsParser parser;
    private volatile String lastError;
    private volatile GuideSettings settings = new GuideSettings("tr", true, true, List.of(), List.of());

    GuideSettingsLoader(StartFlowPlugin plugin, ExecutorService executor, VaultLink vault, ClaimLink claim) {
        this.plugin = plugin;
        this.executor = executor;
        this.parser = new GuideSettingsParser(plugin, new StepParser(plugin, vault, claim));
    }

    CompletableFuture<Void> reload(Runnable messageReload) {
        return CompletableFuture.runAsync(() -> {
            saveDefaults();
            reloadSettings();
            messageReload.run();
        }, executor);
    }

    String lastError() {
        return lastError;
    }

    GuideSettings settings() {
        return settings;
    }

    Set<String> stepIds() {
        return settings.stepIds();
    }

    Set<String> enabledStepIds() {
        return settings.enabledStepIds();
    }

    private void saveDefaults() {
        try {
            Files.createDirectories(plugin.getDataFolder().toPath());
            copy("config.yml");
            for (String language : Messages.LANGUAGES) {
                copy("messages/" + language + ".yml");
            }
        } catch (Exception error) {
            plugin.getLogger().warning("Could not create default files: " + Errors.concise(error));
        }
    }

    private void copy(String resource) throws Exception {
        Path target = plugin.getDataFolder().toPath().resolve(resource);
        if (Files.exists(target)) return;
        InputStream input = plugin.getResource(resource);
        if (input == null) throw new IllegalStateException("Bundled resource is missing: " + resource);
        try (input) {
            Files.createDirectories(target.getParent());
            Files.copy(input, target);
        }
    }

    private void reloadSettings() {
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.load(new File(plugin.getDataFolder(), "config.yml"));
            settings = parser.parse(yaml);
            lastError = null;
        } catch (Exception error) {
            lastError = Errors.concise(error);
            plugin.getLogger().warning("Invalid config.yml: " + lastError + ". Keeping the previous settings.");
            if (settings.steps().isEmpty()) settings = bundled();
        }
    }

    private GuideSettings bundled() {
        InputStream input = plugin.getResource("config.yml");
        if (input == null) return new GuideSettings("tr", true, true, List.of(), List.of());
        try (Reader reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
            return parser.parse(YamlConfiguration.loadConfiguration(reader));
        } catch (Exception error) {
            plugin.getLogger().severe("Bundled config.yml could not be read: " + Errors.concise(error));
            return new GuideSettings("tr", true, true, List.of(), List.of());
        }
    }
}
