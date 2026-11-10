import { HttpClient, HttpEvent, HttpRequest, HttpResponse } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import { DocumentResponse, DocumentVisibility } from './document.models';

@Injectable({ providedIn: 'root' })
export class DocumentApiService {
  private readonly http = inject(HttpClient);
  private readonly documentsUrl = environment.documentApiBaseUrl;

  upload(file: File, visibility?: DocumentVisibility): Observable<HttpEvent<DocumentResponse>> {
    const formData = new FormData();
    formData.append('file', file, file.name);
    if (visibility) {
      formData.append('visibility', visibility);
    }

    const request = new HttpRequest<FormData>('POST', this.documentsUrl, formData, {
      reportUploadProgress: true,
    });

    return this.http.request<DocumentResponse>(request);
  }

  findById(documentId: string): Observable<DocumentResponse> {
    const encodedDocumentId = encodeURIComponent(documentId);
    return this.http.get<DocumentResponse>(`${this.documentsUrl}/${encodedDocumentId}`);
  }

  list(): Observable<DocumentResponse[]> {
    return this.http.get<DocumentResponse[]>(this.documentsUrl);
  }

  download(documentId: string): Observable<HttpResponse<Blob>> {
    const encodedDocumentId = encodeURIComponent(documentId);
    return this.http.get(`${this.documentsUrl}/${encodedDocumentId}/content`, {
      observe: 'response',
      responseType: 'blob',
    });
  }

  previewContentUrl(documentId: string): string {
    const encodedDocumentId = encodeURIComponent(documentId);
    return `${this.documentsUrl}/${encodedDocumentId}/preview`;
  }

  delete(documentId: string): Observable<void> {
    const encodedDocumentId = encodeURIComponent(documentId);
    return this.http.delete<void>(`${this.documentsUrl}/${encodedDocumentId}`);
  }

  retry(documentId: string): Observable<HttpResponse<DocumentResponse>> {
    const encodedDocumentId = encodeURIComponent(documentId);
    return this.http.post<DocumentResponse>(
      `${this.documentsUrl}/${encodedDocumentId}/retry`,
      null,
      { observe: 'response' },
    );
  }

  changeVisibility(
    documentId: string,
    visibility: DocumentVisibility,
  ): Observable<DocumentResponse> {
    const encodedDocumentId = encodeURIComponent(documentId);
    return this.http.patch<DocumentResponse>(
      `${this.documentsUrl}/${encodedDocumentId}/visibility`,
      { visibility },
    );
  }
}
