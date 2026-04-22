import SockJS from 'sockjs-client';
import { Client } from '@stomp/stompjs';

/**
 * socket.js — thin STOMP client factory.
 *
 * All reactive logic (state tracking, subscriptions) lives in the
 * useWebSocket hook. This module just creates/destroys the client.
 */

let _client = null;

/**
 * Create and activate a STOMP client.
 *
 * @param {object} handlers
 * @param {Function} handlers.onConnect   - Called when STOMP handshake completes
 * @param {Function} handlers.onDisconnect - Called when session closes
 * @param {Function} handlers.onError     - Called on STOMP protocol error
 */
export function createClient({ onConnect, onDisconnect, onError }) {
  if (_client) {
    _client.deactivate();
    _client = null;
  }

  const token = localStorage.getItem('sc_token') || '';

  _client = new Client({
    webSocketFactory: () => {
      const WS_URL = import.meta.env.VITE_WS_BASE_URL || '/ws';
      // SockJS requires absolute URLs if it's hitting a different domain
      const finalUrl = WS_URL.startsWith('http') ? WS_URL : (window.location.origin + WS_URL);
      return new SockJS(finalUrl);
    },
    // Send JWT in STOMP CONNECT frame — picked up by WebSocketChannelInterceptor
    connectHeaders: {
      Authorization: `Bearer ${token}`,
    },
    reconnectDelay: 4000,
    onConnect,
    onDisconnect,
    onStompError: onError,
  });

  _client.activate();
  return _client;
}

/**
 * Publish a location update directly over the WebSocket connection.
 * Returns true if the message was published, false if WS is not connected.
 */
export function publishLocation(payload) {
  if (_client?.connected) {
    _client.publish({
      destination: '/app/location.update',
      body: JSON.stringify(payload),
    });
    return true;
  }
  return false;
}

export function destroyClient() {
  if (_client) {
    _client.deactivate();
    _client = null;
  }
}
