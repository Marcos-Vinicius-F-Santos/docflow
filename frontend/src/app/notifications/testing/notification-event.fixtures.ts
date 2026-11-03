export interface NotificationEventFixture {
  eventId: string;
  schemaVersion: 1;
  type:
    | 'DOCUMENT_PROCESSING_STARTED'
    | 'DOCUMENT_COMPLETED'
    | 'DOCUMENT_FAILED'
    | 'DOCUMENT_DELETION_WARNING'
    | 'DOCUMENT_DELETED';
  documentId: string;
  occurredAt: string;
  status?: 'PROCESSING' | 'COMPLETED' | 'FAILED';
  failureCount?: number;
  exclusionScheduledAt?: string;
}

export const notificationEventFixtures = [
  {
    eventId: '00000000-0000-0000-0000-000000000201',
    schemaVersion: 1,
    type: 'DOCUMENT_PROCESSING_STARTED',
    documentId: '00000000-0000-0000-0000-000000000101',
    occurredAt: '2026-10-29T12:00:00Z',
    status: 'PROCESSING',
  },
  {
    eventId: '00000000-0000-0000-0000-000000000202',
    schemaVersion: 1,
    type: 'DOCUMENT_COMPLETED',
    documentId: '00000000-0000-0000-0000-000000000101',
    occurredAt: '2026-10-29T12:00:30Z',
    status: 'COMPLETED',
  },
  {
    eventId: '00000000-0000-0000-0000-000000000203',
    schemaVersion: 1,
    type: 'DOCUMENT_FAILED',
    documentId: '00000000-0000-0000-0000-000000000101',
    occurredAt: '2026-10-29T12:01:00Z',
    status: 'FAILED',
    failureCount: 1,
  },
  {
    eventId: '00000000-0000-0000-0000-000000000204',
    schemaVersion: 1,
    type: 'DOCUMENT_DELETION_WARNING',
    documentId: '00000000-0000-0000-0000-000000000101',
    occurredAt: '2026-10-29T12:01:30Z',
    status: 'FAILED',
    failureCount: 3,
    exclusionScheduledAt: '2026-10-29T14:30:00Z',
  },
  {
    eventId: '00000000-0000-0000-0000-000000000205',
    schemaVersion: 1,
    type: 'DOCUMENT_DELETED',
    documentId: '00000000-0000-0000-0000-000000000101',
    occurredAt: '2026-10-29T12:02:00Z',
  },
] satisfies readonly NotificationEventFixture[];
