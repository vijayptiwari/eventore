package com.eventore.connector.spi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class PayloadCodecTest {

    @Test
    void textPayloadRoundTripsAsUtf8() {
        byte[] bytes = PayloadCodec.toBytes("hello world", "text/plain");
        assertThat(new String(bytes, StandardCharsets.UTF_8)).isEqualTo("hello world");
    }

    @Test
    void nullPayloadBecomesEmptyBytes() {
        assertThat(PayloadCodec.toBytes(null, "text/plain")).isEmpty();
    }

    @Test
    void base64PayloadIsDecodedToRawBytes() {
        byte[] raw = {-28, 0, -1, 5, 127, -128}; // arbitrary binary
        String encoded = Base64.getEncoder().encodeToString(raw);
        assertThat(PayloadCodec.toBytes(encoded, PayloadCodec.BASE64_CONTENT_TYPE)).isEqualTo(raw);
    }

    @Test
    void invalidBase64IsRejectedWithClearError() {
        assertThatThrownBy(() -> PayloadCodec.toBytes("@@not-base64@@", PayloadCodec.BASE64_CONTENT_TYPE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("base64");
    }

    @Test
    void validUtf8BytesPassThroughAsText() {
        PayloadCodec.Decoded decoded = PayloadCodec.fromBytes("héllo ✓".getBytes(StandardCharsets.UTF_8));
        assertThat(decoded.base64()).isFalse();
        assertThat(decoded.text()).isEqualTo("héllo ✓");
        assertThat(decoded.contentType()).isEqualTo(PayloadCodec.TEXT_CONTENT_TYPE);
    }

    @Test
    void binaryBytesAreBase64EncodedAndFlagged() {
        byte[] binary = {(byte) 0xFF, (byte) 0xFE, 0x00, 0x01, (byte) 0x80};
        PayloadCodec.Decoded decoded = PayloadCodec.fromBytes(binary);
        assertThat(decoded.base64()).isTrue();
        assertThat(decoded.contentType()).isEqualTo(PayloadCodec.BASE64_CONTENT_TYPE);
        assertThat(Base64.getDecoder().decode(decoded.text())).isEqualTo(binary);
    }

    @Test
    void emptyAndNullBytesDecodeToEmptyText() {
        assertThat(PayloadCodec.fromBytes(null).text()).isEmpty();
        assertThat(PayloadCodec.fromBytes(new byte[0]).text()).isEmpty();
    }

    @Test
    void isBase64MatchesContentTypeVariants() {
        assertThat(PayloadCodec.isBase64("application/base64")).isTrue();
        assertThat(PayloadCodec.isBase64("application/octet-stream;encoding=base64")).isTrue();
        assertThat(PayloadCodec.isBase64("text/plain")).isFalse();
        assertThat(PayloadCodec.isBase64(null)).isFalse();
    }
}
