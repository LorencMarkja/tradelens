import { Component, computed, inject } from '@angular/core';
import { OfferView } from '../api';
import { AmountPipe, DayPipe } from '../format';
import { Store } from '../store';

@Component({
  selector: 'app-offer-table',
  imports: [AmountPipe, DayPipe],
  template: `
    @if (scenario(); as s) {
      <table class="offers" data-testid="offers-table">
        <caption class="visually-hidden">Comparison of sale offers for lot {{ s.lot.code }}</caption>
        <thead>
          <tr>
            <th scope="col" class="left">Buyer</th>
            <th scope="col">Sale price<br /><span class="unit">USD/mt</span></th>
            <th scope="col">Logistics<br /><span class="unit">USD/mt</span></th>
            <th scope="col">Financed<br /><span class="unit">days</span></th>
            <th scope="col">Financing cost<br /><span class="unit">USD/mt</span></th>
            <th scope="col">Est. margin<br /><span class="unit">USD/mt</span></th>
            <th scope="col" class="total">Est. total margin<br /><span class="unit">USD</span></th>
            <th scope="col" class="left">Status</th>
          </tr>
        </thead>
        <tbody>
          @for (o of s.offers; track o.id) {
            <tr
              [class.highest]="o.highest"
              [class.excluded]="o.status !== 'RANKED'"
              [class.selected]="store.selectedOfferId() === o.id"
              [attr.data-testid]="'offer-row-' + o.id"
            >
              <th scope="row" class="left buyer">
                <span class="buyer-name">{{ o.buyer }}</span>
                @if (o.highest) {
                  <span class="badge best" data-testid="highest-badge">Highest estimated margin</span>
                }
              </th>
              <td [class.changed]="changed(o, 'salePrice')">{{ o.salePricePerMt | amount }}</td>
              <td [class.changed]="changed(o, 'logistics')" data-testid="cell-logistics">
                {{ o.logisticsCostPerMt | amount }}
                @if (o.logisticsQuoteExpiry) {
                  <span class="sub">quote until {{ o.logisticsQuoteExpiry | day }}</span>
                }
              </td>
              <td [class.changed]="changed(o, 'days')">{{ o.financingDays ?? '—' }}</td>
              <td [class.changed]="changed(o, 'financing')">{{ o.financingCostPerMt | amount }}</td>
              <td [class.changed]="changed(o, 'margin')">
                <button
                  type="button"
                  class="link-number"
                  (click)="select(o)"
                  [attr.aria-expanded]="store.selectedOfferId() === o.id"
                  [attr.aria-label]="'Show calculation for ' + o.buyer"
                  [attr.data-testid]="'margin-' + o.id"
                >
                  {{ o.marginPerMt | amount }}
                </button>
              </td>
              <td [class.changed]="changed(o, 'total')" class="total" [attr.data-testid]="'total-' + o.id">
                {{ o.totalMargin | amount }}
              </td>
              <td class="left status-cell" [class.changed]="changed(o, 'status')">
                @switch (o.status) {
                  @case ('RANKED') {
                    <span class="chip ok">Ranked #{{ o.rank }}</span>
                  }
                  @case ('INCOMPLETE') {
                    <span class="chip warn">Incomplete</span>
                  }
                  @case ('EXPIRED') {
                    <span class="chip warn">Quote expired</span>
                  }
                }
                @if (o.status !== 'RANKED') {
                  <span class="sub">Excluded: {{ o.statusReasons.join('; ') }}</span>
                }
                @if (anyChanged(o)) {
                  <span class="sub updated-marker" data-testid="updated-marker">Updated</span>
                }
              </td>
            </tr>
          }
        </tbody>
      </table>
      <p class="hint">Select a margin to see how it is calculated or to edit the offer.</p>
      @if (s.rankingNote) {
        <p class="hint">{{ s.rankingNote }}</p>
      }
    }
  `,
})
export class OfferTable {
  protected readonly store = inject(Store);
  protected readonly scenario = this.store.scenario;
  private readonly cells = computed(() => this.store.changedCells());

  protected changed(o: OfferView, key: string): boolean {
    return this.cells().has(`${o.id}:${key}`);
  }

  protected anyChanged(o: OfferView): boolean {
    for (const k of this.cells()) if (k.startsWith(`${o.id}:`)) return true;
    return false;
  }

  protected select(o: OfferView): void {
    this.store.selectedOfferId.set(this.store.selectedOfferId() === o.id ? null : o.id);
  }
}
