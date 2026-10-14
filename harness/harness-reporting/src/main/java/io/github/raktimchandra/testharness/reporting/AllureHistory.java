package io.github.raktimchandra.testharness.reporting;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.stream.Stream;

/**
 * Allure builds its trend charts from a {@code history} folder that must be copied from the
 * previous report into the next run's results. This class does both halves of that hand-off.
 */
public final class AllureHistory {

    private AllureHistory() {}

    /** Before a run: copy the stored history into the results directory. Returns files copied. */
    public static int restore(Path historyStore, Path resultsDir) {
        return copyTree(historyStore, resultsDir.resolve("history"));
    }

    /** After a report is generated: keep its history for the next run. Returns files copied. */
    public static int save(Path reportDir, Path historyStore) {
        return copyTree(reportDir.resolve("history"), historyStore);
    }

    static int copyTree(Path from, Path to) {
        if (!Files.isDirectory(from)) {
            return 0;
        }
        try (Stream<Path> files = Files.walk(from)) {
            int[] count = {0};
            for (Path f : (Iterable<Path>) files::iterator) {
                Path target = to.resolve(from.relativize(f).toString());
                if (Files.isDirectory(f)) {
                    Files.createDirectories(target);
                } else {
                    Files.createDirectories(target.getParent());
                    Files.copy(f, target, StandardCopyOption.REPLACE_EXISTING);
                    count[0]++;
                }
            }
            return count[0];
        } catch (IOException e) {
            throw new UncheckedIOException("Could not copy Allure history from " + from + " to " + to, e);
        }
    }

    /** {@code java ... AllureHistory save <reportDir> <historyStore>} (used by the build after reporting). */
    public static void main(String[] args) {
        if (args.length != 3 || !args[0].equals("save")) {
            throw new IllegalArgumentException("usage: AllureHistory save <reportDir> <historyStore>");
        }
        save(Path.of(args[1]), Path.of(args[2]));
    }
}
