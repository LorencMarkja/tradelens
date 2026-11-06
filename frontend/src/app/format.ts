import { Pipe, PipeTransform } from '@angular/core';
import { Origin, ValueSource } from './api';

/**
 * Formats backend decimal strings for display without converting them to floating point.
 * "105000.00" -> "105,000.00"; "-5000.5" -> "-5,000.50".
 */
export function formatAmount(value: string | null | undefined, decimals = 2): string {
  if (value === null || value === undefined || value === '') {
    return '—';
  }
  const negative = value.startsWith('-');
  const unsigned = negative ? value.slice(1) : value;
  const [intPart, fracPart = ''] = unsigned.split('.');
  const grouped = intPart.replace(/\B(?=(\d{3})+(?!\d))/g, ',');
  const frac = decimals > 0 ? '.' + (fracPart + '0'.repeat(decimals)).slice(0, decimals) : '';
  return (negative ? '-' : '') + grouped + frac;
}

/** Signed variant for changes: "+20,000.00", "-5,000.00", "0.00". */
export function formatSigned(value: string | null | undefined): string {
  if (value === null || value === undefined || value === '') {
    return '—';
  }
  const formatted = formatAmount(value);
  return isPositive(value) ? '+' + formatted : formatted;
}

/** Compact form for prose: "170.00" -> "170", "170.50" -> "170.50". */
export function formatCompact(value: string | null | undefined): string {
  const formatted = formatAmount(value);
  return formatted.endsWith('.00') ? formatted.slice(0, -3) : formatted;
}

export function isPositive(value: string | null | undefined): boolean {
  return !!value && !value.startsWith('-') && /[1-9]/.test(value);
}

export function isNegative(value: string | null | undefined): boolean {
  return !!value && value.startsWith('-') && /[1-9]/.test(value);
}

export function absolute(value: string): string {
  return value.startsWith('-') ? value.slice(1) : value;
}

export function formatDate(iso: string | null | undefined): string {
  if (!iso) {
    return '—';
  }
  const [y, m, d] = iso.split('-').map(Number);
  const months = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];
  return `${d} ${months[m - 1]} ${y}`;
}

export const SOURCE_LABELS: Record<ValueSource, string> = {
  DEMO_DATA: 'Demo data',
  MANUAL_EDIT: 'Edited manually',
  MESSAGE_CONFIRMED: 'From message, confirmed',
};

export function originLabel(origin: Origin, model?: string | null): string {
  switch (origin) {
    case 'MANUAL':
      return 'Manual edit';
    case 'AI_DEMO':
      return 'Demo extraction — no live AI call';
    case 'AI_LIVE':
      return 'Live AI extraction' + (model ? ` (${model})` : '');
  }
}

@Pipe({ name: 'amount' })
export class AmountPipe implements PipeTransform {
  transform(value: string | null | undefined, decimals = 2): string {
    return formatAmount(value, decimals);
  }
}

@Pipe({ name: 'signed' })
export class SignedPipe implements PipeTransform {
  transform(value: string | null | undefined): string {
    return formatSigned(value);
  }
}

@Pipe({ name: 'compact' })
export class CompactPipe implements PipeTransform {
  transform(value: string | null | undefined): string {
    return formatCompact(value);
  }
}

@Pipe({ name: 'day' })
export class DayPipe implements PipeTransform {
  transform(value: string | null | undefined): string {
    return formatDate(value);
  }
}

@Pipe({ name: 'source' })
export class SourcePipe implements PipeTransform {
  transform(value: ValueSource): string {
    return SOURCE_LABELS[value] ?? value;
  }
}
