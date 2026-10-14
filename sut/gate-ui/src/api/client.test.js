import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiError, createClient, session } from './client.js';

const ok = (body, status = 200) => ({ ok: true, status, text: async () => JSON.stringify(body) });
const fail = (body, status) => ({ ok: false, status, text: async () => JSON.stringify(body) });

describe('API client', () => {
  beforeEach(() => sessionStorage.clear());

  it('sends credentials and no token on login', async () => {
    const fetch = vi.fn().mockResolvedValue(ok({ token: 't' }));
    await createClient(fetch).login('driver1', 'pw');
    const [url, init] = fetch.mock.calls[0];
    expect(url).toBe('/api/auth/login');
    expect(init.method).toBe('POST');
    expect(JSON.parse(init.body)).toEqual({ username: 'driver1', password: 'pw' });
    expect(init.headers.Authorization).toBeUndefined();
  });

  it('adds the bearer token once signed in', async () => {
    session.set('abc');
    const fetch = vi.fn().mockResolvedValue(ok([]));
    await createClient(fetch).listSlots('2026-10-20');
    expect(fetch.mock.calls[0][0]).toBe('/api/slots?date=2026-10-20');
    expect(fetch.mock.calls[0][1].headers.Authorization).toBe('Bearer abc');
  });

  it('drops empty search filters', async () => {
    const fetch = vi.fn().mockResolvedValue(ok([]));
    await createClient(fetch).listBookings({ containerNumber: '', status: 'BOOKED', date: null });
    expect(fetch.mock.calls[0][0]).toBe('/api/bookings?status=BOOKED');
  });

  it('encodes references in paths', async () => {
    const fetch = vi.fn().mockResolvedValue(ok({}));
    const c = createClient(fetch);
    await c.reschedule('GA-1/2', 7);
    await c.cancel('GA-ABC');
    await c.checkIn('GA-ABC');
    expect(fetch.mock.calls.map((x) => [x[1].method, x[0]])).toEqual([
      ['PUT', '/api/bookings/GA-1%2F2/slot'],
      ['POST', '/api/bookings/GA-ABC/cancel'],
      ['POST', '/api/bookings/GA-ABC/check-in'],
    ]);
  });

  it('turns problem responses into ApiError with the code', async () => {
    const fetch = vi.fn().mockResolvedValue(fail({ code: 'SLOT_FULL', detail: 'Slot is full' }, 409));
    const err = await createClient(fetch).createBooking(1, 'CSQU3054383', 'DROP_OFF').catch((e) => e);
    expect(err).toBeInstanceOf(ApiError);
    expect(err.status).toBe(409);
    expect(err.code).toBe('SLOT_FULL');
    expect(err.message).toBe('Slot is full');
  });

  it('handles empty error bodies', async () => {
    const fetch = vi.fn().mockResolvedValue({ ok: false, status: 500, text: async () => '' });
    const err = await createClient(fetch).me().catch((e) => e);
    expect(err.message).toBe('Request failed with status 500');
    expect(err.code).toBe('UNKNOWN');
  });
});
