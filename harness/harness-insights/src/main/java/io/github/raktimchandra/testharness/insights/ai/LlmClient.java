package io.github.raktimchandra.testharness.insights.ai;

import java.util.List;

/** A chat-style language model. Implementations are thin, dependency-free HTTP clients. */
public interface LlmClient {

    /** A PNG image sent with the prompt (only when the user opted in, see {@code ai.send-screenshots}). */
    record Image(String mediaType, byte[] data) {}

    String complete(String system, String user, List<Image> images);

    /** e.g. "anthropic/claude-x" — recorded in every draft so a reviewer knows what wrote it. */
    String describe();
}
