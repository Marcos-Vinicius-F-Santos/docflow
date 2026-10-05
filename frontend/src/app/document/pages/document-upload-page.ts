import { HttpEventType, HttpResponse } from '@angular/common/http';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import {
  ChangeDetectionStrategy,
  ChangeDetectorRef,
  Component,
  DestroyRef,
  inject,
} from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { Router } from '@angular/router';
import { DocumentApiService } from '../document-api.service';
import { DocumentApiError, mapDocumentError } from '../document-error.mapper';
import { DocumentFileValidator } from '../document-file-validator';
import { DocumentResponse } from '../document.models';

@Component({
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [MatButtonModule, MatCardModule, MatIconModule, MatProgressBarModule],
  selector: 'app-document-upload-page',
  styleUrl: './document-upload-page.scss',
  templateUrl: './document-upload-page.html',
})
export class DocumentUploadPage {
  private readonly documentApi = inject(DocumentApiService);
  private readonly changeDetector = inject(ChangeDetectorRef);
  private readonly destroyRef = inject(DestroyRef);
  private readonly router = inject(Router);
  private readonly validator = new DocumentFileValidator();

  selectedFile: File | null = null;
  validationError: string | null = null;
  uploadError: DocumentApiError | null = null;
  uploadProgress = 0;
  uploadInProgress = false;
  isDragActive = false;

  onFileInputChange(event: Event): void {
    const input = event.target as HTMLInputElement;
    this.selectFile(input.files?.item(0) ?? null);
  }

  onDrop(event: DragEvent): void {
    event.preventDefault();
    this.isDragActive = false;

    if (this.uploadInProgress) {
      return;
    }

    this.selectFile(event.dataTransfer?.files.item(0) ?? null);
  }

  onDragOver(event: DragEvent): void {
    event.preventDefault();

    if (!this.uploadInProgress) {
      this.isDragActive = true;
    }
  }

  onDragLeave(event: DragEvent): void {
    event.preventDefault();
    this.isDragActive = false;
  }

  selectFile(file: File | null): void {
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
    if (this.uploadInProgress) {
      return;
    }

    this.selectedFile = null;
    this.validationError = null;
    this.uploadError = null;
    this.uploadProgress = 0;
    this.changeDetector.markForCheck();
  }

  submit(): void {
    if (!this.selectedFile || this.validationError || this.uploadInProgress) {
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
            this.uploadProgress = event.total ? Math.round((event.loaded / event.total) * 100) : 0;
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

  formatFileSize(sizeBytes: number): string {
    if (sizeBytes < 1024) {
      return `${sizeBytes} bytes`;
    }

    if (sizeBytes < 1024 * 1024) {
      return `${(sizeBytes / 1024).toFixed(1)} KiB`;
    }

    return `${(sizeBytes / (1024 * 1024)).toFixed(1)} MiB`;
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

    void this.router.navigate(['/documents', response.body.id]);
  }
}
