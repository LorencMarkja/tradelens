import { Component, computed, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { Store } from '../store';
import { ProposalReview } from './proposal-review';

const MAX_LENGTH = 5000;

@Component({
  selector: 'app-import-panel',
  imports: [ReactiveFormsModule, ProposalReview],
  template: `
    <section class="card panel" aria-labelledby="import-title">
      <header class="card-head">
        <h2 id="import-title">Import an update</h2>
        @if (store.scenario(); as s) {
          <span class="ai-mode" [class.live]="s.ai.mode === 'live'" data-testid="ai-mode">
            {{ s.ai.label }}
          </span>
        }
      </header>

      <div class="sample-row">
        <label for="sample" class="visually-hidden">Example message</label>
        <select id="sample" [formControl]="sample" [attr.disabled]="locked() ? true : null" data-testid="sample-select">
          @for (s of store.scenario()?.samples ?? []; track s.id) {
            <option [value]="s.id">{{ s.label }}</option>
          }
        </select>
        <button type="button" class="secondary" (click)="loadExample()" [disabled]="locked()" data-testid="load-example">
          Load example
        </button>
      </div>

      <label for="message">Message</label>
      <textarea
        id="message"
        rows="6"
        [formControl]="message"
        [maxlength]="max"
        [readonly]="locked()"
        placeholder="Paste a message about an existing offer for lot CU-104…"
        data-testid="message-input"
      ></textarea>
      <div class="counter" [class.over]="length() > max">{{ length() }} / 5,000 characters</div>
      @if (locked()) {
        <p class="hint">The message stays as analysed while the proposal is open.</p>
      }

      <button
        type="button"
        class="primary"
        (click)="analyze()"
        [disabled]="!canAnalyze()"
        data-testid="analyze-button"
      >
        {{ store.busy() === 'analyze' ? 'Analyzing…' : 'Analyze message' }}
      </button>

      @if (store.analyzeError(); as e) {
        <div class="notice error" role="alert" data-testid="analyze-error">
          <p>{{ e.message }}</p>
          <div class="choice">
            <button type="button" class="secondary small" (click)="analyze()" [disabled]="!canAnalyze()">Try again</button>
            <label>
              Enter manually for
              <select [formControl]="manualOffer">
                @for (o of store.scenario()?.offers ?? []; track o.id) {
                  <option [ngValue]="o.id">{{ o.buyer }}</option>
                }
              </select>
            </label>
            <button type="button" class="secondary small" (click)="enterManually()">Edit offer</button>
          </div>
        </div>
      }

      <app-proposal-review />

      @if (store.applied(); as a) {
        <div class="notice success" role="status" data-testid="applied-notice">
          @if (a.alreadyApplied) {
            <p>This update had already been applied. Nothing was changed again.</p>
          } @else {
            <p>
              <strong>Update applied.</strong> The comparison has been recalculated and the changed values are highlighted.
              The source is recorded in the history.
            </p>
          }
          <button type="button" class="secondary small" (click)="newMessage()">Import another update</button>
        </div>
      }
    </section>
  `,
})
export class ImportPanel {
  protected readonly store = inject(Store);
  protected readonly max = MAX_LENGTH;
  protected readonly message = new FormControl('', {
    nonNullable: true,
    validators: [Validators.required, Validators.maxLength(MAX_LENGTH)],
  });
  protected readonly sample = new FormControl('transport-update', { nonNullable: true });
  protected readonly manualOffer = new FormControl<number>(3, { nonNullable: true });

  private readonly text = toSignal(this.message.valueChanges, { initialValue: '' });
  protected readonly length = computed(() => this.text().length);
  protected readonly locked = computed(() => this.store.proposal() !== null);
  protected readonly canAnalyze = computed(
    () => this.text().trim().length > 0 && this.length() <= MAX_LENGTH && this.store.busy() === null && !this.locked(),
  );

  protected loadExample(): void {
    const s = this.store.scenario()?.samples.find((x) => x.id === this.sample.value);
    if (s) {
      this.message.setValue(s.message);
      this.store.analyzeError.set(null);
      this.store.clearApplied();
    }
  }

  protected async analyze(): Promise<void> {
    if (this.canAnalyze() && (await this.store.analyze(this.message.value))) {
      setTimeout(() =>
        document.querySelector('[data-testid="proposal-review"]')?.scrollIntoView({ behavior: 'smooth', block: 'start' }),
      );
    }
  }

  protected enterManually(): void {
    this.store.selectedOfferId.set(this.manualOffer.value);
    document.querySelector('[data-testid="manual-edit-form"]')?.scrollIntoView({ behavior: 'smooth' });
  }

  protected newMessage(): void {
    this.message.setValue('');
    this.store.clearApplied();
  }
}
