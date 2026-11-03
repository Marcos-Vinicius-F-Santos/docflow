import { HttpErrorResponse } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { LogsApiService } from './logs-api.service';
import { ApplicationLogPageResponse } from './logs.models';
import { mapApplicationLogError } from './logs-error.mapper';

describe('LogsApiService', () => {
  let service: LogsApiService;
  let http: HttpTestingController;

  const pageResponse: ApplicationLogPageResponse = {
    items: [],
    page: 0,
    size: 50,
    totalElements: 0,
    totalPages: 0,
    first: true,
    last: true,
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        LogsApiService,
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
    });
    service = TestBed.inject(LogsApiService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('sends every approved filter and pagination parameter to GET /logs', () => {
    service.find({
      page: 2,
      size: 25,
      level: 'ERROR',
      service: 'document-api',
      component: 'storage-consumer',
      operation: 'document.processing',
      documentId: 'document-id',
      correlationId: 'correlation-id',
      occurredFrom: '2026-10-29T10:00:00Z',
      occurredTo: '2026-10-29T11:00:00Z',
    }).subscribe((response) => expect(response).toEqual(pageResponse));

    const request = http.expectOne((candidate) => candidate.url.endsWith('/api/logs'));
    expect(request.request.method).toBe('GET');
    expect(request.request.params.keys()).toEqual([
      'page',
      'size',
      'level',
      'service',
      'component',
      'operation',
      'documentId',
      'correlationId',
      'occurredFrom',
      'occurredTo',
    ]);
    expect(request.request.params.get('level')).toBe('ERROR');
    expect(request.request.params.get('occurredTo')).toBe('2026-10-29T11:00:00Z');
    request.flush(pageResponse);
  });

  it('omits empty filters while preserving the backend defaults', () => {
    service.find({ service: '', operation: undefined }).subscribe();

    const request = http.expectOne((candidate) => candidate.url.endsWith('/api/logs'));
    expect(request.request.params.keys()).toHaveLength(0);
    request.flush(pageResponse);
  });
});

describe('mapApplicationLogError', () => {
  it('maps validation and unavailable responses to safe messages', () => {
    const validation = mapApplicationLogError(new HttpErrorResponse({ status: 400, error: { detail: 'SQL leak' } }));
    const unavailable = mapApplicationLogError(new HttpErrorResponse({ status: 503, error: { detail: 'stack trace' } }));

    expect(validation).toMatchObject({ kind: 'validation', canRetryManually: false });
    expect(validation.message).not.toContain('SQL leak');
    expect(unavailable).toMatchObject({ kind: 'unavailable', canRetryManually: true });
    expect(unavailable.message).not.toContain('stack trace');
  });
});
