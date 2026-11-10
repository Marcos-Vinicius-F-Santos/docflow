import { ComponentFixture, TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { provideRouter, Router } from '@angular/router';
import { HttpEventType, HttpResponse } from '@angular/common/http';
import { of, Subject, throwError } from 'rxjs';
import { DocumentApiService } from '../document/document-api.service';
import { DocumentResponse } from '../document/document.models';
import { NeonAuthService } from '../auth/neon-auth.service';
import { DocumentNotificationEvent, NotificationConnectionState } from '../notifications/notification-event.models';
import { NotificationSessionState } from '../notifications/notification-session-state.service';
import { notificationEventFixtures } from '../notifications/testing/notification-event.fixtures';
import { HubPage } from './hub-page';
import { routes } from '../app.routes';

describe('HubPage', () => {
  let fixture: ComponentFixture<HubPage>;
  let documentApi: {
    list: ReturnType<typeof vi.fn>;
    upload: ReturnType<typeof vi.fn>;
  };
  let notificationService: {
    events$: Subject<DocumentNotificationEvent>;
    connectionState$: Subject<NotificationConnectionState>;
    start: ReturnType<typeof vi.fn>;
    stop: ReturnType<typeof vi.fn>;
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
    visibility: 'PRIVATE',
    ownedByCurrentUser: true,
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
    };
    notificationService = {
      events$: new Subject<DocumentNotificationEvent>(),
      connectionState$: new Subject<NotificationConnectionState>(),
      start: vi.fn(),
      stop: vi.fn(),
    };
    await TestBed.configureTestingModule({
      imports: [HubPage],
      providers: [
        provideRouter(routes),
        { provide: DocumentApiService, useValue: documentApi },
        {
          provide: NeonAuthService,
          useValue: { session: signal({ user: { email: 'user@example.com' } }) },
        },
      ],
    }).overrideProvider(NotificationSessionState, { useValue: notificationService }).compileComponents();

    fixture = TestBed.createComponent(HubPage);
    fixture.detectChanges();
  });

  it('renders the DocFlow hub heading without separate upload or list navigation', () => {
    const element = fixture.nativeElement as HTMLElement;

    expect(element.querySelector('h1')?.textContent).toContain('Hub de documentos');
    expect(element.querySelector('.eyebrow')?.textContent).toContain('DocFlow');
    expect(element.querySelector('a[href="/documents/new"]')).toBeNull();
    expect(element.querySelector('a[href="/documents"]')).toBeNull();
    expect(element.querySelector('.hub-layout')).not.toBeNull();
  });

  it('shows five recent documents and only five recent failed documents in the sidebar', async () => {
    documentApi.list.mockReturnValue(
      of([
        documentResponse('oldest', 'COMPLETED', '2026-09-01T10:00:00Z'),
        documentResponse('recent-1', 'COMPLETED', '2026-09-06T10:00:00Z'),
        documentResponse('recent-2', 'PROCESSING', '2026-09-05T10:00:00Z'),
        documentResponse('recent-3', 'COMPLETED', '2026-09-04T10:00:00Z'),
        documentResponse('recent-4', 'PENDING', '2026-09-03T10:00:00Z'),
        documentResponse('recent-5', 'COMPLETED', '2026-09-02T10:00:00Z'),
        documentResponse('failed-1', 'FAILED', '2026-09-07T10:00:00Z'),
        documentResponse('failed-2', 'FAILED', '2026-09-06T10:00:00Z'),
        documentResponse('failed-3', 'FAILED', '2026-09-05T10:00:00Z'),
        documentResponse('failed-4', 'FAILED', '2026-09-04T10:00:00Z'),
        documentResponse('failed-5', 'FAILED', '2026-09-03T10:00:00Z'),
        documentResponse('failed-not-recent', 'FAILED', '2026-08-01T10:00:00Z'),
      ]),
    );

    fixture = TestBed.createComponent(HubPage);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    const sections = fixture.nativeElement.querySelectorAll('aside section');
    expect(sections[0].textContent).toContain('failed-1.pdf');
    expect(sections[0].textContent).toContain('recent-1.pdf');
    expect(sections[0].textContent).not.toContain('oldest.pdf');
    expect(sections[0].querySelectorAll('li')).toHaveLength(5);
    expect(sections[1].textContent).toContain('failed-1.pdf');
    expect(sections[1].textContent).toContain('failed-5.pdf');
    expect(sections[1].textContent).not.toContain('failed-not-recent.pdf');
    expect(sections[1].querySelectorAll('li')).toHaveLength(5);
    expect(fixture.nativeElement.querySelector('.document-card[title="failed-1.pdf"]')).not.toBeNull();
    expect(fixture.nativeElement.textContent).not.toContain('downloads');
  });

  it('navigates document cards to tracking and keeps the full name in the tooltip', async () => {
    const document = documentResponse('document-with-a-long-name', 'COMPLETED', '2026-09-01T10:00:00Z');
    document.originalFilename = 'document-with-a-long-name-that-needs-a-tooltip.pdf';
    documentApi.list.mockReturnValue(of([document]));

    fixture = TestBed.createComponent(HubPage);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    const card = fixture.nativeElement.querySelector('.document-card') as HTMLAnchorElement;
    expect(card.title).toBe(document.originalFilename);
    expect(card.href).toContain('/documents/document-with-a-long-name');
    expect(card.getAttribute('aria-label')).toContain(document.originalFilename);
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
    expect(fixture.nativeElement.querySelector('a[href="/documents/failed"]')).not.toBeNull();
  });

  it('refreshes the REST list after the notification connection is recovered', async () => {
    documentApi.list.mockReturnValueOnce(
      of([documentResponse('fresh', 'COMPLETED', '2026-09-02T10:00:00Z')]),
    );
    expect(documentApi.list).toHaveBeenCalledTimes(1);
    expect(notificationService.start).toHaveBeenCalledTimes(1);

    notificationService.connectionState$.next('CONNECTED');
    notificationService.connectionState$.next('DISCONNECTED');
    notificationService.connectionState$.next('RECONNECTING');
    notificationService.connectionState$.next('CONNECTED');
    await fixture.whenStable();
    fixture.detectChanges();

    expect(documentApi.list).toHaveBeenCalledTimes(2);
    expect(fixture.nativeElement.textContent).toContain('fresh.pdf');
  });

  it('shows each valid event in a temporary toast and persistent session banner', () => {
    const event = notificationEventFixtures[1] as DocumentNotificationEvent;

    notificationService.events$.next(event);
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('.notification-toast')?.textContent).toContain(
      'Documento concluído',
    );
    expect(fixture.nativeElement.querySelector('.notification-banner')?.textContent).toContain(
      'Documento ' + event.documentId,
    );
    expect(fixture.nativeElement.querySelector('[aria-live="assertive"]')).not.toBeNull();
  });

  it('updates an existing document and links its notification to tracking', () => {
    const existing = documentResponse('existing', 'PROCESSING', '2026-09-01T10:00:00Z');
    documentApi.list.mockReturnValue(of([existing]));
    const page = fixture.componentInstance;
    page.loadDocuments();
    fixture.detectChanges();

    notificationService.events$.next({
      ...notificationEventFixtures[1],
      documentId: existing.id,
    } as DocumentNotificationEvent);
    fixture.detectChanges();

    expect(page.documents).toHaveLength(1);
    expect(page.documents[0].status).toBe('COMPLETED');
    expect(fixture.nativeElement.querySelector('.notification-banner a')?.getAttribute('href')).toContain(
      '/documents/existing',
    );
  });

  it('does not recreate or link a document that is already absent', () => {
    const missingEvent = {
      ...notificationEventFixtures[4],
      documentId: 'already-removed',
    } as DocumentNotificationEvent;

    notificationService.events$.next(missingEvent);
    fixture.detectChanges();

    expect(fixture.componentInstance.documents).toHaveLength(0);
    expect(fixture.nativeElement.querySelector('.notification-banner a')).toBeNull();
    expect(fixture.nativeElement.querySelector('.notification-document-unavailable')).not.toBeNull();
  });

  it('announces connection loss without blocking upload controls', () => {
    notificationService.connectionState$.next('RECONNECTING');
    fixture.detectChanges();

    const status = fixture.nativeElement.querySelector('.connection-status') as HTMLElement;
    const fileInput = fixture.nativeElement.querySelector('#hub-document-file-input') as HTMLInputElement;
    expect(status.textContent).toContain('Reconectando notificações');
    expect(status.getAttribute('aria-live')).toBe('polite');
    expect(fileInput.disabled).toBe(false);

    notificationService.connectionState$.next('CONNECTED');
    fixture.detectChanges();
    expect(status.textContent).toContain('Notificações conectadas');
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

  it('keeps the upload dropbox keyboard reachable and blocks concurrent uploads', () => {
    fixture = TestBed.createComponent(HubPage);
    fixture.detectChanges();
    const page = fixture.componentInstance;
    const dropZone = fixture.nativeElement.querySelector('.drop-zone') as HTMLElement;

    expect(dropZone.getAttribute('role')).toBe('button');
    expect(dropZone.getAttribute('tabindex')).toBe('0');
    expect(dropZone.querySelector('input')).not.toBeNull();

    page.uploadInProgress = true;
    page.selectFile(new File(['content'], 'document.pdf', { type: 'application/pdf' }));

    expect(page.selectedFile).toBeNull();
    expect(page.hasActiveAction()).toBe(true);
  });
});

describe('application routes', () => {
  it('loads the Hub at the root while preserving document routes', () => {
    expect(routes[0]).toMatchObject({ path: '', pathMatch: 'full' });
    expect(routes.some((route) => route.path === 'documents')).toBe(false);
    expect(routes.some((route) => route.path === 'documents/new')).toBe(false);
    expect(routes.some((route) => route.path === 'documents/:documentId')).toBe(true);
    expect(routes.find((route) => route.path === '**')).toMatchObject({ redirectTo: '' });
  });

  it('navigates to the Hub at the root', async () => {
    const router = TestBed.inject(Router);

    await router.navigateByUrl('/');

    expect(router.url).toBe('/');
  });
});
