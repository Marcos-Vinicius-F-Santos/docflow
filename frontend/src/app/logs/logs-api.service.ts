import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import { ApplicationLogPageResponse, ApplicationLogQuery } from './logs.models';

@Injectable({ providedIn: 'root' })
export class LogsApiService {
  private readonly http = inject(HttpClient);
  private readonly logsUrl = environment.applicationLogsApiUrl;

  find(query: ApplicationLogQuery = {}): Observable<ApplicationLogPageResponse> {
    let params = new HttpParams();
    for (const [name, value] of Object.entries(query)) {
      if (value !== undefined && value !== null && value !== '') {
        params = params.set(name, String(value));
      }
    }

    return this.http.get<ApplicationLogPageResponse>(this.logsUrl, { params });
  }
}
