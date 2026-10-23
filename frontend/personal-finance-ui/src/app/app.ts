
import {
  Component,
  inject,
  PLATFORM_ID,
  signal,
  ViewChild
} from '@angular/core';

import { isPlatformBrowser } from '@angular/common';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { BreakpointObserver } from '@angular/cdk/layout';
import { toSignal } from '@angular/core/rxjs-interop';
import { map } from 'rxjs';

import { MatSidenav, MatSidenavModule } from '@angular/material/sidenav';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatListModule } from '@angular/material/list';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';

import { AiAssistant } from './shared/components/ai-assistant/ai-assistant';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [
    RouterOutlet,
    RouterLink,
    RouterLinkActive,
    MatSidenavModule,
    MatToolbarModule,
    MatListModule,
    MatButtonModule,
    MatIconModule,
    AiAssistant
  ],
  templateUrl: './app.html',
  styleUrl: './app.scss'
})
export class App {
  private readonly platformId = inject(PLATFORM_ID);
  private readonly breakpoints = inject(BreakpointObserver);

  @ViewChild('drawer') drawer?: MatSidenav;

  readonly isDarkMode = signal(false);

  readonly isMobile = toSignal(
    this.breakpoints.observe('(max-width: 768px)').pipe(
      map(result => result.matches)
    ),
    { initialValue: false }
  );

  constructor() {
    if (isPlatformBrowser(this.platformId)) {
      try {
        this.isDarkMode.set(
          localStorage.getItem('finance-theme') === 'dark'
        );
      } catch {
        // Continue with the default light theme.
      }
    }
  }

  toggleTheme(): void {
    const dark = !this.isDarkMode();
    this.isDarkMode.set(dark);

    if (isPlatformBrowser(this.platformId)) {
      try {
        localStorage.setItem(
          'finance-theme',
          dark ? 'dark' : 'light'
        );
      } catch {
        // Theme works even if storage is unavailable.
      }
    }
  }

  closeOnMobile(): void {
    if (this.isMobile()) {
      this.drawer?.close();
    }
  }
}
