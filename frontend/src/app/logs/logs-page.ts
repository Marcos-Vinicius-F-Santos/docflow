import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import {
  ChangeDetectionStrategy,
  ChangeDetectorRef,
  Component,
  DestroyRef,
  inject,
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { LogsApiService } from './logs-api.service';
import { ApplicationLogApiError, mapApplicationLogError } from './logs-error.mapper';
import { ApplicationLogRecord } from './logs.models';

@Component({
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule, RouterLink],
  selector: 'app-logs-page',
  styleUrl: './logs-page.scss',
  templateUrl: './logs-page.html',
})
export class LogsPage {
  private readonly changeDetector = inject(ChangeDetectorRef);
  private readonly destroyRef = inject(DestroyRef);
  private readonly logsApi = inject(LogsApiService);

  logs: ApplicationLogRecord[] = [];
  isLoading = true;
  error: ApplicationLogApiError | null = null;
  page = 0;
  size = 50;
  totalElements = 0;
  totalPages = 0;
  filterLevel: ApplicationLogRecord['level'] | '' = '';
  filterService = '';
  filterComponent = '';
  filterOperation = '';
  filterDocumentId = '';
  filterCorrelationId = '';
  filterOccurredFrom = '';
  filterOccurredTo = '';

  constructor() {
    this.loadLogs();
  }

  loadLogs(): void {
    this.isLoading = true;
    this.error = null;
    this.changeDetector.markForCheck();

    this.logsApi
      .find(this.buildQuery())
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (response) => {
          this.logs = response.items;
          this.page = response.page;
          this.size = response.size;
          this.totalElements = response.totalElements;
          this.totalPages = response.totalPages;
          this.isLoading = false;
          this.changeDetector.markForCheck();
        },
        error: (error: unknown) => {
          this.logs = [];
          this.isLoading = false;
          this.error = mapApplicationLogError(error);
          this.changeDetector.markForCheck();
        },
      });
  }

  applyFilters(): void {
    this.page = 0;
    this.loadLogs();
  }

  resetFilters(): void {
    this.filterLevel = '';
    this.filterService = '';
    this.filterComponent = '';
    this.filterOperation = '';
    this.filterDocumentId = '';
    this.filterCorrelationId = '';
    this.filterOccurredFrom = '';
    this.filterOccurredTo = '';
    this.applyFilters();
  }

  hasPreviousPage(): boolean {
    return this.page > 0;
  }

  hasNextPage(): boolean {
    return this.totalPages > 0 && this.page < this.totalPages - 1;
  }

  goToPage(page: number): void {
    if (this.isLoading || page < 0 || page >= this.totalPages || page === this.page) {
      return;
    }

    this.page = page;
    this.loadLogs();
  }

  private buildQuery() {
    return {
      page: this.page,
      size: this.size,
      level: this.filterLevel || undefined,
      service: this.trimOrUndefined(this.filterService),
      component: this.trimOrUndefined(this.filterComponent),
      operation: this.trimOrUndefined(this.filterOperation),
      documentId: this.trimOrUndefined(this.filterDocumentId),
      correlationId: this.trimOrUndefined(this.filterCorrelationId),
      occurredFrom: this.toInstantOrUndefined(this.filterOccurredFrom),
      occurredTo: this.toInstantOrUndefined(this.filterOccurredTo),
    };
  }

  private trimOrUndefined(value: string): string | undefined {
    const trimmed = value.trim();
    return trimmed || undefined;
  }

  private toInstantOrUndefined(value: string): string | undefined {
    if (!value) {
      return undefined;
    }

    const parsed = new Date(value);
    return Number.isNaN(parsed.getTime()) ? undefined : parsed.toISOString();
  }

  formatDate(value: string): string {
    return new Intl.DateTimeFormat('pt-BR', {
      dateStyle: 'medium',
      timeStyle: 'medium',
    }).format(new Date(value));
  }

  levelLabel(level: ApplicationLogRecord['level']): string {
    return level === 'ERROR' ? 'Erro' : level === 'WARN' ? 'Aviso' : 'Informação';
  }

  isNavigableDocumentId(documentId: string | null): documentId is string {
    return documentId !== null &&
      /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(documentId);
  }
}
