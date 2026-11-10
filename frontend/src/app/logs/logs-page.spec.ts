import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { Observable, of, throwError } from 'rxjs';
import { LogsApiService } from './logs-api.service';
import { LogsPage } from './logs-page';
import { ApplicationLogPageResponse, ApplicationLogQuery } from './logs.models';
import { routes } from '../app.routes';

const emptyPage: ApplicationLogPageResponse = {
  items: [],
  page: 0,
  size: 50,
  totalElements: 0,
  totalPages: 0,
  first: true,
  last: true,
};

describe('LogsPage', () => {
  let fixture: ComponentFixture<LogsPage>;
  const logsApi = {
    find: vi.fn<(query: ApplicationLogQuery) => Observable<ApplicationLogPageResponse>>(() => of(emptyPage)),
  };

  beforeEach(async () => {
    logsApi.find.mockClear();
    await TestBed.configureTestingModule({
      imports: [LogsPage],
      providers: [provideRouter(routes), { provide: LogsApiService, useValue: logsApi }],
    }).compileComponents();

    fixture = TestBed.createComponent(LogsPage);
    fixture.detectChanges();
  });

  it('renders a read-only operational logs page shell', () => {
    const element = fixture.nativeElement as HTMLElement;

    expect(element.querySelector('h1')?.textContent).toContain('Logs operacionais');
    expect(element.querySelector('.logs-filters')).not.toBeNull();
    expect(element.querySelector('button[aria-label="Excluir"]')).toBeNull();
  });

  it('renders the safe fields returned by the paginated API', async () => {
    logsApi.find.mockReturnValueOnce(of({
      items: [{
        eventId: 'event-1',
        schemaVersion: 1,
        occurredAt: '2026-10-29T12:00:00Z',
        level: 'ERROR',
        service: 'document-api',
        component: 'storage-consumer',
        operation: 'document.processing',
        message: 'Falha ao confirmar o storage final',
        correlationId: 'correlation-1',
        documentId: 'document-1',
        details: { errorCode: 'STORAGE_UNAVAILABLE' },
      }],
      page: 0,
      size: 50,
      totalElements: 1,
      totalPages: 1,
      first: true,
      last: true,
    }));
    fixture.componentInstance.loadLogs();
    await fixture.whenStable();
    fixture.detectChanges();

    const element = fixture.nativeElement as HTMLElement;
    expect(element.querySelector('tbody')?.textContent).toContain('Falha ao confirmar o storage final');
    expect(element.querySelector('.level-error')?.textContent).toContain('Erro');
    expect(element.textContent).toContain('document-api');
    expect(element.textContent).toContain('document-1');
    expect(logsApi.find).toHaveBeenLastCalledWith({ page: 0, size: 50 });
  });

  it('combines all filters and resets them from the same read-only page', () => {
    const page = fixture.componentInstance;
    page.filterLevel = 'ERROR';
    page.filterService = ' document-api ';
    page.filterComponent = 'storage-consumer';
    page.filterOperation = 'document.processing';
    page.filterDocumentId = 'document-1';
    page.filterCorrelationId = 'correlation-1';
    page.filterOccurredFrom = '2026-10-29T10:00';
    page.filterOccurredTo = '2026-10-29T11:00';
    page.applyFilters();

    expect(logsApi.find).toHaveBeenLastCalledWith({
      page: 0,
      size: 50,
      level: 'ERROR',
      service: 'document-api',
      component: 'storage-consumer',
      operation: 'document.processing',
      documentId: 'document-1',
      correlationId: 'correlation-1',
      occurredFrom: new Date('2026-10-29T10:00').toISOString(),
      occurredTo: new Date('2026-10-29T11:00').toISOString(),
    });

    page.resetFilters();

    expect(page.filterLevel).toBe('');
    expect(page.filterService).toBe('');
    expect(page.filterOccurredFrom).toBe('');
    expect(logsApi.find).toHaveBeenLastCalledWith({
      page: 0,
      size: 50,
      level: undefined,
      service: undefined,
      component: undefined,
      operation: undefined,
      documentId: undefined,
      correlationId: undefined,
      occurredFrom: undefined,
      occurredTo: undefined,
    });
  });

  it('keeps pagination bounded and preserves active filters', () => {
    const page = fixture.componentInstance;
    page.filterService = 'document-api';
    logsApi.find
      .mockReturnValueOnce(of({ ...emptyPage, items: [{
      eventId: 'event-2',
      schemaVersion: 1,
      occurredAt: '2026-10-29T12:00:00Z',
      level: 'INFO',
      service: 'document-api',
      component: 'api',
      operation: 'document.accepted',
      message: 'Documento aceito',
      correlationId: null,
      documentId: null,
      details: null,
      }], totalElements: 51, totalPages: 2, last: false }))
      .mockReturnValueOnce(of({ ...emptyPage, page: 1, totalElements: 51, totalPages: 2, first: false, last: true }));
    page.loadLogs();
    fixture.detectChanges();

    expect(page.hasPreviousPage()).toBe(false);
    expect(page.hasNextPage()).toBe(true);
    page.goToPage(1);
    expect(logsApi.find).toHaveBeenLastCalledWith({
      page: 1,
      size: 50,
      level: undefined,
      service: 'document-api',
      component: undefined,
      operation: undefined,
      documentId: undefined,
      correlationId: undefined,
      occurredFrom: undefined,
      occurredTo: undefined,
    });
    expect(page.hasPreviousPage()).toBe(true);
    expect(page.hasNextPage()).toBe(false);
  });

  it('shows a safe error with a manual retry and clears stale rows', () => {
    const page = fixture.componentInstance;
    logsApi.find.mockReturnValueOnce(throwError(() => new Error('internal stack trace')));
    page.loadLogs();
    fixture.detectChanges();

    expect(page.logs).toEqual([]);
    expect(page.error?.canRetryManually).toBe(true);
    expect(fixture.nativeElement.textContent).toContain('Tentar novamente');
    expect(fixture.nativeElement.textContent).not.toContain('internal stack trace');
  });

  it('links only a valid document UUID to tracking and never creates a document', () => {
    const page = fixture.componentInstance;
    const documentId = '11111111-1111-4111-8111-111111111111';
    logsApi.find.mockReturnValueOnce(of({
      ...emptyPage,
      items: [{
        eventId: 'event-3',
        schemaVersion: 1,
        occurredAt: '2026-10-29T12:00:00Z',
        level: 'INFO',
        service: 'document-api',
        component: 'api',
        operation: 'document.accepted',
        message: 'Documento aceito',
        correlationId: null,
        documentId,
        details: null,
      }, {
        eventId: 'event-4',
        schemaVersion: 1,
        occurredAt: '2026-10-29T12:00:00Z',
        level: 'WARN',
        service: 'document-api',
        component: 'api',
        operation: 'document.warning',
        message: 'Documento indisponível',
        correlationId: null,
        documentId: 'not-a-uuid',
        details: null,
      }],
      totalElements: 2,
      totalPages: 1,
    }));
    page.loadLogs();
    fixture.detectChanges();

    const link = fixture.nativeElement.querySelector(`a[href="/documents/${documentId}"]`);
    expect(link).not.toBeNull();
    expect(fixture.nativeElement.querySelector('a[href="/documents/not-a-uuid"]')).toBeNull();
    expect(page.isNavigableDocumentId('not-a-uuid')).toBe(false);
  });
});

describe('logs route removal', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter(routes),
        { provide: LogsApiService, useValue: { find: vi.fn(() => of(emptyPage)) } },
      ],
    });
  });

  it('does not register the operational logs page route', () => {
    expect(routes.find((route) => route.path === 'logs')).toBeUndefined();
  });

  it('redirects legacy navigation to the Hub', async () => {
    const router = TestBed.inject(Router);

    await router.navigateByUrl('/logs');

    expect(router.url).toBe('/');
  });
});
