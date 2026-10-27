import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { CdkTrapFocus } from '@angular/cdk/a11y';
import { HttpResponse } from '@angular/common/http';
import {
  ChangeDetectionStrategy,
  ChangeDetectorRef,
  Component,
  DestroyRef,
  ElementRef,
  ViewChild,
  inject,
} from '@angular/core';
import { DomSanitizer, SafeResourceUrl } from '@angular/platform-browser';
import { ActivatedRoute, Router } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { EMPTY, Subject, Subscription, catchError } from 'rxjs';
import { DocumentStatusPanel } from '../components/document-status-panel';
import { DocumentApiService } from '../document-api.service';
import { DocumentApiError, mapDocumentError } from '../document-error.mapper';
import { DocumentResponse } from '../document.models';
import { DocumentStatusPollerService } from '../document-status-poller.service';

@Component({
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    CdkTrapFocus,
    DocumentStatusPanel,
    MatButtonModule,
    MatCardModule,
    MatIconModule,
    MatProgressSpinnerModule,
  ],
  selector: 'app-document-detail-page',
  styleUrl: './document-detail-page.scss',
  templateUrl: './document-detail-page.html',
})
export class DocumentDetailPage {
  private readonly destroyRef = inject(DestroyRef);
  private readonly changeDetector = inject(ChangeDetectorRef);
  private readonly documentApi = inject(DocumentApiService);
  private readonly sanitizer = inject(DomSanitizer);
  private readonly poller = inject(DocumentStatusPollerService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  documentId = '';
  document: DocumentResponse | null = null;
  error: DocumentApiError | null = null;
  isLoading = true;
  isRefreshing = false;
  actionError: DocumentApiError | null = null;
  pendingDeletion: DocumentResponse | null = null;
  downloading = false;
  retrying = false;
  deleting = false;
  previewUrl: SafeResourceUrl | null = null;

  @ViewChild('confirmDeleteButton', { read: ElementRef })
  private confirmDeleteButton?: ElementRef<HTMLButtonElement>;

  private pollingActive = false;
  private manualRefresh$ = new Subject<void>();
  private observationSubscription: Subscription | null = null;

  constructor() {
    this.route.paramMap.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((params) => {
      const documentId = params.get('documentId');

      if (!documentId) {
        return;
      }

      this.documentId = documentId;
      this.startObservation();
    });
  }

  refresh(): void {
    if (this.isRefreshing) {
      return;
    }

    this.isRefreshing = true;

    if (this.pollingActive) {
      this.manualRefresh$.next();
      return;
    }

    this.startObservation();
  }

  openNewUpload(): void {
    void this.router.navigate(['/']);
  }

  hasActiveAction(): boolean {
    return this.downloading || this.retrying || this.deleting;
  }

  canRetry(document: DocumentResponse): boolean {
    return document.status === 'FAILED' && document.falhas < 3 && !document.isExcluded;
  }

  downloadDocument(): void {
    const document = this.document;
    if (!document || document.status !== 'COMPLETED' || this.hasActiveAction()) {
      return;
    }

    this.actionError = null;
    this.downloading = true;
    this.changeDetector.markForCheck();

    this.documentApi
      .download(document.id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (response) => this.startBrowserDownload(response, document),
        error: (error: unknown) => {
          this.downloading = false;
          this.actionError = mapDocumentError(error, 'download');
          this.changeDetector.markForCheck();
        },
      });
  }

