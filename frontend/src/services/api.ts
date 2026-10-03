export interface SeatDto {
  seat_number: string;
  status: 'available' | 'held' | 'confirmed';
  booked_by?: string;
  reservation_id?: string;
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
  owner_user_id?: string;
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

export interface EventProposal {
  id: string;
  name: string;
  seats: string[];
  price_paise: number;
  per_user_limit: number;
  requested_by: string;
  status: 'PENDING' | 'APPROVED' | 'REJECTED';
  reviewed_by?: string;
  show_id?: string;
  rejection_reason?: string;
  created_at: string;
  reviewed_at?: string;
}

export interface ReadinessResponse {
  status: string;
  database: string;
  timestamp: string;
}

const BASE_URL = '';

export async function fetchShows(userToken?: string): Promise<ShowResponse[]> {
  const headers: HeadersInit = {};
  if (userToken) {
    headers['Authorization'] = `Bearer ${userToken}`;
  }
  const res = await fetch(`${BASE_URL}/shows`, { headers });
  if (!res.ok) {
    const err = await res.json().catch(() => ({}));
    throw new Error(err.message || `Failed to list shows: ${res.status}`);
  }
  return res.json();
}

export async function fetchShowState(showId: string, userToken?: string): Promise<ShowResponse> {
  const headers: HeadersInit = {};
  if (userToken) {
    headers['Authorization'] = `Bearer ${userToken}`;
  }
  const res = await fetch(`${BASE_URL}/shows/${showId}`, { headers });
  if (!res.ok) {
    const err = await res.json().catch(() => ({}));
    throw new Error(err.message || `Failed to fetch show: ${res.status}`);
  }
  return res.json();
}

export async function createShow(
  payload: {
    name: string;
    seats: string[];
    price_paise: number;
    per_user_limit?: number;
  },
  userToken: string = 'admin'
): Promise<ShowResponse> {
  const res = await fetch(`${BASE_URL}/shows`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'Authorization': `Bearer ${userToken}`,
    },
    body: JSON.stringify(payload),
  });
  if (!res.ok) {
    const err = await res.json().catch(() => ({}));
    throw new Error(err.message || `Failed to create show: ${res.status}`);
  }
  return res.json();
}

export async function submitEventProposal(
  payload: {
    name: string;
    seats: string[];
    price_paise: number;
    per_user_limit?: number;
  },
  userToken: string
): Promise<EventProposal> {
  const res = await fetch(`${BASE_URL}/event-requests`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'Authorization': `Bearer ${userToken}`,
    },
    body: JSON.stringify(payload),
  });
  if (!res.ok) {
    const err = await res.json().catch(() => ({}));
    throw new Error(err.message || `Failed to submit event proposal: ${res.status}`);
  }
  return res.json();
}

export async function fetchEventProposals(
  userToken: string,
  status?: 'PENDING' | 'APPROVED' | 'REJECTED'
): Promise<EventProposal[]> {
  const url = status ? `${BASE_URL}/event-requests?status=${status}` : `${BASE_URL}/event-requests`;
  const res = await fetch(url, {
    headers: {
      'Authorization': `Bearer ${userToken}`,
    },
  });
  if (!res.ok) {
    const err = await res.json().catch(() => ({}));
    throw new Error(err.message || `Failed to fetch event proposals: ${res.status}`);
  }
  return res.json();
}

export async function approveEventProposal(
  proposalId: string,
  userToken: string
): Promise<EventProposal> {
  const res = await fetch(`${BASE_URL}/event-requests/${proposalId}/approve`, {
    method: 'POST',
    headers: {
      'Authorization': `Bearer ${userToken}`,
    },
  });
  if (!res.ok) {
    const err = await res.json().catch(() => ({}));
    throw new Error(err.message || `Failed to approve event proposal: ${res.status}`);
  }
  return res.json();
}

export async function rejectEventProposal(
  proposalId: string,
  reason: string | undefined,
  userToken: string
): Promise<EventProposal> {
  const res = await fetch(`${BASE_URL}/event-requests/${proposalId}/reject`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'Authorization': `Bearer ${userToken}`,
    },
    body: JSON.stringify({ reason }),
  });
  if (!res.ok) {
    const err = await res.json().catch(() => ({}));
    throw new Error(err.message || `Failed to reject event proposal: ${res.status}`);
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
