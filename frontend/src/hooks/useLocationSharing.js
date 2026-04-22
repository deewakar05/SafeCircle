import { useState, useRef, useCallback, useEffect } from 'react';
import { locationApi } from '../services/api';

/**
 * useLocationSharing
 *
 * Encapsulates browser GPS watch + location publishing.
 *
 * Publishing strategy (Phase 4):
 *   1. Try WebSocket (instant broadcast, also persists via WS handler)
 *   2. Fall back to HTTP POST if WS is not connected
 *
 * @param {string}   groupId   - The group to broadcast into
 * @param {Function} wsPublish - publish(payload) from useWebSocket; returns true if sent
 *
 * @returns {{ sharing, accuracy, gpsError, startSharing, stopSharing }}
 */
export function useLocationSharing(groupId, wsPublish) {
  const [sharing,  setSharing]  = useState(false);
  const [accuracy, setAccuracy] = useState(null);   // metres
  const [gpsError, setGpsError] = useState(null);

  const watchIdRef  = useRef(null);
  const lastSentRef = useRef(null);  // throttle: { lat, lng, ts }

  const sendPayload = useCallback((payload) => {
    // WS-first: instant + persisted via LocationWebSocketHandler
    const sentViaWs = wsPublish && wsPublish(payload);
    // HTTP fallback: used when WS is CONNECTING or DISCONNECTED
    if (!sentViaWs) {
      locationApi.update(payload).catch(() => {});
    }
  }, [wsPublish]);

  const stopSharing = useCallback(() => {
    if (watchIdRef.current != null) {
      navigator.geolocation.clearWatch(watchIdRef.current);
      watchIdRef.current = null;
    }
    setSharing(false);
    setAccuracy(null);
    setGpsError(null);
    sendPayload({ groupId, lat: 0, lng: 0, status: 'OFFLINE', accuracy: null });
  }, [groupId, sendPayload]);

  const startSharing = useCallback(() => {
    if (!navigator.geolocation) {
      setGpsError('Geolocation not supported by this browser.');
      return;
    }
    setGpsError(null);
    setSharing(true);

    const onSuccess = (pos) => {
      const { latitude: lat, longitude: lng, accuracy: acc } = pos.coords;
      setAccuracy(Math.round(acc));

      // Throttle: only publish if moved >5 m OR >5 s since last send
      const now  = Date.now();
      const last = lastSentRef.current;
      const moved =
        !last ||
        Math.abs(lat - last.lat) > 0.00005 ||
        Math.abs(lng - last.lng) > 0.00005 ||
        now - last.ts > 5000;

      if (moved) {
        lastSentRef.current = { lat, lng, ts: now };
        sendPayload({ groupId, lat, lng, status: 'ONLINE', accuracy: acc });
      }
    };

    const onError = (err) => {
      console.warn('[GPS]', err.message);
      setGpsError(err.message);
      sendPayload({ groupId, lat: 0, lng: 0, status: 'NO_GPS', accuracy: null });
    };

    watchIdRef.current = navigator.geolocation.watchPosition(onSuccess, onError, {
      enableHighAccuracy: true,
      maximumAge: 5000,
      timeout: 10000,
    });
  }, [groupId, sendPayload]);

  // Cleanup on unmount
  useEffect(() => () => {
    if (watchIdRef.current != null) stopSharing();
  }, [stopSharing]);

  return { sharing, accuracy, gpsError, startSharing, stopSharing };
}