  retryDocument(): void {
    const document = this.document;
    if (!document || !this.canRetry(document) || this.hasActiveAction()) {
      return;
    }

    this.actionError = null;
    this.retrying = true;
    this.changeDetector.markForCheck();

    this.documentApi
      .retry(document.id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (response) => {
          this.retrying = false;
          if (!response.body) {
            this.actionError = {
              kind: 'operational',
              status: response.status,
              message: 'O backend não retornou os dados do documento.',
              canRetryManually: true,
            };
          } else {
            this.document = response.body;
          }
          this.changeDetector.markForCheck();
        },
        error: (error: unknown) => {
          this.retrying = false;
          this.actionError = mapDocumentError(error);
          this.changeDetector.markForCheck();
        },
      });
  }

  requestDelete(): void {
    if (!this.document || this.hasActiveAction()) {
      return;
    }

    this.actionError = null;
    this.pendingDeletion = this.document;
    this.changeDetector.detectChanges();
    this.confirmDeleteButton?.nativeElement?.focus();
  }

  cancelDelete(): void {
    this.pendingDeletion = null;
    this.changeDetector.markForCheck();
  }

  confirmDelete(): void {
    const document = this.pendingDeletion;
    if (!document || this.deleting) {
      return;
    }

    this.pendingDeletion = null;
    this.deleting = true;
    this.actionError = null;
    this.changeDetector.markForCheck();

    this.documentApi
      .delete(document.id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.deleting = false;
          void this.router.navigate(['/']);
        },
        error: (error: unknown) => {
          this.deleting = false;
          this.actionError = mapDocumentError(error, 'delete');
          this.changeDetector.markForCheck();
        },
      });
  }

  formatDate(date: string): string {
    return new Intl.DateTimeFormat('pt-BR', {
      dateStyle: 'medium',
      timeStyle: 'short',
    }).format(new Date(date));
  }

  formatFileSize(sizeBytes: number): string {
    if (sizeBytes < 1024) {
      return `${sizeBytes} bytes`;
    }

    if (sizeBytes < 1024 * 1024) {
      return `${(sizeBytes / 1024).toFixed(1)} KiB`;
    }

    return `${(sizeBytes / (1024 * 1024)).toFixed(1)} MiB`;
  }

  private startObservation(): void {
    this.observationSubscription?.unsubscribe();
    this.manualRefresh$ = new Subject<void>();
    this.pollingActive = true;
    this.isLoading = true;
    this.isRefreshing = true;
    this.document = null;
    this.error = null;
    this.actionError = null;
    this.previewUrl = null;

    this.observationSubscription = this.poller
      .observe(this.documentId, this.manualRefresh$)
      .pipe(
        catchError((error: unknown) => {
          this.document = null;
          this.error = mapDocumentError(error);
          this.isLoading = false;
          this.isRefreshing = false;
          this.pollingActive = false;
          this.changeDetector.markForCheck();
          return EMPTY;
        }),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (document) => {
          this.document = document;
          this.previewUrl =
            document.status === 'COMPLETED' && this.isPdf(document)
              ? this.sanitizer.bypassSecurityTrustResourceUrl(
                  this.documentApi.previewContentUrl(document.id),
                )
              : null;
          this.error = null;
          this.isLoading = false;
          this.isRefreshing = false;
          this.changeDetector.markForCheck();
        },
        complete: () => {
          this.isLoading = false;
          this.isRefreshing = false;
          this.pollingActive = false;
          this.changeDetector.markForCheck();
        },
      });
  }

  private isPdf(document: DocumentResponse): boolean {
    return document.contentType.toLowerCase().split(';', 1)[0] === 'application/pdf';
  }

  private startBrowserDownload(
    response: HttpResponse<Blob>,
    document: DocumentResponse,
  ): void {
    this.downloading = false;

    if (!response.body) {
      this.actionError = {
        kind: 'operational',
        status: response.status,
        message: 'Não foi possível concluir o download.',
        canRetryManually: true,
      };
      this.changeDetector.markForCheck();
      return;
    }

    const objectUrl = URL.createObjectURL(response.body);
    const anchor = window.document.createElement('a');
    anchor.href = objectUrl;
    anchor.download = this.downloadFilename(response, document);
    anchor.click();
    URL.revokeObjectURL(objectUrl);
    this.changeDetector.markForCheck();
  }

  private downloadFilename(response: HttpResponse<Blob>, document: DocumentResponse): string {
    const contentDisposition = response.headers.get('Content-Disposition');
    const filename = contentDisposition?.match(/filename\*?=(?:UTF-8'')?"?([^";]+)"?/i)?.[1];

    if (filename) {
      try {
        return decodeURIComponent(filename);
      } catch {
        return filename;
      }
    }

    return document.originalFilename;
  }
}
