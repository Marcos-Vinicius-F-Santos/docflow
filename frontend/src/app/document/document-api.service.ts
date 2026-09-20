import { HttpClient, HttpEvent, HttpRequest } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import { DocumentResponse } from './document.models';

@Injectable({ providedIn: 'root' })
export class DocumentApiService {
  private readonly documentsUrl = environment.documentApiBaseUrl;

  constructor(private readonly http: HttpClient) {}

  upload(file: File): Observable<HttpEvent<DocumentResponse>> {
    const formData = new FormData();
    formData.append('file', file, file.name);

    const request = new HttpRequest<FormData>('POST', this.documentsUrl, formData, {
      reportUploadProgress: true,
    });

    return this.http.request<DocumentResponse>(request);
  }

  findById(documentId: string): Observable<DocumentResponse> {
    const encodedDocumentId = encodeURIComponent(documentId);
    return this.http.get<DocumentResponse>(`${this.documentsUrl}/${encodedDocumentId}`);
  }
}
