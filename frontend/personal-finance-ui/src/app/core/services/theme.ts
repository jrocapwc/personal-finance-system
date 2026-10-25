import { afterNextRender, Injectable, signal } from '@angular/core';
@Injectable({ providedIn: 'root' })
export class ThemeService {
  private readonly dark = signal(false);
  readonly isDarkMode = this.dark.asReadonly();
  constructor() {
    // Read browser preferences after hydration to keep server/client markup consistent.
    afterNextRender(() => {
      try {
        this.dark.set(localStorage.getItem('finance-theme') === 'dark');
      } catch {
        /* Storage is optional. */
      }
    });
  }
  toggleTheme(): void {
    this.dark.update((value) => !value);
    try {
      localStorage.setItem('finance-theme', this.dark() ? 'dark' : 'light');
    } catch {
      /* Storage is optional. */
    }
  }
}
