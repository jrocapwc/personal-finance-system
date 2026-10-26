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
        title: 'Sign in | YamanTracker',
        canActivate: [guestGuard],
        loadComponent: () => import('./features/auth/login/login').then((m) => m.Login),
      },
      {
        path: 'register',
        title: 'Create account | YamanTracker',
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
        path: 'bank-connections',
        title: 'Bank Connections | YamanTracker',
        loadComponent: () =>
          import('./features/bank-connections/bank-connections').then((m) => m.BankConnections),
      },
      {
        path: 'dashboard',
        title: 'Dashboard | YamanTracker',
        loadComponent: () => import('./features/dashboard/dashboard').then((m) => m.Dashboard),
      },
      {
        path: 'transactions',
        title: 'Transactions | YamanTracker',
        loadComponent: () =>
          import('./features/transactions/transactions').then((m) => m.Transactions),
      },
      {
        path: 'categories',
        title: 'Categories | YamanTracker',
        loadComponent: () => import('./features/categories/categories').then((m) => m.Categories),
      },
      {
        path: 'budgets',
        title: 'Budgets | YamanTracker',
        loadComponent: () => import('./features/budgets/budgets').then((m) => m.Budgets),
      },
    ],
  },
  { path: '**', redirectTo: 'dashboard' },
];
