import { Component, OnInit, inject, signal } from '@angular/core';
import { History } from './components/history';
import { ImportPanel } from './components/import-panel';
import { OfferDetail } from './components/offer-detail';
import { OfferTable } from './components/offer-table';
import { AmountPipe, DayPipe } from './format';
import { Store } from './store';

@Component({
  selector: 'app-root',
  imports: [OfferTable, OfferDetail, ImportPanel, History, AmountPipe, DayPipe],
  template: `
    <header class="app-header">
      <div>
        <h1>TradeLens</h1>
        <p class="tagline">Compare Sales Options</p>
      </div>
      <div class="header-side">
        <span class="chip" data-testid="synthetic-data">Synthetic data</span>
        @if (store.scenario(); as s) {
          <span class="scenario-date" data-testid="scenario-date">Scenario date: {{ s.scenarioDate | day }}</span>
        }
        @if (!confirmReset()) {
          <button type="button" class="ghost" (click)="confirmReset.set(true)" data-testid="reset-button">Reset scenario</button>
        } @else {
          <span class="confirm" role="group" aria-label="Confirm reset">
            Restore the initial offers and clear the history?
            <button type="button" class="danger small" (click)="reset()" data-testid="confirm-reset">Reset</button>
            <button type="button" class="ghost small" (click)="confirmReset.set(false)">Cancel</button>
          </span>
        }
      </div>
    </header>

    @if (store.loadError(); as error) {
      <p class="notice error" role="alert">{{ error }}</p>
    }

    @if (store.scenario(); as s) {
      <main class="layout">
        <div class="main-col">
          <section class="card lot" aria-label="Lot and assumptions">
            <dl class="lot-facts">
              <div><dt>Lot</dt><dd>{{ s.lot.code }}</dd></div>
              <div><dt>Product</dt><dd>{{ s.lot.product }}</dd></div>
              <div><dt>Quantity</dt><dd>{{ s.lot.quantityMt }} mt</dd></div>
              <div><dt>Location</dt><dd>{{ s.lot.location }}</dd></div>
              <div><dt>Purchase cost</dt><dd>{{ s.lot.purchaseCostPerMt | amount }} USD/mt</dd></div>
              <div>
                <dt>Financing</dt>
                <dd>{{ s.lot.annualRatePercent }}% a year, {{ s.lot.financingConvention }}</dd>
              </div>
            </dl>
          </section>

          <section class="card" aria-labelledby="comparison-title">
            <h2 id="comparison-title">Offers for {{ s.lot.code }}</h2>
            <app-offer-table />
          </section>

          <app-offer-detail />
          <app-history />
        </div>

        <aside class="side-col">
          <app-import-panel />
        </aside>
      </main>
    } @else if (!store.loadError()) {
      <p class="hint loading" role="status">Loading scenario…</p>
    }
  `,
})
export class App implements OnInit {
  protected readonly store = inject(Store);
  protected readonly confirmReset = signal(false);

  ngOnInit(): void {
    void this.store.load();
  }

  protected async reset(): Promise<void> {
    this.confirmReset.set(false);
    await this.store.reset();
  }
}
