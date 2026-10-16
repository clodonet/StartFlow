package net.clodo.startflow;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

final class Storage {
    private final Database database;
    private final ProgressStorage progress;
    private final RewardStorage rewards;
    private final ReportStorage reports;

    Storage(Path path) {
        this.database = new Database(path);
        this.progress = new ProgressStorage(database);
        this.rewards = new RewardStorage(database);
        this.reports = new ReportStorage(database);
    }

    CompletableFuture<Void> initialize() {
        return database.initialize();
    }

    void close() {
        database.close();
    }

    CompletableFuture<Boolean> recordJoinAndClaimWelcome(UUID uuid, String day, boolean enabled) {
        return progress.recordJoinAndClaimWelcome(uuid, day, enabled);
    }

    CompletableFuture<ProgressService.Progress> getProgress(UUID uuid) {
        return progress.getProgress(uuid);
    }

    CompletableFuture<Boolean> completeStep(UUID uuid, String stepId, Set<String> expected) {
        return progress.completeStep(uuid, stepId, expected);
    }

    CompletableFuture<Void> skip(UUID uuid) {
        return progress.skip(uuid);
    }

    CompletableFuture<Void> reset(UUID uuid) {
        return progress.reset(uuid);
    }

    CompletableFuture<Void> resetStep(UUID uuid, String stepId) {
        return progress.resetStep(uuid, stepId);
    }

    CompletableFuture<Boolean> claimReward(UUID uuid, String key) {
        return rewards.claimReward(uuid, key);
    }

    CompletableFuture<DailyRecord> getDaily(UUID uuid) {
        return rewards.getDaily(uuid);
    }

    CompletableFuture<DailyClaim> claimDaily(UUID uuid, String today, int resetAfterDays) {
        return rewards.claimDaily(uuid, today, resetAfterDays);
    }

    CompletableFuture<Void> rollbackDailyClaim(UUID uuid, String today, DailyClaim claim) {
        return rewards.rollbackDailyClaim(uuid, today, claim);
    }

    CompletableFuture<Void> resetDaily(UUID uuid) {
        return rewards.resetDaily(uuid);
    }

    CompletableFuture<Long> getPlayTime(UUID uuid) {
        return rewards.getPlayTime(uuid);
    }

    CompletableFuture<Void> savePlayTime(UUID uuid, long seconds) {
        return rewards.savePlayTime(uuid, seconds);
    }

    CompletableFuture<Void> saveAllPlayTime(Map<UUID, Long> snapshot) {
        return rewards.saveAllPlayTime(snapshot);
    }

    CompletableFuture<Void> resetPlayTime(UUID uuid) {
        return rewards.resetPlayTime(uuid);
    }

    CompletableFuture<Boolean> hasAcceptedRules(UUID uuid) {
        return rewards.hasAcceptedRules(uuid);
    }

    CompletableFuture<Boolean> acceptRules(UUID uuid) {
        return rewards.acceptRules(uuid);
    }

    CompletableFuture<Void> resetRules(UUID uuid) {
        return rewards.resetRules(uuid);
    }

    CompletableFuture<Void> resetKit(UUID uuid) {
        return rewards.resetKit(uuid);
    }

    CompletableFuture<FunnelReport> funnelReport(List<String> enabledStepIds) {
        return reports.funnelReport(enabledStepIds);
    }

    public record FunnelStep(String id, int completed, int percent, int stuck) {
    }

    public record FunnelReport(int totalPlayers, int started, int completed, int skipped, List<FunnelStep> steps, String biggestDropStep) {
        public int completionPercent() {
            return totalPlayers == 0 ? 0 : Math.min(100, Math.round((float) completed * 100 / totalPlayers));
        }
    }

    public record DailyRecord(int streak, int best, String lastDay) {
    }

    public record DailyClaim(int streak, int best, boolean granted, DailyRecord previous) {
    }
}
