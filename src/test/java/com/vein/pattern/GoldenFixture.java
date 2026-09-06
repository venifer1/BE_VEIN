package com.vein.pattern;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vein.pattern.core.Bar;

/**
 * Loads golden fixture JSON files for the ABC/Triangle golden tests.
 *
 * <p>Fixtures are copied into {@code src/test/resources/golden_fixtures/} by the
 * Gradle {@code copyGoldenFixtures} task; we also fall back to the repo path
 * {@code ../golden_fixtures} when running outside Gradle.
 */
public final class GoldenFixture {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private GoldenFixture() {
    }

    public record SimpleBar(Instant openTime, BigDecimal open, BigDecimal high,
                            BigDecimal low, BigDecimal close, BigDecimal volume) implements Bar {
    }

    public record Loaded(String fixtureId, JsonNode root, List<SimpleBar> candles) {
    }

    /** Locate all fixture files, skipping {@code _index.json}. */
    public static List<Path> fixtureFiles() throws IOException {
        Path dir = locateDir();
        if (dir == null || !Files.isDirectory(dir)) {
            return List.of();
        }
        try (var stream = Files.list(dir)) {
            return stream
                    .filter(p -> p.getFileName().toString().endsWith(".json"))
                    .filter(p -> !p.getFileName().toString().equals("_index.json"))
                    .sorted()
                    .collect(Collectors.toList());
        }
    }

    private static Path locateDir() {
        // 1) test classpath (copied by Gradle). Use toURI() so URL-encoded path
        //    segments (e.g. non-ASCII directory names) are decoded correctly.
        URL url = GoldenFixture.class.getClassLoader().getResource("golden_fixtures");
        if (url != null && "file".equals(url.getProtocol())) {
            try {
                Path p = Paths.get(url.toURI());
                if (Files.isDirectory(p)) {
                    return p;
                }
            } catch (URISyntaxException ignore) {
                // fall through to repo-relative fallbacks
            }
        }
        // 2) repo-relative fallbacks
        for (String rel : new String[] {"../golden_fixtures", "../../golden_fixtures", "golden_fixtures"}) {
            Path p = Paths.get(rel).toAbsolutePath().normalize();
            if (Files.isDirectory(p)) {
                return p;
            }
        }
        return null;
    }

    public static Loaded load(Path file) throws IOException {
        try (InputStream in = Files.newInputStream(file)) {
            JsonNode root = MAPPER.readTree(in);
            List<SimpleBar> bars = new ArrayList<>();
            for (JsonNode c : root.path("input_candles")) {
                // Daily fixtures: index-based comparison is what matters (KST→UTC note).
                // We materialize a deterministic UTC instant from the date for ordering only.
                String date = c.path("date").asText();
                Instant openTime = LocalDate.parse(date).atStartOfDay(ZoneOffset.UTC).toInstant();
                bars.add(new SimpleBar(
                        openTime,
                        BigDecimal.valueOf(c.path("open").asDouble()),
                        BigDecimal.valueOf(c.path("high").asDouble()),
                        BigDecimal.valueOf(c.path("low").asDouble()),
                        BigDecimal.valueOf(c.path("close").asDouble()),
                        BigDecimal.valueOf(c.path("volume").asDouble())));
            }
            return new Loaded(root.path("fixture_id").asText(file.getFileName().toString()), root, bars);
        }
    }
}
