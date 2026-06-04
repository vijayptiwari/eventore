import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useRef,
  useState,
  type ReactNode,
} from 'react';
import { getRuntimeConfig } from '../config/runtime';
import type { ConnectionProfile } from '../api/types';
import {
  loadActiveStreamIdFromStorage,
  loadStreamSessionsFromStorage,
  saveActiveStreamIdToStorage,
  saveStreamSessionsToStorage,
  toPersisted,
} from './sessionCookies';
import type {
  LiveStreamSession,
  LiveViewDurationMinutes,
  LiveViewState,
  StreamFrame,
  StreamStatus,
} from './types';

interface StreamWorkspaceContextValue {
  wsConnected: boolean;
  sessions: LiveStreamSession[];
  activeSessionId: string | null;
  activeSession: LiveStreamSession | undefined;
  setActiveSessionId: (id: string | null) => void;
  addStream: (params: {
    connectionId: string;
    connectionName: string;
    protocol: ConnectionProfile['protocol'];
    destination: string;
    consumerGroup?: string;
    autoStart?: boolean;
  }) => string;
  removeStream: (id: string) => void;
  startStream: (id: string) => void;
  stopStream: (id: string) => void;
  restartAllStreams: () => void;
  sendWsUnsubscribe: (session: LiveStreamSession) => void;
  liveViews: Record<string, LiveViewState>;
  startLiveView: (
    sessionId: string,
    config: {
      topics: string[];
      headerRegex?: string;
      bodyRegex?: string;
      durationMinutes: LiveViewDurationMinutes;
    },
  ) => void;
  stopLiveView: (sessionId: string) => void;
}

const StreamWorkspaceContext = createContext<StreamWorkspaceContextValue | null>(null);

function newSessionId(): string {
  return crypto.randomUUID();
}

function hydrateFromStorage(): { sessions: LiveStreamSession[]; activeId: string | null } {
  const persisted = loadStreamSessionsFromStorage();
  const activeId = loadActiveStreamIdFromStorage();
  const sessions: LiveStreamSession[] = persisted.map((p) => ({
    ...p,
    status: (p.status === 'active' ? 'idle' : p.status) as StreamStatus,
    subscriptionId: undefined,
    messages: [],
  }));
  return {
    sessions,
    activeId: activeId && sessions.some((s) => s.id === activeId) ? activeId : sessions[0]?.id ?? null,
  };
}

