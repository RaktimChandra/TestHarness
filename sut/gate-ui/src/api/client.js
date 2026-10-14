// The UI's only gateway to the Gate Appointment API. Keeping every call here makes this
// module the consumer contract surface (Phase 2 contract tests are derived from it).
const TOKEN_KEY = 'gate.token';

export class ApiError extends Error {
  constructor(status, body) {
    super(body?.detail || `Request failed with status ${status}`);
    this.status = status;
    this.code = body?.code ?? 'UNKNOWN';
    this.errors = body?.errors ?? [];
  }
}

export const session = {
  get token() {
    return sessionStorage.getItem(TOKEN_KEY);
  },
  set(token) {
    sessionStorage.setItem(TOKEN_KEY, token);
  },
  clear() {
    sessionStorage.removeItem(TOKEN_KEY);
  },
};

async function request(method, path, body, fetchImpl = fetch) {
  const headers = { Accept: 'application/json' };
  if (body !== undefined) headers['Content-Type'] = 'application/json';
  const token = session.token;
  if (token) headers.Authorization = `Bearer ${token}`;
  const res = await fetchImpl(path, {
    method,
    headers,
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  const text = await res.text();
  const data = text ? JSON.parse(text) : null;
  if (!res.ok) throw new ApiError(res.status, data);
  return data;
}

export function createClient(fetchImpl = fetch) {
  const call = (method, path, body) => request(method, path, body, fetchImpl);
  return {
    login: (username, password) => call('POST', '/api/auth/login', { username, password }),
    me: () => call('GET', '/api/auth/me'),
    listSlots: (date) => call('GET', `/api/slots?date=${encodeURIComponent(date)}`),
    createBooking: (slotId, containerNumber, direction) =>
      call('POST', '/api/bookings', { slotId, containerNumber, direction }),
    listBookings: (filters = {}) => {
      const q = new URLSearchParams(
        Object.entries(filters).filter(([, v]) => v !== undefined && v !== null && v !== ''),
      ).toString();
      return call('GET', `/api/bookings${q ? `?${q}` : ''}`);
    },
    getBooking: (reference) => call('GET', `/api/bookings/${encodeURIComponent(reference)}`),
    reschedule: (reference, slotId) =>
      call('PUT', `/api/bookings/${encodeURIComponent(reference)}/slot`, { slotId }),
    cancel: (reference) => call('POST', `/api/bookings/${encodeURIComponent(reference)}/cancel`),
    checkIn: (reference) => call('POST', `/api/bookings/${encodeURIComponent(reference)}/check-in`),
  };
}

export const api = createClient((...args) => fetch(...args));
