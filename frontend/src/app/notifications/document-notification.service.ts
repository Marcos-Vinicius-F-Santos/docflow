import { inject, Injectable, InjectionToken } from '@angular/core';
import { NeonAuthService } from '../auth/neon-auth.service';
import { environment } from '../../environments/environment';
import { BehaviorSubject, Observable, Subject } from 'rxjs';
import {
  DocumentNotificationEvent,
  DocumentNotificationType,
  NotificationConnectionState,
} from './notification-event.models';

export const NOTIFICATION_SOCKET_FACTORY = new InjectionToken<(url: string) => WebSocket>(
  'NOTIFICATION_SOCKET_FACTORY',
  {
    providedIn: 'root',
    factory: () => (url: string) => new WebSocket(url),
  },
);

const NOTIFICATION_TYPES: readonly DocumentNotificationType[] = [
  'DOCUMENT_PROCESSING_STARTED',
  'DOCUMENT_COMPLETED',
  'DOCUMENT_FAILED',
  'DOCUMENT_DELETION_WARNING',
  'DOCUMENT_DELETED',
];

@Injectable({ providedIn: 'root' })
export class DocumentNotificationService {
  private readonly socketFactory = inject(NOTIFICATION_SOCKET_FACTORY);
  private readonly auth = inject(NeonAuthService);
  private readonly eventsSubject = new Subject<DocumentNotificationEvent>();
  private readonly stateSubject = new BehaviorSubject<NotificationConnectionState>('DISCONNECTED');

  private socket: WebSocket | null = null;
  private reconnectTimer: ReturnType<typeof setTimeout> | null = null;
  private reconnectAttempt = 0;
  private shouldReconnect = false;

  readonly events$: Observable<DocumentNotificationEvent> = this.eventsSubject.asObservable();
  readonly connectionState$: Observable<NotificationConnectionState> =
    this.stateSubject.asObservable();

  connect(): void {
    this.shouldReconnect = true;
    if (this.socket?.readyState === WebSocket.OPEN || this.socket?.readyState === WebSocket.CONNECTING) {
      return;
    }

    this.openSocket(this.reconnectAttempt > 0);
  }

  disconnect(): void {
    this.shouldReconnect = false;
    this.clearReconnectTimer();
    this.reconnectAttempt = 0;
    const socket = this.socket;
    this.socket = null;
    socket?.close();
    this.stateSubject.next('DISCONNECTED');
  }

  private async openSocket(reconnecting: boolean): Promise<void> {
    this.stateSubject.next(reconnecting ? 'RECONNECTING' : 'CONNECTING');
    const token = await this.auth.getAccessToken();
    if (!token) {
      this.scheduleReconnect();
      return;
    }

    const socket = this.socketFactory(this.websocketUrl(token));
    this.socket = socket;

    socket.onopen = () => {
      this.reconnectAttempt = 0;
      this.stateSubject.next('CONNECTED');
    };
    socket.onmessage = (message) => {
      const event = this.parseEvent(message.data);
      if (event) {
        this.eventsSubject.next(event);
      }
    };
    socket.onerror = () => {
      this.stateSubject.next('DISCONNECTED');
    };
    socket.onclose = () => {
      if (this.socket === socket) {
        this.socket = null;
      }
      if (this.shouldReconnect) {
        this.scheduleReconnect();
      } else {
        this.stateSubject.next('DISCONNECTED');
      }
    };
  }

  private scheduleReconnect(): void {
    if (this.reconnectTimer !== null) {
      return;
    }

    const delay = Math.min(1000 * 2 ** this.reconnectAttempt, 30_000);
    this.reconnectAttempt += 1;
    this.stateSubject.next('RECONNECTING');
    this.reconnectTimer = setTimeout(() => {
      this.reconnectTimer = null;
      if (this.shouldReconnect) {
        this.openSocket(true);
      }
    }, delay);
  }

  private clearReconnectTimer(): void {
    if (this.reconnectTimer !== null) {
      clearTimeout(this.reconnectTimer);
      this.reconnectTimer = null;
    }
  }

  private websocketUrl(accessToken: string | null): string {
    const appendToken = (url: string): string => accessToken
      ? `${url}${url.includes('?') ? '&' : '?'}access_token=${encodeURIComponent(accessToken)}`
      : url;
    if (environment.apiBaseUrl) {
      const websocketBaseUrl = environment.apiBaseUrl.replace(/^http/, 'ws');
      return appendToken(`${websocketBaseUrl}${environment.notificationWebSocketPath}`);
    }

    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
    return appendToken(`${protocol}//${window.location.host}${environment.notificationWebSocketPath}`);
  }

  private parseEvent(data: string): DocumentNotificationEvent | null {
    try {
      const candidate: unknown = JSON.parse(data);
      if (!this.isRecord(candidate)) {
        return null;
      }
      if (
        typeof candidate['eventId'] !== 'string' ||
        candidate['schemaVersion'] !== 1 ||
        !this.isNotificationType(candidate['type']) ||
        typeof candidate['documentId'] !== 'string' ||
        typeof candidate['occurredAt'] !== 'string'
      ) {
        return null;
      }
      return candidate as unknown as DocumentNotificationEvent;
    } catch {
      return null;
    }
  }

  private isNotificationType(value: unknown): value is DocumentNotificationType {
    return typeof value === 'string' && NOTIFICATION_TYPES.includes(value as DocumentNotificationType);
  }

  private isRecord(value: unknown): value is Record<string, unknown> {
    return typeof value === 'object' && value !== null && !Array.isArray(value);
  }
}
