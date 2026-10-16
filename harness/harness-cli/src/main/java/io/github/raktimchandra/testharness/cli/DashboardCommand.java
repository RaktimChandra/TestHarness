package io.github.raktimchandra.testharness.cli;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.SimpleFileServer;
import io.github.raktimchandra.testharness.insights.dashboard.DashboardData;
import io.github.raktimchandra.testharness.insights.dashboard.DashboardSite;
import io.github.raktimchandra.testharness.insights.history.HistoryJson;
import io.github.raktimchandra.testharness.insights.history.HistoryStore;
import io.github.raktimchandra.testharness.insights.history.TestResult;
import java.awt.Desktop;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;
import picocli.CommandLine.Model.CommandSpec;

@Command(name = "dashboard", mixinStandardHelpOptions = true,
        description = {"Build the quality dashboard from the results already on disk (and optionally PostgreSQL).",
                "Writes index.html, data.js and data.json to --out; index.html opens straight from disk."})
class DashboardCommand implements Callable<Integer> {

    @Spec
    CommandSpec spec;

    @Option(names = "--out", defaultValue = "target/dashboard", description = "output directory (default ${DEFAULT-VALUE})")
    Path out;

    @Option(names = "--jdbc-url", description = "also read history from PostgreSQL")
    String jdbcUrl;

    @Option(names = "--db-user", defaultValue = "harness")
    String dbUser;

    @Option(names = "--jsonl", description = "extra history .jsonl file or directory (repeatable)")
    List<Path> jsonl = new ArrayList<>();

    @Option(names = "--open", description = "open the page in the default browser")
    boolean open;

    @Option(names = "--serve", description = "serve over HTTP on --port instead of opening the file (Ctrl+C to stop)")
    boolean serve;

    @Option(names = "--port", defaultValue = "8001")
    int port;

    @Override
    public Integer call() throws Exception {
        Repo repo = Repo.find();
        PrintWriter w = spec.commandLine().getOut();
        List<TestResult> extra = new ArrayList<>();
        jsonl.forEach(p -> extra.addAll(HistoryJson.read(p)));
        if (jdbcUrl != null) {
            String pw = System.getenv("HARNESS_HISTORY_DB_PASSWORD");
            try (HistoryStore store = HistoryStore.open(jdbcUrl, dbUser, pw == null || pw.isBlank() ? "harness" : pw)) {
                extra.addAll(store.all());
            }
        }
        Path dir = repo.root().resolve(out).normalize();
        ObjectNode doc = new DashboardData(repo.root()).build(dir, extra);
        Path index = DashboardSite.write(doc, dir);
        w.println("Dashboard: " + index);
        w.printf("  %d test results, %d runs, %d flaky, %d quarantined%n", doc.get("historyResults").asInt(),
                doc.get("runs").size(), doc.get("flaky").size(), doc.get("quarantine").get("tests").size());
        doc.get("missing").forEach(m -> w.println("  missing: " + m.asText()));
        w.flush();
        if (serve) {
            HttpServer server = SimpleFileServer.createFileServer(new InetSocketAddress("127.0.0.1", port),
                    dir.toAbsolutePath(), SimpleFileServer.OutputLevel.NONE);
            server.start();
            URI url = URI.create("http://localhost:" + port + "/");
            w.println("Serving " + url + "  (Ctrl+C to stop)");
            w.flush();
            browse(url);
            new CountDownLatch(1).await();
        } else if (open) {
            browse(index.toUri());
        }
        return 0;
    }

    private static void browse(URI uri) throws Exception {
        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            Desktop.getDesktop().browse(uri);
        }
    }
}
