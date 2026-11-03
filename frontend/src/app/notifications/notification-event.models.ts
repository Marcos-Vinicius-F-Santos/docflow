export type DocumentNotificationType =
  | 'DOCUMENT_PROCESSING_STARTED'
  | 'DOCUMENT_COMPLETED'
  | 'DOCUMENT_FAILED'
  | 'DOCUMENT_DELETION_WARNING'
  | 'DOCUMENT_DELETED';

export type DocumentNotificationEvent = {
  eventId: string;
  schemaVersion: 1;
  type: DocumentNotificationType;
  documentId: string;
  occurredAt: string;
  status?: 'PROCESSING' | 'COMPLETED' | 'FAILED';
  failureCount?: number;
  exclusionScheduledAt?: string;
};

export type NotificationConnectionState =
  | 'DISCONNECTED'
  | 'CONNECTING'
  | 'CONNECTED'
  | 'RECONNECTING';
