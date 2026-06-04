package com.eventore.stream;

import com.eventore.domain.UnifiedMessage;
import java.util.Map;
import java.util.regex.Pattern;

public final class LiveViewFilter {

    private LiveViewFilter() {}

    public static void validateRegex(String headerRegex, String bodyRegex) {
        if (headerRegex != null && !headerRegex.isBlank()) {
            Pattern.compile(headerRegex);
        }
        if (bodyRegex != null && !bodyRegex.isBlank()) {
            Pattern.compile(bodyRegex);
        }
    }

    public static boolean matches(String headerRegex, String bodyRegex, UnifiedMessage message) {
        if (message == null) {
            return false;
        }
        if (bodyRegex != null && !bodyRegex.isBlank()) {
            String payload = message.getPayload() != null ? message.getPayload() : "";
            if (!Pattern.compile(bodyRegex, Pattern.DOTALL).matcher(payload).find()) {
                return false;
            }
        }
        if (headerRegex != null && !headerRegex.isBlank()) {
            String headerText = formatHeaders(message.getHeaders());
            if (!Pattern.compile(headerRegex, Pattern.DOTALL).matcher(headerText).find()) {
                return false;
            }
        }
        return true;
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
