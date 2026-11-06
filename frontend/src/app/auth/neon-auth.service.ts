import { Injectable, signal } from '@angular/core';
import { createInternalNeonAuth } from '@neon/auth';
import { environment } from '../../environments/environment';

export interface NeonAuthSession {
  user: {
    email?: string;
    name?: string;
  };
}

interface NeonAuthTokenResult {
  data: { token: string } | null;
}

interface NeonAuthTokenClient {
  token(): Promise<NeonAuthTokenResult>;
}

/** Thin Angular boundary around the Neon Managed Auth client. */
@Injectable({ providedIn: 'root' })
export class NeonAuthService {
  private readonly authClient = environment.neonAuthBaseUrl
    ? createInternalNeonAuth(environment.neonAuthBaseUrl)
    : null;
  private readonly client = this.authClient?.adapter ?? null;
  private readonly sessionState = signal<NeonAuthSession | null>(null);

  readonly session = this.sessionState.asReadonly();

  isConfigured(): boolean {
    return this.client !== null;
  }

  signUp(email: string, password: string, name: string) {
    return this.requireClient().signUp.email({ email, password, name });
  }

  signIn(email: string, password: string) {
    return this.requireClient().signIn.email({ email, password });
  }

  getSession() {
    return this.requireClient().getSession();
  }

  async getAccessToken(): Promise<string | null> {
    if (!this.client) {
      return null;
    }

    const tokenClient = this.client as typeof this.client & NeonAuthTokenClient;
    const result = await tokenClient.token();
    return result.data?.token ?? null;
  }

  signOut() {
    return this.requireClient().signOut();
  }

  async refreshSession(): Promise<void> {
    if (!this.client) {
      this.sessionState.set(null);
      return;
    }

    const result = await this.client.getSession();
    this.sessionState.set((result.data as NeonAuthSession | null) ?? null);
  }

  async clearSession(): Promise<void> {
    if (this.client) {
      await this.client.signOut();
    }
    this.sessionState.set(null);
  }

  private requireClient() {
    if (!this.client) {
      throw new Error('Neon Auth is not configured for this environment');
    }
    return this.client;
  }
}
