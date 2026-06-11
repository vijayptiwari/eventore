package com.eventore.connector.spi;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Locale;

/**
 * Encodes and decodes message payloads so binary data survives the string-based
 * transport contract. Outbound payloads with a base64 content type are decoded
 * to raw bytes before hitting the broker; inbound bytes that are not valid
 * UTF-8 are base64-encoded and flagged via content type.
 */
public final class PayloadCodec {

    public static final String BASE64_CONTENT_TYPE = "application/base64";
    public static final String TEXT_CONTENT_TYPE = "text/plain";

    private PayloadCodec() {}

    /** True when the content type indicates a base64-encoded payload. */
    public static boolean isBase64(String contentType) {
        return contentType != null && contentType.toLowerCase(Locale.ROOT).contains("base64");
    }

    /**
     * Converts an outbound payload string to raw bytes. Base64 content types are
     * decoded; everything else is treated as UTF-8 text. Null payloads become
     * empty byte arrays.
     */
    public static byte[] toBytes(String payload, String contentType) {
        if (payload == null) {
            return new byte[0];
        }
        if (isBase64(contentType)) {
            try {
                return Base64.getDecoder().decode(payload.trim());
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException(
                        "Payload declared as base64 but is not valid base64: " + e.getMessage(), e);
            }
        }
        return payload.getBytes(StandardCharsets.UTF_8);
    }

    /** Result of decoding inbound bytes: payload text plus the effective content type. */
    public record Decoded(String text, String contentType, boolean base64) {}

    /**
     * Converts inbound raw bytes to a transport-safe string. Valid UTF-8 is
     * passed through as text; anything else is base64-encoded and marked with
     * {@link #BASE64_CONTENT_TYPE} so clients can reverse the encoding.
     */
    public static Decoded fromBytes(byte[] data) {
        if (data == null || data.length == 0) {
            return new Decoded("", TEXT_CONTENT_TYPE, false);
        }
        if (isValidUtf8(data)) {
            return new Decoded(new String(data, StandardCharsets.UTF_8), TEXT_CONTENT_TYPE, false);
        }
        return new Decoded(Base64.getEncoder().encodeToString(data), BASE64_CONTENT_TYPE, true);
    }

    static boolean isValidUtf8(byte[] data) {
        CharsetDecoder decoder = StandardCharsets.UTF_8
                .newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        try {
            decoder.decode(ByteBuffer.wrap(data));
            return true;
        } catch (CharacterCodingException e) {
            return false;
        }
    }
}
