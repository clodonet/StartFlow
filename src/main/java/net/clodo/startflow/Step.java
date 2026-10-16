package net.clodo.startflow;

import org.bukkit.Material;

import java.util.List;

record Step(
        String id,
        String displayNameKey,
        String descriptionKey,
        String hintKey,
        String trigger,
        Material icon,
        boolean enabled,
        String command,
        RegionBounds region,
        Material itemMaterial,
        int itemAmount,
        double minGain,
        Material placeMaterial,
        List<String> rewards,
        String rewardMessageKey
) {
    Step(
            String id,
            String displayNameKey,
            String descriptionKey,
            String hintKey,
            String trigger,
            Material icon,
            boolean enabled,
            String command,
            RegionBounds region,
            Material itemMaterial,
            int itemAmount,
            double minGain,
            Material placeMaterial,
            List<String> rewards
    ) {
        this(id, displayNameKey, descriptionKey, hintKey, trigger, icon, enabled, command, region, itemMaterial, itemAmount, minGain, placeMaterial, rewards, "reward." + id + ".message");
    }

    Step(
            String id,
            String displayNameKey,
            String descriptionKey,
            String hintKey,
            String trigger,
            Material icon,
            boolean enabled,
            String command,
            RegionBounds region,
            Material itemMaterial,
            int itemAmount,
            double minGain,
            Material placeMaterial
    ) {
        this(id, displayNameKey, descriptionKey, hintKey, trigger, icon, enabled, command, region, itemMaterial, itemAmount, minGain, placeMaterial, List.of(), "reward." + id + ".message");
    }

    Step(
            String id,
            String displayNameKey,
            String descriptionKey,
            String hintKey,
            String trigger,
            Material icon,
            boolean enabled,
            String command,
            RegionBounds region,
            Material itemMaterial,
            int itemAmount,
            double minGain
    ) {
        this(id, displayNameKey, descriptionKey, hintKey, trigger, icon, enabled, command, region, itemMaterial, itemAmount, minGain, null, List.of(), "reward." + id + ".message");
    }

    static Step disabled(String id, String displayNameKey, String descriptionKey, String hintKey, String trigger, Material icon) {
        return new Step(id, displayNameKey, descriptionKey, hintKey, trigger, icon, false, "", null, null, 0, 0.0, null, List.of(), "reward." + id + ".message");
    }

    record RegionBounds(String world, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        boolean contains(String worldName, int x, int y, int z) {
            return world.equalsIgnoreCase(worldName)
                    && x >= minX && x <= maxX
                    && y >= minY && y <= maxY
                    && z >= minZ && z <= maxZ;
        }
    }
}
