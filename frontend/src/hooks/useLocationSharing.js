import { useState, useRef, useCallback, useEffect } from 'react';
import { locationApi } from '../services/api';

/**
 * useLocationSharing
 * Encapsulates browser GPS watch + periodic upload to the backend.
 *
 * @param {string} groupId  - The group to broadcast into
 * @returns {{ sharing, accuracy, startSharing, stopSharing, gpsError }}
 */
export function useLocationSharing(groupId) {
  const [sharing, setSharing]   = useState(false);
  const [accuracy, setAccuracy] = useState(null);   // metres
  const [gpsError, setGpsError] = useState(null);

  const watchIdRef   = useRef(null);
  const lastSentRef  = useRef(null);   // throttle: don't spam if not moved much

  const stopSharing = useCallback(() => {
    if (watchIdRef.current != null) {
      navigator.geolocation.clearWatch(watchIdRef.current);
      watchIdRef.current = null;
    }
    setSharing(false);
    setAccuracy(null);
    setGpsError(null);
    // Tell server we're offline
    locationApi
      .update({ groupId, lat: 0, lng: 0, status: 'OFFLINE' })
      .catch(() => {});
  }, [groupId]);

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

      // Throttle: only send if moved >5 m or >5 s since last send
      const now = Date.now();
      const last = lastSentRef.current;
      const moved = !last || Math.abs(lat - last.lat) > 0.00005 ||
                             Math.abs(lng - last.lng) > 0.00005 ||
                             now - last.ts > 5000;
      if (moved) {
        lastSentRef.current = { lat, lng, ts: now };
        locationApi.update({ groupId, lat, lng, status: 'ONLINE' }).catch(() => {});
      }
    };

    const onError = (err) => {
      console.warn('[GPS]', err.message);
      setGpsError(err.message);
      locationApi
        .update({ groupId, lat: 0, lng: 0, status: 'NO_GPS' })
        .catch(() => {});
    };

    watchIdRef.current = navigator.geolocation.watchPosition(onSuccess, onError, {
      enableHighAccuracy: true,
      maximumAge: 5000,
      timeout: 10000,
    });
  }, [groupId]);

  // Cleanup when component unmounts
  useEffect(() => () => {
    if (watchIdRef.current != null) stopSharing();
  }, [stopSharing]);

  return { sharing, accuracy, gpsError, startSharing, stopSharing };
}
