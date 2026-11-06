import { DatePipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { AmountPipe, SignedPipe, formatAmount, formatDate, originLabel } from '../format';
import { FieldChange, HistoryEntry } from '../api';
import { Store } from '../store';

@Component({
  selector: 'app-history',
  imports: [AmountPipe, SignedPipe, DatePipe],
  template: `
    <section class="card history" aria-labelledby="history-title">
      <header class="card-head">
        <h2 id="history-title">
          <button
            type="button"
            class="disclosure"
            (click)="open.set(!open())"
            [attr.aria-expanded]="open()"
            aria-controls="history-body"
            data-testid="history-toggle"
          >
            <span aria-hidden="true">{{ open() ? '▾' : '▸' }}</span> Change history ({{ store.history().length }})
          </button>
        </h2>
      </header>
      @if (open()) {
        <div id="history-body" data-testid="history-body">
          @if (store.history().length === 0) {
            <p class="hint">No changes applied yet.</p>
          }
          <ol class="history-list">
            @for (e of store.history(); track e.id) {
              <li data-testid="history-entry">
                <div class="history-head">
                  <strong>{{ e.buyer }}</strong>
                  <span class="chip" data-testid="history-origin">{{ origin(e) }}</span>
                  <time [attr.datetime]="e.occurredAt">{{ e.occurredAt | date: 'd MMM y, HH:mm:ss' }}</time>
                </div>
                <ul class="history-changes">
                  @for (c of e.changes; track c.field) {
                    <li>{{ c.label }}: {{ show(c, c.before) }} → <strong>{{ show(c, c.after) }}</strong> {{ c.unit }}</li>
                  }
                </ul>
                @if (e.totalMarginChange) {
                  <p class="sub">
                    Estimated total margin {{ e.totalMarginBefore | amount }} → {{ e.totalMarginAfter | amount }} USD
                    ({{ e.totalMarginChange | signed }} USD)
                  </p>
                }
                @if (e.message) {
                  <blockquote class="quote" data-testid="history-message">{{ e.message }}</blockquote>
                }
                @if (e.corrections.length > 0) {
                  <p class="sub">User confirmations:</p>
                  <ul class="sub">
                    @for (c of e.corrections; track $index) {
                      <li>{{ c.description }}</li>
                    }
                  </ul>
                }
                @if (e.promptVersion || e.aiModel || e.originalExtraction) {
                  <details class="tech">
                    <summary>Technical details</summary>
                    @if (e.aiModel) {
                      <p class="sub">Model: {{ e.aiModel }}</p>
                    }
                    @if (e.promptVersion) {
                      <p class="sub">Extraction version: {{ e.promptVersion }}</p>
                    }
                    @if (e.originalExtraction) {
                      <p class="sub">Original extraction:</p>
                      <pre>{{ e.originalExtraction }}</pre>
                    }
                  </details>
                }
              </li>
            }
          </ol>
        </div>
      }
    </section>
  `,
})
export class History {
  protected readonly store = inject(Store);
  protected readonly open = signal(false);

  protected origin(e: HistoryEntry): string {
    return originLabel(e.origin, e.aiModel);
  }

  protected show(c: FieldChange, value: string | null): string {
    if (value === null) return '—';
    if (c.field === 'QUOTE_VALID_UNTIL') return formatDate(value);
    if (c.field === 'FINANCING_DAYS') return value;
    return formatAmount(value);
  }
}
