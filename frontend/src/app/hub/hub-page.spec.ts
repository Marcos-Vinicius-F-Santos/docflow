import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { HttpEventType, HttpResponse } from '@angular/common/http';
import { of, Subject, throwError } from 'rxjs';
import { DocumentApiService } from '../document/document-api.service';
import { DocumentResponse } from '../document/document.models';
import { HubPage } from './hub-page';
import { routes } from '../app.routes';

describe('HubPage', () => {
  let fixture: ComponentFixture<HubPage>;
  let documentApi: {
    list: ReturnType<typeof vi.fn>;
    upload: ReturnType<typeof vi.fn>;
    download: ReturnType<typeof vi.fn>;
    retry: ReturnType<typeof vi.fn>;
    delete: ReturnType<typeof vi.fn>;
  };

  const documentResponse = (
    id: string,
    status: DocumentResponse['status'],
    updatedAt: string,
    createdAt = '2026-09-01T10:00:00Z',
  ): DocumentResponse => ({
    id,
    originalFilename: `${id}.pdf`,
    contentType: 'application/pdf',
    sizeBytes: 42,
    status,
    createdAt,
    updatedAt,
    falhas: status === 'FAILED' ? 1 : 0,
    isExcluded: false,
    exclusionScheduledAt: null,
  });

  beforeEach(async () => {
    documentApi = {
      list: vi.fn(() => of([])),
      upload: vi.fn(),
      download: vi.fn(),
      retry: vi.fn(),
      delete: vi.fn(),
    };
    await TestBed.configureTestingModule({
      imports: [HubPage],
      providers: [provideRouter(routes), { provide: DocumentApiService, useValue: documentApi }],
    }).compileComponents();

    fixture = TestBed.createComponent(HubPage);
    fixture.detectChanges();
  });

  it('renders the DocFlow hub heading and existing document navigation', () => {
    const element = fixture.nativeElement as HTMLElement;

    expect(element.querySelector('h1')?.textContent).toContain('Hub de documentos');
    expect(element.querySelector('.eyebrow')?.textContent).toContain('DocFlow');
    expect(element.querySelector('a[href="/documents/new"]')).not.toBeNull();
    expect(element.querySelector('a[href="/documents"]')).not.toBeNull();
  });

  it('shows the five most recently updated documents and every failed document', async () => {
    documentApi.list.mockReturnValue(
      of([
        documentResponse('oldest', 'COMPLETED', '2026-09-01T10:00:00Z'),
        documentResponse('recent-1', 'COMPLETED', '2026-09-06T10:00:00Z'),
        documentResponse('recent-2', 'PROCESSING', '2026-09-05T10:00:00Z'),
        documentResponse('recent-3', 'COMPLETED', '2026-09-04T10:00:00Z'),
        documentResponse('recent-4', 'PENDING', '2026-09-03T10:00:00Z'),
        documentResponse('recent-5', 'COMPLETED', '2026-09-02T10:00:00Z'),
        documentResponse('failed-not-recent', 'FAILED', '2026-08-01T10:00:00Z'),
      ]),
    );

    fixture = TestBed.createComponent(HubPage);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    const sections = fixture.nativeElement.querySelectorAll('aside section');
    expect(sections[0].textContent).toContain('recent-1.pdf');
    expect(sections[0].textContent).toContain('recent-5.pdf');
    expect(sections[0].textContent).not.toContain('oldest.pdf');
    expect(sections[1].textContent).toContain('failed-not-recent.pdf');
    expect(fixture.nativeElement.textContent).not.toContain('downloads');
  });

  it('uses createdAt when updatedAt is unavailable for recent ordering', () => {
    const documents = [
      documentResponse('created-newer', 'COMPLETED', '', '2026-09-02T10:00:00Z'),
      documentResponse('created-older', 'COMPLETED', '', '2026-09-01T10:00:00Z'),
    ];
    documentApi.list.mockReturnValue(of(documents));

    fixture = TestBed.createComponent(HubPage);
    const page = fixture.componentInstance;

    expect(page.recentDocuments().map((document) => document.id)).toEqual([
      'created-newer',
      'created-older',
    ]);
  });

  it('validates and uploads a selected file without leaving the Hub', async () => {
    const events$ = new Subject<HttpResponse<DocumentResponse> | { type: HttpEventType }>();
    documentApi.upload.mockReturnValue(events$.asObservable());
    fixture = TestBed.createComponent(HubPage);
    const page = fixture.componentInstance;
    const file = new File(['content'], 'document.pdf', { type: 'application/pdf' });

    page.selectFile(file);
    page.submit();
    page.submit();

    expect(documentApi.upload).toHaveBeenCalledTimes(1);
    expect(page.uploadInProgress).toBe(true);

    events$.next({ type: HttpEventType.UploadProgress });
    events$.next(new HttpResponse({
      body: documentResponse('uploaded', 'PENDING', '2026-09-07T10:00:00Z'),
      status: 201,
    }));
    await fixture.whenStable();

    expect(page.uploadInProgress).toBe(false);
    expect(page.selectedFile).toBeNull();
    expect(page.documents[0].id).toBe('uploaded');
  });

  it('shows upload validation and operational errors without retrying automatically', () => {
    fixture = TestBed.createComponent(HubPage);
    const page = fixture.componentInstance;

    page.selectFile(new File([''], 'empty.pdf', { type: 'application/pdf' }));
    page.submit();
    expect(page.validationError).toBe('O arquivo está vazio.');
    expect(documentApi.upload).not.toHaveBeenCalled();

    documentApi.upload.mockReturnValue(
      throwError(() => new Error('offline')),
    );
    page.selectFile(new File(['content'], 'document.pdf', { type: 'application/pdf' }));
    page.submit();

    expect(page.uploadError?.canRetryManually).toBe(true);
    expect(page.uploadInProgress).toBe(false);
  });

  it('loads all documents once and presents the central list', async () => {
    documentApi.list.mockReturnValue(
      of([
        documentResponse('pending', 'PENDING', '2026-09-01T10:00:00Z'),
        documentResponse('processing', 'PROCESSING', '2026-09-02T10:00:00Z'),
        documentResponse('completed', 'COMPLETED', '2026-09-03T10:00:00Z'),
        documentResponse('failed', 'FAILED', '2026-09-04T10:00:00Z'),
      ]),
    );
    documentApi.list.mockClear();

    fixture = TestBed.createComponent(HubPage);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(documentApi.list).toHaveBeenCalledTimes(1);
    expect(fixture.nativeElement.querySelectorAll('.document-list li')).toHaveLength(4);
    expect(fixture.nativeElement.textContent).toContain('pending.pdf');
    expect(fixture.nativeElement.textContent).toContain('FAILED');
  });

  it('shows the empty central state without removing the upload dropbox', async () => {
    documentApi.list.mockReturnValue(of([]));
    documentApi.list.mockClear();

    fixture = TestBed.createComponent(HubPage);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Nenhum documento encontrado.');
    expect(fixture.nativeElement.querySelector('.document-list')).toBeNull();
    expect(fixture.nativeElement.querySelector('.drop-zone')).not.toBeNull();
  });

  it('clears stale documents on list failure and offers a manual retry', async () => {
    documentApi.list
      .mockReturnValueOnce(of([documentResponse('stale', 'COMPLETED', '2026-09-01T10:00:00Z')]))
      .mockReturnValueOnce(throwError(() => new Error('offline')))
      .mockReturnValueOnce(of([documentResponse('fresh', 'PENDING', '2026-09-02T10:00:00Z')]));
    documentApi.list.mockClear();

    fixture = TestBed.createComponent(HubPage);
    const page = fixture.componentInstance;
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('stale.pdf');

    page.loadDocuments();
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).not.toContain('stale.pdf');
    expect(fixture.nativeElement.textContent).toContain('Não foi possível exibir itens listados');

    page.loadDocuments();
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('fresh.pdf');
    expect(documentApi.list).toHaveBeenCalledTimes(3);
  });

  it('renders the approved visual indicator for every document state', async () => {
    documentApi.list.mockReturnValue(
      of([
        documentResponse('completed', 'COMPLETED', '2026-09-01T10:00:00Z'),
        documentResponse('pending', 'PENDING', '2026-09-02T10:00:00Z'),
        documentResponse('processing', 'PROCESSING', '2026-09-03T10:00:00Z'),
        documentResponse('failed', 'FAILED', '2026-09-04T10:00:00Z'),
      ]),
    );
    documentApi.list.mockClear();

    fixture = TestBed.createComponent(HubPage);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('.state-completed')).not.toBeNull();
    expect(fixture.nativeElement.querySelector('.state-pending')).not.toBeNull();
    expect(fixture.nativeElement.querySelector('.state-processing')).not.toBeNull();
    expect(fixture.nativeElement.querySelector('.state-failed')).not.toBeNull();
  });

  it('offers download only for completed documents and retries eligible failures', async () => {
    const completed = documentResponse('completed', 'COMPLETED', '2026-09-01T10:00:00Z');
    const retryable = documentResponse('retryable', 'FAILED', '2026-09-02T10:00:00Z');
    const exhausted = { ...documentResponse('exhausted', 'FAILED', '2026-09-03T10:00:00Z'), falhas: 3, isExcluded: true };
    documentApi.list.mockReturnValue(of([completed, retryable, exhausted]));
    documentApi.retry.mockReturnValue(
      of(new HttpResponse({ body: { ...retryable, status: 'PROCESSING' }, status: 202 })),
    );
    documentApi.list.mockClear();

    fixture = TestBed.createComponent(HubPage);
    const page = fixture.componentInstance;
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Baixar');
    expect(fixture.nativeElement.textContent).toContain('Tentar novamente');
    expect(fixture.nativeElement.querySelectorAll('.document-actions button')).toHaveLength(5);

    page.retryDocument(retryable);
    expect(documentApi.retry).toHaveBeenCalledWith('retryable');
    expect(page.documents[0].status).toBe('PROCESSING');
    page.retryDocument(exhausted);
    expect(documentApi.retry).toHaveBeenCalledTimes(1);
  });

  it('keeps manual deletion behind confirmation and removes only after success', async () => {
    const document = documentResponse('to-delete', 'FAILED', '2026-09-01T10:00:00Z');
    documentApi.list.mockReturnValue(of([document]));
    documentApi.delete.mockReturnValue(of(void 0));
    documentApi.list.mockClear();

    fixture = TestBed.createComponent(HubPage);
    const page = fixture.componentInstance;
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    page.requestDelete(document);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[role="dialog"]')).not.toBeNull();
    expect(documentApi.delete).not.toHaveBeenCalled();

    page.cancelDelete();
    fixture.detectChanges();
    expect(documentApi.delete).not.toHaveBeenCalled();
    expect(page.documents).toHaveLength(1);

    page.requestDelete(document);
    fixture.detectChanges();
    page.confirmDelete();
    expect(documentApi.delete).toHaveBeenCalledWith('to-delete');
    expect(page.documents).toHaveLength(0);
  });

  it('keeps actions keyboard reachable and blocks concurrent operations', () => {
    fixture = TestBed.createComponent(HubPage);
    const page = fixture.componentInstance;
    const dropZone = fixture.nativeElement.querySelector('.drop-zone') as HTMLElement;

    expect(dropZone.getAttribute('role')).toBe('button');
    expect(dropZone.getAttribute('tabindex')).toBe('0');
    expect(dropZone.querySelector('input')).not.toBeNull();

    page.retryingDocumentId = 'retrying';
    const completed = documentResponse('completed', 'COMPLETED', '2026-09-01T10:00:00Z');
    page.downloadDocument(completed);
    page.requestDelete(completed);

    expect(documentApi.download).not.toHaveBeenCalled();
    expect(documentApi.delete).not.toHaveBeenCalled();
    expect(page.pendingDeletion).toBeNull();
  });
});

describe('application routes', () => {
  it('loads the Hub at the root while preserving document routes', () => {
    expect(routes[0]).toMatchObject({ path: '', pathMatch: 'full' });
    expect(routes.some((route) => route.path === 'documents')).toBe(true);
    expect(routes.some((route) => route.path === 'documents/new')).toBe(true);
    expect(routes.some((route) => route.path === 'documents/:documentId')).toBe(true);
  });

  it('navigates to the Hub at the root', async () => {
    const router = TestBed.inject(Router);

    await router.navigateByUrl('/');

    expect(router.url).toBe('/');
  });
});
