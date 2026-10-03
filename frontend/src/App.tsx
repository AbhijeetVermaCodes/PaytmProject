import { useState, useEffect } from 'react';
import {
  Ticket,
  User,
  RefreshCw,
  CheckCircle,
  XCircle,
  Clock,
  Layers,
  ShieldCheck
} from 'lucide-react';
import {
  fetchShows,
  fetchShowState,
  createShow,
  reserveSeats,
  cancelReservation,
  ShowResponse
} from './services/api';
import { SeatMap } from './components/SeatMap';
import { ContentionSimulator } from './components/ContentionSimulator';
import { ObservabilityHUD } from './components/ObservabilityHUD';
import { ShowCreator } from './components/ShowCreator';

import { showAlert } from './utils/alert';

const STORAGE_KEY_SHOW_ID = 'seatrush_current_show_id';
const STORAGE_KEY_USER_TOKEN = 'seatrush_user_token';

export function App() {
  const [availableShows, setAvailableShows] = useState<ShowResponse[]>([]);
  const [currentShowId, setCurrentShowId] = useState<string>(() => {
    return localStorage.getItem(STORAGE_KEY_SHOW_ID) || '';
  });
  const [show, setShow] = useState<ShowResponse | null>(null);
  const [selectedSeats, setSelectedSeats] = useState<string[]>([]);
  const [userToken, setUserToken] = useState<string>(() => {
    return localStorage.getItem(STORAGE_KEY_USER_TOKEN) || 'user-alice';
  });
  const [idempotencyKey, setIdempotencyKey] = useState<string>(`key-${Date.now()}`);
  const [isBooking, setIsBooking] = useState<boolean>(false);
  const [lastReservationId, setLastReservationId] = useState<string | null>(null);
  const [logs, setLogs] = useState<string[]>([]);
  const [activeTab, setActiveTab] = useState<'simulator' | 'observability' | 'create'>('simulator');
  const [feedback, setFeedback] = useState<{ type: 'success' | 'error'; message: string } | null>(null);

  const isAdmin = userToken.toLowerCase().startsWith('admin');

  const addLog = (msg: string) => {
    setLogs((prev) => [msg, ...prev.slice(0, 50)]);
  };

  // Sync token to localStorage and re-fetch with new role privileges
  const handleUserTokenChange = (token: string) => {
    setUserToken(token);
    localStorage.setItem(STORAGE_KEY_USER_TOKEN, token);
    showAlert.toast(`Switched active user to '${token}'`, 'info');
    loadShow(currentShowId, token);
    refreshShowList(token);
  };

  // Sync show to localStorage
  const handleSelectShow = (showId: string) => {
    setCurrentShowId(showId);
    localStorage.setItem(STORAGE_KEY_SHOW_ID, showId);
    setSelectedSeats([]);
    setFeedback(null);
    showAlert.toast(`Event switched`, 'info');
  };

  // Fetch show details
  const loadShow = async (id?: string, token?: string) => {
    const targetId = id || currentShowId;
    if (!targetId) return;

    try {
      const data = await fetchShowState(targetId, token || userToken);
      setShow(data);
      if (!currentShowId || currentShowId !== data.id) {
        setCurrentShowId(data.id);
        localStorage.setItem(STORAGE_KEY_SHOW_ID, data.id);
      }
    } catch (e: any) {
      addLog(`Failed to fetch show state: ${e.message}`);
    }
  };

  // Refresh shows list
  const refreshShowList = async (token?: string) => {
    try {
      const list = await fetchShows(token || userToken);
      setAvailableShows(list);

      if (list.length > 0) {
        const savedId = localStorage.getItem(STORAGE_KEY_SHOW_ID);
        const existing = list.find((s) => s.id === savedId);
        const targetId = existing ? existing.id : list[0].id;
        if (targetId !== currentShowId) {
          setCurrentShowId(targetId);
          localStorage.setItem(STORAGE_KEY_SHOW_ID, targetId);
        }
      } else {
        // Auto-seed initial show if none exist
        const seats: string[] = [];
        ['A', 'B', 'C', 'D', 'E', 'F'].forEach((row) => {
          for (let i = 1; i <= 6; i++) {
            seats.push(`${row}${i}`);
          }
        });
        const created = await createShow({
          name: 'Coldplay: Music of the Spheres World Tour 2026',
          seats,
          price_paise: 250000,
          per_user_limit: 4,
        });
        setAvailableShows([created]);
        setCurrentShowId(created.id);
        localStorage.setItem(STORAGE_KEY_SHOW_ID, created.id);
        setShow(created);
        addLog(`Auto-provisioned initial event: '${created.name}'`);
      }
    } catch (e: any) {
      addLog(`Failed to load shows list: ${e.message}`);
    }
  };

  // Initial load
  useEffect(() => {
    refreshShowList();
  }, []);

  // Polling interval for active show
  useEffect(() => {
    if (!currentShowId) return;
    loadShow(currentShowId);
    const interval = setInterval(() => {
      loadShow(currentShowId);
    }, 2500);
    return () => clearInterval(interval);
  }, [currentShowId, userToken]);

  const handleToggleSeat = (seatNumber: string) => {
    setSelectedSeats((prev) =>
      prev.includes(seatNumber) ? prev.filter((s) => s !== seatNumber) : [...prev, seatNumber]
    );
  };

  const handleReserve = async () => {
    if (!show || selectedSeats.length === 0) return;
    setIsBooking(true);
    setFeedback(null);

    try {
      const res = await reserveSeats(
        show.id,
        { seats: selectedSeats, idempotency_key: idempotencyKey },
        userToken
      );
      setLastReservationId(res.reservation_id);
      setFeedback({
        type: 'success',
        message: `Reservation Confirmed! Reserved ${res.seats.join(', ')} for ₹${(res.amount_paise / 100).toFixed(2)} (ID: ${res.reservation_id.slice(0, 8)}...)`,
      });
      addLog(`CONFIRMED: User '${userToken}' booked ${res.seats.join(', ')} (₹${(res.amount_paise / 100).toFixed(2)})`);

      // SweetAlert2 Success Receipt Modal
      const receiptHtml = `
        <div class="text-left space-y-2 mt-2 p-3 bg-gray-900 rounded-xl border border-gray-800">
          <div class="flex justify-between text-xs text-gray-400">
            <span>Event:</span>
            <span class="font-bold text-white">${show.name}</span>
          </div>
          <div class="flex justify-between text-xs text-gray-400">
            <span>Seats:</span>
            <span class="font-bold text-blue-400 font-mono">${res.seats.join(', ')}</span>
          </div>
          <div class="flex justify-between text-xs text-gray-400">
            <span>Customer:</span>
            <span class="font-bold text-purple-300">${res.user_id}</span>
          </div>
          <div class="flex justify-between text-xs text-gray-400 border-t border-gray-800 pt-1.5">
            <span>Total Paid:</span>
            <span class="font-extrabold text-emerald-400 font-mono text-sm">₹${(res.amount_paise / 100).toFixed(2)}</span>
          </div>
          <div class="text-[10px] text-gray-500 font-mono break-all pt-1">
            Ref: ${res.reservation_id}
          </div>
        </div>
      `;
      showAlert.success('Reservation Confirmed! 🎟️', '', receiptHtml);

      setSelectedSeats([]);
      setIdempotencyKey(`key-${Date.now()}`);
      loadShow();
      refreshShowList();
    } catch (err: any) {
      setFeedback({
        type: 'error',
        message: `${err.reason || 'DECLINED'}: ${err.message}`,
      });
      addLog(`DECLINED (${err.reason || err.status}): ${err.message}`);

      // SweetAlert2 Conflict / Error Modal
      showAlert.error(
        err.reason === 'USER_LIMIT_EXCEEDED' ? 'Booking Limit Exceeded ⚠️' : 'Reservation Conflict 🛑',
        err.message,
        err.conflictingSeats
      );
    } finally {
      setIsBooking(false);
    }
  };

  const handleCancelLast = async () => {
    if (!lastReservationId) return;

    const confirmed = await showAlert.confirm(
      'Cancel Reservation?',
      'Are you sure you want to release these seats back to available inventory?',
      'Yes, Cancel It'
    );
    if (!confirmed) return;

    try {
      const res = await cancelReservation(lastReservationId, userToken);
      setFeedback({
        type: 'success',
        message: `Cancelled reservation ${res.reservation_id.slice(0, 8)}... Freed seats: ${res.freed_seats.join(', ')}`,
      });
      addLog(`CANCELLED: User '${userToken}' released seats ${res.freed_seats.join(', ')}`);
      showAlert.toast(`Released seats ${res.freed_seats.join(', ')}`, 'success');
      setLastReservationId(null);
      loadShow();
      refreshShowList();
    } catch (err: any) {
      setFeedback({
        type: 'error',
        message: `Cancellation failed: ${err.message}`,
      });
      addLog(`CANCEL FAILED: ${err.message}`);
      showAlert.error('Cancellation Failed', err.message);
    }
  };

  const handleCancelSpecificReservation = async (reservationId: string) => {
    const confirmed = await showAlert.confirm(
      'Admin Revoke Reservation?',
      `Are you sure you want to revoke reservation ${reservationId.slice(0, 8)}... and return seats to inventory?`,
      'Yes, Revoke Reservation'
    );
    if (!confirmed) return;

    try {
      const res = await cancelReservation(reservationId, userToken);
      setFeedback({
        type: 'success',
        message: `Cancelled reservation ${res.reservation_id.slice(0, 8)}... Freed seats: ${res.freed_seats.join(', ')}`,
      });
      addLog(`ADMIN OVERRIDE: '${userToken}' cancelled reservation ${res.reservation_id.slice(0, 8)}... (Freed seats: ${res.freed_seats.join(', ')})`);
      showAlert.toast(`Admin revoked reservation (Freed: ${res.freed_seats.join(', ')})`, 'success');
      if (lastReservationId === reservationId) {
        setLastReservationId(null);
      }
      loadShow();
      refreshShowList();
    } catch (err: any) {
      setFeedback({
        type: 'error',
        message: `Cancellation failed: ${err.message}`,
      });
      addLog(`CANCEL FAILED: ${err.message}`);
      showAlert.error('Revocation Failed', err.message);
    }
  };

  const totalPriceRupees = show ? ((show.price_paise * selectedSeats.length) / 100).toFixed(2) : '0.00';

  return (
    <div className="min-h-screen bg-[#0B0F19] text-gray-100 flex flex-col">
      {/* Header Bar */}
      <header className="border-b border-gray-800/80 bg-gray-950/60 backdrop-blur-md sticky top-0 z-50">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 h-16 flex items-center justify-between">
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-xl bg-gradient-to-tr from-blue-600 to-purple-600 flex items-center justify-center shadow-lg shadow-blue-500/20">
              <Ticket className="w-5 h-5 text-white" />
            </div>
            <div>
              <div className="flex items-center gap-2">
                <h1 className="font-extrabold text-xl tracking-tight text-white">SeatRush</h1>
                <span className="text-[10px] uppercase font-mono px-2 py-0.5 rounded bg-blue-500/10 text-blue-400 border border-blue-500/20">
                  High-Concurrency
                </span>
                {isAdmin && (
                  <span className="text-[10px] uppercase font-mono px-2 py-0.5 rounded bg-purple-500/20 text-purple-300 border border-purple-500/30 flex items-center gap-1 font-bold">
                    <ShieldCheck className="w-3 h-3" /> Admin Mode
                  </span>
                )}
              </div>
              <p className="text-[11px] text-gray-400">Race-Free Distributed Ticket Booking Engine</p>
            </div>
          </div>

          {/* Show Switcher & User Token Switcher */}
          <div className="flex items-center gap-3">
            {/* Show Dropdown */}
            {availableShows.length > 0 && (
              <div className="hidden sm:flex items-center gap-2 bg-gray-900 border border-gray-800 rounded-xl px-3 py-1.5">
                <Layers className="w-4 h-4 text-blue-400" />
                <span className="text-xs text-gray-400">Event:</span>
                <select
                  value={currentShowId}
                  onChange={(e) => handleSelectShow(e.target.value)}
                  className="bg-transparent text-xs font-bold text-white focus:outline-none cursor-pointer max-w-[160px] truncate"
                >
                  {availableShows.map((s) => (
                    <option key={s.id} value={s.id} className="bg-gray-900 text-white">
                      {s.name} ({s.available_seats} left)
                    </option>
                  ))}
                </select>
              </div>
            )}

            {/* User Token Switcher */}
            <div className={`flex items-center gap-2 bg-gray-900 border ${isAdmin ? 'border-purple-500/50 ring-1 ring-purple-500/30' : 'border-gray-800'} rounded-xl px-3 py-1.5`}>
              <User className={`w-4 h-4 ${isAdmin ? 'text-purple-300' : 'text-purple-400'}`} />
              <span className="text-xs text-gray-400">Auth:</span>
              <select
                value={userToken}
                onChange={(e) => handleUserTokenChange(e.target.value)}
                className="bg-transparent text-xs font-bold text-white focus:outline-none cursor-pointer"
              >
                <option value="user-alice" className="bg-gray-900">user-alice (Regular)</option>
                <option value="user-bob" className="bg-gray-900">user-bob (Regular)</option>
                <option value="user-charlie" className="bg-gray-900">user-charlie (Regular)</option>
                <option value="admin" className="bg-gray-900 font-bold text-purple-400">admin (Admin Role)</option>
              </select>
            </div>

            <button
              onClick={() => {
                loadShow();
                refreshShowList();
              }}
              title="Refresh State"
              className="p-2 rounded-xl bg-gray-900 border border-gray-800 hover:bg-gray-800 text-gray-300 transition-all cursor-pointer"
            >
              <RefreshCw className="w-4 h-4" />
            </button>
          </div>
        </div>
      </header>

      {/* Main Container */}
      <main className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8 flex-1 w-full space-y-8">
        {/* Show Selector & Summary Banner */}
        {show && (
          <div className="glass-panel p-6 rounded-2xl flex flex-wrap items-center justify-between gap-4">
            <div>
              <div className="flex items-center gap-2">
                <span className="text-xs font-bold text-purple-400 uppercase tracking-widest">Active On-Sale Event</span>
                {availableShows.length > 1 && (
                  <select
                    value={currentShowId}
                    onChange={(e) => handleSelectShow(e.target.value)}
                    className="sm:hidden bg-gray-900 border border-gray-800 text-xs text-blue-300 rounded px-2 py-0.5"
                  >
                    {availableShows.map((s) => (
                      <option key={s.id} value={s.id}>
                        {s.name}
                      </option>
                    ))}
                  </select>
                )}
              </div>
              <h2 className="text-2xl font-black text-white">{show.name}</h2>
              <span className="text-xs text-gray-400 font-mono">Show ID: {show.id}</span>
            </div>

            {/* Inventory Status Badges */}
            <div className="flex flex-wrap gap-4">
              <div className="px-4 py-2 rounded-xl bg-gray-900/90 border border-gray-800 text-center">
                <span className="text-[10px] uppercase font-bold text-gray-400 block">Available</span>
                <span className="text-xl font-extrabold text-emerald-400 font-mono">{show.available_seats}</span>
              </div>
              <div className="px-4 py-2 rounded-xl bg-gray-900/90 border border-gray-800 text-center">
                <span className="text-[10px] uppercase font-bold text-gray-400 block">Held</span>
                <span className="text-xl font-extrabold text-amber-400 font-mono">{show.held_seats}</span>
              </div>
              <div className="px-4 py-2 rounded-xl bg-gray-900/90 border border-gray-800 text-center">
                <span className="text-[10px] uppercase font-bold text-gray-400 block">Confirmed</span>
                <span className="text-xl font-extrabold text-blue-400 font-mono">{show.confirmed_seats}</span>
              </div>
              <div className="px-4 py-2 rounded-xl bg-gray-900/90 border border-gray-800 text-center">
                <span className="text-[10px] uppercase font-bold text-gray-400 block">Total</span>
                <span className="text-xl font-extrabold text-white font-mono">{show.total_seats}</span>
              </div>
            </div>
          </div>
        )}

        {/* Feedback Alert */}
        {feedback && (
          <div
            className={`p-4 rounded-xl border flex items-center gap-3 animate-fadeIn ${
              feedback.type === 'success'
                ? 'bg-emerald-950/60 border-emerald-500/50 text-emerald-200'
                : 'bg-red-950/60 border-red-500/50 text-red-200'
            }`}
          >
            {feedback.type === 'success' ? <CheckCircle className="w-5 h-5 text-emerald-400 shrink-0" /> : <XCircle className="w-5 h-5 text-red-400 shrink-0" />}
            <span className="text-sm font-medium">{feedback.message}</span>
          </div>
        )}

        {/* Main Grid: Left = SeatMap + Checkout, Right = Simulator/HUD */}
        <div className="grid grid-cols-1 lg:grid-cols-12 gap-8">
          {/* Left Column: Seat Map & Checkout */}
          <div className="lg:col-span-7 space-y-6">
            {show ? (
              <SeatMap
                seats={show.seats}
                selectedSeats={selectedSeats}
                pricePaise={show.price_paise}
                perUserLimit={show.per_user_limit}
                onToggleSeat={handleToggleSeat}
                disabled={isBooking}
                isAdmin={isAdmin}
                onCancelReservation={handleCancelSpecificReservation}
              />
            ) : (
              <div className="glass-panel p-12 text-center rounded-2xl space-y-4">
                <Clock className="w-12 h-12 text-purple-400 mx-auto animate-bounce" />
                <h3 className="text-lg font-bold text-white">Loading Active Show...</h3>
                <p className="text-sm text-gray-400 max-w-sm mx-auto">
                  Connecting to the backend booking engine and fetching assigned seat inventory.
                </p>
              </div>
            )}

            {/* Booking Checkout Card */}
            {show && (
              <div className="glass-panel p-6 rounded-2xl shadow-xl flex flex-col md:flex-row items-center justify-between gap-6">
                <div className="space-y-1 w-full md:w-auto">
                  <div className="flex items-center gap-2">
                    <span className="text-xs text-gray-400">Selected Seats:</span>
                    <span className="font-bold text-white font-mono">
                      {selectedSeats.length > 0 ? selectedSeats.join(', ') : 'None'}
                    </span>
                  </div>
                  <div className="text-xs text-gray-400">
                    Total Amount: <span className="text-lg font-extrabold text-emerald-400 font-mono">₹{totalPriceRupees}</span>
                  </div>
                  <div className="flex items-center gap-2 pt-2">
                    <span className="text-[10px] text-gray-500">Idempotency Key:</span>
                    <input
                      type="text"
                      value={idempotencyKey}
                      onChange={(e) => setIdempotencyKey(e.target.value)}
                      className="px-2 py-0.5 bg-gray-900 border border-gray-800 rounded text-[10px] font-mono text-gray-300 w-48 focus:outline-none"
                    />
                  </div>
                </div>

                <div className="flex items-center gap-3 w-full md:w-auto justify-end">
                  {lastReservationId && (
                    <button
                      onClick={handleCancelLast}
                      className="py-2.5 px-4 rounded-xl bg-gray-800 hover:bg-gray-700 text-gray-300 text-xs font-semibold transition-all cursor-pointer"
                    >
                      Cancel Last
                    </button>
                  )}
                  <button
                    id="btn-reserve-seats"
                    disabled={selectedSeats.length === 0 || isBooking}
                    onClick={handleReserve}
                    className="py-3 px-6 rounded-xl bg-gradient-to-r from-blue-600 via-indigo-600 to-purple-600 hover:from-blue-500 hover:to-purple-500 font-extrabold text-sm text-white shadow-lg glow-blue disabled:opacity-40 disabled:pointer-events-none transition-all cursor-pointer"
                  >
                    {isBooking ? 'Securing Seats...' : `Confirm Reservation (${selectedSeats.length})`}
                  </button>
                </div>
              </div>
            )}
          </div>

          {/* Right Column: Tabbed Control Center */}
          <div className="lg:col-span-5 space-y-6">
            {/* Tab Header */}
            <div className="flex items-center bg-gray-900/90 p-1.5 rounded-xl border border-gray-800">
              <button
                onClick={() => setActiveTab('simulator')}
                className={`flex-1 py-2 text-xs font-bold rounded-lg transition-all cursor-pointer ${
                  activeTab === 'simulator' ? 'bg-blue-600 text-white shadow-md' : 'text-gray-400 hover:text-white'
                }`}
              >
                Contention Simulator
              </button>
              <button
                onClick={() => setActiveTab('observability')}
                className={`flex-1 py-2 text-xs font-bold rounded-lg transition-all cursor-pointer ${
                  activeTab === 'observability' ? 'bg-blue-600 text-white shadow-md' : 'text-gray-400 hover:text-white'
                }`}
              >
                Telemetry & Health
              </button>
              <button
                onClick={() => setActiveTab('create')}
                className={`flex-1 py-2 text-xs font-bold rounded-lg transition-all cursor-pointer ${
                  activeTab === 'create' ? 'bg-blue-600 text-white shadow-md' : 'text-gray-400 hover:text-white'
                }`}
              >
                Create Show
              </button>
            </div>

            {/* Tab Contents */}
            {activeTab === 'simulator' && <ContentionSimulator show={show} onRefreshShow={() => loadShow()} />}
            {activeTab === 'observability' && <ObservabilityHUD show={show} logs={logs} />}
            {activeTab === 'create' && (
              <ShowCreator
                onShowCreated={(created) => {
                  setShow(created);
                  setCurrentShowId(created.id);
                  localStorage.setItem(STORAGE_KEY_SHOW_ID, created.id);
                  setAvailableShows((prev) => [created, ...prev.filter((s) => s.id !== created.id)]);
                  setActiveTab('simulator');
                  addLog(`Show created: '${created.name}' with ${created.total_seats} seats`);
                }}
              />
            )}
          </div>
        </div>
      </main>

      {/* Footer */}
      <footer className="border-t border-gray-800/60 py-4 text-center text-xs text-gray-500 font-mono">
        SeatRush Engine v1.0.0 • Java 17 + Spring Boot 3 + PostgreSQL • Race-Free ACID Decision Engine
      </footer>
    </div>
  );
}
