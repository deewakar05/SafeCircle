import { useEffect, useRef, useState, useCallback } from 'react';
import { createClient, destroyClient, publishLocation } from '../services/socket';

/**
 * useWebSocket
 *
 * Manages the entire WebSocket lifecycle for a group tracking session.
 *
 * @param {string}   groupId    - Group to subscribe to
 * @param {Function} onLocation - Called with each LocationResponse update
 * @param {Function} onAlert    - Called with each alert string
 *
 * @returns {{ wsState: 'CONNECTING'|'CONNECTED'|'DISCONNECTED', publish: Function }}
 */
export function useWebSocket(groupId, onLocation, onAlert) {
  const [wsState, setWsState] = useState('CONNECTING');

  // Use refs for callbacks so we never need to re-subscribe when they change
  const onLocationRef = useRef(onLocation);
  const onAlertRef    = useRef(onAlert);
  useEffect(() => { onLocationRef.current = onLocation; }, [onLocation]);
  useEffect(() => { onAlertRef.current    = onAlert;    }, [onAlert]);

  // Keep a ref to the STOMP client so publish() always has access
  const clientRef = useRef(null);

  useEffect(() => {
    let subscriptions = [];

    const client = createClient({
      onConnect: () => {
        setWsState('CONNECTED');
        console.log('[WS] ▶ Connected to group', groupId);

        // Subscribe to location broadcasts for this group
        const s1 = client.subscribe(`/topic/group/${groupId}`, (msg) => {
          try {
            onLocationRef.current(JSON.parse(msg.body));
          } catch (e) {
            console.error('[WS] Failed to parse location message', e);
          }
        });

        // Subscribe to alert messages
        const s2 = client.subscribe(`/topic/alerts/${groupId}`, (msg) => {
          onAlertRef.current(msg.body);
        });

        subscriptions = [s1, s2];
        clientRef.current = client;
      },

      onDisconnect: () => {
        setWsState('DISCONNECTED');
        console.warn('[WS] ■ Disconnected');
      },

      onError: (frame) => {
        setWsState('DISCONNECTED');
        console.error('[WS] STOMP error:', frame);
      },
    });

    return () => {
      subscriptions.forEach(s => s?.unsubscribe?.());
      destroyClient();
      clientRef.current = null;
      setWsState('CONNECTING');
    };
  }, [groupId]); // only re-init if group changes

  /**
   * Publish a location payload over the WebSocket connection.
   * Returns true if sent via WS, false if WS is not ready (caller should HTTP fallback).
   */
  const publish = useCallback((payload) => publishLocation(payload), []);

  return { wsState, publish };
}
