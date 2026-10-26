import { RenderMode, ServerRoute } from '@angular/ssr';

export const serverRoutes: ServerRoute[] = [
  { path: 'account-settings', renderMode: RenderMode.Client },
  { path: 'dashboard', renderMode: RenderMode.Client },
  { path: 'categories', renderMode: RenderMode.Client },
  { path: 'transactions', renderMode: RenderMode.Client },
  { path: 'budgets', renderMode: RenderMode.Client },
  { path: 'bank-connections', renderMode: RenderMode.Client },
  { path: 'session-error', renderMode: RenderMode.Client },
  {
    path: '**',
    renderMode: RenderMode.Prerender,
  },
];
