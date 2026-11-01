package com.minemaster.libresearch;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Проверяет, что каждый ключ перевода {@code libresearch.*}, упомянутый в коде,
 * есть и в {@code ru_ru}, и в {@code en_us}. Ловит опечатки и забытые языки —
 * требование заказчика «всё видимое переводится».
 *
 * <p>JSON разбирается построчно регуляркой: файлы плоские (ключ → строка), а
 * внешние зависимости тесту не нужны.</p>
 */
class LangKeysTest {
    private static final Pattern USED_KEY = Pattern.compile("\"(libresearch\\.[a-z0-9_.]+)\"");
    private static final Pattern JSON_KEY = Pattern.compile("^\\s*\"(libresearch\\.[a-z0-9_.]+)\"\\s*:");

    @Test
    void everyUsedKeyIsTranslated() throws IOException {
        Set<String> used = new TreeSet<>();
        Path sourceRoot = Path.of("src/main/java");
        assertTrue(Files.isDirectory(sourceRoot), "нет каталога исходников: " + sourceRoot.toAbsolutePath());

        try (Stream<Path> files = Files.walk(sourceRoot)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                Matcher matcher = USED_KEY.matcher(Files.readString(file, StandardCharsets.UTF_8));
                while (matcher.find()) {
                    used.add(matcher.group(1));
                }
            }
        }
        assertTrue(used.size() > 50, "подозрительно мало ключей: " + used.size());
        // Системное свойство отладки, не ключ перевода.
        used.remove("libresearch.uitest");

        Set<String> missingRu = new TreeSet<>(used);
        missingRu.removeAll(keys("ru_ru"));
        Set<String> missingEn = new TreeSet<>(used);
        missingEn.removeAll(keys("en_us"));

        assertTrue(missingRu.isEmpty(), "нет в ru_ru: " + missingRu);
        assertTrue(missingEn.isEmpty(), "нет в en_us: " + missingEn);
    }

    @Test
    void languagesHaveSameKeys() throws IOException {
        Set<String> onlyRu = new TreeSet<>(keys("ru_ru"));
        Set<String> en = keys("en_us");
        Set<String> onlyEn = new TreeSet<>(en);
        onlyRu.removeAll(en);
        onlyEn.removeAll(keys("ru_ru"));
        assertTrue(onlyRu.isEmpty(), "только в ru_ru: " + onlyRu);
        assertTrue(onlyEn.isEmpty(), "только в en_us: " + onlyEn);
    }

    private static Set<String> keys(String language) throws IOException {
        Path path = Path.of("src/main/resources/assets/libresearch/lang/" + language + ".json");
        Set<String> result = new TreeSet<>();
        for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
            Matcher matcher = JSON_KEY.matcher(line);
            if (matcher.find()) {
                result.add(matcher.group(1));
            }
        }
        assertTrue(result.size() > 50, language + ": подозрительно мало ключей: " + result.size());
        return result;
    }
}
