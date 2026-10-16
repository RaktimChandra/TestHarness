package io.github.raktimchandra.testharness.insights.dashboard;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Writes the quality dashboard: the prebuilt single-file React page (built from
 * {@code dashboard/} with {@code npm run build}, committed so the framework needs no Node)
 * plus its data as {@code data.json} and {@code data.js}. The page loads {@code data.js} as a
 * classic script, so it works opened straight from disk as well as served.
 */
public final class DashboardSite {

    static final String PAGE = "/io/github/raktimchandra/testharness/insights/dashboard/site/index.html";

    private DashboardSite() {}

    /** Returns the index.html written. */
    public static Path write(ObjectNode doc, Path outDir) {
        try (InputStream page = DashboardSite.class.getResourceAsStream(PAGE)) {
            if (page == null) {
                throw new IllegalStateException("Dashboard page missing from the classpath (" + PAGE
                        + "); build it with: cd dashboard && npm ci && npm run build");
            }
            Files.createDirectories(outDir);
            Path index = outDir.resolve("index.html");
            Files.copy(page, index, StandardCopyOption.REPLACE_EXISTING);
            DashboardData.write(doc, outDir.resolve("data.json"));
            String json = new ObjectMapper().writeValueAsString(doc).replace("</", "<\\/");
            Files.writeString(outDir.resolve("data.js"), "window.__TESTHARNESS__ = " + json + ";\n",
                    StandardCharsets.UTF_8);
            return index;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
