import { MockWebSocketFactory } from './testing/mock-web-socket';
import { notificationEventFixtures } from './testing/notification-event.fixtures';

describe('notification test doubles', () => {
  it('opens a local websocket double and receives a lifecycle event', () => {
    const factory = new MockWebSocketFactory();
    const socket = factory.connect();
    const messages: string[] = [];

    socket.onmessage = (event) => messages.push(event.data);
    socket.open();
    socket.receive(JSON.stringify(notificationEventFixtures[1]));

    expect(factory.sockets).toHaveLength(1);
    expect(socket.readyState).toBe(1);
    expect(JSON.parse(messages[0]).type).toBe('DOCUMENT_COMPLETED');
  });

  it('captures client messages and closes without using a real socket', () => {
    const socket = new MockWebSocketFactory().connect();

    socket.open();
    socket.send('{"action":"subscribe"}');
    socket.close();

    expect(socket.sentMessages).toEqual(['{"action":"subscribe"}']);
    expect(socket.readyState).toBe(3);
  });
});
