import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import {
  ChangeDetectionStrategy,
  ChangeDetectorRef,
  Component,
  DestroyRef,
  inject,
} from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { EMPTY, Subject, Subscription, catchError } from 'rxjs';
import { DocumentStatusPanel } from '../components/document-status-panel';
import { DocumentApiError, mapDocumentError } from '../document-error.mapper';
import { DocumentResponse } from '../document.models';
import { DocumentStatusPollerService } from '../document-status-poller.service';

@Component({
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
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
  private readonly poller = inject(DocumentStatusPollerService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  documentId = '';
  document: DocumentResponse | null = null;
  error: DocumentApiError | null = null;
  isLoading = true;
  isRefreshing = false;

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
    void this.router.navigate(['/documents/new']);
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
}
