import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { HttpEvent, HttpEventType, HttpResponse } from '@angular/common/http';
import {
  ChangeDetectionStrategy,
  ChangeDetectorRef,
  Component,
  DestroyRef,
  inject,
} from '@angular/core';
import { RouterLink } from '@angular/router';
import { DocumentApiService } from '../document/document-api.service';
import { DocumentApiError, mapDocumentError } from '../document/document-error.mapper';
import { DocumentFileValidator } from '../document/document-file-validator';
import { DocumentResponse } from '../document/document.models';

@Component({
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink],
  selector: 'app-hub-page',
  styleUrl: './hub-page.scss',
  templateUrl: './hub-page.html',
})
export class HubPage {
  private readonly changeDetector = inject(ChangeDetectorRef);
  private readonly destroyRef = inject(DestroyRef);
  private readonly documentApi = inject(DocumentApiService);
  private readonly validator = new DocumentFileValidator();

  documents: DocumentResponse[] = [];
  isLoading = true;
  listError: DocumentApiError | null = null;
  selectedFile: File | null = null;
  validationError: string | null = null;
  uploadError: DocumentApiError | null = null;
  uploadProgress = 0;
  uploadInProgress = false;
  isDragActive = false;

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

  recentDocuments(): DocumentResponse[] {
    return [...this.documents]
      .sort((left, right) => this.documentDate(right) - this.documentDate(left))
      .slice(0, 5);
  }

  failedDocuments(): DocumentResponse[] {
    return this.documents
      .filter((document) => document.status === 'FAILED')
      .sort((left, right) => this.documentDate(right) - this.documentDate(left))
      .slice(0, 5);
  }

  hasActiveAction(): boolean {
    return this.uploadInProgress;
  }

  openFilePicker(input: HTMLInputElement, event: Event): void {
    const keyboardEvent = event as KeyboardEvent;
    if (keyboardEvent.key !== 'Enter' && keyboardEvent.key !== ' ') {
      return;
    }

    keyboardEvent.preventDefault();
    if (!this.hasActiveAction()) {
      input.click();
    }
  }

  onFileInputChange(event: Event): void {
    const input = event.target as HTMLInputElement;
    this.selectFile(input.files?.item(0) ?? null);
  }

  onDrop(event: DragEvent): void {
    event.preventDefault();
    this.isDragActive = false;

    if (!this.hasActiveAction()) {
      this.selectFile(event.dataTransfer?.files.item(0) ?? null);
    }
  }

  onDragOver(event: DragEvent): void {
    event.preventDefault();

    if (!this.hasActiveAction()) {
      this.isDragActive = true;
    }
  }

  onDragLeave(event: DragEvent): void {
    event.preventDefault();
    this.isDragActive = false;
  }

  selectFile(file: File | null): void {
    if (this.hasActiveAction()) {
      return;
    }

    this.selectedFile = file;
    this.uploadError = null;
    this.uploadProgress = 0;

    if (!file) {
      this.validationError = null;
      this.changeDetector.markForCheck();
      return;
    }

    const validation = this.validator.validate(file);
    this.validationError = validation.valid ? null : validation.error.message;
    this.changeDetector.markForCheck();
  }

  clearFile(): void {
    if (!this.hasActiveAction()) {
      this.selectedFile = null;
      this.validationError = null;
      this.uploadError = null;
      this.uploadProgress = 0;
      this.changeDetector.markForCheck();
    }
  }

  submit(): void {
    if (!this.selectedFile || this.validationError || this.hasActiveAction()) {
      return;
    }

    this.uploadInProgress = true;
    this.uploadError = null;
    this.uploadProgress = 0;
    this.changeDetector.markForCheck();

    this.documentApi
      .upload(this.selectedFile)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (event) => {
          if (event.type === HttpEventType.UploadProgress) {
            this.uploadProgress = event.total
              ? Math.round((event.loaded / event.total) * 100)
              : 0;
            this.changeDetector.markForCheck();
            return;
          }

          if (event instanceof HttpResponse) {
            this.handleUploadResponse(event);
          }
        },
        error: (error: unknown) => {
          this.uploadInProgress = false;
          this.uploadError = mapDocumentError(error);
          this.changeDetector.markForCheck();
        },
      });
  }

  private documentDate(document: DocumentResponse): number {
    const updatedAt = Date.parse(document.updatedAt);
    return Number.isNaN(updatedAt) ? Date.parse(document.createdAt) : updatedAt;
  }

  private handleUploadResponse(response: HttpResponse<DocumentResponse>): void {
    this.uploadInProgress = false;
    this.uploadProgress = 100;

    if (!response.body) {
      this.uploadError = {
        kind: 'operational',
        status: response.status,
        message: 'O backend não retornou os dados do documento.',
        canRetryManually: true,
      };
      this.changeDetector.markForCheck();
      return;
    }

    this.documents = [
      response.body,
      ...this.documents.filter((document) => document.id !== response.body?.id),
    ];
    this.selectedFile = null;
    this.validationError = null;
    this.uploadError = null;
    this.changeDetector.markForCheck();
  }

}
