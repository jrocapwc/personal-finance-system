import { afterNextRender, DestroyRef, inject, Injectable, signal } from '@angular/core';
import { DOCUMENT } from '@angular/common';
@Injectable({ providedIn: 'root' })
export class ThemeService {
  private readonly document = inject(DOCUMENT);
  private readonly dark = signal(false);
  readonly isDarkMode = this.dark.asReadonly();
  constructor() {
    inject(DestroyRef).onDestroy(() => this.document.documentElement.classList.remove('dark-theme'));
    // Read browser preferences after hydration to keep server/client markup consistent.
    afterNextRender(() => {
      try {
        this.dark.set(localStorage.getItem('finance-theme') === 'dark');
      } catch {
        /* Storage is optional. */
      }
      this.applyDocumentTheme();
    });
  }
  private applyDocumentTheme(): void {
    this.document.documentElement.classList.toggle('dark-theme', this.dark());
  }
  toggleTheme(): void {
    this.dark.update((value) => !value);
    this.applyDocumentTheme();
    try {
      localStorage.setItem('finance-theme', this.dark() ? 'dark' : 'light');
    } catch {
      /* Storage is optional. */
    }
  }
}
