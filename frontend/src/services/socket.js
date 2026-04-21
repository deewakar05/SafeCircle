import SockJS from 'sockjs-client';
import { Client } from '@stomp/stompjs';

let stompClient = null;

export const connectSocket = (groupId, onLocation, onAlert) => {
  stompClient = new Client({
    webSocketFactory: () => new SockJS('/ws'),
    reconnectDelay: 5000,
    onConnect: () => {
      console.log('[WS] Connected');

      // Subscribe to location broadcasts
      stompClient.subscribe(`/topic/group/${groupId}`, (msg) => {
        try {
          const data = JSON.parse(msg.body);
          onLocation(data);
        } catch (e) {
          console.error('[WS] Failed to parse location message', e);
        }
      });

      // Subscribe to alerts
      stompClient.subscribe(`/topic/alerts/${groupId}`, (msg) => {
        onAlert(msg.body);
      });
    },
    onStompError: (frame) => {
      console.error('[WS] STOMP error:', frame);
    }
  });

  stompClient.activate();
};

export const sendLocation = (userId, groupId, lat, lng, status = 'ONLINE') => {
  if (stompClient && stompClient.connected) {
    stompClient.publish({
      destination: '/app/location.update',
      body: JSON.stringify({ userId, groupId, lat, lng, status }),
    });
  }
};

export const disconnectSocket = () => {
  if (stompClient) {
    stompClient.deactivate();
    stompClient = null;
  }
};
