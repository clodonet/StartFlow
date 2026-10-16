package net.clodo.startflow;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.entity.Player;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;

final class StepCommandMatcher {

    static String commandLabel(String raw) {
        String[] parts = raw.split("\\s+", 2);
        if (parts.length == 0 || parts[0].isEmpty()) return "";
        String label = parts[0].toLowerCase(Locale.ROOT);
        int colon = label.indexOf(':');
        return colon >= 0 ? label.substring(colon + 1) : label;
    }

    static void recordRecipient(String label, String message, Map<UUID, Long> lastPayment) {
        String[] tokens = message.split("\\s+");
        if (label.equals("pay") && tokens.length >= 2) {
            Player target = Bukkit.getPlayerExact(tokens[1]);
            if (target != null) lastPayment.put(target.getUniqueId(), System.currentTimeMillis());
        } else if (label.equals("eco") && tokens.length >= 3 && (tokens[1].equalsIgnoreCase("give") || tokens[1].equalsIgnoreCase("set"))) {
            Player target = Bukkit.getPlayerExact(tokens[2]);
            if (target != null) lastPayment.put(target.getUniqueId(), System.currentTimeMillis());
        }
    }

    static boolean matchesCommand(String message, String label, String target) {
        String lowerTarget = target.toLowerCase(Locale.ROOT);
        if (message.startsWith(lowerTarget)) return true;
        String targetLabel = lowerTarget.split("\\s+", 2)[0];
        Command command = Bukkit.getCommandMap().getCommand(label);
        Command targetCommand = Bukkit.getCommandMap().getCommand(targetLabel);
        if (command != null && targetCommand != null && command == targetCommand) {
            String remainder = lowerTarget.substring(targetLabel.length()).trim();
            String messageRemainder = message.substring(label.length()).trim();
            return remainder.isEmpty() || messageRemainder.startsWith(remainder);
        }
        return false;
    }
}
