import { CommonModule } from '@angular/common';
import { Component, inject } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { NeonAuthService } from './neon-auth.service';

type AuthMode = 'sign-in' | 'sign-up';

@Component({
  imports: [CommonModule, ReactiveFormsModule, RouterLink],
  selector: 'app-auth-page',
  styleUrl: './auth-page.scss',
  templateUrl: './auth-page.html',
})
export class AuthPage {
  private readonly formBuilder = inject(NonNullableFormBuilder);
  private readonly auth = inject(NeonAuthService);
  private readonly router = inject(Router);

  protected readonly form = this.formBuilder.group({
    name: [''],
    email: ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required, Validators.minLength(8)]],
  });
  protected mode: AuthMode = 'sign-in';
  protected submitting = false;
  protected errorMessage = '';
  protected successMessage = '';

  protected get isConfigured(): boolean {
    return this.auth.isConfigured();
  }

  protected setMode(mode: AuthMode): void {
    this.mode = mode;
    this.errorMessage = '';
    this.successMessage = '';
    this.form.controls.name.setValue('');
    this.form.reset({ name: '', email: this.form.controls.email.value, password: '' });
  }

  protected async submit(): Promise<void> {
    this.errorMessage = '';
    this.successMessage = '';

    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    this.submitting = true;
    const { email, password, name } = this.form.getRawValue();

    try {
      const result = this.mode === 'sign-in'
        ? await this.auth.signIn(email, password)
        : await this.auth.signUp(email, password, name.trim() || email.split('@')[0]);

      if (result.error) {
        this.errorMessage = result.error.message || 'Não foi possível concluir a autenticação.';
        return;
      }

      await this.auth.refreshSession();
      this.successMessage = this.mode === 'sign-in'
        ? 'Sessão iniciada.'
        : 'Cadastro concluído. Sua sessão está ativa.';
      await this.router.navigateByUrl('/');
    } catch {
      this.errorMessage = 'Não foi possível comunicar com o Neon Auth. Tente novamente.';
    } finally {
      this.submitting = false;
    }
  }
}
