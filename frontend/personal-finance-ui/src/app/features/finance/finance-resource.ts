import { isPlatformBrowser } from '@angular/common';
import { computed, inject, PLATFORM_ID, Signal, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { catchError, EMPTY, map, Observable, of, switchMap } from 'rxjs';
import { AuthService } from '../../core/services/auth';
import { FinanceChanges } from '../../core/services/finance-changes';
import { crudError } from './finance-form';

export function financeResource<Q, T>(query: Signal<Q>, load: (query: Q) => Observable<T>) {
  const auth = inject(AuthService),
    changes = inject(FinanceChanges);
  const browser = isPlatformBrowser(inject(PLATFORM_ID));
  const refresh = signal(0);
  const key = computed(() => ({
    owner: auth.currentUser(),
    query: query(),
    version: changes.revision(),
    refresh: refresh(),
  }));
  type Key = ReturnType<typeof key>;
  const raw = signal<{ key: Key | null; data: T | null; loading: boolean; error: string }>({
    key: null,
    data: null,
    loading: false,
    error: '',
  });
  toObservable(key)
    .pipe(
      switchMap((request) => {
        raw.set({ key: request, data: null, loading: browser && !!request.owner, error: '' });
        if (!browser || !request.owner || !auth.isAuthenticated()) return EMPTY;
        return load(request.query).pipe(
          map((data) => ({ key: request, data, loading: false, error: '' })),
          catchError((error) =>
            of({ key: request, data: null, loading: false, error: crudError(error) }),
          ),
        );
      }),
      takeUntilDestroyed(),
    )
    .subscribe((value) => {
      if (value.key === key()) raw.set(value);
    });
  const state = computed(() =>
    raw().key === key()
      ? raw()
      : { key: key(), data: null, loading: browser && !!auth.currentUser(), error: '' },
  );
  return {
    state,
    data: computed(() => state().data),
    refresh: () => refresh.update((value) => value + 1),
  };
}
