package net.clodo.startflow;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DailyClaimTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void dailyClaimsRejectDuplicatesBuildStreaksAndPreserveBestAfterReset() {
        Storage storage = new Storage(temporaryDirectory.resolve("startflow.db"));
        UUID player = UUID.randomUUID();
        try {
            storage.initialize().join();

            Storage.DailyClaim first = storage.claimDaily(player, "2026-01-01", 3).join();
            assertTrue(first.granted());
            assertEquals(1, first.streak());
            assertEquals(1, first.best());

            Storage.DailyClaim duplicate = storage.claimDaily(player, "2026-01-01", 3).join();
            assertFalse(duplicate.granted());
            assertEquals(1, duplicate.streak());

            Storage.DailyClaim nextDay = storage.claimDaily(player, "2026-01-02", 3).join();
            assertTrue(nextDay.granted());
            assertEquals(2, nextDay.streak());
            assertEquals(2, nextDay.best());

            Storage.DailyClaim afterGap = storage.claimDaily(player, "2026-01-06", 3).join();
            assertTrue(afterGap.granted());
            assertEquals(1, afterGap.streak());
            assertEquals(2, afterGap.best());
        } finally {
            storage.close();
        }
    }
}
