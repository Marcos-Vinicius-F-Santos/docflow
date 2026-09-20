import { HttpErrorResponse, HttpEvent, HttpEventType, HttpResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, convertToParamMap } from '@angular/router';
import { Observable, Subject, of, throwError } from 'rxjs';
import { DocumentDetailPage } from './pages/document-detail-page';
import { DocumentUploadPage } from './pages/document-upload-page';
import { DocumentApiService } from './document-api.service';
import { DocumentResponse } from './document.models';
import { DocumentStatusPollerService } from './document-status-poller.service';

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

function submitButton(fixture: ComponentFixture<DocumentUploadPage>): HTMLButtonElement {
  const buttons = Array.from(
    fixture.nativeElement.querySelectorAll('button'),
  ) as HTMLButtonElement[];
  return buttons[buttons.length - 1];
}
