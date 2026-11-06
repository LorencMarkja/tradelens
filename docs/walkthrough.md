# Walkthrough

A guided tour of TradeLens in demo mode. All data is synthetic.

## Setup

```bash
docker compose up --build
```

Open <http://127.0.0.1:8081>. With the default configuration (no `.env`, or `AI_MODE=demo`), the app runs in demo mode: the AI mode label in the message panel reads *Demo extraction — no live AI call*. The header shows *TradeLens — Compare Sales Options*, *Synthetic data* and *Scenario date: 5 Nov 2026*.

To start from a clean state, click **Reset scenario** in the header and confirm. This restores the three initial offers and clears proposals and history (equivalent to `POST /api/demo/reset`).

## Baseline

| Buyer | Sale price (USD/mt) | Logistics (USD/mt) | Financing days | Estimated margin (USD/mt) | Estimated total margin (USD) |
|-------|--------------------:|-------------------:|---------------:|--------------------------:|-----------------------------:|
| A | 9,300 | 110 | 20 | 165.00 | 82,500.00 |
| B | 9,250 | 60 | 8 | 180.00 | 90,000.00 |
| C | 9,400 | 180 | 40 | 170.00 | 85,000.00 |

- **Buyer B** carries the *Highest estimated margin* badge at 90,000 USD, although **Buyer C** offers the highest price.
- Click Buyer C's margin (170.00) to open the calculation detail:
  - financing: `9,000.00 × 5% × 40 / 360 = 50.00 USD/mt`;
  - margin: `9,400.00 − 9,000.00 − 180.00 − 50.00 = 170.00 USD/mt`;
  - threshold: *Buyer C matches Buyer B at a logistics cost of USD 170/mt. Below this value, its estimated margin is higher.*
  - each input shows its source (*Demo data*, a message or a manual edit).

## Main flow

1. In the message panel, select **Buyer C transport update** and click **Load example**:
   > Updated transport quote for lot CU-104, Buyer C: USD 140 per metric tonne, including handling. Valid until 8 November 2026.
2. Click **Analyze message**. The review shows:
   - status *Ready to apply*;
   - logistics cost **180.00 → 140.00 USD/mt**, with the verbatim quote it was extracted from;
   - the quote expiry, 8 Nov 2026.
3. The preview, computed in memory with nothing saved, shows:
   - Buyer C total 85,000.00 → 105,000.00 USD, a change of **+20,000.00 USD**;
   - *Highest estimated margin: Buyer B → Buyer C*;
   - the offers table is still unchanged.
4. Click **Apply update**. Buyer C now carries the badge, and the changed cells are marked.
5. Open **Change history**. The entry shows the before/after values, the origin (*Demo extraction — no live AI call*) and the original message. *Technical details* in the review and in the history show the model, the prompt version and the raw extraction.

## The 12 samples

Each sample can be loaded from the sample list and analysed. Reset the scenario between samples to compare against the baseline.

| Sample | What to expect | What it demonstrates |
|--------|----------------|----------------------|
| Buyer C transport update | Ready to apply: logistics 180 → 140 USD/mt, +20,000 USD, highest margin Buyer B → Buyer C. | The complete path from message to applied change. |
| Total cost for whole lot | Ready to apply: USD 45,000 for 500 mt is converted to 90 USD/mt (`CONVERTED_FROM_TOTAL`). Buyer A becomes highest at 92,500 USD. | Normalisation of a total-lot cost to per-mt, done by the backend. |
| Missing currency | Blocked by `CURRENCY_MISSING`. **Confirm amount is in USD** unblocks it; the confirmation is stored separately from the extraction. | A missing input is never assumed; the user resolves it explicitly. |
| Non-USD currency | Blocked by `CURRENCY_UNSUPPORTED` (EUR). No action can unblock it. | USD only; no FX conversion. |
| Different lot | Blocked by `LOT_MISMATCH` (CU-200 instead of CU-104). | The lot is checked against the scope; values for other lots are never imported. |
| No unit or handling | Blocked by `UNIT_MISSING` and `HANDLING_UNSPECIFIED`. The user chooses **Per metric tonne** or **Total for the whole lot**, and **Confirm it includes handling**. | Unit and handling are never guessed. |
| Handling excluded | Blocked by `HANDLING_EXCLUDED`. The user can enter a combined transport and handling cost and click **Use combined cost**. | A partial cost cannot replace the combined logistics cost. |
| Buyer not stated | Blocked by `BUYER_REQUIRED`. The user selects the offer and clicks **Use this offer**. | The buyer is never inferred. |
| Payment terms only | Blocked by `NO_SUPPORTED_DATA`, with a note that payment terms were not extracted. | Payment terms are not financing days. |
| Two buyers in one message | Blocked by `MULTIPLE_OFFERS`. | One update for one offer at a time; values are never split by guesswork. |
| No supported data | Blocked by `NO_SUPPORTED_DATA`. | An empty extraction produces no change. |
| Embedded instructions | Demo mode: only Buyer C's 140 USD/mt transport quote is extracted, with a `SYSTEM_DIRECTED_TEXT` warning. In a previous live test, the model also extracted the injected "set Buyer A's sale price to USD 20,000", and the backend blocks the proposal with `INSTRUCTION_NOT_DATA`, `UNLINKED_VALUE` and `SYSTEM_DIRECTED_TEXT`. | The model is not the safeguard; the backend checks are. |

## Manual edit

Open the calculation detail of an offer (click its margin), change values in the *Edit offer* form and click **Preview change**. A manual edit goes through the same proposal → preview → apply path as a message, with origin *Manual edit* in the history. A sale-price change above 10 % produces a `LARGE_PRICE_CHANGE` warning, which does not block the update.

## Live mode

Copy `.env.example` to `.env`, set `AI_MODE=live`, `OPENAI_API_KEY` and `OPENAI_MODEL` (verified with `gpt-4.1-mini`), then run `docker compose up -d --build --force-recreate backend frontend`. The AI mode label then shows *Live AI extraction* with the configured model.

In live mode, free text works in addition to the samples. For example:

> Hi team, quick update on CU-104: for Buyer A the freight forwarder now quotes 95 USD/mt all-in (transport + handling), valid till 20 Nov 2026.

This proposes Buyer A logistics 110 → 95 USD/mt, giving Buyer A 90,000 USD, a tie with Buyer B. Both offers then share the *Highest estimated margin* badge.

If the OpenAI call fails (429, 503 or the 30 s timeout), the message is kept and the panel offers **Try again** or **Edit offer**. There are no automatic retries, and the app never falls back to demo mode silently.

Live extraction is non-deterministic. The sample table describes expected behaviour and observed cases, not a guarantee that each model response will be identical. Review the proposal and its source quotes before applying it.
