import { Injectable, signal } from '@angular/core';
import { createInternalNeonAuth } from '@neon/auth';
import { environment } from '../../environments/environment';

export interface NeonAuthSession {
  user: {
    email?: string;
    name?: string;
  };
}

/** Thin Angular boundary around the Neon Managed Auth client. */
@Injectable({ providedIn: 'root' })
export class NeonAuthService {
  private readonly authClient = environment.neonAuthBaseUrl
    ? createInternalNeonAuth(environment.neonAuthBaseUrl)
    : null;
  private readonly client = this.authClient?.adapter ?? null;
  private readonly sessionState = signal<NeonAuthSession | null>(null);
  private sessionReady: Promise<void> | null = null;

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

  async waitForSession(): Promise<void> {
    if (!this.sessionReady) {
      this.sessionReady = this.loadSession();
    }
    await this.sessionReady;
  }

  async getAccessToken(): Promise<string | null> {
    if (!this.authClient) {
      return null;
    }

    try {
      return await this.authClient.getJWTToken();
    } catch {
      return null;
    }
  }

  signOut() {
    return this.requireClient().signOut();
  }

  async refreshSession(): Promise<void> {
    this.sessionReady = this.loadSession();
    await this.sessionReady;
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

  private async loadSession(): Promise<void> {
    if (!this.client) {
      this.sessionState.set(null);
      return;
    }

    try {
      const result = await this.client.getSession();
      this.sessionState.set((result.data as NeonAuthSession | null) ?? null);
    } catch {
      this.sessionState.set(null);
    }
  }
}
