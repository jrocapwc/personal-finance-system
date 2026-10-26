import { Component, computed, inject, ViewChild } from '@angular/core';

import { ThemeService } from '../../core/services/theme';
import { AuthService } from '../../core/services/auth';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { BreakpointObserver } from '@angular/cdk/layout';
import { toSignal } from '@angular/core/rxjs-interop';
import { filter, map } from 'rxjs';

import { MatSidenav, MatSidenavModule } from '@angular/material/sidenav';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatListModule } from '@angular/material/list';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';

import { AiAssistant } from '../../shared/components/ai-assistant/ai-assistant';

@Component({
  selector: 'app-main-layout',
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
    AiAssistant,
  ],
  templateUrl: './main-layout.html',
  styleUrl: './main-layout.scss',
})
export class MainLayout {
  readonly theme = inject(ThemeService);
  readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly currentUrl = toSignal(
    this.router.events.pipe(
      filter((event) => event instanceof NavigationEnd),
      map(() => this.router.url),
    ),
    { initialValue: this.router.url },
  );
  readonly pageTitle = computed(
    () =>
      ({
        transactions: 'Transactions',
        categories: 'Categories',
        budgets: 'Budgets',
        dashboard: 'Dashboard',
        'bank-connections': 'Bank Connections',
      })[this.currentUrl().split('/')[1]?.split('?')[0]] ?? 'FinanceAI',
  );
  private readonly breakpoints = inject(BreakpointObserver);

  @ViewChild('drawer') drawer?: MatSidenav;

  readonly isDarkMode = this.theme.isDarkMode;

  readonly isMobile = toSignal(
    this.breakpoints.observe('(max-width: 768px)').pipe(map((result) => result.matches)),
    { initialValue: false },
  );

  toggleTheme(): void {
    this.theme.toggleTheme();
  }

  closeOnMobile(): void {
    if (this.isMobile()) {
      this.drawer?.close();
    }
  }
}
