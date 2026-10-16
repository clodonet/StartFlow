package net.clodo.startflow;

import java.util.List;

record PlayTimeSettings(
        boolean enabled,
        int afkAfterSeconds,
        List<Milestone> milestones
) {
    PlayTimeSettings() {
        this(false, 120, List.of());
    }

    record Milestone(
            String id,
            double hours,
            long requiredSeconds,
            List<String> commands,
            String messageKey
    ) {
        Milestone(String id, double hours, List<String> commands, String messageKey) {
            this(id, hours, Math.round(hours * 3600.0), commands, messageKey);
        }
    }
}
