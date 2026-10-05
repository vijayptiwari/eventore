import { useState, useEffect } from 'react';
import { useQuery } from '@tanstack/react-query';
import { api, type UserPrincipalDto, type WorkspaceDto, type ClusterStatusDto } from '../api/client';

const WORKSPACE_STORAGE_KEY = 'eventore_active_workspace';

export function useAuthAndWorkspaces() {
  const { data: principal, isLoading: isAuthLoading } = useQuery<UserPrincipalDto>({
    queryKey: ['auth', 'me'],
    queryFn: () => api.getAuthMe(),
    staleTime: 60_000,
    retry: false,
  });

  const { data: workspaces = [], isLoading: isWorkspacesLoading } = useQuery<WorkspaceDto[]>({
    queryKey: ['workspaces'],
    queryFn: () => api.listWorkspaces(),
    staleTime: 60_000,
    retry: false,
  });

  const { data: clusterStatus } = useQuery<ClusterStatusDto>({
    queryKey: ['cluster', 'status'],
    queryFn: () => api.getClusterStatus(),
    refetchInterval: 30_000,
    retry: false,
  });

  const [activeWorkspaceId, setActiveWorkspaceIdState] = useState<string>(() => {
    return localStorage.getItem(WORKSPACE_STORAGE_KEY) || 'default';
  });

  useEffect(() => {
    if (principal?.currentWorkspaceId && !localStorage.getItem(WORKSPACE_STORAGE_KEY)) {
      setActiveWorkspaceIdState(principal.currentWorkspaceId);
    }
  }, [principal]);

  const setActiveWorkspaceId = (id: string) => {
    setActiveWorkspaceIdState(id);
    localStorage.setItem(WORKSPACE_STORAGE_KEY, id);
  };

  const activeWorkspace = workspaces.find((w) => w.id === activeWorkspaceId) || {
    id: activeWorkspaceId,
    name: activeWorkspaceId === 'default' ? 'Default Workspace' : activeWorkspaceId,
    description: 'Scoped workspace',
    createdAt: new Date().toISOString(),
    tags: [],
  };

  return {
    principal,
    workspaces,
    activeWorkspace,
    activeWorkspaceId,
    setActiveWorkspaceId,
    clusterStatus,
    isLoading: isAuthLoading || isWorkspacesLoading,
  };
}
