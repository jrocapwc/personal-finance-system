import { PLATFORM_ID, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { HttpErrorResponse, provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter, Router } from '@angular/router';
import { Subject } from 'rxjs';
import { AuthService } from '../../core/services/auth';
import { FinanceChanges } from '../../core/services/finance-changes';
import { testCredentials, testLogin } from '../../core/auth.testing';
import { financeResource } from './finance-resource';

describe('Session-scoped finance resources', () => {
  let http: HttpTestingController;
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
    vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
  });
  afterEach(() => {
    try {
      http.verify({ ignoreCancelled: true });
    } finally {
      TestBed.resetTestingModule();
    }
  });
  function login() {
    TestBed.inject(AuthService).login(testCredentials).subscribe();
    http.expectOne('/api/v1/auth/login').flush({ ...testLogin, user: { ...testLogin.user } });
  }
  function resource() {
    const query = signal('first');
    const requests: Subject<string[]>[] = [];
    const state = TestBed.runInInjectionContext(() =>
      financeResource(query, () => {
        const request = new Subject<string[]>();
        requests.push(request);
        return request;
      }),
    );
    TestBed.tick();
    return { query, state, requests };
  }
  it('never requests private data without a user', () => {
    expect(resource().requests).toHaveLength(0);
  });
  it('cancels old queries and rejects responses racing a query change', () => {
    login();
    const { query, state, requests } = resource();
    query.set('second');
    requests[0].next(['private old result']);
    expect(state.data()).toBeNull();
    TestBed.tick();
    expect(requests[0].observed).toBe(false);
    requests[1].next(['new']);
    expect(state.data()).toEqual(['new']);
  });
  it('clears loaded records synchronously on logout', () => {
    login();
    const { state, requests } = resource();
    requests[0].next(['private']);
    TestBed.inject(AuthService).logout();
    expect(state.data()).toBeNull();
    TestBed.tick();
    expect(state.data()).toBeNull();
  });
  it('cancels pending reads and rejects late logout responses', () => {
    login();
    const { state, requests } = resource();
    TestBed.inject(AuthService).logout();
    requests[0].next(['private']);
    expect(state.data()).toBeNull();
    TestBed.tick();
    expect(requests[0].observed).toBe(false);
  });
  it('never reuses a same-user previous session result', () => {
    login();
    const { state, requests } = resource();
    requests[0].next(['old session']);
    login();
    expect(state.data()).toBeNull();
    TestBed.tick();
    requests[1].next(['fresh session']);
    expect(state.data()).toEqual(['fresh session']);
  });
  it('requeries active resources after a successful finance change', () => {
    login();
    const { state, requests } = resource();
    requests[0].next(['old']);
    TestBed.inject(FinanceChanges).track()();
    expect(state.data()).toBeNull();
    TestBed.tick();
    requests[1].next(['updated']);
    expect(state.data()).toEqual(['updated']);
  });
  it('shows failures and supports retry without fabricated data', () => {
    login();
    const { state, requests } = resource();
    requests[0].error(new HttpErrorResponse({ status: 503 }));
    expect(state.data()).toBeNull();
    expect(state.state().error).toContain('could not be completed');
    state.refresh();
    TestBed.tick();
    requests[1].next([]);
    expect(state.data()).toEqual([]);
  });
  it('does not request private finance data during SSR', () => {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      providers: [
        { provide: PLATFORM_ID, useValue: 'server' },
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
    });
    http = TestBed.inject(HttpTestingController);
    login();
    const { state, requests } = resource();
    expect(requests).toHaveLength(0);
    expect(state.data()).toBeNull();
  });
});
