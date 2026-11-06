import { Component, computed, effect, inject, signal } from '@angular/core';
import { AbstractControl, ReactiveFormsModule, FormControl, FormGroup, ValidationErrors, Validators } from '@angular/forms';
import { ApiError } from '../api';
import { AmountPipe, DayPipe, SourcePipe, absolute, formatCompact, isNegative } from '../format';
import { Store } from '../store';

const AMOUNT = /^\d{1,9}(\.\d{1,6})?$/;

function positive(control: AbstractControl): ValidationErrors | null {
  const v = String(control.value ?? '').trim();
  return AMOUNT.test(v) && /[1-9]/.test(v) ? null : { positive: true };
}

@Component({
  selector: 'app-offer-detail',
  imports: [AmountPipe, DayPipe, SourcePipe, ReactiveFormsModule],
  template: `
    @if (offer(); as o) {
      @if (lot(); as lot) {
        <section class="card detail" data-testid="offer-detail" aria-labelledby="detail-title">
          <header class="card-head">
            <h2 id="detail-title">Calculation — {{ o.buyer }}</h2>
            <button type="button" class="ghost" (click)="close()">Close</button>
          </header>

          <div class="detail-grid">
            <div>
              <h3>Formula</h3>
              <ol class="formula">
                <li>
                  <span class="f-label">Financing cost</span>
                  @if (o.financingDays !== null) {
                    = {{ lot.financedCapitalPerMt | amount }} × {{ lot.annualRatePercent }}% × {{ o.financingDays }} /
                    {{ lot.dayCountBasis }} = <strong>{{ o.financingCostPerMt | amount }} USD/mt</strong>
                  } @else {
                    = <em>missing financing days</em>
                  }
                </li>
                <li>
                  <span class="f-label">Estimated margin</span>
                  @if (o.marginPerMt !== null) {
                    = {{ o.salePricePerMt | amount }} − {{ lot.purchaseCostPerMt | amount }} −
                    {{ o.logisticsCostPerMt | amount }} − {{ o.financingCostPerMt | amount }} =
                    <strong>{{ o.marginPerMt | amount }} USD/mt</strong>
                  } @else {
                    = <em>not computed: {{ o.statusReasons.join('; ') }}</em>
                  }
                  <span class="sub">sale price − purchase cost − logistics − financing</span>
                </li>
                <li>
                  <span class="f-label">Estimated total margin</span>
                  @if (o.totalMargin !== null) {
                    = margin × {{ lot.quantityMt }} mt = <strong>{{ o.totalMargin | amount }} USD</strong>
                    <span class="sub">Computed from the unrounded margin; values are rounded to cents only for display.</span>
                  } @else {
                    = <em>not computed</em>
                  }
                </li>
              </ol>

              <h3>Comparison threshold</h3>
              <p data-testid="threshold-text">{{ thresholdText() }}</p>
            </div>

            <div>
              <h3>Values used</h3>
              <table class="inputs">
                <tbody>
                  <tr>
                    <th scope="row">Sale price</th>
                    <td>{{ o.salePricePerMt | amount }} USD/mt</td>
                    <td><span class="source">{{ o.salePriceSource | source }}</span></td>
                  </tr>
                  <tr>
                    <th scope="row">Logistics (transport &amp; handling)</th>
                    <td>{{ o.logisticsCostPerMt | amount }} USD/mt</td>
                    <td><span class="source" data-testid="logistics-source">{{ o.logisticsSource | source }}</span></td>
                  </tr>
                  <tr>
                    <th scope="row">Financed days</th>
                    <td>{{ o.financingDays ?? '—' }}</td>
                    <td><span class="source">{{ o.financingDaysSource | source }}</span></td>
                  </tr>
                  <tr>
                    <th scope="row">Purchase cost</th>
                    <td>{{ lot.purchaseCostPerMt | amount }} USD/mt</td>
                    <td><span class="source">Demo data</span></td>
                  </tr>
                  <tr>
                    <th scope="row">Offer valid until</th>
                    <td>{{ o.validUntil | day }}</td>
                    <td></td>
                  </tr>
                  @if (o.logisticsQuoteExpiry) {
                    <tr>
                      <th scope="row">Logistics quote valid until</th>
                      <td>{{ o.logisticsQuoteExpiry | day }}</td>
                      <td></td>
                    </tr>
                  }
                </tbody>
              </table>
              @if (o.status !== 'RANKED') {
                <p class="notice warn">Excluded from the ranking: {{ o.statusReasons.join('; ') }}.</p>
              }
              <h3>Assumptions</h3>
              <ul class="assumptions">
                <li>Estimated margin = sale price − purchase cost − logistics − financing.</li>
                <li>Whole lot, same product and quality; amounts in USD per metric tonne; logistics paid by the seller.</li>
                <li>Financing: {{ lot.financingConvention }} on {{ lot.financedCapitalPerMt | amount }} USD/mt at {{ lot.annualRatePercent }}% a year.</li>
                <li>Financed days are the estimated capital immobilisation, not payment terms.</li>
                <li>Covers only the modelled costs: excludes taxes, hedging, credit risk and other costs. Not a recommendation.</li>
              </ul>
            </div>
          </div>

          <form class="edit" [formGroup]="form" (ngSubmit)="preview(o.id)" data-testid="manual-edit-form">
            <h3>Edit offer</h3>
            <p class="hint">Changes are previewed before they are saved.</p>
            <div class="edit-fields">
              <label>
                Sale price <span class="unit">USD/mt</span>
                <input formControlName="salePricePerMt" inputmode="decimal" [attr.aria-invalid]="invalid('salePricePerMt')" />
                @if (invalid('salePricePerMt')) {
                  <span class="field-error">Enter a price greater than zero.</span>
                }
              </label>
              <label>
                Logistics <span class="unit">USD/mt</span>
                <input formControlName="logisticsCostPerMt" inputmode="decimal" [attr.aria-invalid]="invalid('logisticsCostPerMt')" />
                @if (invalid('logisticsCostPerMt')) {
                  <span class="field-error">Enter a cost of zero or more.</span>
                }
              </label>
              <label>
                Financed days
                <input formControlName="financingDays" inputmode="numeric" [attr.aria-invalid]="invalid('financingDays')" />
                @if (invalid('financingDays')) {
                  <span class="field-error">Enter a whole number from 0 to 365.</span>
                }
              </label>
            </div>
            @if (formError()) {
              <p class="notice error" role="alert">{{ formError()!.message }}</p>
            }
            @if (proposalOpen()) {
              <p class="hint">Apply or discard the open proposal before editing.</p>
            }
            <button type="submit" class="secondary" [disabled]="form.invalid || proposalOpen() || store.busy() !== null">
              Preview change
            </button>
          </form>
        </section>
      }
    }
  `,
})
export class OfferDetail {
  protected readonly store = inject(Store);
  protected readonly offer = this.store.selectedOffer;
  protected readonly lot = computed(() => this.store.scenario()?.lot ?? null);
  protected readonly proposalOpen = computed(() => this.store.proposal() !== null);
  protected readonly formError = signal<ApiError | null>(null);