export function StreamWorkspaceProvider({ children }: { children: ReactNode }) {
  const initial = useMemo(() => hydrateFromStorage(), []);
  const [sessions, setSessions] = useState<LiveStreamSession[]>(initial.sessions);
  const [activeSessionId, setActiveSessionIdState] = useState<string | null>(initial.activeId);
  const [wsConnected, setWsConnected] = useState(false);
  const [liveViews, setLiveViews] = useState<Record<string, LiveViewState>>({});
  const wsRef = useRef<WebSocket | null>(null);
  const retryRef = useRef(0);
  const sessionsRef = useRef(sessions);
  const activeIdRef = useRef(activeSessionId);
  const liveViewsRef = useRef(liveViews);

  sessionsRef.current = sessions;
  activeIdRef.current = activeSessionId;
  liveViewsRef.current = liveViews;

  const persist = useCallback((list: LiveStreamSession[], activeId: string | null) => {
    saveStreamSessionsToStorage(list.map((s) => toPersisted(s)));
    saveActiveStreamIdToStorage(activeId);
  }, []);

  const updateSession = useCallback((id: string, patch: Partial<LiveStreamSession>) => {
    setSessions((prev) => {
      const next = prev.map((s) => (s.id === id ? { ...s, ...patch, updatedAt: Date.now() } : s));
      persist(next, activeIdRef.current);
      return next;
    });
  }, [persist]);

  const sendWsUnsubscribe = useCallback((session: LiveStreamSession) => {
    const ws = wsRef.current;
    if (ws?.readyState === WebSocket.OPEN && session.subscriptionId) {
      ws.send(
        JSON.stringify({
          type: 'UNSUBSCRIBE',
          clientStreamId: session.id,
          subscriptionId: session.subscriptionId,
        }),
      );
    }
  }, []);

  const sendSubscribe = useCallback(
    (session: LiveStreamSession) => {
      const ws = wsRef.current;
      if (!ws || ws.readyState !== WebSocket.OPEN) return false;
      const existing = sessionsRef.current.find((s) => s.id === session.id);
      if (existing?.subscriptionId) {
        sendWsUnsubscribe(existing);
      }
      updateSession(session.id, { status: 'connecting', lastError: undefined });
      ws.send(
        JSON.stringify({
          type: 'SUBSCRIBE',
          clientStreamId: session.id,
          connectionId: session.connectionId,
          destination: session.destination,
          consumerGroup: session.consumerGroup,
        }),
      );
      return true;
    },
    [sendWsUnsubscribe, updateSession],
  );

  const connectWs = useCallback(() => {
    const { wsUrl } = getRuntimeConfig();
    if (wsRef.current?.readyState === WebSocket.OPEN) return;

    const ws = new WebSocket(wsUrl);
    wsRef.current = ws;

    ws.onopen = () => {
      retryRef.current = 0;
      setWsConnected(true);
      for (const s of sessionsRef.current) {
        if (s.status !== 'stopped') {
          sendSubscribe(s);
        }
      }
    };

    ws.onmessage = (ev) => {
      try {
        const frame = JSON.parse(ev.data) as StreamFrame;
        const streamId = frame.clientStreamId;
        if (!streamId) return;

        if (frame.type === 'SUBSCRIBED' && frame.subscriptionId) {
          updateSession(streamId, {
            status: 'active',
            subscriptionId: frame.subscriptionId,
            lastError: undefined,
          });
        } else if (frame.type === 'MESSAGE' && frame.message) {
          setSessions((prev) => {
            const next = prev.map((s) => {
              if (s.id !== streamId) return s;
              const messages = [frame.message!, ...s.messages].slice(0, 500);
              return {
                ...s,
                status: 'active' as StreamStatus,
                messages,
                messageCount: messages.length,
                updatedAt: Date.now(),
              };
            });
            persist(next, activeIdRef.current);
            return next;
          });
        } else if (frame.type === 'ERROR') {
          if (liveViewsRef.current[streamId]?.active) {
            setLiveViews((prev) => {
              const cur = prev[streamId];
              if (!cur?.active) return prev;
              return {
                ...prev,
                [streamId]: {
                  ...cur,
                  active: false,
                  status: 'error',
                  lastError: frame.detail ?? 'Live view error',
                },
              };
            });
          } else {
            updateSession(streamId, { status: 'error', lastError: frame.detail ?? 'Stream error' });
          }
        } else if (frame.type === 'SLOW_CONSUMER') {
          updateSession(streamId, { lastError: frame.detail ?? 'Slow consumer' });
        } else if (frame.type === 'LIVE_VIEW_STARTED') {
          setLiveViews((prev) => {
            const cur = prev[streamId];
            if (!cur) return prev;
            return {
              ...prev,
              [streamId]: {
                ...cur,
                active: true,
                status: 'active',
                subscriptionId: frame.subscriptionId,
                expiresAt: frame.expiresAt,
                lastError: undefined,
              },
            };
          });
        } else if (frame.type === 'LIVE_VIEW_MESSAGE' && frame.message) {
          setLiveViews((prev) => {
            const cur = prev[streamId];
            if (!cur) return prev;
            const messages = [frame.message!, ...cur.messages].slice(0, 500);
            return {
              ...prev,
              [streamId]: { ...cur, status: 'active', messages },
            };
          });
        } else if (frame.type === 'LIVE_VIEW_EXPIRED' || frame.type === 'LIVE_VIEW_STOPPED') {
          setLiveViews((prev) => {
            const cur = prev[streamId];
            if (!cur) return prev;
            return {
              ...prev,
              [streamId]: {
                ...cur,
                active: false,
                status: frame.type === 'LIVE_VIEW_EXPIRED' ? 'expired' : 'idle',
                subscriptionId: undefined,
                expiresAt: undefined,
              },
            };
          });
        }
      } catch {
        // ignore malformed
      }
    };

    ws.onclose = () => {
      setWsConnected(false);
      setLiveViews((prev) => {
        const next: Record<string, LiveViewState> = {};
        for (const [id, lv] of Object.entries(prev)) {
          next[id] = {
            ...lv,
            active: false,
            status: lv.status === 'active' || lv.status === 'connecting' ? 'idle' : lv.status,
            subscriptionId: undefined,
            expiresAt: undefined,
          };
        }
        return next;
      });
      setSessions((prev) => {
        const next = prev.map((s) =>
          s.status === 'active' || s.status === 'connecting'
            ? { ...s, status: 'idle' as StreamStatus, subscriptionId: undefined }
            : s,
        );
        persist(next, activeIdRef.current);
        return next;
      });
      const delay = Math.min(30_000, 1000 * 2 ** retryRef.current);
      retryRef.current += 1;
      setTimeout(connectWs, delay);
    };

    ws.onerror = () => setWsConnected(false);
  }, [persist, sendSubscribe, updateSession]);

  useEffect(() => {
    connectWs();
    return () => {
      wsRef.current?.close();
      wsRef.current = null;
    };
  }, [connectWs]);

  const setActiveSessionId = useCallback(
    (id: string | null) => {
      setActiveSessionIdState(id);
      saveActiveStreamIdToStorage(id);
    },
    [],
  );

  const addStream = useCallback(
    (params: {
      connectionId: string;
      connectionName: string;
      protocol: ConnectionProfile['protocol'];
      destination: string;
      consumerGroup?: string;
      autoStart?: boolean;
    }) => {
      const existing = sessionsRef.current.find(
        (s) =>
          s.connectionId === params.connectionId &&
          s.destination === params.destination &&
          s.consumerGroup === params.consumerGroup,
      );
      if (existing) {
        setActiveSessionId(existing.id);
        if (params.autoStart !== false) sendSubscribe(existing);
        return existing.id;
      }

      const id = newSessionId();
      const now = Date.now();
      const session: LiveStreamSession = {
        id,
        connectionId: params.connectionId,
        connectionName: params.connectionName,
        protocol: params.protocol,
        destination: params.destination,
        consumerGroup: params.consumerGroup,
        status: params.autoStart === false ? 'stopped' : 'connecting',
        messages: [],
        messageCount: 0,
        createdAt: now,
        updatedAt: now,
      };

      setSessions((prev) => {
        const next = [...prev, session];
        persist(next, id);
        return next;
      });
      setActiveSessionId(id);
      if (params.autoStart !== false) {
        if (!sendSubscribe(session)) connectWs();
      }
      return id;
    },
    [connectWs, persist, sendSubscribe, setActiveSessionId],
  );

  const stopStream = useCallback(
    (id: string) => {
      const session = sessionsRef.current.find((s) => s.id === id);
      if (session) sendWsUnsubscribe(session);
      updateSession(id, { status: 'stopped', subscriptionId: undefined });
    },
    [sendWsUnsubscribe, updateSession],
  );

  const startStream = useCallback(
    (id: string) => {
      const session = sessionsRef.current.find((s) => s.id === id);
      if (!session) return;
      if (!sendSubscribe(session)) connectWs();
    },
    [connectWs, sendSubscribe],
  );

  const stopLiveView = useCallback((sessionId: string) => {
    const ws = wsRef.current;
    if (ws?.readyState === WebSocket.OPEN) {
      ws.send(
        JSON.stringify({
          type: 'STOP_LIVE_VIEW',
          clientStreamId: sessionId,
        }),
      );
    }
    setLiveViews((prev) => {
      const cur = prev[sessionId];
      if (!cur) return prev;
      return {
        ...prev,
        [sessionId]: {
          ...cur,
          active: false,
          status: 'idle',
          subscriptionId: undefined,
          expiresAt: undefined,
        },
      };
    });
  }, []);

  const startLiveView = useCallback(
    (
      sessionId: string,
      config: {
        topics: string[];
        headerRegex?: string;
        bodyRegex?: string;
        durationMinutes: LiveViewDurationMinutes;
      },
    ) => {
      const session = sessionsRef.current.find((s) => s.id === sessionId);
      if (!session) return;
      const existing = liveViewsRef.current[sessionId];
      if (existing?.active) {
        return;
      }
      const ws = wsRef.current;
      if (!ws || ws.readyState !== WebSocket.OPEN) {
        connectWs();
        return;
      }
      setLiveViews((prev) => ({
        ...prev,
        [sessionId]: {
          active: true,
          status: 'connecting',
          topics: config.topics,
          headerRegex: config.headerRegex ?? '',
          bodyRegex: config.bodyRegex ?? '',
          durationMinutes: config.durationMinutes,
          messages: [],
        },
      }));
      ws.send(
        JSON.stringify({
          type: 'START_LIVE_VIEW',
          clientStreamId: sessionId,
          connectionId: session.connectionId,
          topics: config.topics,
          headerRegex: config.headerRegex || undefined,
          bodyRegex: config.bodyRegex || undefined,
          durationMinutes: config.durationMinutes,
        }),
      );
    },
    [connectWs],
  );

  const removeStream = useCallback(
    (id: string) => {
      const session = sessionsRef.current.find((s) => s.id === id);
      if (session) sendWsUnsubscribe(session);
      stopLiveView(id);
      setSessions((prev) => {
        const next = prev.filter((s) => s.id !== id);
        const newActive =
          activeIdRef.current === id ? (next[0]?.id ?? null) : activeIdRef.current;
        setActiveSessionIdState(newActive);
        persist(next, newActive);
        return next;
      });
    },
    [persist, sendWsUnsubscribe, stopLiveView],
  );

  const restartAllStreams = useCallback(() => {
    for (const s of sessionsRef.current) {
      if (s.status !== 'stopped') sendSubscribe(s);
    }
  }, [sendSubscribe]);

  const activeSession = sessions.find((s) => s.id === activeSessionId);

  const value: StreamWorkspaceContextValue = {
    wsConnected,
    sessions,
    activeSessionId,
    activeSession,
    setActiveSessionId,
    addStream,
    removeStream,
    startStream,
    stopStream,
    restartAllStreams,
    sendWsUnsubscribe,
    liveViews,
    startLiveView,
    stopLiveView,
  };

  return (
    <StreamWorkspaceContext.Provider value={value}>{children}</StreamWorkspaceContext.Provider>
  );
}

export function useStreamWorkspace(): StreamWorkspaceContextValue {
  const ctx = useContext(StreamWorkspaceContext);
  if (!ctx) {
    throw new Error('useStreamWorkspace must be used within StreamWorkspaceProvider');
  }
  return ctx;
}
