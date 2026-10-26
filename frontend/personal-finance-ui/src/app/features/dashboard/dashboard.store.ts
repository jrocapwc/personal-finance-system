import { isPlatformBrowser } from '@angular/common';
import { computed, inject, Injectable, PLATFORM_ID, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { EMPTY, catchError, map, merge, of, switchMap } from 'rxjs';
import { UserResponse } from '../../core/models/auth.model';
import { CategoryResponse } from '../../core/models/category.model';
import {
  DashboardResponse,
  FinancialSummary,
  ReportingPeriod,
} from '../../core/models/dashboard.model';
import { AuthService } from '../../core/services/auth';
import { CategoryService } from '../../core/services/category';
import { DashboardService } from '../../core/services/dashboard';
import {
  checkedDashboard,
  financeError,
  periodKey,
  periodLabel,
  sixMonths,
  validPeriod,
} from './dashboard.mapper';

export interface HistoryMonth extends ReportingPeriod {
  key: string;
  label: string;
  status: 'loading' | 'ready' | 'error' | 'unsupported';
  summary: FinancialSummary | null;
}
interface DashboardState {
  owner: UserResponse | null;
  period: ReportingPeriod;
  data: DashboardResponse | null;
  loading: boolean;
  error: string;
  categories: CategoryResponse[];
  categoryError: string;
  history: HistoryMonth[];
}
type Result =
  | { kind: 'current'; data: DashboardResponse | null; error: string }
  | { kind: 'categories'; data: CategoryResponse[]; error: string }
  | { kind: 'history'; period: ReportingPeriod; data: DashboardResponse | null };

// Provided by Dashboard, never by the root injector: no cross-session finance cache.
@Injectable()
export class DashboardStore {
  private readonly auth = inject(AuthService);
  private readonly api = inject(DashboardService);
  private readonly categoryApi = inject(CategoryService);
  private readonly browser = isPlatformBrowser(inject(PLATFORM_ID));
  private readonly selected = signal<ReportingPeriod>({
    month: new Date().getMonth() + 1,
    year: new Date().getFullYear(),
  });
  private readonly refreshVersion = signal(0);
  readonly period = this.selected.asReadonly();
  private readonly raw = signal<DashboardState>(this.empty(null, this.period()));
  readonly state = computed(() => {
    const state = this.raw();
    return state.owner === this.auth.currentUser() &&
      periodKey(state.period) === periodKey(this.period())
      ? state
      : this.empty(this.auth.currentUser(), this.period());
  });
  readonly data = computed(() => this.state().data);
  readonly periodLabel = computed(() => periodLabel(this.period()));
  private readonly query = computed(() => ({
    owner: this.auth.currentUser(),
    period: this.period(),
    refresh: this.refreshVersion(),
  }));

  constructor() {
    toObservable(this.query)
      .pipe(
        switchMap(({ owner, period }) => {
          this.raw.set(this.empty(owner, period));
          if (!this.browser || !owner || !this.auth.isAuthenticated()) return EMPTY;
          const request = (target: ReportingPeriod) =>
            this.api.getMonthly(target).pipe(map((data) => checkedDashboard(data, target)));
          const current = request(period).pipe(
            map((data) => ({ kind: 'current', data, error: '' }) as Result),
            catchError((error) =>
              of<Result>({ kind: 'current', data: null, error: financeError(error) }),
            ),
          );
          const categories = this.categoryApi.list().pipe(
            map((data) => ({ kind: 'categories', data, error: '' }) as Result),
            catchError(() =>
              of<Result>({
                kind: 'categories',
                data: [],
                error: 'Category names could not be loaded. Some labels may be unavailable.',
              }),
            ),
          );
          const history = sixMonths(period)
            .filter((target) => validPeriod(target) && periodKey(target) !== periodKey(period))
            .map((target) =>
              request(target).pipe(
                map((data) => ({ kind: 'history', period: target, data }) as Result),
                catchError(() => of<Result>({ kind: 'history', period: target, data: null })),
              ),
            );
          return merge(current, categories, ...history).pipe(
            map((result) => ({ result, owner, period })),
          );
        }),
        takeUntilDestroyed(),
      )
      .subscribe(({ result, owner, period }) => {
        // Guard writes too: a response may race a session/period change before switchMap runs.
        if (owner !== this.auth.currentUser() || periodKey(period) !== periodKey(this.period()))
          return;
        this.raw.update((state) => {
          if (result.kind === 'categories')
            return { ...state, categories: result.data, categoryError: result.error };
          const target = result.kind === 'current' ? period : result.period;
          const history = state.history.map((month) =>
            month.key === periodKey(target)
              ? {
                  ...month,
                  summary: result.data?.summary ?? null,
                  status: result.data ? ('ready' as const) : ('error' as const),
                }
              : month,
          );
          return result.kind === 'current'
            ? { ...state, history, data: result.data, loading: false, error: result.error }
            : { ...state, history };
        });
      });
  }
  setPeriod(period: ReportingPeriod): void {
    if (validPeriod(period) && periodKey(period) !== periodKey(this.period()))
      this.selected.set(period);
  }
  refresh(): void {
    this.refreshVersion.update((value) => value + 1);
  }
  private empty(owner: UserResponse | null, period: ReportingPeriod): DashboardState {
    return {
      owner,
      period,
      data: null,
      loading: !!owner && this.browser,
      error: '',
      categories: [],
      categoryError: '',
      history: sixMonths(period).map((target) => ({
        ...target,
        key: periodKey(target),
        label: periodLabel(target),
        status: validPeriod(target) ? 'loading' : 'unsupported',
        summary: null,
      })),
    };
  }
}
