import { Component, inject } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { ThemeService } from '../../core/services/theme';
@Component({
  selector: 'app-auth-layout',
  standalone: true,
  imports: [RouterOutlet, MatIconModule, MatButtonModule],
  template: ` <main class="auth-page">
    <button
      class="theme-toggle"
      mat-icon-button
      type="button"
      [attr.aria-label]="theme.isDarkMode() ? 'Switch to light mode' : 'Switch to dark mode'"
      (click)="theme.toggleTheme()"
    >
      <mat-icon>{{ theme.isDarkMode() ? 'light_mode' : 'dark_mode' }}</mat-icon>
    </button>
    <section class="auth-card" aria-label="FinanceAI account">
      <div class="auth-brand">
        <mat-icon>account_balance_wallet</mat-icon><strong>FinanceAI</strong>
      </div>
      <router-outlet />
    </section>
  </main>`,
  styleUrl: './auth-layout.scss',
})
export class AuthLayout {
  readonly theme = inject(ThemeService);
}
