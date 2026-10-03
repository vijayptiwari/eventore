package com.eventore.api;

import com.eventore.masking.MaskingConfig;
import com.eventore.masking.MaskingService;
import java.util.Set;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/masking")
public class MaskingController {

    private final MaskingService maskingService;

    public MaskingController(MaskingService maskingService) {
        this.maskingService = maskingService;
    }

    @GetMapping("/config")
    public ResponseEntity<MaskingConfigResponse> getConfig() {
        MaskingConfig config = maskingService.getConfig();
        return ResponseEntity.ok(new MaskingConfigResponse(
                config.isEnabled(),
                config.getReplacement(),
                config.getSensitiveFieldPatterns(),
                config.getSensitiveHeaderPatterns(),
                config.isMaskValuesByPattern()
        ));
    }

    @PostMapping("/preview")
    public ResponseEntity<MaskingPreviewResponse> preview(@RequestBody MaskingPreviewRequest request) {
        String payload = request != null ? request.payload() : "";
        String contentType = request != null ? request.contentType() : "application/json";
        com.eventore.masking.DataMasker.MaskResult result = maskingService.maskPayloadWithStats(payload, contentType);
        return ResponseEntity.ok(new MaskingPreviewResponse(payload, result.maskedPayload(), result.isRedacted()));
    }

    public record MaskingConfigResponse(
            boolean enabled,
            String replacement,
            Set<String> sensitiveFieldPatterns,
            Set<String> sensitiveHeaderPatterns,
            boolean maskValuesByPattern
    ) {}

    public record MaskingPreviewRequest(
            String payload,
            String contentType
    ) {}

    public record MaskingPreviewResponse(
            String original,
            String masked,
            boolean wasMasked
    ) {}
}
