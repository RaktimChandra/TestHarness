package io.github.raktimchandra.testharness.insights.ai;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Helpers shared by the drafting features. */
final class Drafts {

    private static final Pattern FENCE = Pattern.compile("```[a-zA-Z]*\\s*\\n(.*?)```", Pattern.DOTALL);

    private Drafts() {}

    /** The first fenced code block, or the whole answer if there is none. */
    static String codeBlock(String answer) {
        Matcher m = FENCE.matcher(answer);
        return m.find() ? m.group(1).strip() : answer.strip();
    }

    static String slug(String text) {
        String s = text.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
        return s.isEmpty() ? "draft" : s.length() > 60 ? s.substring(0, 60).replaceAll("-$", "") : s;
    }
}
