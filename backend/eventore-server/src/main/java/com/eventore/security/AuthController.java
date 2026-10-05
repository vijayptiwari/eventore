package com.eventore.security;

import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller exposing current authenticated principal identity and roles (REQ-103).
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> getCurrentUser() {
        UserPrincipal principal = SecurityContextHolder.getPrincipal();
        return ResponseEntity.ok(Map.of(
                "userId", principal.userId(),
                "email", principal.email(),
                "displayName", principal.displayName(),
                "roles", principal.roles(),
                "assignedWorkspaces", principal.assignedWorkspaces(),
                "currentWorkspaceId", principal.currentWorkspaceId(),
                "isJwt", principal.isJwt(),
                "isPlatformAdmin", principal.isPlatformAdmin()));
    }
}
