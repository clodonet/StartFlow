package net.clodo.startflow;

import org.bstats.bukkit.Metrics;
import org.bstats.charts.SimplePie;
import org.bukkit.command.PluginCommand;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class StartFlowPlugin extends JavaPlugin {
    private static final int METRICS_ID = 0;

    private ExecutorService configurationExecutor;
    private GuideSettingsLoader settingsLoader;
    private Messages messages;
    private Storage storage;
    private ProgressService progressService;
    private StepTriggers stepTriggers;
    private DailyReward dailyReward;
    private PlayTime playTime;
    private RulesGate rulesGate;
    private StarterKit starterKit;
    private Templates templates;
    private Tasks tasks;
    private UpdateCheck updateCheck;

    @Override
    public void onEnable() {
        configurationExecutor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "StartFlow-Config");
            thread.setDaemon(true);
            return thread;
        });
        tasks = new Tasks(this);
        VaultLink vaultLink = new VaultLink(this);
        ClaimLink claimLink = new ClaimLink(this);
        settingsLoader = new GuideSettingsLoader(this, configurationExecutor, vaultLink, claimLink);
        messages = new Messages(this, settingsLoader::settings);
        CompletableFuture<Void> initialFiles = settingsLoader.reload(messages::reload);
        initialFiles.exceptionally(error -> {
            getLogger().warning("Could not load StartFlow files: " + Errors.concise(error));
            return null;
        });
        storage = new Storage(getDataFolder().toPath().resolve("startflow.db"));
        Feedback feedback = new Feedback(this, messages, settingsLoader::settings, tasks);
        RewardService rewardService = new RewardService(this, storage, messages, settingsLoader::settings, tasks);
        dailyReward = new DailyReward(this, storage, messages, settingsLoader::settings, tasks);
        playTime = new PlayTime(this, storage, messages, settingsLoader::settings, tasks);
        starterKit = new StarterKit(this, storage, messages, settingsLoader::settings, tasks);
        rulesGate = new RulesGate(this, storage, messages, starterKit, settingsLoader::settings, tasks);
        JoinMessages joinMessages = new JoinMessages(messages, settingsLoader::settings);
        progressService = new ProgressService(storage, settingsLoader::enabledStepIds, completion -> {
            rewardService.completed(completion);
            feedback.completed(completion);
        });
        storage.initialize().exceptionally(error -> {
            getLogger().severe("Could not initialize the StartFlow database: " + Errors.concise(error));
            return null;
        });
        stepTriggers = new StepTriggers(this, progressService, settingsLoader::settings, vaultLink, claimLink, tasks);
        initialFiles.whenComplete((unused, error) -> tasks.global(stepTriggers::refresh));
        GuideMenu menu = new GuideMenu(this, progressService, messages, settingsLoader::settings, tasks);
        AdminMenu adminMenu = new AdminMenu(this, progressService, messages, settingsLoader, stepTriggers, tasks);
        ReportMenu reportMenu = new ReportMenu(this, storage, messages, settingsLoader, tasks, adminMenu::open);
        adminMenu.setReportMenu(reportMenu);
        templates = new Templates(this, configurationExecutor, settingsLoader, messages, stepTriggers, tasks);
        GuideCommands commands = new GuideCommands(this, progressService, messages, settingsLoader, menu, adminMenu, reportMenu, stepTriggers, dailyReward, playTime, rulesGate, starterKit, templates, tasks);
        register("rehber", commands);
        register("basla", commands);
        register("startflow", commands);
        register("rules", commands);
        register("kurallar", commands);
        registerEvents(progressService);
        registerEvents(dailyReward);
        registerEvents(playTime);
        registerEvents(playTime.activity());
        registerEvents(rulesGate);
        registerEvents(starterKit);
        registerEvents(joinMessages);
        registerEvents(menu);
        registerEvents(adminMenu);
        registerEvents(reportMenu);
        registerEvents(stepTriggers);
        for (org.bukkit.entity.Player player : getServer().getOnlinePlayers()) {
            progressService.load(player.getUniqueId());
            dailyReward.load(player.getUniqueId());
            playTime.load(player.getUniqueId());
            playTime.start(player);
            rulesGate.load(player.getUniqueId());
        }
        registerPlaceholders();
        tasks.global(() -> {
            boolean wasVault = vaultLink.isAvailable();
            boolean wasClaim = claimLink.isAvailable();
            vaultLink.retry();
            claimLink.retry();
            if (wasVault != vaultLink.isAvailable() || wasClaim != claimLink.isAvailable()) {
                settingsLoader.reload(messages::reload).whenComplete((unused, error) -> tasks.global(stepTriggers::refresh));
            }
        });
        registerEvents(new Welcome(this, storage, messages, settingsLoader::settings, initialFiles, tasks));
        if (METRICS_ID > 0 && getConfig().getBoolean("metrics", true)) {
            Metrics metrics = new Metrics(this, METRICS_ID);
            metrics.addCustomChart(new SimplePie("guide_language", () -> settingsLoader.settings().defaultLanguage()));
            metrics.addCustomChart(new SimplePie("enabled_steps", () -> String.valueOf(settingsLoader.settings().enabledStepIds().size())));
        }
        updateCheck = new UpdateCheck(this, messages, tasks);
        registerEvents(updateCheck);
        updateCheck.start();
    }

    @Override
    public void onDisable() {
        if (playTime != null) {
            playTime.close();
        }
        if (updateCheck != null) {
            updateCheck.close();
        }
        if (stepTriggers != null) {
            stepTriggers.close();
        }
        if (storage != null) {
            storage.close();
        }
        if (configurationExecutor != null) {
            configurationExecutor.shutdown();
        }
    }

    public ProgressService progress() {
        return progressService;
    }

    public StepTriggers triggers() {
        return stepTriggers;
    }

    public DailyReward dailyReward() {
        return dailyReward;
    }

    public PlayTime playTime() {
        return playTime;
    }

    public RulesGate rulesGate() {
        return rulesGate;
    }

    public StarterKit starterKit() {
        return starterKit;
    }

    public Templates templates() {
        return templates;
    }

    public Tasks tasks() {
        return tasks;
    }

    private void register(String name, GuideCommands commands) {
        PluginCommand command = getCommand(name);
        if (command != null) {
            command.setExecutor(commands);
            command.setTabCompleter(commands);
        }
    }

    private void registerEvents(Listener listener) {
        getServer().getPluginManager().registerEvents(listener, this);
    }

    private void registerPlaceholders() {
        if (!getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            getLogger().info("PlaceholderAPI not found; placeholder expansion is disabled.");
            return;
        }
        new Placeholders(this, progressService, dailyReward, playTime, messages, settingsLoader).register();
    }
}
