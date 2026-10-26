import { Routes } from '@angular/router';
import { authGuard, guestGuard } from './core/guards/auth.guard';
export const routes: Routes = [
  { path: 'session-error', loadComponent: () => import('./features/auth/session-error').then(m => m.SessionError) },
  { path: '', pathMatch: 'full', redirectTo: 'dashboard' },
  {
    path: '',
    loadComponent: () => import('./layouts/auth-layout/auth-layout').then((m) => m.AuthLayout),
    children: [
      {
        path: 'login',
        title: 'Sign in | FinanceAI',
        canActivate: [guestGuard],
        loadComponent: () => import('./features/auth/login/login').then((m) => m.Login),
      },
      {
        path: 'register',
        title: 'Create account | FinanceAI',
        canActivate: [guestGuard],
        loadComponent: () => import('./features/auth/register/register').then((m) => m.Register),
      },
    ],
  },
  {
    path: '',
    canActivate: [authGuard],
    canActivateChild: [authGuard],
    loadComponent: () => import('./layouts/main-layout/main-layout').then((m) => m.MainLayout),
    children: [
      {
        path: 'dashboard',
        title: 'Dashboard | FinanceAI',
        loadComponent: () => import('./features/dashboard/dashboard').then((m) => m.Dashboard),
      },
      {
        path: 'transactions',
        title: 'Transactions | FinanceAI',
        loadComponent: () =>
          import('./features/transactions/transactions').then((m) => m.Transactions),
      },
      {
        path: 'categories',
        title: 'Categories | FinanceAI',
        loadComponent: () => import('./features/categories/categories').then((m) => m.Categories),
      },
      {
        path: 'budgets',
        title: 'Budgets | FinanceAI',
        loadComponent: () => import('./features/budgets/budgets').then((m) => m.Budgets),
      },
    ],
  },
  { path: '**', redirectTo: 'dashboard' },
];
