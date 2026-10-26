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
      <mat-icon
        ><svg
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          stroke-width="1.7"
          stroke-linecap="round"
          stroke-linejoin="round"
          aria-hidden="true"
        >
          <use
            [attr.href]="'ui-icons.svg#' + (theme.isDarkMode() ? 'light_mode' : 'dark_mode')"
          ></use></svg
      ></mat-icon>
    </button>
    <section class="auth-card" aria-label="YamanTracker account">
      <div class="auth-form-panel">
        <div class="auth-brand">
          <img src="favicon.svg" width="48" height="48" alt="" />
          <div>
            <strong>Yaman<span>Tracker</span></strong
            ><small>Personal Finance Management System</small>
          </div>
        </div>
        <router-outlet />
      </div>
      <aside class="auth-story" aria-label="YamanTracker introduction">
        <img class="auth-skyline" src="auth-skyline.png" alt="" />
        <div class="story-copy">
          <span>YOUR NEXT CHAPTER</span>
          <h2>Take control of<br />your finances<br />with YamanTracker</h2>
          <p>Track. Plan. Grow.<br />With AI by your side.</p>
        </div>
      </aside>
    </section>
  </main>`,
  styleUrl: './auth-layout.scss',
})
export class AuthLayout {
  readonly theme = inject(ThemeService);
}
