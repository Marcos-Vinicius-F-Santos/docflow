import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { Observable, Subject, of, throwError } from 'rxjs';
import { DocumentApiService } from './document-api.service';
import { DocumentResponse } from './document.models';
import { DocumentStatusPollerService } from './document-status-poller.service';

describe('DocumentStatusPollerService', () => {
  let service: DocumentStatusPollerService;
  let documentApi: { findById: ReturnType<typeof vi.fn> };

  const document = (status: DocumentResponse['status']): DocumentResponse => ({
    id: 'doc-123',
    originalFilename: 'document.pdf',
    contentType: 'application/pdf',
    sizeBytes: 42,
    status,
    createdAt: '2026-09-19T20:00:00Z',
    updatedAt: '2026-09-19T20:00:00Z',
  });

  beforeEach(() => {
    vi.useFakeTimers();
    documentApi = { findById: vi.fn() };
    TestBed.configureTestingModule({
      providers: [
        DocumentStatusPollerService,
        { provide: DocumentApiService, useValue: documentApi },
      ],
    });
    service = TestBed.inject(DocumentStatusPollerService);
  });

  afterEach(() => vi.useRealTimers());

  it('queries immediately and every three seconds until a terminal state', () => {
    const responses = [document('PENDING'), document('PROCESSING'), document('COMPLETED')];
    documentApi.findById.mockImplementation(() => of(responses.shift()));
    const received: DocumentResponse[] = [];
    let completed = false;

    service.observe('doc-123').subscribe({
      next: (response) => received.push(response),
      complete: () => (completed = true),
    });

    expect(documentApi.findById).toHaveBeenCalledTimes(1);
    expect(received.at(-1)?.status).toBe('PENDING');

    vi.advanceTimersByTime(2_999);
    expect(documentApi.findById).toHaveBeenCalledTimes(1);

    vi.advanceTimersByTime(1);
    expect(documentApi.findById).toHaveBeenCalledTimes(2);
    expect(received.at(-1)?.status).toBe('PROCESSING');

    vi.advanceTimersByTime(3_000);
    expect(documentApi.findById).toHaveBeenCalledTimes(3);
    expect(received.at(-1)?.status).toBe('COMPLETED');
    expect(completed).toBe(true);

    vi.advanceTimersByTime(9_000);
    expect(documentApi.findById).toHaveBeenCalledTimes(3);
  });

  it('supports a manual refresh while processing', () => {
    documentApi.findById.mockReturnValue(of(document('PROCESSING')));
    const manualRefresh$ = new Subject<void>();

    service.observe('doc-123', manualRefresh$).subscribe();
    expect(documentApi.findById).toHaveBeenCalledTimes(1);

    manualRefresh$.next();

    expect(documentApi.findById).toHaveBeenCalledTimes(2);
  });

  it('stops with the 404 error and does not poll again', () => {
    const notFound = new HttpErrorResponse({ status: 404, statusText: 'Not Found' });
    documentApi.findById.mockReturnValue(
      throwError(() => notFound) as Observable<DocumentResponse>,
    );
    let receivedError: HttpErrorResponse | undefined;

    service.observe('missing-id').subscribe({
      error: (error: HttpErrorResponse) => (receivedError = error),
    });

    expect(receivedError?.status).toBe(404);
    vi.advanceTimersByTime(9_000);
    expect(documentApi.findById).toHaveBeenCalledTimes(1);
  });
});
