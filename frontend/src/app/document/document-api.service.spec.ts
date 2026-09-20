import { HttpEventType, provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { DocumentApiService } from './document-api.service';
import { DocumentResponse } from './document.models';

describe('DocumentApiService', () => {
  let service: DocumentApiService;
  let http: HttpTestingController;

  const documentResponse: DocumentResponse = {
    id: 'doc-123',
    originalFilename: 'document.pdf',
    contentType: 'application/pdf',
    sizeBytes: 42,
    status: 'PENDING',
    createdAt: '2026-09-19T20:00:00Z',
    updatedAt: '2026-09-19T20:00:00Z',
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), DocumentApiService],
    });
    service = TestBed.inject(DocumentApiService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    try {
      http.verify();
    } finally {
      TestBed.resetTestingModule();
    }
  });

  it('uploads only the file part and emits upload progress and the 201 response', () => {
    const file = new File(['content'], 'document.pdf', { type: 'application/pdf' });
    const events: unknown[] = [];

    service.upload(file).subscribe((event) => events.push(event));

    const request = http.expectOne('/api/documents');
    expect(request.request.method).toBe('POST');
    expect(request.request.reportUploadProgress).toBe(true);
    expect(request.request.headers.has('Content-Type')).toBe(false);

    const body = request.request.body as FormData;
    expect(Array.from(body.keys())).toEqual(['file']);
    expect(body.get('file')).toBeInstanceOf(File);

    request.event({ type: HttpEventType.UploadProgress, loaded: 50, total: 100 });
    request.flush(documentResponse, { status: 201, statusText: 'Created' });

    expect(events).toHaveLength(3);
    expect((events[0] as { type: HttpEventType }).type).toBe(HttpEventType.Sent);
    expect((events[1] as { type: HttpEventType }).type).toBe(HttpEventType.UploadProgress);
    expect((events[2] as { type: HttpEventType }).type).toBe(HttpEventType.Response);
  });

  it('queries a document by an encoded identifier and preserves the response contract', () => {
    let result: DocumentResponse | undefined;

    service.findById('doc/123').subscribe((response) => (result = response));

    const request = http.expectOne('/api/documents/doc%2F123');
    expect(request.request.method).toBe('GET');
    request.flush(documentResponse);

    expect(result).toEqual(documentResponse);
    expect(result?.createdAt).toBe(documentResponse.createdAt);
    expect(result?.updatedAt).toBe(documentResponse.updatedAt);
    expect(result?.status).toBe('PENDING');
  });

  it('propagates HTTP errors without issuing a second request', () => {
    let errorStatus: number | undefined;

    service.findById('doc-123').subscribe({
      error: (error: { status: number }) => (errorStatus = error.status),
    });

    const request = http.expectOne('/api/documents/doc-123');
    request.flush(
      { errorCode: 'DOCUMENT_CONTENT_TYPE_MISMATCH' },
      { status: 400, statusText: 'Bad Request' },
    );

    expect(errorStatus).toBe(400);
    http.verify();
  });
});