  protected readonly form = new FormGroup({
    salePricePerMt: new FormControl('', { nonNullable: true, validators: [Validators.required, positive] }),
    logisticsCostPerMt: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.pattern(AMOUNT)],
    }),
    financingDays: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.pattern(/^\d{1,3}$/), Validators.max(365)],
    }),
  });

  protected readonly thresholdText = computed(() => {
    const o = this.offer();
    if (!o) return '';
    const t = o.threshold;
    if (!t) {
      return o.salePricePerMt === null || o.financingDays === null
        ? 'Not available: sale price or financed days are missing.'
        : 'Not available: there is no other ranked offer to compare with.';
    }
    if (isNegative(t.logisticsCostPerMt)) {
      return `${o.buyer} does not reach ${t.competitorBuyer} even with zero logistics cost: it would remain USD ${formatCompact(absolute(t.logisticsCostPerMt))}/mt below.`;
    }
    return `${o.buyer} matches ${t.competitorBuyer} at a logistics cost of USD ${formatCompact(t.logisticsCostPerMt)}/mt. Below this value, its estimated margin is higher.`;
  });

  constructor() {
    effect(() => {
      const o = this.offer();
      this.formError.set(null);
      if (o) {
        this.form.reset({
          salePricePerMt: plain(o.salePricePerMt),
          logisticsCostPerMt: plain(o.logisticsCostPerMt),
          financingDays: o.financingDays === null ? '' : String(o.financingDays),
        });
      }
    });
  }

  protected invalid(name: keyof typeof this.form.controls): boolean {
    const c = this.form.controls[name];
    return c.invalid && (c.dirty || c.touched);
  }

  protected close(): void {
    this.store.selectedOfferId.set(null);
  }

  protected async preview(offerId: number): Promise<void> {
    if (this.form.invalid) return;
    const v = this.form.getRawValue();
    const error = await this.store.manual({
      offerId,
      salePricePerMt: v.salePricePerMt.trim(),
      logisticsCostPerMt: v.logisticsCostPerMt.trim(),
      financingDays: Number(v.financingDays),
    });
    this.formError.set(error);
  }
}

/** "9300.00" -> "9300" for editing. */
function plain(value: string | null): string {
  if (value === null) return '';
  return value.endsWith('.00') ? value.slice(0, -3) : value;
}
