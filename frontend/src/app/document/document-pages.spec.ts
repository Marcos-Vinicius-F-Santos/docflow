import { HttpErrorResponse, HttpEvent, HttpEventType, HttpResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, convertToParamMap } from '@angular/router';
import { Observable, Subject, of, throwError } from 'rxjs';
import { DocumentDetailPage } from './pages/document-detail-page';
import { DocumentListPage } from './pages/document-list-page';
import { DocumentUploadPage } from './pages/document-upload-page';
import { DocumentApiService } from './document-api.service';
import { DocumentResponse } from './document.models';
import { DocumentStatusPollerService } from './document-status-poller.service';
import { documentRoutes } from './document.routes';

describe('DocumentUploadPage', () => {
  let fixture: ComponentFixture<DocumentUploadPage>;
  let page: DocumentUploadPage;
  let documentApi: { upload: ReturnType<typeof vi.fn> };
  let router: { navigate: ReturnType<typeof vi.fn> };

  const response: DocumentResponse = {
    id: 'doc-123',
    originalFilename: 'document.pdf',
    contentType: 'application/pdf',
    sizeBytes: 42,
    status: 'PENDING',
    createdAt: '2026-09-19T20:00:00Z',
    updatedAt: '2026-09-19T20:00:00Z',
  };

  beforeEach(async () => {
    documentApi = { upload: vi.fn() };
    router = { navigate: vi.fn(() => Promise.resolve(true)) };

    await TestBed.configureTestingModule({
      imports: [DocumentUploadPage],
      providers: [
        { provide: DocumentApiService, useValue: documentApi },
        { provide: Router, useValue: router },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(DocumentUploadPage);
    page = fixture.componentInstance;
    fixture.detectChanges();
  });

  afterEach(() => TestBed.resetTestingModule());

  it('shows the selected file metadata and blocks an invalid file', () => {
    page.selectFile(new File([''], 'empty.pdf', { type: 'application/pdf' }));
    expect(page.selectedFile?.name).toBe('empty.pdf');
    expect(page.validationError).toBe('O arquivo está vazio.');
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('empty.pdf');
    expect(fixture.nativeElement.textContent).toContain('O arquivo está vazio.');
    expect(submitButton(fixture).disabled).toBe(true);
    expect(documentApi.upload).not.toHaveBeenCalled();
  });

  it('shows upload progress, prevents duplicate submits and navigates after 201', async () => {
    const events$ = new Subject<HttpEvent<DocumentResponse>>();
    documentApi.upload.mockReturnValue(events$.asObservable());
    page.selectFile(new File(['content'], 'document.pdf', { type: 'application/pdf' }));

    page.submit();
    page.submit();
    expect(documentApi.upload).toHaveBeenCalledTimes(1);
    expect(page.uploadInProgress).toBe(true);

    events$.next({ type: HttpEventType.UploadProgress, loaded: 50, total: 100 });
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('50%');
    expect(submitButton(fixture).disabled).toBe(true);

    events$.next(new HttpResponse({ body: response, status: 201, statusText: 'Created' }));
    await fixture.whenStable();

    expect(router.navigate).toHaveBeenCalledWith(['/documents', 'doc-123']);
    expect(page.uploadInProgress).toBe(false);
  });

  it('presents a ProblemDetail validation error from the backend', () => {
    documentApi.upload.mockReturnValue(
      throwError(
        () =>
          new HttpErrorResponse({
            status: 400,
            error: {
              errorCode: 'DOCUMENT_CONTENT_EMPTY',
              invalidField: 'file',
            },
          }),
      ),
    );
    page.selectFile(new File(['content'], 'document.pdf', { type: 'application/pdf' }));

    page.submit();
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('O arquivo enviado está vazio.');
    expect(fixture.nativeElement.textContent).not.toContain('Você pode tentar enviar novamente');
    expect(submitButton(fixture).textContent).toContain('Tentar enviar novamente');
  });
});

describe('DocumentDetailPage', () => {
  let fixture: ComponentFixture<DocumentDetailPage>;
  let router: { navigate: ReturnType<typeof vi.fn> };
  let poller: { observe: ReturnType<typeof vi.fn> };

  const response = (status: DocumentResponse['status']): DocumentResponse => ({
    id: 'doc-123',
    originalFilename: 'document.pdf',
    contentType: 'application/pdf',
    sizeBytes: 42,
    status,
    createdAt: '2026-09-19T20:00:00Z',
    updatedAt: '2026-09-19T20:00:00Z',
  });

  async function createDetail(observed: Observable<DocumentResponse>): Promise<void> {
    router = { navigate: vi.fn(() => Promise.resolve(true)) };
    poller = { observe: vi.fn(() => observed) };

    await TestBed.configureTestingModule({
      imports: [DocumentDetailPage],
      providers: [
        {
          provide: ActivatedRoute,
          useValue: { paramMap: of(convertToParamMap({ documentId: 'doc-123' })) },
        },
        { provide: DocumentStatusPollerService, useValue: poller },
        { provide: Router, useValue: router },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(DocumentDetailPage);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
  }

  afterEach(() => TestBed.resetTestingModule());

  it('loads a direct document route and presents completed metadata and status', async () => {
    await createDetail(of(response('COMPLETED')));

    const text = fixture.nativeElement.textContent;
    expect(text).toContain('doc-123');
    expect(text).toContain('document.pdf');
    expect(text).toContain('COMPLETED');
    expect(text).toContain('armazenamento final foi confirmado');
    expect(poller.observe).toHaveBeenCalledWith('doc-123', expect.anything());
  });

  it('shows not found without presenting a local pending state', async () => {
    const error = new HttpErrorResponse({ status: 404, statusText: 'Not Found' });
    await createDetail(throwError(() => error));

    const text = fixture.nativeElement.textContent;
    expect(text).toContain('Documento não encontrado.');
    expect(text).toContain('doc-123');
    expect(text).not.toContain('PENDING');
  });

  it('presents the FAILED state as terminal and explains the next action', async () => {
    await createDetail(of(response('FAILED')));

    const text = fixture.nativeElement.textContent;
    expect(text).toContain('FAILED');
    expect(text).toContain('O processamento não foi concluído');
    expect(text).toContain('não oferece reprocessamento');
  });

  it('presents an operational error with a manual retry action', async () => {
    const error = new HttpErrorResponse({ status: 503, statusText: 'Service Unavailable' });
    await createDetail(throwError(() => error));

    const text = fixture.nativeElement.textContent;
    expect(text).toContain('Não foi possível concluir a operação.');
    expect(text).toContain('Você pode tentar a consulta novamente.');
    expect(text).toContain('Tentar consulta novamente');
  });
});

describe('DocumentListPage', () => {
  let fixture: ComponentFixture<DocumentListPage>;
  let page: DocumentListPage;
  let documentApi: {
    list: ReturnType<typeof vi.fn>;
    download: ReturnType<typeof vi.fn>;
    delete: ReturnType<typeof vi.fn>;
  };
  let router: { navigate: ReturnType<typeof vi.fn> };

  const response = (id: string, status: DocumentResponse['status']): DocumentResponse => ({
    id,
    originalFilename: `${id}.pdf`,
    contentType: 'application/pdf',
    sizeBytes: 2048,
    status,
    createdAt: '2026-09-19T20:00:00Z',
    updatedAt: '2026-09-19T20:00:00Z',
  });

  async function createPage(documents: DocumentResponse[] = [response('doc-1', 'PENDING')]): Promise<void> {
    documentApi = {
      list: vi.fn(() => of(documents)),
      download: vi.fn(),
      delete: vi.fn(),
    };
    router = { navigate: vi.fn(() => Promise.resolve(true)) };

    await TestBed.configureTestingModule({
      imports: [DocumentListPage],
      providers: [
        { provide: DocumentApiService, useValue: documentApi },
        { provide: Router, useValue: router },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(DocumentListPage);
    page = fixture.componentInstance;
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
  }

  afterEach(() => TestBed.resetTestingModule());

  it('renders all seven fields, all statuses and selection actions', async () => {
    await createPage([
      response('pending', 'PENDING'),
      response('processing', 'PROCESSING'),
      response('completed', 'COMPLETED'),
      response('failed', 'FAILED'),
    ]);

    const text = fixture.nativeElement.textContent;
    expect(text).toContain('pending.pdf');
    expect(text).toContain('application/pdf');
    expect(text).toContain('2.0 KiB');
    expect(text).toContain('19 de set. de 2026');
    expect(text).toContain('PENDING');
    expect(text).toContain('PROCESSING');
    expect(text).toContain('COMPLETED');
    expect(text).toContain('FAILED');
    expect(fixture.nativeElement.querySelectorAll('tbody tr')).toHaveLength(4);
    expect(fixture.nativeElement.querySelectorAll('button')).toHaveLength(13);
  });

  it('shows the approved empty-list message', async () => {
    await createPage([]);

    expect(fixture.nativeElement.textContent).toContain('Nenhum documento encontrado');
    expect(fixture.nativeElement.querySelector('table')).toBeNull();
  });

  it('shows the approved list error and allows a retry', async () => {
    documentApi = {
      list: vi.fn()
        .mockReturnValueOnce(throwError(() => new HttpErrorResponse({ status: 503 })))
        .mockReturnValueOnce(of([response('doc-1', 'COMPLETED')])),
      download: vi.fn(),
      delete: vi.fn(),
    };
    router = { navigate: vi.fn(() => Promise.resolve(true)) };

    await TestBed.configureTestingModule({
      imports: [DocumentListPage],
      providers: [
        { provide: DocumentApiService, useValue: documentApi },
        { provide: Router, useValue: router },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(DocumentListPage);
    page = fixture.componentInstance;
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Não foi possível exibir itens listados');
    page.loadDocuments();
    fixture.detectChanges();

    expect(documentApi.list).toHaveBeenCalledTimes(2);
    expect(fixture.nativeElement.textContent).toContain('doc-1.pdf');
  });

  it('starts a browser download and revokes the temporary object URL', async () => {
    await createPage([response('doc-1', 'COMPLETED')]);
    const blob = new Blob(['document'], { type: 'application/pdf' });
    documentApi.download.mockReturnValue(
      of(new HttpResponse({
        body: blob,
        status: 200,
        headers: new (await import('@angular/common/http')).HttpHeaders({
          'Content-Disposition': 'attachment; filename="server-name.pdf"',
        }),
      })),
    );
    const createObjectURL = vi.fn(() => 'blob:document');
    const revokeObjectURL = vi.fn();
    vi.stubGlobal('URL', { createObjectURL, revokeObjectURL });
    const click = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => undefined);

    page.downloadDocument(page.documents[0]);

    expect(documentApi.download).toHaveBeenCalledWith('doc-1');
    expect(createObjectURL).toHaveBeenCalledWith(blob);
    expect(click).toHaveBeenCalled();
    expect(revokeObjectURL).toHaveBeenCalledWith('blob:document');

    click.mockRestore();
    vi.unstubAllGlobals();
  });

  it.each([404, 409, 503, 500])('keeps the row and shows the download error for HTTP %s', async (status) => {
    await createPage([response('doc-1', 'COMPLETED')]);
    documentApi.download.mockReturnValue(
      throwError(() => new HttpErrorResponse({ status, statusText: 'Error' })),
    );

    page.downloadDocument(page.documents[0]);
    fixture.detectChanges();

    expect(page.documents).toHaveLength(1);
    expect(fixture.nativeElement.textContent).toContain(
      status === 404 ? 'Documento não encontrado.' : 'Não foi possível concluir a operação.',
    );
  });

  it('requires confirmation, does not delete when cancelled and removes after success', async () => {
    await createPage([response('doc-1', 'FAILED')]);
    documentApi.delete.mockReturnValue(of(void 0));

    page.requestDelete(page.documents[0]);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[role="dialog"]')).not.toBeNull();
    expect(document.activeElement?.textContent).toContain('Excluir definitivamente');

    page.cancelDelete();
    expect(documentApi.delete).not.toHaveBeenCalled();
    expect(page.documents).toHaveLength(1);

    page.requestDelete(page.documents[0]);
    page.confirmDelete();
    expect(documentApi.delete).toHaveBeenCalledWith('doc-1');
    expect(page.documents).toHaveLength(0);
  });

  it('keeps the document visible when deletion fails', async () => {
    await createPage([response('doc-1', 'COMPLETED')]);
    documentApi.delete.mockReturnValue(
      throwError(() => new HttpErrorResponse({ status: 503, statusText: 'Unavailable' })),
    );

    page.requestDelete(page.documents[0]);
    page.confirmDelete();
    fixture.detectChanges();

    expect(page.documents).toHaveLength(1);
    expect(fixture.nativeElement.textContent).toContain('Não foi possível concluir a operação.');
  });
});

describe('document routes', () => {
  it('keeps the list route ahead of the existing upload and detail routes', () => {
    expect(documentRoutes.map((route) => route.path)).toEqual([
      'documents',
      'documents/new',
      'documents/:documentId',
    ]);
  });
});

function submitButton(fixture: ComponentFixture<DocumentUploadPage>): HTMLButtonElement {
  const buttons = Array.from(
    fixture.nativeElement.querySelectorAll('button'),
  ) as HTMLButtonElement[];
  return buttons[buttons.length - 1];
}
