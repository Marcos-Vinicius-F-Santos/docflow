import { TestBed } from '@angular/core/testing';
import { DocumentNotificationService, NOTIFICATION_SOCKET_FACTORY } from './document-notification.service';
import { notificationEventFixtures } from './testing/notification-event.fixtures';
import { MockWebSocket, MockWebSocketFactory } from './testing/mock-web-socket';

describe('DocumentNotificationService', () => {
  let factory: MockWebSocketFactory;
  let service: DocumentNotificationService;

  beforeEach(() => {
    vi.useFakeTimers();
    factory = new MockWebSocketFactory();
    TestBed.configureTestingModule({
      providers: [
        DocumentNotificationService,
        {
          provide: NOTIFICATION_SOCKET_FACTORY,
          useValue: () => factory.connect() as unknown as WebSocket,
        },
      ],
    });
    service = TestBed.inject(DocumentNotificationService);
  });

  afterEach(() => {
    service.disconnect();
    vi.useRealTimers();
  });

  const flushSocketCreation = async (): Promise<void> => {
    await Promise.resolve();
    await Promise.resolve();
  };

  it('opens the native socket and emits valid lifecycle events', async () => {
    const received: string[] = [];
    service.events$.subscribe((event) => received.push(event.type));

    service.connect();
    await flushSocketCreation();
    const socket = factory.sockets[0];
    socket.open();
    socket.receive(JSON.stringify(notificationEventFixtures[1]));

    expect(factory.sockets).toHaveLength(1);
    expect(received).toEqual(['DOCUMENT_COMPLETED']);
  });

  it('exposes connection state transitions', async () => {
    const states: string[] = [];
    service.connectionState$.subscribe((state) => states.push(state));

    service.connect();
    await flushSocketCreation();
    factory.sockets[0].open();
    factory.sockets[0].close();

    expect(states).toContain('CONNECTING');
    expect(states).toContain('CONNECTED');
    expect(states).toContain('RECONNECTING');
  });

  it('reconnects with progressive delays after a close', async () => {
    service.connect();
    await flushSocketCreation();
    const first = factory.sockets[0];
    first.open();
    first.close();

    vi.advanceTimersByTime(999);
    expect(factory.sockets).toHaveLength(1);
    vi.advanceTimersByTime(1);
    await flushSocketCreation();
    expect(factory.sockets).toHaveLength(2);

    factory.sockets[1].close();
    vi.advanceTimersByTime(1999);
    expect(factory.sockets).toHaveLength(2);
    vi.advanceTimersByTime(1);
    await flushSocketCreation();
    expect(factory.sockets).toHaveLength(3);
  });

  it('ignores malformed and unknown messages without breaking the connection', async () => {
    const received: string[] = [];
    service.events$.subscribe((event) => received.push(event.eventId));
    service.connect();
    await flushSocketCreation();
    const socket = factory.sockets[0];
    socket.open();

    socket.receive('{invalid');
    socket.receive(JSON.stringify({ ...notificationEventFixtures[0], type: 'UNKNOWN' }));
    socket.receive(JSON.stringify(notificationEventFixtures[0]));

    expect(received).toEqual([notificationEventFixtures[0].eventId]);
    expect(socket.readyState).toBe(MockWebSocket.OPEN);
  });
});
