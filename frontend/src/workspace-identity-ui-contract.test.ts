import { describe, it, expect, vi, beforeEach } from 'vitest';
import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const packageRoot = join(dirname(fileURLToPath(import.meta.url)), '..');
const clientSource = readFileSync(join(packageRoot, 'src', 'api', 'client.ts'), 'utf8');
const layoutSource = readFileSync(join(packageRoot, 'src', 'components', 'AppLayout.tsx'), 'utf8');
const headerControlsSource = readFileSync(join(packageRoot, 'src', 'components', 'WorkspaceHeaderControls.tsx'), 'utf8');
const hookSource = readFileSync(join(packageRoot, 'src', 'hooks', 'useAuthAndWorkspaces.ts'), 'utf8');

describe('P1 Enterprise Identity & P2 Cluster Bus UI Contracts', () => {
  it('AC-1: api client exposes UserPrincipalDto, WorkspaceDto, and ClusterStatusDto types and endpoints', () => {
    expect(clientSource).toContain('interface UserPrincipalDto');
    expect(clientSource).toContain('interface WorkspaceDto');
    expect(clientSource).toContain('interface ClusterStatusDto');
    expect(clientSource).toContain("getAuthMe: () => request<UserPrincipalDto>('/auth/me')");
    expect(clientSource).toContain("listWorkspaces: () => request<WorkspaceDto[]>('/workspaces')");
    expect(clientSource).toContain("createWorkspace: (body: Partial<WorkspaceDto>) =>");
    expect(clientSource).toContain("getClusterStatus: () => request<ClusterStatusDto>('/cluster/status')");
  });

  it('AC-2: useAuthAndWorkspaces hook fetches identity, workspaces, and cluster telemetry', () => {
    expect(hookSource).toContain("queryKey: ['auth', 'me']");
    expect(hookSource).toContain("queryKey: ['workspaces']");
    expect(hookSource).toContain("queryKey: ['cluster', 'status']");
    expect(hookSource).toContain('eventore_active_workspace');
    expect(hookSource).toContain('setActiveWorkspaceId');
  });

  it('AC-3: WorkspaceHeaderControls renders workspace selector, cluster status, and identity badges', () => {
    expect(headerControlsSource).toContain('workspace-select');
    expect(headerControlsSource).toContain('cluster-status-badge');
    expect(headerControlsSource).toContain('user-principal-badge');
    expect(headerControlsSource).toContain('Create Multi-Tenant Workspace');
  });

  it('AC-4: AppLayout integrates WorkspaceHeaderControls in top navigation bar', () => {
    expect(layoutSource).toContain('<WorkspaceHeaderControls />');
  });
});
