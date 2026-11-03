export type ApplicationLogLevel = 'INFO' | 'WARN' | 'ERROR';

export interface ApplicationLogRecord {
  eventId: string;
  schemaVersion: number;
  occurredAt: string;
  level: ApplicationLogLevel;
  service: string;
  component: string;
  operation: string;
  message: string;
  correlationId: string | null;
  documentId: string | null;
  details: Record<string, unknown> | null;
}

export interface ApplicationLogPageResponse {
  items: ApplicationLogRecord[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  first: boolean;
  last: boolean;
}

export interface ApplicationLogQuery {
  page?: number;
  size?: number;
  level?: ApplicationLogLevel;
  service?: string;
  component?: string;
  operation?: string;
  documentId?: string;
  correlationId?: string;
  occurredFrom?: string;
  occurredTo?: string;
}
