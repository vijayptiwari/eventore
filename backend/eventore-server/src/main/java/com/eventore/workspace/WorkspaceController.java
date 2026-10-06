package com.eventore.workspace;

import com.eventore.security.SecurityContextHolder;
import com.eventore.security.UserPrincipal;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * REST controller for multi-tenant workspace management (REQ-104).
 */
@RestController
@RequestMapping("/api/v1/workspaces")
public class WorkspaceController {

    private final WorkspaceManager workspaceManager;
    private final com.eventore.security.DeploymentModePolicy policy;

    public WorkspaceController(WorkspaceManager workspaceManager, com.eventore.security.DeploymentModePolicy policy) {
        this.workspaceManager = workspaceManager;
        this.policy = policy;
    }

    @GetMapping
    public ResponseEntity<List<Workspace>> listWorkspaces() {
        UserPrincipal principal = SecurityContextHolder.getPrincipal();
        return ResponseEntity.ok(workspaceManager.listWorkspaces(principal));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Workspace> getWorkspace(@PathVariable("id") String id) {
        UserPrincipal principal = SecurityContextHolder.getPrincipal();
        return workspaceManager.getWorkspace(id, principal)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Workspace not found: " + id));
    }

    @PostMapping
    public ResponseEntity<Workspace> createWorkspace(@RequestBody Workspace workspace) {
        policy.require(com.eventore.security.Action.MANAGE_CONNECTIONS);
        UserPrincipal principal = SecurityContextHolder.getPrincipal();
        Workspace created = workspaceManager.createWorkspace(workspace, principal);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteWorkspace(@PathVariable("id") String id) {
        policy.require(com.eventore.security.Action.MANAGE_CONNECTIONS);
        UserPrincipal principal = SecurityContextHolder.getPrincipal();
        if (workspaceManager.deleteWorkspace(id, principal)) {
            return ResponseEntity.noContent().build();
        }
        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Workspace not found: " + id);
    }
}
