import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

/** Decimal values are strings computed by the backend; the frontend never calculates margins. */
export type Decimal = string;

export type ValueSource = 'DEMO_DATA' | 'MANUAL_EDIT' | 'MESSAGE_CONFIRMED';
export type OfferStatus = 'RANKED' | 'INCOMPLETE' | 'EXPIRED';
export type ProposalField = 'SALE_PRICE' | 'LOGISTICS' | 'FINANCING_DAYS' | 'QUOTE_VALID_UNTIL';
export type Origin = 'MANUAL' | 'AI_LIVE' | 'AI_DEMO';
export type ProposalStatus = 'NEEDS_REVIEW' | 'READY' | 'APPLIED' | 'DISCARDED';
export type Basis = 'PER_MT' | 'TOTAL_LOT' | 'NOT_STATED' | 'NOT_APPLICABLE';
export type Handling = 'INCLUDED' | 'EXCLUDED' | 'NOT_STATED' | 'NOT_APPLICABLE';
export type IssueAction = 'SELECT_BUYER' | 'CONFIRM_CURRENCY' | 'CHOOSE_BASIS' | 'CONFIRM_HANDLING' | 'ENTER_COMBINED_VALUE';

export interface AiInfo {
  mode: 'demo' | 'live';
  label: string;
  model?: string;
  promptVersion?: string;
}

export interface LotView {
  code: string;
  product: string;
  quantityMt: Decimal;
  location: string;
  currency: string;
  purchaseCostPerMt: Decimal;
  financedCapitalPerMt: Decimal;
  annualRatePercent: Decimal;
  dayCountBasis: number;
  financingConvention: string;
}

export interface ThresholdView {
  competitorOfferId: number;
  competitorBuyer: string;
  competitorMarginPerMt: Decimal;
  logisticsCostPerMt: Decimal;
}

export interface OfferView {
  id: number;
  buyer: string;
  version: number;
  salePricePerMt: Decimal | null;
  salePriceSource: ValueSource;
  logisticsCostPerMt: Decimal | null;
  logisticsSource: ValueSource;
  financingDays: number | null;
  financingDaysSource: ValueSource;
  validUntil: string;
  logisticsQuoteExpiry: string | null;
  financingCostPerMt: Decimal | null;
  marginPerMt: Decimal | null;
  totalMargin: Decimal | null;
  status: OfferStatus;
  statusReasons: string[];
  highest: boolean;
  rank: number | null;
  threshold: ThresholdView | null;
}

export interface SampleView {
  id: string;
  label: string;
  message: string;
}

export interface Scenario {
  scenarioDate: string;
  ai: AiInfo;
  lot: LotView;
  offers: OfferView[];
  highestOfferIds: number[];
  rankingNote: string | null;
  samples: SampleView[];
}

export interface Issue {
  code: string;
  severity: 'BLOCKING' | 'WARNING' | 'INFO';
  message: string;
  field?: ProposalField;
  action?: IssueAction;
}

export interface UserInput {
  currencyConfirmedUsd: boolean | null;
  basis: Basis | null;
  handlingConfirmedIncluded: boolean | null;
  combinedValue: string | null;
}

export interface ChangeView {
  field: ProposalField;
  label: string;
  unit: string;
  buyer?: string;
  extractedValue?: string;
  extractedCurrency?: string;
  extractedBasis?: Basis;
  extractedHandling?: Handling;
  quote?: string;
  userInput?: UserInput;
  currentValue?: string;
  proposedValue?: string;
  note?: string;
}

export interface Correction {
  at: string;
  field: ProposalField | null;
  kind: string;
  description: string;
}

export interface ProposalView {
  id: string;
  origin: Origin;
  status: ProposalStatus;
  message?: string;
  offerId?: number;
  buyer?: string;
  buyerSelectable: boolean;
  baseOfferVersion?: number;
  createdAt: string;
  aiModel?: string;
  promptVersion?: string;
  lotReference?: string;
  originalExtraction?: string;
  changes: ChangeView[];
  corrections: Correction[];
}

export interface Metrics {
  financingCostPerMt: Decimal | null;
  marginPerMt: Decimal | null;
  totalMargin: Decimal | null;
  status: OfferStatus;
  highest: boolean;
  rank: number | null;
}

export interface ImpactView {
  offerId: number;
  buyer: string;
  before: Metrics;
  after: Metrics;
  marginPerMtChange: Decimal | null;
  totalMarginChange: Decimal | null;
}

export interface ProposalResponse {
  proposal: ProposalView;
  canApply: boolean;
  issues: Issue[];
  impact?: ImpactView;
  comparisonBefore?: OfferView[];
  comparisonAfter?: OfferView[];
}

export interface ApplyResponse {
  proposalId: string;
  alreadyApplied: boolean;
  changeEventId: number | null;
  offerId: number | null;
  changedFields: ProposalField[];
  scenario: Scenario;
}

export interface FieldChange {
  field: ProposalField;
  label: string;
  unit: string;
  before: string | null;
  after: string | null;
}

export interface HistoryEntry {
  id: number;
  proposalId: string;
  occurredAt: string;
  offerId: number;
  buyer: string;
  origin: Origin;
  changes: FieldChange[];
  message?: string;
  originalExtraction?: string;
  corrections: Correction[];
  aiModel?: string;
  promptVersion?: string;
  totalMarginBefore?: Decimal;
  totalMarginAfter?: Decimal;
  totalMarginChange?: Decimal;
}

export interface ApiError {
  code: string;
  message: string;
  field?: string;
}

export interface ManualRequest {
  offerId: number;
  salePricePerMt?: string;
  logisticsCostPerMt?: string;
  financingDays?: number;
}

export interface ChangeCorrection {
  field: ProposalField;
  confirmCurrencyUsd?: boolean;
  basis?: Basis;
  confirmHandlingIncluded?: boolean;
  combinedValue?: string;
}

export interface PatchRequest {
  offerId?: number;
  changes?: ChangeCorrection[];
}

@Injectable({ providedIn: 'root' })
export class Api {
  private readonly http = inject(HttpClient);

  scenario(): Observable<Scenario> {
    return this.http.get<Scenario>('/api/scenario');
  }

  analyze(message: string): Observable<ProposalResponse> {
    return this.http.post<ProposalResponse>('/api/proposals/analyze', { message });
  }

  manual(request: ManualRequest): Observable<ProposalResponse> {
    return this.http.post<ProposalResponse>('/api/proposals/manual', request);
  }

  patch(id: string, request: PatchRequest): Observable<ProposalResponse> {
    return this.http.patch<ProposalResponse>(`/api/proposals/${id}`, request);
  }

  apply(id: string): Observable<ApplyResponse> {
    return this.http.post<ApplyResponse>(`/api/proposals/${id}/apply`, null);
  }

  discard(id: string): Observable<ProposalResponse> {
    return this.http.post<ProposalResponse>(`/api/proposals/${id}/discard`, null);
  }

  history(): Observable<HistoryEntry[]> {
    return this.http.get<HistoryEntry[]>('/api/history');
  }

  reset(): Observable<Scenario> {
    return this.http.post<Scenario>('/api/demo/reset', null);
  }
}
