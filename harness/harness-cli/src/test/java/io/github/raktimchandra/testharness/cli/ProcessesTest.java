package io.github.raktimchandra.testharness.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class ProcessesTest {

    @Test
    void batchFilesOnWindowsGetPropertiesQuoted() {
        List<String> cmd = List.of("C:\\repo\\mvnw.cmd", "-B", "verify", "-Dharness.select=C:\\t\\a.txt", "-pl", "a,b");
        assertThat(Processes.forPlatform(cmd, "Windows 11")).containsExactly("C:\\repo\\mvnw.cmd", "-B", "verify",
                "\"-Dharness.select=C:\\t\\a.txt\"", "-pl", "\"a,b\"");
    }

    @Test
    void everythingElseIsPassedAsIs() {
        List<String> cmd = List.of("/repo/mvnw", "-Dx=1");
        assertThat(Processes.forPlatform(cmd, "Linux")).isSameAs(cmd);
        List<String> exe = List.of("C:\\java.exe", "-Dx=1");
        assertThat(Processes.forPlatform(exe, "Windows 11")).isSameAs(exe);
        assertThat(Processes.forPlatform(List.of("C:\\m.cmd", "\"-Dx=1\""), "Windows 10")).containsExactly("C:\\m.cmd", "\"-Dx=1\"");
    }
}
