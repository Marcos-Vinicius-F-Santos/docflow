import { Component, inject } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { RouterOutlet } from '@angular/router';
import { NeonAuthService } from './auth/neon-auth.service';

@Component({
  imports: [RouterLink, RouterLinkActive, RouterOutlet],
  selector: 'app-root',
  styleUrl: './app.scss',
  templateUrl: './app.html',
})
export class App {
  protected readonly auth = inject(NeonAuthService);
  protected readonly session = this.auth.session;

  constructor() {
    void this.auth.refreshSession();
  }

  protected async signOut(): Promise<void> {
    await this.auth.clearSession();
  }
}
