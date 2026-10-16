package net.clodo.startflow;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class Words {
    private static final Map<String, String> COMMANDS = Map.ofEntries(
            Map.entry("atla", "atla"), Map.entry("skip", "atla"),
            Map.entry("sifirla", "sifirla"), Map.entry("reset", "sifirla"),
            Map.entry("yenile", "yenile"), Map.entry("reload", "yenile"),
            Map.entry("oyuncu", "oyuncu"), Map.entry("player", "oyuncu"),
            Map.entry("adim", "adim"), Map.entry("step", "adim"),
            Map.entry("bilgi", "bilgi"), Map.entry("info", "bilgi"),
            Map.entry("about", "about"), Map.entry("hakkinda", "about"),
            Map.entry("menu", "menu"),
            Map.entry("rapor", "rapor"), Map.entry("report", "rapor"),
            Map.entry("csv", "csv"),
            Map.entry("gunluk", "gunluk"), Map.entry("daily", "gunluk"),
            Map.entry("gunluk-sifirla", "gunluk-sifirla"), Map.entry("reset-daily", "gunluk-sifirla"),
            Map.entry("daily-reset", "gunluk-sifirla"),
            Map.entry("sure", "sure"), Map.entry("time", "sure"), Map.entry("playtime", "sure"),
            Map.entry("sure-sifirla", "sure-sifirla"), Map.entry("reset-playtime", "sure-sifirla"),
            Map.entry("playtime-reset", "sure-sifirla"),
            Map.entry("kurallar", "kurallar"), Map.entry("rules", "kurallar"),
            Map.entry("kabul", "kabul"), Map.entry("accept", "kabul"),
            Map.entry("kural-sifirla", "kural-sifirla"), Map.entry("reset-rules", "kural-sifirla"),
            Map.entry("rules-reset", "kural-sifirla"),
            Map.entry("kit-sifirla", "kit-sifirla"), Map.entry("reset-kit", "kit-sifirla"),
            Map.entry("kit-reset", "kit-sifirla"),
            Map.entry("sablon", "sablon"), Map.entry("template", "sablon"),
            Map.entry("onayla", "onayla"), Map.entry("confirm", "onayla")
    );

    private Words() {
    }

    static String command(String value) {
        return COMMANDS.getOrDefault(fold(value), "");
    }

    static String fold(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}", "").replace('ı', 'i').toLowerCase(Locale.ROOT);
    }

    static List<String> guide(boolean turkish) {
        return turkish ? List.of("günlük", "süre", "kurallar", "atla", "sıfırla") : List.of("daily", "playtime", "rules", "skip", "reset");
    }

    static List<String> rulesActions(boolean turkish) {
        return turkish ? List.of("kabul") : List.of("accept");
    }

    static List<String> basla(boolean turkish) {
        return guide(turkish);
    }

    static List<String> startflow(boolean turkish) {
        return turkish ? List.of("menü", "yenile", "oyuncu", "hakkında", "rapor", "şablon") : List.of("menu", "reload", "player", "about", "report", "template");
    }

    static List<String> playerActions(boolean turkish) {
        return turkish ? List.of("adım", "sıfırla", "günlük-sıfırla", "süre-sıfırla", "kural-sıfırla", "kit-sıfırla", "bilgi") : List.of("step", "reset", "reset-daily", "reset-playtime", "reset-rules", "reset-kit", "info");
    }
}
