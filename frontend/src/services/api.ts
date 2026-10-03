export interface SeatDto {
  seat_number: string;
  status: 'available' | 'held' | 'confirmed';
}

export interface ShowResponse {
  id: string;
  name: string;
  price_paise: number;
  per_user_limit: number;
  total_seats: number;
  available_seats: number;
  held_seats: number;
  confirmed_seats: number;
  reconciliation_valid: boolean;
  seats: SeatDto[];
}

export interface ReserveSeatResponse {
  reservation_id: string;
  show_id: string;
  user_id: string;
  seats: string[];
  amount_paise: number;
  status: string;
}

export interface CancelReservationResponse {
  reservation_id: string;
  show_id: string;
  user_id: string;
  status: string;
  freed_seats: string[];
}

export interface ReadinessResponse {
  status: string;
  database: string;
  timestamp: string;
}

const BASE_URL = '';

export async function fetchShows(): Promise<ShowResponse[]> {
  const res = await fetch(`${BASE_URL}/shows`);
  if (!res.ok) {
    const err = await res.json().catch(() => ({}));
    throw new Error(err.message || `Failed to list shows: ${res.status}`);
  }
  return res.json();
}

export async function fetchShowState(showId: string): Promise<ShowResponse> {
  const res = await fetch(`${BASE_URL}/shows/${showId}`);
  if (!res.ok) {
    const err = await res.json().catch(() => ({}));
    throw new Error(err.message || `Failed to fetch show: ${res.status}`);
  }
  return res.json();
}

export async function createShow(payload: {
  name: string;
  seats: string[];
  price_paise: number;
  per_user_limit?: number;
}): Promise<ShowResponse> {
  const res = await fetch(`${BASE_URL}/shows`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'Authorization': 'Bearer admin',
    },
    body: JSON.stringify(payload),
  });
  if (!res.ok) {
    const err = await res.json().catch(() => ({}));
    throw new Error(err.message || `Failed to create show: ${res.status}`);
  }
  return res.json();
}

export async function reserveSeats(
  showId: string,
  payload: { seats: string[]; idempotency_key?: string },
  userToken: string
): Promise<ReserveSeatResponse> {
  const res = await fetch(`${BASE_URL}/shows/${showId}/reserve`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'Authorization': `Bearer ${userToken}`,
    },
    body: JSON.stringify(payload),
  });

  if (!res.ok) {
    const err = await res.json().catch(() => ({}));
    const error: any = new Error(err.message || `Decline: HTTP ${res.status}`);
    error.status = res.status;
    error.reason = err.reason || 'UNKNOWN_REASON';
    error.conflictingSeats = err.conflicting_seats;
    throw error;
  }
  return res.json();
}

export async function cancelReservation(
  reservationId: string,
  userToken: string
): Promise<CancelReservationResponse> {
  const res = await fetch(`${BASE_URL}/reservations/${reservationId}/cancel`, {
    method: 'POST',
    headers: {
      'Authorization': `Bearer ${userToken}`,
    },
  });

  if (!res.ok) {
    const err = await res.json().catch(() => ({}));
    throw new Error(err.message || `Failed to cancel: ${res.status}`);
  }
  return res.json();
}

export async function fetchReadiness(): Promise<ReadinessResponse> {
  const res = await fetch(`${BASE_URL}/health/ready`);
  return res.json();
}

export async function fetchMetrics(): Promise<string> {
  const res = await fetch(`${BASE_URL}/metrics`);
  return res.text();
}
