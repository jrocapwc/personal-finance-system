import { FormControl, FormGroup } from '@angular/forms';
import { calendarDate, positiveMoney, dateRange, integer } from './finance-form';
describe('Finance form validation', () => {
  it.each(['0', '-1', '1.234', '1e2', 'NaN', '', '.', '1000000000000.01'])(
    'rejects invalid or unsupported money %s',
    (value) => expect(positiveMoney(new FormControl(value))).toBeTruthy(),
  );
  it.each(['0.01', '125.50', '1000000000000', '10'])(
    'accepts supported decimal money %s',
    (value) => expect(positiveMoney(new FormControl(value))).toBeNull(),
  );
  it.each(['2024-02-29', '2026-10-26'])('accepts a real calendar date %s', (value) =>
    expect(calendarDate(new FormControl(value))).toBeNull(),
  );
  it.each(['2026-02-29', '2026-04-31', '2026-13-01', 'bad'])(
    'rejects invalid calendar date %s',
    (value) => expect(calendarDate(new FormControl(value))).toBeTruthy(),
  );
  it('rejects reversed date ranges', () =>
    expect(
      dateRange(
        new FormGroup({
          startDate: new FormControl('2026-10-31'),
          endDate: new FormControl('2026-10-01'),
        }),
      ),
    ).toBeTruthy());
  it('rejects fractional thresholds and years', () =>
    expect(integer(new FormControl(80.5))).toBeTruthy());
});
