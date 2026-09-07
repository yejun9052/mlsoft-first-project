import { describe, expect, it } from 'vitest';

import {
  NEXT_CYCLE_RESERVATION_LABEL,
  formatNextCycleReservation,
  hasNextCycleReservation,
} from './leaveSummary.js';

describe('hasNextCycleReservation', () => {
  it('0이면 false다', () => {
    expect(hasNextCycleReservation('0.0')).toBe(false);
    expect(hasNextCycleReservation(0)).toBe(false);
    expect(hasNextCycleReservation(undefined)).toBe(false);
  });

  it('0보다 크면 true다', () => {
    expect(hasNextCycleReservation('3.0')).toBe(true);
  });
});

describe('formatNextCycleReservation', () => {
  it('라벨과 일수를 합쳐 같은 문구를 만든다', () => {
    expect(formatNextCycleReservation('3.0')).toBe(`${NEXT_CYCLE_RESERVATION_LABEL} 3일`);
  });
});
