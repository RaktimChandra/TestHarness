// Consumer-driven contract for the Gate API, generated from the UI's real API client.
// Each interaction calls a function in client.js against a Pact mock server; the
// resulting contract (contracts/pacts/gate-ui-gate-api.json) is verified against the
// real provider by gate-api's PactProviderTest. CI fails if the committed file is stale.
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { afterEach, describe, expect, it } from 'vitest';
import { MatchersV3, PactV4 } from '@pact-foundation/pact';
import { createClient, session } from './client.js';

const { like, eachLike, regex, integer, string, datetime } = MatchersV3;
const here = path.dirname(fileURLToPath(import.meta.url));

const provider = new PactV4({
  consumer: 'gate-ui',
  provider: 'gate-api',
  dir: path.resolve(here, '../../../../contracts/pacts'),
  logLevel: 'warn',
});

const TIME = "yyyy-MM-dd'T'HH:mm:ssXXX";
const terminalTime = (example) => datetime(TIME, example);
const reference = (example = 'GA-PACT0001') => regex(/^GA-[0-9A-HJKMNP-TV-Z]{8}$/.source, example);
const bearer = (role) => regex(/^Bearer .+$/.source, `Bearer ${role}-token`);

const slot = {
  id: integer(9001),
  lane: string('PACT'),
  start: terminalTime('2026-12-01T10:00:00+05:30'),
  end: terminalTime('2026-12-01T10:30:00+05:30'),
  capacity: integer(4),
  booked: integer(0),
  remaining: integer(4),
};

const booking = (status = 'BOOKED') => ({
  reference: reference(),
  status: string(status),
  direction: regex('^(DROP_OFF|PICK_UP)$', 'DROP_OFF'),
  containerNumber: regex('^[A-Z]{3}[UJZ][0-9]{7}$', 'CSQU3054383'),
  driver: { username: string('driver1'), displayName: string('Anika Rao') },
  slot: {
    id: integer(9001),
    lane: string('PACT'),
    start: terminalTime('2026-12-01T10:00:00+05:30'),
    end: terminalTime('2026-12-01T10:30:00+05:30'),
  },
  createdAt: terminalTime('2026-11-20T09:00:00+05:30'),
  updatedAt: terminalTime('2026-11-20T09:00:00+05:30'),
});

const clientFor = (server) => createClient((url, init) => fetch(server.url + url, init));

