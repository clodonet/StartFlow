package net.clodo.startflow;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WordsTest {
    @Test
    void foldRemovesTurkishDiacriticsAndIgnoresCase() {
        assertEquals("gunluk-sifirla", Words.fold("GÜNLÜK-SIFIRLA"));
        assertEquals("hakkinda", Words.fold("Hakkında"));
        assertEquals("menu", Words.fold("MENÜ"));
    }

    @Test
    void commandRecognizesTurkishAndEnglishAliases() {
        assertEquals("gunluk", Words.command("GÜNLÜK"));
        assertEquals("gunluk", Words.command("daily"));
        assertEquals("sure-sifirla", Words.command("RESET-PLAYTIME"));
        assertEquals("kural-sifirla", Words.command("rules-reset"));
        assertEquals("", Words.command("unknown"));
    }

    @Test
    void completionListsUseDocumentedLanguageForms() {
        assertEquals(List.of("günlük", "süre", "kurallar", "atla", "sıfırla"), Words.guide(true));
        assertEquals(List.of("daily", "playtime", "rules", "skip", "reset"), Words.guide(false));
        assertEquals(List.of("menü", "yenile", "oyuncu", "hakkında", "rapor", "şablon"), Words.startflow(true));
    }
}
