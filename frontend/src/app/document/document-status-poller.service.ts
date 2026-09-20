import { Injectable } from '@angular/core';
import { Observable, EMPTY, defer, merge, of, timer } from 'rxjs';
import { exhaustMap, takeWhile } from 'rxjs/operators';
import { DocumentApiService } from './document-api.service';
import { DocumentResponse } from './document.models';

export const DOCUMENT_STATUS_POLL_INTERVAL_MS = 3_000;

@Injectable({ providedIn: 'root' })
export class DocumentStatusPollerService {
  constructor(private readonly documentApi: DocumentApiService) {}

  observe(
    documentId: string,
    manualRefresh$: Observable<unknown> = EMPTY,
  ): Observable<DocumentResponse> {
    const scheduledRefreshes$ = timer(
      DOCUMENT_STATUS_POLL_INTERVAL_MS,
      DOCUMENT_STATUS_POLL_INTERVAL_MS,
    );

    return merge(of(null), scheduledRefreshes$, manualRefresh$).pipe(
      exhaustMap(() => defer(() => this.documentApi.findById(documentId))),
      takeWhile(
        (document) => document.status === 'PENDING' || document.status === 'PROCESSING',
        true,
      ),
    );
  }
}
