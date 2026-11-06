import { Component, computed, inject } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { Basis, ChangeCorrection, ChangeView, OfferView, ProposalField } from '../api';
import { AmountPipe, SignedPipe, formatAmount, formatDate, isNegative, isPositive, originLabel } from '../format';
import { Store } from '../store';

@Component({
  selector: 'app-proposal-review',
  imports: [AmountPipe, SignedPipe, ReactiveFormsModule],
  template: `
    @if (store.proposal(); as r) {
      <section class="review" data-testid="proposal-review" aria-labelledby="review-title">
        <header class="review-head">
          <h3 id="review-title">
            Proposed update{{ r.proposal.buyer ? ' — ' + r.proposal.buyer : '' }}
          </h3>
          @switch (r.proposal.status) {
            @case ('READY') {
              <span class="chip ok" data-testid="proposal-status">Ready to apply</span>
            }
            @case ('NEEDS_REVIEW') {
              <span class="chip warn" data-testid="proposal-status">Needs your input</span>
            }
            @default {
              <span class="chip" data-testid="proposal-status">{{ r.proposal.status }}</span>
            }
          }
        </header>
        <p class="origin">{{ origin() }}</p>

        @if (r.proposal.changes.length > 0) {
          <ul class="changes">
            @for (c of r.proposal.changes; track c.field) {
              <li [attr.data-testid]="'change-' + c.field">
                <div class="change-label">{{ c.label }}</div>
                @if (c.proposedValue) {
                  <div class="change-values">
                    <span class="old" data-testid="change-old">{{ show(c, c.currentValue) }}</span>
                    <span aria-hidden="true">→</span>
                    <span class="visually-hidden">changes to</span>
                    <strong data-testid="change-new">{{ show(c, c.proposedValue) }}</strong>
                    <span class="unit">{{ c.unit }}</span>
                  </div>
                } @else {
                  <div class="change-values pending">
                    Extracted: {{ c.extractedValue }} {{ c.extractedCurrency ?? '(currency not stated)' }}
                    {{ basisText(c.extractedBasis) }}
                  </div>
                }
                @if (c.quote) {
                  <blockquote class="quote" data-testid="change-quote">“{{ c.quote }}”</blockquote>
                }
                @if (c.note) {
                  <p class="sub">{{ c.note }}</p>
                }
              </li>
            }
          </ul>
        }

        @if (blocking().length > 0) {
          <div class="issues blocking" role="alert">
            <h4>To resolve before applying</h4>
            <ul>
              @for (i of blocking(); track $index) {
                <li [attr.data-testid]="'issue-' + i.code">
                  <p>{{ i.message }}</p>
                  @switch (i.action) {
                    @case ('CONFIRM_CURRENCY') {
                      <button type="button" class="secondary small" (click)="correct(i.field!, { confirmCurrencyUsd: true })" [disabled]="busy()">
                        Confirm amount is in USD
                      </button>
                    }
                    @case ('CHOOSE_BASIS') {
                      <div class="choice">
                        <button type="button" class="secondary small" (click)="correct(i.field!, { basis: 'PER_MT' })" [disabled]="busy()">Per metric tonne</button>
                        <button type="button" class="secondary small" (click)="correct(i.field!, { basis: 'TOTAL_LOT' })" [disabled]="busy()">Total for the whole lot</button>
                      </div>
                    }
                    @case ('CONFIRM_HANDLING') {
                      <button type="button" class="secondary small" (click)="correct(i.field!, { confirmHandlingIncluded: true })" [disabled]="busy()">
                        Confirm it includes handling
                      </button>
                    }
                    @case ('ENTER_COMBINED_VALUE') {
                      <div class="choice">
                        <label>
                          Combined transport &amp; handling <span class="unit">USD/mt</span>
                          <input [formControl]="combined" inputmode="decimal" data-testid="combined-input" />
                        </label>
                        <button type="button" class="secondary small" (click)="correct(i.field!, { combinedValue: combined.value.trim() })" [disabled]="busy() || !combined.value.trim()">
                          Use combined cost
                        </button>
                      </div>
                    }
                    @case ('SELECT_BUYER') {
                      <div class="choice">
                        <label>
                          Offer
                          <select [formControl]="buyer" data-testid="buyer-select">
                            <option [ngValue]="null" disabled>Select…</option>
                            @for (o of store.scenario()?.offers ?? []; track o.id) {
                              <option [ngValue]="o.id">{{ o.buyer }}</option>
                            }
                          </select>
                        </label>
                        <button type="button" class="secondary small" (click)="selectBuyer()" [disabled]="busy() || buyer.value === null">
                          Use this offer
                        </button>
                      </div>
                    }
                  }
                </li>
              }
            </ul>
          </div>
        }

        @if (notes().length > 0) {
          <ul class="issues notes">
            @for (i of notes(); track $index) {
              <li [class.warning]="i.severity === 'WARNING'" [attr.data-testid]="'issue-' + i.code">
                <span class="sev">{{ i.severity === 'WARNING' ? 'Warning' : 'Note' }}:</span> {{ i.message }}
              </li>
            }
          </ul>
        }

        @if (r.impact; as impact) {
          <div class="impact-callout" [class.pos]="up()" [class.neg]="down()" data-testid="impact">
            <p class="impact-caption">Preview — nothing is saved until you apply</p>
            <p class="impact-big" data-testid="impact-change">{{ impact.totalMarginChange | signed }} USD</p>
            <p class="sub">
              {{ up() ? 'Increase' : down() ? 'Decrease' : 'No change' }} in estimated total margin for {{ impact.buyer }}
            </p>
            <p class="best-switch" data-testid="best-switch">
              @if (bestChanges()) {
                Highest estimated margin:
                <span class="old">{{ bestBefore() }}</span>
                <span aria-hidden="true">→</span><span class="visually-hidden">becomes</span>
                <strong>{{ bestAfter() }}</strong>
              } @else if (bestAfter()) {
                Highest estimated margin stays with <strong>{{ bestAfter() }}</strong>
              } @else {
                No offer would be ranked after this update
              }
            </p>
          </div>
          <table class="impact">
            <tbody>
              <tr>
                <th scope="row">Estimated total margin</th>
                <td data-testid="impact-total">
                  {{ impact.before.totalMargin | amount }} → <strong>{{ impact.after.totalMargin | amount }}</strong>
                  <span class="unit">USD</span>
                </td>
              </tr>
              <tr>
                <th scope="row">Estimated margin per mt</th>
                <td>
                  {{ impact.before.marginPerMt | amount }} → <strong>{{ impact.after.marginPerMt | amount }}</strong>
                  <span class="unit">USD/mt</span>
                </td>
              </tr>
            </tbody>
          </table>
          @if (impact.after.status !== 'RANKED') {
            <p class="notice warn">After this update {{ impact.buyer }} would be excluded from the ranking.</p>
          }
        }

        @if (r.proposal.corrections.length > 0) {
          <div class="corrections">
            <h4>Your confirmations</h4>
            <ul>
              @for (c of r.proposal.corrections; track $index) {
                <li>{{ c.description }}</li>
              }
            </ul>
          </div>
        }

        @if (store.reviewError(); as e) {
          <p class="notice error" role="alert" data-testid="review-error">{{ e.message }}</p>
        }

        @if (r.proposal.promptVersion || r.proposal.aiModel || r.proposal.originalExtraction) {
          <details class="tech" data-testid="technical-details">
            <summary>Technical details</summary>
            @if (r.proposal.aiModel) {
              <p class="sub">Model: {{ r.proposal.aiModel }}</p>
            }
            @if (r.proposal.promptVersion) {
              <p class="sub">Extraction version: {{ r.proposal.promptVersion }}</p>
            }
            @if (r.proposal.originalExtraction) {
              <p class="sub">Original extraction:</p>
              <pre>{{ r.proposal.originalExtraction }}</pre>
            }
          </details>
        }

        <div class="actions">
          <button type="button" class="primary" (click)="store.apply()" [disabled]="!r.canApply || busy()" data-testid="apply-button">
            {{ store.busy() === 'apply' ? 'Applying…' : 'Apply update' }}
          </button>
          <button type="button" class="ghost" (click)="store.discard()" [disabled]="busy()" data-testid="discard-button">Discard</button>
        </div>
      </section>
    }
  `,
})
export class ProposalReview {
  protected readonly store = inject(Store);
  protected readonly combined = new FormControl('', { nonNullable: true });
  protected readonly buyer = new FormControl<number | null>(null);

