import { HttpErrorResponse } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { firstValueFrom, Observable } from 'rxjs';
import {
  Api,
  ApiError,
  ApplyResponse,
  HistoryEntry,
  ManualRequest,
  PatchRequest,
  ProposalField,
  ProposalResponse,
  Scenario,
} from './api';

type Busy = 'analyze' | 'manual' | 'patch' | 'apply' | 'discard' | 'reset' | null;

/** Single source of UI state. All numbers shown come from backend responses. */
@Injectable({ providedIn: 'root' })
export class Store {
  private readonly api = inject(Api);

  readonly scenario = signal<Scenario | null>(null);
  readonly loadError = signal<string | null>(null);
  readonly proposal = signal<ProposalResponse | null>(null);
  readonly applied = signal<ApplyResponse | null>(null);
  readonly history = signal<HistoryEntry[]>([]);
  readonly changedCells = signal<ReadonlySet<string>>(new Set());
  readonly selectedOfferId = signal<number | null>(null);
  readonly busy = signal<Busy>(null);
  readonly analyzeError = signal<ApiError | null>(null);
  readonly reviewError = signal<ApiError | null>(null);

  readonly selectedOffer = computed(() => {
    const id = this.selectedOfferId();
    return this.scenario()?.offers.find((o) => o.id === id) ?? null;
  });

  async load(): Promise<void> {
    try {
      this.scenario.set(await firstValueFrom(this.api.scenario()));
      this.loadError.set(null);
      await this.refreshHistory();
    } catch (e) {
      this.loadError.set(toApiError(e).message);
    }
  }

  async refreshHistory(): Promise<void> {
    this.history.set(await firstValueFrom(this.api.history()));
  }

  async analyze(message: string): Promise<boolean> {
    this.analyzeError.set(null);
    this.applied.set(null);
    const result = await this.run('analyze', this.api.analyze(message));
    if (isError(result)) {
      this.analyzeError.set(result);
      return false;
    }
    this.proposal.set(result);
    return true;
  }

  /** Returns an error for the manual edit form, or null on success. */
  async manual(request: ManualRequest): Promise<ApiError | null> {
    this.applied.set(null);
    const result = await this.run('manual', this.api.manual(request));
    if (isError(result)) {
      return result;
    }
    this.proposal.set(result);
    return null;
  }

  async patch(request: PatchRequest): Promise<void> {
    const p = this.proposal();
    if (!p) return;
    this.reviewError.set(null);
    const result = await this.run('patch', this.api.patch(p.proposal.id, request));
    if (isError(result)) {
      this.reviewError.set(result);
    } else {
      this.proposal.set(result);
    }
  }

  async apply(): Promise<void> {
    const p = this.proposal();
    if (!p) return;
    this.reviewError.set(null);
    const result = await this.run('apply', this.api.apply(p.proposal.id));
    if (isError(result)) {
      this.reviewError.set(result);
      return;
    }
    const cells = new Set<string>();
    if (result.offerId !== null) {
      for (const f of result.changedFields) {
        for (const key of cellKeys(f)) cells.add(`${result.offerId}:${key}`);
      }
    }
    this.changedCells.set(cells);
    this.scenario.set(result.scenario);
    this.applied.set(result);
    this.proposal.set(null);
    await this.refreshHistory();
  }

  async discard(): Promise<void> {
    const p = this.proposal();
    if (!p) return;
    const result = await this.run('discard', this.api.discard(p.proposal.id));
    if (isError(result)) {
      this.reviewError.set(result);
      return;
    }
    this.proposal.set(null);
    this.reviewError.set(null);
  }

  async reset(): Promise<void> {
    const result = await this.run('reset', this.api.reset());
    if (isError(result)) {
      this.loadError.set(result.message);
      return;
    }
    this.scenario.set(result);
    this.proposal.set(null);
    this.applied.set(null);
    this.analyzeError.set(null);
    this.reviewError.set(null);
    this.changedCells.set(new Set());
    this.selectedOfferId.set(null);
    await this.refreshHistory();
  }

  clearApplied(): void {
    this.applied.set(null);
  }

  private async run<T>(kind: Busy, request: Observable<T>): Promise<T | ApiError> {
    this.busy.set(kind);
    try {
      return await firstValueFrom(request);
    } catch (e) {
      return toApiError(e);
    } finally {
      this.busy.set(null);
    }
  }
}

/** Cells to highlight in the table after a field changes (derived values change too). */
function cellKeys(field: ProposalField): string[] {
  switch (field) {
    case 'SALE_PRICE':
      return ['salePrice', 'margin', 'total'];
    case 'LOGISTICS':
      return ['logistics', 'margin', 'total'];
    case 'FINANCING_DAYS':
      return ['days', 'financing', 'margin', 'total'];
    case 'QUOTE_VALID_UNTIL':
      return ['status'];
  }
}

function isError(value: unknown): value is ApiError {
  return typeof value === 'object' && value !== null && '__apiError' in value;
}

function toApiError(e: unknown): ApiError {
  const base = { __apiError: true } as ApiError & { __apiError: true };
  if (e instanceof HttpErrorResponse) {
    const body = e.error as Partial<ApiError> | null;
    if (body && typeof body === 'object' && body.code && body.message) {
      return { ...base, code: body.code, message: body.message, field: body.field };
    }
    if (e.status === 0) {
      return { ...base, code: 'NETWORK', message: 'The server cannot be reached. Check that the backend is running.' };
    }
    return { ...base, code: 'HTTP_' + e.status, message: `Unexpected server response (${e.status}).` };
  }
  return { ...base, code: 'UNKNOWN', message: 'Unexpected error.' };
}
