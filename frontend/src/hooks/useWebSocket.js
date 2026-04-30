import { useEffect, useRef, useState, useCallback } from 'react';
import { createClient, destroyClient, publishLocation } from '../services/socket';

/**
 * useWebSocket
 *
 * Manages the complete WebSocket lifecycle for a group tracking session.
 *
 * @param {string}   groupId    - Group to subscribe to
 * @param {Function} onLocation - Called with each LocationResponse update
 * @param {Function} onAlert    - Called with each alert string
 *
 * @returns {{ wsState: 'CONNECTING'|'CONNECTED'|'DISCONNECTED', publish: Function }}
 */
export function useWebSocket(groupId, onLocation, onAlert) {
  const [wsState, setWsState] = useState('CONNECTING');

  // Stable refs for callbacks — avoids re-subscribing when handlers change identity
  const onLocationRef = useRef(onLocation);
  const onAlertRef    = useRef(onAlert);
  useEffect(() => { onLocationRef.current = onLocation; }, [onLocation]);
  useEffect(() => { onAlertRef.current    = onAlert;    }, [onAlert]);

  // Keeps a ref to the STOMP client so publish() always has the latest instance
  const clientRef      = useRef(null);

  // Guard against duplicate subscriptions within the same connection
  const subscribedRef  = useRef(false);

  useEffect(() => {
    let subscriptions = [];
    subscribedRef.current = false;

    const client = createClient({
      onConnect: () => {
        // Prevent duplicate subscriptions if onConnect fires more than once
        // (e.g., after an internal reconnect within the same effect lifecycle)
        if (subscribedRef.current) {
          console.warn('[WS] onConnect fired but already subscribed — skipping duplicate');
          return;
        }
        subscribedRef.current = true;
        setWsState('CONNECTED');
        console.info('[WS] ▶ Connected to group', groupId);

        // Subscribe to live location broadcasts
        const s1 = client.subscribe(`/topic/group/${groupId}`, (msg) => {
          try {
            onLocationRef.current(JSON.parse(msg.body));
          } catch (e) {
            console.error('[WS] Failed to parse location message', e);
          }
        });

        // Subscribe to alert/toast messages
        const s2 = client.subscribe(`/topic/alerts/${groupId}`, (msg) => {
          onAlertRef.current(msg.body);
        });

        subscriptions = [s1, s2];
        clientRef.current = client;
      },

      onDisconnect: () => {
        subscribedRef.current = false;
        setWsState('DISCONNECTED');
        console.warn('[WS] ■ Disconnected');
      },

      onError: (frame) => {
        subscribedRef.current = false;
        setWsState('DISCONNECTED');
        console.error('[WS] STOMP error:', frame);
      },
    });

    return () => {
      // Unsubscribe and tear down on groupId change or unmount
      subscriptions.forEach(s => s?.unsubscribe?.());
      subscribedRef.current = false;
      destroyClient();
      clientRef.current = null;
      setWsState('CONNECTING');
    };
  }, [groupId]); // only re-initialise if the group changes

  /**
   * Publish a location payload over the active WebSocket connection.
   * Returns true if sent via WS, false if the connection isn't ready
   * (caller should use HTTP fallback in that case).
   */
  const publish = useCallback((payload) => publishLocation(payload), []);

  return { wsState, publish };
}