  protected readonly busy = computed(() => this.store.busy() !== null);
  protected readonly blocking = computed(() => this.store.proposal()?.issues.filter((i) => i.severity === 'BLOCKING') ?? []);
  protected readonly notes = computed(() => this.store.proposal()?.issues.filter((i) => i.severity !== 'BLOCKING') ?? []);
  protected readonly origin = computed(() => {
    const p = this.store.proposal()?.proposal;
    return p ? originLabel(p.origin) : '';
  });
  protected readonly up = computed(() => isPositive(this.store.proposal()?.impact?.totalMarginChange));
  protected readonly down = computed(() => isNegative(this.store.proposal()?.impact?.totalMarginChange));
  protected readonly bestBefore = computed(() => bestNames(this.store.proposal()?.comparisonBefore));
  protected readonly bestAfter = computed(() => bestNames(this.store.proposal()?.comparisonAfter));
  protected readonly bestChanges = computed(
    () => !!this.bestBefore() && !!this.bestAfter() && this.bestBefore() !== this.bestAfter(),
  );

  protected show(c: ChangeView, value: string | undefined): string {
    if (value === undefined || value === null) return c.field === 'QUOTE_VALID_UNTIL' ? 'not set' : '—';
    if (c.field === 'QUOTE_VALID_UNTIL') return formatDate(value);
    if (c.field === 'FINANCING_DAYS') return value;
    return formatAmount(value);
  }

  protected basisText(b: Basis | undefined): string {
    switch (b) {
      case 'PER_MT':
        return 'per mt';
      case 'TOTAL_LOT':
        return 'for the whole lot';
      case 'NOT_STATED':
        return '(unit not stated)';
      default:
        return '';
    }
  }

  protected correct(field: ProposalField, correction: Omit<ChangeCorrection, 'field'>): void {
    void this.store.patch({ changes: [{ field, ...correction }] });
  }

  protected selectBuyer(): void {
    const id = this.buyer.value;
    if (id !== null) {
      void this.store.patch({ offerId: id });
    }
  }
}

function bestNames(offers: OfferView[] | undefined): string {
  return (offers ?? []).filter((o) => o.highest).map((o) => o.buyer).join(' and ');
}
