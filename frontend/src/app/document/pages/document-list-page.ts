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
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { Router } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { DocumentApiService } from '../document-api.service';
import { DocumentApiError, documentListMessage, mapDocumentError } from '../document-error.mapper';
import { DocumentResponse } from '../document.models';

@Component({
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    CdkTrapFocus,
    MatButtonModule,
    MatCardModule,
    MatIconModule,
    MatProgressSpinnerModule,
  ],
  selector: 'app-document-list-page',
  styleUrl: './document-list-page.scss',
  templateUrl: './document-list-page.html',
})
export class DocumentListPage {
  private readonly changeDetector = inject(ChangeDetectorRef);
  private readonly destroyRef = inject(DestroyRef);
  private readonly documentApi = inject(DocumentApiService);
  private readonly router = inject(Router);

  @ViewChild('confirmDeleteButton') private confirmDeleteButton?: ElementRef<HTMLButtonElement>;

  documents: DocumentResponse[] = [];
  isLoading = true;
  listError: DocumentApiError | null = null;
  actionError: DocumentApiError | null = null;
  pendingDeletion: DocumentResponse | null = null;
  downloadingDocumentId: string | null = null;
  deletingDocumentId: string | null = null;

  constructor() {
    this.loadDocuments();
  }

  loadDocuments(): void {
    this.isLoading = true;
    this.listError = null;
    this.changeDetector.markForCheck();

    this.documentApi
      .list()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (documents) => {
          this.documents = documents;
          this.isLoading = false;
          this.changeDetector.markForCheck();
        },
        error: (error: unknown) => {
          this.documents = [];
          this.isLoading = false;
          this.listError = mapDocumentError(error, 'list');
          this.changeDetector.markForCheck();
        },
      });
  }

  openNewUpload(): void {
    void this.router.navigate(['/documents/new']);
  }

  openDocument(document: DocumentResponse): void {
    void this.router.navigate(['/documents', document.id]);
  }

  emptyListMessage(): string | null {
    return this.isLoading || this.listError ? null : documentListMessage(this.documents);
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

  downloadDocument(document: DocumentResponse): void {
    if (this.downloadingDocumentId || this.deletingDocumentId) {
      return;
    }

    this.actionError = null;
    this.downloadingDocumentId = document.id;
    this.changeDetector.markForCheck();

    this.documentApi
      .download(document.id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (response) => this.startBrowserDownload(response, document),
        error: (error: unknown) => {
          this.downloadingDocumentId = null;
          this.actionError = mapDocumentError(error, 'download');
          this.changeDetector.markForCheck();
        },
      });
  }

  requestDelete(document: DocumentResponse): void {
    if (this.deletingDocumentId || this.downloadingDocumentId) {
      return;
    }

    this.actionError = null;
    this.pendingDeletion = document;
    this.changeDetector.detectChanges();
    const confirmButton =
      this.confirmDeleteButton?.nativeElement ??
      window.document.querySelector<HTMLButtonElement>('[data-confirm-delete]');
    confirmButton?.focus();
  }

  cancelDelete(): void {
    this.pendingDeletion = null;
    this.changeDetector.markForCheck();
  }

  confirmDelete(): void {
    const document = this.pendingDeletion;
    if (!document || this.deletingDocumentId) {
      return;
    }

    this.pendingDeletion = null;
    this.deletingDocumentId = document.id;
    this.actionError = null;
    this.changeDetector.markForCheck();

    this.documentApi
      .delete(document.id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.documents = this.documents.filter((item) => item.id !== document.id);
          this.deletingDocumentId = null;
          this.changeDetector.markForCheck();
        },
        error: (error: unknown) => {
          this.deletingDocumentId = null;
          this.actionError = mapDocumentError(error, 'delete');
          this.changeDetector.markForCheck();
        },
      });
  }

  private startBrowserDownload(response: HttpResponse<Blob>, document: DocumentResponse): void {
    this.downloadingDocumentId = null;

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
