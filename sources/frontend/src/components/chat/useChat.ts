'use client';

/**
 * useChat — single hook owning sessions + active session + messages + streaming state.
 *
 * Phase 22 Plan 05. Wires SSE wire format D-14 (JSON-line events
 * `{type:'delta'|'done'|'error', text?, sessionId?, error?}`) into a regular
 * fetch + ReadableStream.getReader() loop with a TextDecoder + buffer accumulator
 * so partial chunks across newlines are handled correctly.
 *
 * Mitigations:
 * - T-22-07 (resource leak on close): exposes `abortStream` via AbortController.
 * - 429 surfaces a Vietnamese-friendly error to the UI (D-13 toast + retry).
 */
import { useCallback, useEffect, useRef, useState } from 'react';
import { getAccessToken } from '@/services/token';
import {
  listChatSessions,
  getChatMessages,
  type ChatSessionDto,
  type ChatMessageDto,
} from '@/services/chat';

export interface UseChatState {
  sessions: ChatSessionDto[];
  activeSessionId: number | null;
  messages: ChatMessageDto[];
  isStreaming: boolean;
  lastError: string | null;
  lastUserMessage: string | null;
}

export function useChat(enabled: boolean) {
  const [sessions, setSessions] = useState<ChatSessionDto[]>([]);
  const [activeSessionId, setActiveSessionId] = useState<number | null>(null);
  const [messages, setMessages] = useState<ChatMessageDto[]>([]);
  const [isStreaming, setIsStreaming] = useState(false);
  const [lastError, setLastError] = useState<string | null>(null);
  const [lastUserMessage, setLastUserMessage] = useState<string | null>(null);
  const abortRef = useRef<AbortController | null>(null);

  const refreshSessions = useCallback(async () => {
    try {
      const list = await listChatSessions(20);
      setSessions(list);
    } catch {
      /* silent — sidebar is non-critical */
    }
  }, []);

  useEffect(() => {
    if (enabled) void refreshSessions();
  }, [enabled, refreshSessions]);

  const openSession = useCallback(async (id: number) => {
    setActiveSessionId(id);
    setMessages([]);
    setLastError(null);
    try {
      const msgs = await getChatMessages(id);
      setMessages(msgs);
    } catch {
      setLastError('Không thể tải lịch sử đoạn chat');
    }
  }, []);

  const newSession = useCallback(() => {
    setActiveSessionId(null);
    setMessages([]);
    setLastError(null);
    setLastUserMessage(null);
  }, []);

  const sendMessage = useCallback(
    async (text: string) => {
      const trimmed = text.trim();
      if (!trimmed) return;
      setLastError(null);
      setLastUserMessage(trimmed);
      setIsStreaming(true);
      // optimistic append: user bubble + empty assistant bubble (will be filled by deltas)
      setMessages((prev) => [
        ...prev,
        { role: 'user', content: trimmed },
        { role: 'assistant', content: '' },
      ]);

      const token = getAccessToken();
      const controller = new AbortController();
      abortRef.current = controller;

      const currentSessionId = activeSessionId;
      let resolvedSessionId: number | null = currentSessionId;

      try {
        const headers: Record<string, string> = { 'Content-Type': 'application/json' };
        if (token) headers['Authorization'] = `Bearer ${token}`;

        const res = await fetch('/api/chat/stream', {
          method: 'POST',
          headers,
          body: JSON.stringify({ sessionId: currentSessionId, message: trimmed }),
          signal: controller.signal,
        });

        if (!res.ok || !res.body) {
          if (res.status === 429) {
            throw new Error('Bạn nhắn quá nhanh, thử lại sau ít phút');
          }
          if (res.status === 401) {
            throw new Error('Phiên đăng nhập đã hết hạn, vui lòng đăng nhập lại');
          }
          throw new Error(`HTTP ${res.status}`);
        }

        const reader = res.body.getReader();
        const decoder = new TextDecoder();
        let buf = '';
        let assistantText = '';

        for (;;) {
          const { value, done } = await reader.read();
          if (done) break;
          buf += decoder.decode(value, { stream: true });
          let nl: number;
          while ((nl = buf.indexOf('\n')) >= 0) {
            const line = buf.slice(0, nl).trim();
            buf = buf.slice(nl + 1);
            if (!line) continue;
            let ev: { type: string; text?: string; sessionId?: number; error?: string };
            try {
              ev = JSON.parse(line);
            } catch {
              continue;
            }
            if (ev.type === 'delta' && ev.text) {
              assistantText += ev.text;
              setMessages((prev) => {
                const copy = prev.slice();
                copy[copy.length - 1] = { role: 'assistant', content: assistantText };
                return copy;
              });
            } else if (ev.type === 'done') {
              if (typeof ev.sessionId === 'number') {
                resolvedSessionId = ev.sessionId;
              }
            } else if (ev.type === 'error') {
              throw new Error(ev.error ?? 'stream_failed');
            }
          }
        }

        if (resolvedSessionId != null && currentSessionId == null) {
          setActiveSessionId(resolvedSessionId);
        }
        await refreshSessions();
      } catch (e) {
        if (e instanceof DOMException && e.name === 'AbortError') {
          // user closed panel mid-stream — not an error
          return;
        }
        const msg = e instanceof Error ? e.message : 'Lỗi không xác định';
        setLastError(msg);
      } finally {
        setIsStreaming(false);
        abortRef.current = null;
      }
    },
    [activeSessionId, refreshSessions],
  );

  const retryLast = useCallback(async () => {
    if (lastUserMessage == null) return;
    // remove last two bubbles (failed user + empty assistant) before resending
    setMessages((prev) => {
      // last bubble is the empty/partial assistant; preceding is the user bubble we re-send
      const copy = prev.slice();
      // pop assistant
      if (copy.length > 0 && copy[copy.length - 1].role === 'assistant') copy.pop();
      // pop user (will be re-added optimistically by sendMessage)
      if (copy.length > 0 && copy[copy.length - 1].role === 'user') copy.pop();
      return copy;
    });
    await sendMessage(lastUserMessage);
  }, [lastUserMessage, sendMessage]);

  const abortStream = useCallback(() => {
    abortRef.current?.abort();
  }, []);

  return {
    sessions,
    activeSessionId,
    messages,
    isStreaming,
    lastError,
    lastUserMessage,
    sendMessage,
    openSession,
    newSession,
    retryLast,
    abortStream,
    refreshSessions,
  };
}
