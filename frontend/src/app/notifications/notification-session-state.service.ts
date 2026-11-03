import { Injectable, inject } from '@angular/core';
import { Observable, Subject } from 'rxjs';
import { DocumentNotificationService } from './document-notification.service';
import {
  DocumentNotificationEvent,
  DocumentNotificationType,
  NotificationConnectionState,
} from './notification-event.models';

const NOTIFICATION_TYPES: readonly DocumentNotificationType[] = [
  'DOCUMENT_PROCESSING_STARTED',
  'DOCUMENT_COMPLETED',
  'DOCUMENT_FAILED',
  'DOCUMENT_DELETION_WARNING',
  'DOCUMENT_DELETED',
];

@Injectable({ providedIn: 'root' })
export class NotificationSessionState {
  private readonly notificationService = inject(DocumentNotificationService);
  private readonly eventSubject = new Subject<DocumentNotificationEvent>();
  private readonly seenEventIds = new Set<string>();

  readonly events$: Observable<DocumentNotificationEvent> = this.eventSubject.asObservable();
  readonly connectionState$: Observable<NotificationConnectionState> =
    this.notificationService.connectionState$;

  constructor() {
    this.notificationService.events$.subscribe((event) => this.accept(event));
  }

  start(): void {
    this.notificationService.connect();
  }

  stop(): void {
    this.notificationService.disconnect();
  }

  accept(candidate: unknown): void {
    if (!this.isEvent(candidate) || this.seenEventIds.has(candidate.eventId)) {
      return;
    }

    this.seenEventIds.add(candidate.eventId);
    this.eventSubject.next(candidate);
  }

  private isEvent(candidate: unknown): candidate is DocumentNotificationEvent {
    if (typeof candidate !== 'object' || candidate === null || Array.isArray(candidate)) {
      return false;
    }
    const event = candidate as Record<string, unknown>;
    return (
      typeof event['eventId'] === 'string' &&
      event['schemaVersion'] === 1 &&
      typeof event['type'] === 'string' &&
      NOTIFICATION_TYPES.includes(event['type'] as DocumentNotificationType) &&
      typeof event['documentId'] === 'string' &&
      typeof event['occurredAt'] === 'string'
    );
  }
}
