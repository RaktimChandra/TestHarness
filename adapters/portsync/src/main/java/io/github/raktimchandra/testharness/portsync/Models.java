package io.github.raktimchandra.testharness.portsync;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The parts of PortSync's responses the tests read, as records. Written from PortSync's
 * {@code Views} DTOs; unknown fields are ignored (the JSON Schemas, not these records, are the
 * contract check).
 */
public final class Models {

    private Models() {}

    public record BlockSummary(String block, int used, int capacity, double utilisation) {}

    public record Overview(Instant simulatedTime, boolean clockSynchronised, long vesselsAtBerth, long vesselsWaiting,
                           long craneMovesLastHour, double yardUtilisation, List<BlockSummary> blocks, long openAlerts,
                           long acknowledgedAlerts, Double avgTruckTurnMinutes, long trucksInTerminal,
                           long cranesFaulted, long eventsProcessed, long dlqEvents) {}

    public record Crane(String id, String status, String berthId, String faultCode, Instant faultedSince,
                        int movesLastHour) {}

    public record Slot(int tier, String containerNumber) {}

    public record Stack(int row, int bay, int height, List<Slot> slots) {}

    public record YardBlock(String block, int rows, int bays, int tiers, int capacity, int used, double utilisation,
                            List<Stack> stacks) {}

    public record Location(String block, int row, int bay, int tier, String label) {}

    public record HistoryItem(UUID eventId, String type, Instant occurredAt, String description) {}

    public record Rejection(UUID eventId, String type, String reason, String detail, Instant deadLetteredAt) {}

    public record Container(String number, boolean valid, String status, String vesselId, Location location,
                            Instant lastEventAt, List<HistoryItem> history, List<Rejection> rejections) {}

    public record ContainerValidation(String input, boolean valid, String reason, Integer expectedCheckDigit) {}

    public record DlqItem(Long id, UUID eventId, String type, String entityId, String reason, String detail,
                          String sourceTopic, Instant deadLetteredAt, String rawValue) {}

    public record EventItem(UUID eventId, String type, String entityId, Instant occurredAt, String outcome,
                            String reason, String detail, Instant processedAt) {}

    public record Alert(long id, String rule, String entityId, String severity, String state, String message,
                        Instant openedAt, Instant acknowledgedAt, Instant resolvedAt, String resolution) {}

    public record Invariants(boolean consistent, List<Map<String, Object>> violations) {}

    public record Published(String eventId, String type, String entityId, Instant occurredAt) {}

    /** What a simulator fault injection published and what the core is expected to do with it. */
    public record Injection(String description, String expectedOutcome, List<Published> events) {}

    public record TerminalStatus(Map<String, Integer> yardOccupancy, Map<String, String> cranes) {}

    public record SimulationStatus(Instant simulatedNow, double speedFactor, boolean paused, boolean running,
                                   TerminalStatus terminal) {}
}
