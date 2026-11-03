export type MockWebSocketMessageEvent = { data: string };

export class MockWebSocket {
  static readonly CONNECTING = 0;
  static readonly OPEN = 1;
  static readonly CLOSING = 2;
  static readonly CLOSED = 3;

  readonly sentMessages: string[] = [];
  readyState = MockWebSocket.CONNECTING;
  onopen: (() => void) | null = null;
  onmessage: ((event: MockWebSocketMessageEvent) => void) | null = null;
  onclose: (() => void) | null = null;

  open(): void {
    this.readyState = MockWebSocket.OPEN;
    this.onopen?.();
  }

  send(message: string): void {
    if (this.readyState !== MockWebSocket.OPEN) {
      throw new Error('MockWebSocket is not open');
    }
    this.sentMessages.push(message);
  }

  receive(message: string): void {
    if (this.readyState === MockWebSocket.OPEN) {
      this.onmessage?.({ data: message });
    }
  }

  close(): void {
    this.readyState = MockWebSocket.CLOSED;
    this.onclose?.();
  }
}

export class MockWebSocketFactory {
  readonly sockets: MockWebSocket[] = [];

  connect(): MockWebSocket {
    const socket = new MockWebSocket();
    this.sockets.push(socket);
    return socket;
  }
}
