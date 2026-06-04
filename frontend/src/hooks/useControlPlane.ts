import { useQuery } from '@tanstack/react-query';
import { api } from '../api/client';
import type { ProtocolType } from '../api/types';
import { useAppConfig } from './useAppConfig';

/** Resolves UI-visible protocols from control plane cascade (falls back to /config). */
export function useControlPlane() {
  const configQuery = useAppConfig();
  const planeQuery = useQuery({
    queryKey: ['control-plane'],
    queryFn: api.getControlPlane,
    staleTime: 30_000,
    enabled: !!configQuery.data,
  });

  const cascade = planeQuery.data?.uiCascade ?? configQuery.data?.controlPlane?.uiCascade;

  const connectionProtocols: ProtocolType[] =
    cascade?.connectionProtocols?.length
      ? (cascade.connectionProtocols as ProtocolType[])
      : (configQuery.data?.supportedProtocols ?? []);

  const inspectProtocols: ProtocolType[] =
    cascade?.inspectProtocols?.length
      ? (cascade.inspectProtocols as ProtocolType[])
      : connectionProtocols;

  const adminProtocols: ProtocolType[] =
    cascade?.adminProtocols?.length ? (cascade.adminProtocols as ProtocolType[]) : [];

  return {
    config: configQuery.data,
    plane: planeQuery.data ?? configQuery.data?.controlPlane,
    revision: planeQuery.data?.revision ?? configQuery.data?.controlPlane?.revision ?? 0,
    connectionProtocols,
    inspectProtocols,
    adminProtocols,
    isLoading: configQuery.isLoading || planeQuery.isLoading,
  };
}
