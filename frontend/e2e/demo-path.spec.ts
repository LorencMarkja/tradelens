import { expect, test } from '@playwright/test';

const MAIN_MESSAGE =
  'Updated transport quote for lot CU-104, Buyer C: USD 140 per metric tonne, including handling. Valid until 8 November 2026.';

test.beforeEach(async ({ request }) => {
  const reset = await request.post('/api/demo/reset');
  expect(reset.ok()).toBeTruthy();
});

test('demo path: message → extraction → preview → apply → new ranking, persisted with source', async ({ page }) => {
  // 1. Open the app
  await page.goto('/');
  await expect(page.getByRole('heading', { name: 'TradeLens', level: 1 })).toBeVisible();
  await expect(page.getByText('Compare Sales Options')).toBeVisible();
  await expect(page).toHaveTitle('TradeLens — Compare Sales Options');
  await expect(page.getByTestId('synthetic-data')).toHaveText('Synthetic data');
  await expect(page.getByTestId('reset-button')).toHaveText('Reset scenario');
  await expect(page.getByTestId('scenario-date')).toHaveText('Scenario date: 5 Nov 2026');
  await expect(page.getByTestId('ai-mode')).toContainText('Demo extraction — no live AI call');

  // 2. Buyer B is highlighted
  await expect(page.getByTestId('offer-row-2').getByTestId('highest-badge')).toHaveText('Highest estimated margin');
  await expect(page.getByTestId('highest-badge')).toHaveCount(1);
  await expect(page.getByTestId('total-2')).toHaveText('90,000.00');

  // 3. Load the example message
  await page.getByTestId('load-example').click();
  await expect(page.getByTestId('message-input')).toHaveValue(MAIN_MESSAGE);

  // 4. Analyse it in demo mode
  await page.getByTestId('analyze-button').click();
  const review = page.getByTestId('proposal-review');
  await expect(review).toBeVisible();
  await expect(page.getByTestId('proposal-status')).toHaveText('Ready to apply');

  // 5. Old and new logistics cost, with the source quote
  const logistics = page.getByTestId('change-LOGISTICS');
  await expect(logistics.getByTestId('change-old')).toHaveText('180.00');
  await expect(logistics.getByTestId('change-new')).toHaveText('140.00');
  await expect(logistics.getByTestId('change-quote')).toContainText('USD 140 per metric tonne, including handling');
  await expect(page.getByTestId('message-input')).toHaveValue(MAIN_MESSAGE);

  // 6. Preview and increase; nothing changed yet
  await expect(page.getByTestId('impact-total')).toContainText('85,000.00');
  await expect(page.getByTestId('impact-total')).toContainText('105,000.00');
  await expect(page.getByTestId('impact-change')).toHaveText('+20,000.00 USD');
  await expect(page.getByTestId('best-switch')).toContainText('Highest estimated margin:');
  await expect(page.getByTestId('best-switch').locator('.old')).toHaveText('Buyer B');
  await expect(page.getByTestId('best-switch').locator('strong')).toHaveText('Buyer C');
  await expect(page.getByTestId('technical-details')).not.toHaveAttribute('open', /.*/);
  await expect(page.getByTestId('offer-row-2').getByTestId('highest-badge')).toBeVisible();
  await expect(page.getByTestId('total-3')).toHaveText('85,000.00');

  // 7. Apply
  await page.getByTestId('apply-button').click();
  await expect(page.getByTestId('applied-notice')).toContainText('Update applied');

  // 8. Buyer C is highlighted
  await expect(page.getByTestId('offer-row-3').getByTestId('highest-badge')).toBeVisible();
  await expect(page.getByTestId('highest-badge')).toHaveCount(1);
  await expect(page.getByTestId('total-3')).toHaveText('105,000.00');
  await expect(page.getByTestId('offer-row-3').getByTestId('updated-marker')).toBeVisible();

  // 9. Reload: the change is persisted
  await page.reload();
  await expect(page.getByTestId('offer-row-3').getByTestId('highest-badge')).toBeVisible();
  await expect(page.getByTestId('total-3')).toHaveText('105,000.00');
  await expect(page.getByTestId('offer-row-3').getByTestId('cell-logistics')).toContainText('140.00');

  // 10. History shows the source
  await page.getByTestId('history-toggle').click();
  const entry = page.getByTestId('history-entry').first();
  await expect(entry).toContainText('Buyer C');
  await expect(entry.getByTestId('history-origin')).toHaveText('Demo extraction — no live AI call');
  await expect(entry).toContainText('180.00 → 140.00');
  await expect(entry.getByTestId('history-message')).toHaveText(MAIN_MESSAGE);
});

test('ambiguous message requires confirmation before Apply is enabled', async ({ page }) => {
  await page.goto('/');
  await page.getByTestId('sample-select').selectOption('missing-currency');
  await page.getByTestId('load-example').click();
  await page.getByTestId('analyze-button').click();

  await expect(page.getByTestId('issue-CURRENCY_MISSING')).toBeVisible();
  await expect(page.getByTestId('apply-button')).toBeDisabled();

  await page.getByRole('button', { name: 'Confirm amount is in USD' }).click();
  await expect(page.getByTestId('proposal-status')).toHaveText('Ready to apply');
  await expect(page.getByTestId('apply-button')).toBeEnabled();

  await page.getByTestId('discard-button').click();
  await expect(page.getByTestId('proposal-review')).toHaveCount(0);
  await expect(page.getByTestId('total-2')).toHaveText('90,000.00');
});

test('calculation detail explains the margin and the logistics threshold', async ({ page }) => {
  await page.goto('/');
  await page.getByTestId('margin-3').click();
  const detail = page.getByTestId('offer-detail');
  await expect(detail).toContainText('9,000.00 × 5% × 40 / 360 = 50.00 USD/mt');
  await expect(detail).toContainText('9,400.00 − 9,000.00 − 180.00 − 50.00 = 170.00 USD/mt');
  await expect(page.getByTestId('threshold-text')).toHaveText(
    'Buyer C matches Buyer B at a logistics cost of USD 170/mt. Below this value, its estimated margin is higher.',
  );
  await expect(page.getByTestId('logistics-source')).toHaveText('Demo data');
});

test('layout fits 1440×900 without horizontal scrolling', async ({ page }) => {
  await page.goto('/');
  await expect(page.getByTestId('offers-table')).toBeVisible();
  const overflow = await page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth);
  expect(overflow).toBeLessThanOrEqual(0);
  const panel = await page.getByTestId('message-input').boundingBox();
  const table = await page.getByTestId('offers-table').boundingBox();
  expect(panel && panel.x + panel.width <= 1440).toBeTruthy();
  expect(table && table.y < 900).toBeTruthy();
  expect(panel && panel.y < 900).toBeTruthy();
});