describe('gate-ui -> gate-api contract', () => {
  afterEach(() => sessionStorage.clear());

  it('logs in', () =>
    provider
      .addInteraction()
      .given('the demo users exist')
      .uponReceiving('a login with valid credentials')
      .withRequest('POST', '/api/auth/login', (b) => b.jsonBody({ username: 'driver1', password: 'gate-demo-pass' }))
      .willRespondWith(200, (b) =>
        b.jsonBody({
          token: string('header.payload.signature'),
          tokenType: 'Bearer',
          expiresAt: terminalTime('2026-12-01T18:00:00+05:30'),
          user: { username: 'driver1', role: 'DRIVER', displayName: string('Anika Rao') },
        }),
      )
      .executeTest(async (server) => {
        const res = await clientFor(server).login('driver1', 'gate-demo-pass');
        expect(res.tokenType).toBe('Bearer');
        expect(res.user.role).toBe('DRIVER');
      }));

  it('rejects a wrong password with a problem code', () =>
    provider
      .addInteraction()
      .given('the demo users exist')
      .uponReceiving('a login with a wrong password')
      .withRequest('POST', '/api/auth/login', (b) => b.jsonBody({ username: 'driver1', password: 'wrong' }))
      .willRespondWith(401, (b) =>
        b.headers({ 'Content-Type': 'application/problem+json' }).jsonBody({ status: 401, code: 'UNAUTHENTICATED' }),
      )
      .executeTest(async (server) => {
        const err = await clientFor(server).login('driver1', 'wrong').catch((e) => e);
        expect(err.status).toBe(401);
        expect(err.code).toBe('UNAUTHENTICATED');
      }));

  it('lists the slots for a date', () =>
    provider
      .addInteraction()
      .given('slot 9001 exists with room')
      .uponReceiving('a request for the slots on a date')
      .withRequest('GET', '/api/slots', (b) => b.query({ date: '2026-12-01' }).headers({ Authorization: bearer('driver') }))
      .willRespondWith(200, (b) => b.jsonBody(eachLike(slot)))
      .executeTest(async (server) => {
        session.set('driver-token');
        const slots = await clientFor(server).listSlots('2026-12-01');
        expect(slots[0].remaining).toBe(4);
      }));

  it('books a slot', () =>
    provider
      .addInteraction()
      .given('slot 9001 exists with room')
      .uponReceiving('a booking for slot 9001')
      .withRequest('POST', '/api/bookings', (b) =>
        b.headers({ Authorization: bearer('driver') }).jsonBody({ slotId: 9001, containerNumber: 'CSQU3054383', direction: 'DROP_OFF' }),
      )
      .willRespondWith(201, (b) => b.jsonBody(like(booking())))
      .executeTest(async (server) => {
        session.set('driver-token');
        const b = await clientFor(server).createBooking(9001, 'CSQU3054383', 'DROP_OFF');
        expect(b.status).toBe('BOOKED');
      }));

  it('is told when the slot is full', () =>
    provider
      .addInteraction()
      .given('slot 9003 is full')
      .uponReceiving('a booking for a full slot')
      .withRequest('POST', '/api/bookings', (b) =>
        b.headers({ Authorization: bearer('driver') }).jsonBody({ slotId: 9003, containerNumber: 'MSCU1000070', direction: 'DROP_OFF' }),
      )
      .willRespondWith(409, (b) =>
        b.headers({ 'Content-Type': 'application/problem+json' }).jsonBody({ status: 409, code: 'SLOT_FULL', detail: string('Slot is full') }),
      )
      .executeTest(async (server) => {
        session.set('driver-token');
        const err = await clientFor(server).createBooking(9003, 'MSCU1000070', 'DROP_OFF').catch((e) => e);
        expect(err.code).toBe('SLOT_FULL');
      }));

  it("lists the driver's bookings", () =>
    provider
      .addInteraction()
      .given('driver1 has booking GA-PACT0001 in slot 9001')
      .uponReceiving('a request for my bookings')
      .withRequest('GET', '/api/bookings', (b) => b.headers({ Authorization: bearer('driver') }))
      .willRespondWith(200, (b) => b.jsonBody(eachLike(booking())))
      .executeTest(async (server) => {
        session.set('driver-token');
        const list = await clientFor(server).listBookings();
        expect(list[0].reference).toMatch(/^GA-/);
      }));

  it('reschedules a booking', () =>
    provider
      .addInteraction()
      .given('driver1 has booking GA-PACT0001 in slot 9001')
      .uponReceiving('a reschedule of GA-PACT0001 to slot 9002')
      .withRequest('PUT', '/api/bookings/GA-PACT0001/slot', (b) =>
        b.headers({ Authorization: bearer('driver') }).jsonBody({ slotId: 9002 }),
      )
      .willRespondWith(200, (b) => b.jsonBody(like({ ...booking(), slot: { ...booking().slot, id: integer(9002) } })))
      .executeTest(async (server) => {
        session.set('driver-token');
        const b = await clientFor(server).reschedule('GA-PACT0001', 9002);
        expect(b.slot.id).toBe(9002);
      }));

  it('cancels a booking', () =>
    provider
      .addInteraction()
      .given('driver1 has booking GA-PACT0001 in slot 9001')
      .uponReceiving('a cancellation of GA-PACT0001')
      .withRequest('POST', '/api/bookings/GA-PACT0001/cancel', (b) => b.headers({ Authorization: bearer('driver') }))
      .willRespondWith(200, (b) => b.jsonBody(like(booking('CANCELLED'))))
      .executeTest(async (server) => {
        session.set('driver-token');
        const b = await clientFor(server).cancel('GA-PACT0001');
        expect(b.status).toBe('CANCELLED');
      }));

  it('lets a clerk check a truck in', () =>
    provider
      .addInteraction()
      .given('booking GA-PACT0002 is inside its check-in window')
      .uponReceiving('a check-in of GA-PACT0002 by a clerk')
      .withRequest('POST', '/api/bookings/GA-PACT0002/check-in', (b) => b.headers({ Authorization: bearer('clerk') }))
      .willRespondWith(200, (b) =>
        b.jsonBody(like({ ...booking('CHECKED_IN'), reference: reference('GA-PACT0002'), checkedInAt: terminalTime('2026-11-20T09:05:00+05:30') })),
      )
      .executeTest(async (server) => {
        session.set('clerk-token');
        const b = await clientFor(server).checkIn('GA-PACT0002');
        expect(b.status).toBe('CHECKED_IN');
      }));
});
