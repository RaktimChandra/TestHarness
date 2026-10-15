package io.github.raktimchandra.testharness.cli;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.SimpleFileServer;
import java.awt.Desktop;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;
import picocli.CommandLine.Model.CommandSpec;

@Command(name = "report", mixinStandardHelpOptions = true, description = {"Generate the Allure report for the last run and open it.",
        "The report is served over HTTP (Allure loads its data with fetch, so file:// does not work).",
        "Stop with Ctrl+C."})
class ReportCommand implements Callable<Integer> {

    @Spec
    CommandSpec spec;

    @Option(names = "--no-generate", description = "serve the existing target/allure-report")
    boolean noGenerate;

    @Option(names = "--port", defaultValue = "8000", description = "port (default ${DEFAULT-VALUE})")
    int port;

    @Option(names = "--no-browser", description = "do not open a browser")
    boolean noBrowser;

    @Override
    public Integer call() throws Exception {
        Repo repo = Repo.find();
        PrintWriter out = spec.commandLine().getOut();
        if (!noGenerate) {
            int rc = Processes.run(List.of(repo.mavenWrapper(), "-B", "-N", "-Preport", "verify"), repo.root(), out);
            if (rc != 0) {
                return rc;
            }
        }
        Path report = repo.root().resolve("target/allure-report");
        if (!Files.exists(report.resolve("index.html"))) {
            throw new IllegalStateException("No report at " + report + "; run the suites first");
        }
        HttpServer server = SimpleFileServer.createFileServer(new InetSocketAddress("127.0.0.1", port),
                report.toAbsolutePath(), SimpleFileServer.OutputLevel.NONE);
        server.start();
        URI url = URI.create("http://localhost:" + port + "/");
        out.println("Allure report at " + url + "  (Ctrl+C to stop)");
        out.flush();
        if (!noBrowser && Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            Desktop.getDesktop().browse(url);
        }
        new CountDownLatch(1).await();
        return 0;
    }
}
