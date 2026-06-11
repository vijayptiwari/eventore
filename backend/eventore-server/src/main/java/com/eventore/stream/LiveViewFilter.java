package com.eventore.stream;

import com.eventore.domain.UnifiedMessage;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public final class LiveViewFilter {

    private static final int MAX_REGEX_LENGTH = 512;

    private LiveViewFilter() {}

    public record Compiled(Pattern headerPattern, Pattern bodyPattern) {}

    public static void validateRegex(String headerRegex, String bodyRegex) {
        compile(headerRegex, bodyRegex);
    }

    public static Compiled compile(String headerRegex, String bodyRegex) {
        return new Compiled(compileOptional(headerRegex), compileOptional(bodyRegex));
    }

    public static boolean matches(String headerRegex, String bodyRegex, UnifiedMessage message) {
        return matches(compile(headerRegex, bodyRegex), message);
    }

    public static boolean matches(Compiled compiled, UnifiedMessage message) {
        if (message == null) {
            return false;
        }
        if (compiled.bodyPattern() != null) {
            String payload = message.getPayload() != null ? message.getPayload() : "";
            if (!compiled.bodyPattern().matcher(payload).find()) {
                return false;
            }
        }
        if (compiled.headerPattern() != null) {
            String headerText = formatHeaders(message.getHeaders());
            if (!compiled.headerPattern().matcher(headerText).find()) {
                return false;
            }
        }
        return true;
    }

    private static Pattern compileOptional(String regex) {
        if (regex == null || regex.isBlank()) {
            return null;
        }
        if (regex.length() > MAX_REGEX_LENGTH) {
            throw new IllegalArgumentException("Regex exceeds max length of " + MAX_REGEX_LENGTH);
        }
        try {
            return Pattern.compile(regex, Pattern.DOTALL);
        } catch (PatternSyntaxException e) {
            throw new IllegalArgumentException("Invalid regex: " + e.getDescription());
        }
    }

    private static String formatHeaders(Map<String, String> headers) {
        if (headers == null || headers.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : headers.entrySet()) {
            sb.append(e.getKey()).append('=').append(e.getValue() != null ? e.getValue() : "").append('\n');
        }
        return sb.toString();
    }
}
