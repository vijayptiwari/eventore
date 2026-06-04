import { useQuery } from '@tanstack/react-query';
import { api } from '../api/client';

export function useAppConfig() {
  return useQuery({
    queryKey: ['config'],
    queryFn: api.getConfig,
    staleTime: 60_000,
  });
}
