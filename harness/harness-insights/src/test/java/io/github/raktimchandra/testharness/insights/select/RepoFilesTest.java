package io.github.raktimchandra.testharness.insights.select;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RepoFilesTest {

    @Test
    void absoluteAndPresentPathsAreTakenAsGiven(@TempDir Path dir) throws Exception {
        Path f = Files.writeString(dir.resolve("q.json"), "{}");
        assertThat(RepoFiles.locate(f.toString())).hasValue(f);
        assertThat(RepoFiles.locate(dir.resolve("missing.json").toString())).isEmpty();
    }

    @Test
    void relativePathsAreSearchedUpToTheRepositoryRoot() {
        // The module runs inside the repository: quarantine.json lives at its root, two levels up.
        assertThat(RepoFiles.locate("quarantine.json")).hasValueSatisfying(p ->
                assertThat(p.getParent().resolve(".git")).exists());
        assertThat(RepoFiles.locate("no-such-file-anywhere.json")).isEmpty();
    }
}
