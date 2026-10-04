import React, { useState } from 'react';
import { Zap, Play, CheckCircle2, AlertTriangle, ShieldAlert, RefreshCw } from 'lucide-react';
import { reserveSeats, ShowResponse } from '../services/api';
import { showAlert } from '../utils/alert';

interface ContentionSimulatorProps {
  show: ShowResponse | null;
  onRefreshShow: () => void;
}

export const ContentionSimulator: React.FC<ContentionSimulatorProps> = ({ show, onRefreshShow }) => {
  const [isRunning, setIsRunning] = useState(false);
  const [targetSeat, setTargetSeat] = useState('A1');
  const [concurrency, setConcurrency] = useState(50);
  const [results, setResults] = useState<{
    confirmed: number;
    declinedSeatTaken: number;
    declinedUserLimit: number;
    declinedIdempotency: number;
    serverErrors: number;
    totalFired: number;
  } | null>(null);

  const runHotSeatBurst = async () => {
    if (!show || isRunning) return;
    setIsRunning(true);
    setResults(null);

    let confirmed = 0;
    let seatTaken = 0;
    let userLimit = 0;
    let idempotency = 0;
    let server5xx = 0;

    const promises = [];
    for (let i = 0; i < concurrency; i++) {
      const userToken = `storm-user-${i + 1}`;
      const idempotencyKey = `sim-key-${Date.now()}-${i}`;

      const p = reserveSeats(show.id, { seats: [targetSeat], idempotency_key: idempotencyKey }, userToken)
        .then(() => {
          confirmed++;
        })
        .catch((err) => {
          if (err.status === 409) {
            if (err.reason === 'SEAT_ALREADY_TAKEN') seatTaken++;
            else if (err.reason === 'PER_USER_LIMIT_EXCEEDED' || err.reason === 'USER_LIMIT_EXCEEDED') userLimit++;
            else if (err.reason?.includes('IDEMPOTENCY')) idempotency++;
            else seatTaken++;
          } else {
            server5xx++;
          }
        });
      promises.push(p);
    }

    await Promise.all(promises);

    setResults({
      confirmed,
      declinedSeatTaken: seatTaken,
      declinedUserLimit: userLimit,
      declinedIdempotency: idempotency,
      serverErrors: server5xx,
      totalFired: concurrency,
    });

    setIsRunning(false);
    onRefreshShow();
    showAlert.toast(`Hot Seat Storm Complete: ${confirmed} Winner, ${seatTaken} Declined`, 'info');
  };

  const runGreedyQuotaBurst = async () => {
    if (!show || isRunning) return;
    setIsRunning(true);
    setResults(null);

    let confirmed = 0;
    let seatTaken = 0;
    let userLimit = 0;
    let server5xx = 0;

    const availableSeats = show.seats.filter((s) => s.status === 'available');
    if (availableSeats.length === 0) {
      setIsRunning(false);
      showAlert.toast('No available seats remaining on this show to test quota burst!', 'warning');
      return;
    }

    const testSeats = availableSeats.slice(0, Math.min(availableSeats.length, 15));
    const greedyUser = `greedy-buyer-${Date.now() % 1000}`;
    const promises = [];

    for (let i = 0; i < testSeats.length; i++) {
      const seatNum = testSeats[i].seat_number;
      const key = `greedy-key-${Date.now()}-${i}`;
      const p = reserveSeats(show.id, { seats: [seatNum], idempotency_key: key }, greedyUser)
        .then(() => confirmed++)
        .catch((err) => {
          if (err.status === 409) {
            if (err.reason === 'PER_USER_LIMIT_EXCEEDED' || err.reason === 'USER_LIMIT_EXCEEDED') userLimit++;
            else seatTaken++;
          } else {
            server5xx++;
          }
        });
      promises.push(p);
    }

    await Promise.all(promises);

    setResults({
      confirmed,
      declinedSeatTaken: seatTaken,
      declinedUserLimit: userLimit,
      declinedIdempotency: 0,
      serverErrors: server5xx,
      totalFired: testSeats.length,
    });

    setIsRunning(false);
    onRefreshShow();
    showAlert.toast(`Quota Burst Complete: Exactly ${confirmed} Seats Booked, ${userLimit} Limited`, 'info');
  };

  return (
    <div className="glass-panel p-4 sm:p-6 rounded-2xl shadow-xl w-full max-w-full overflow-hidden">
      <div className="flex flex-wrap items-center justify-between gap-2 mb-4">
        <div className="flex items-center gap-2">
          <Zap className="w-5 h-5 text-amber-400 shrink-0" />
          <h3 className="font-bold text-base sm:text-lg text-white">Live Contention Simulator</h3>
        </div>
        <span className="text-[11px] px-2.5 py-1 rounded-full bg-amber-500/10 border border-amber-500/30 text-amber-400 font-mono shrink-0">
          Stress Burst Engine
        </span>
      </div>

      <p className="text-xs sm:text-sm text-gray-400 mb-6">
        Simulate real-time on-sale stampedes against the live backend decision engine to observe race-free single winners and clean 409 declines.
      </p>

      {/* Control Buttons */}
      <div className="grid grid-cols-1 md:grid-cols-2 gap-4 mb-6">
        {/* Scenario 1 */}
        <div className="glass-card p-4 rounded-xl flex flex-col justify-between space-y-4">
          <div>
            <div className="flex flex-wrap items-center justify-between gap-1 mb-2">
              <span className="font-semibold text-xs sm:text-sm text-white">Scenario 1: Hot Seat Storm</span>
              <span className="text-[11px] text-blue-400 font-mono">{concurrency} Threads</span>
            </div>
            <p className="text-xs text-gray-400 mb-3">
              {concurrency} distinct users storm the exact same seat ({targetSeat}) simultaneously.
            </p>
            <div className="flex flex-wrap items-center gap-2 mb-2">
              <div className="flex items-center gap-1.5">
                <label className="text-xs text-gray-400">Seat:</label>
                <input
                  type="text"
                  value={targetSeat}
                  onChange={(e) => setTargetSeat(e.target.value.toUpperCase())}
                  className="w-14 px-2 py-1 bg-gray-900 border border-gray-700 rounded text-xs text-center font-bold text-white focus:outline-none focus:border-blue-500"
                />
              </div>
              <div className="flex items-center gap-1.5">
                <label className="text-xs text-gray-400">Load:</label>
                <select
                  value={concurrency}
                  onChange={(e) => setConcurrency(Number(e.target.value))}
                  className="px-2 py-1 bg-gray-900 border border-gray-700 rounded text-xs text-white focus:outline-none focus:border-blue-500"
                >
                  <option value={20}>20 Users</option>
                  <option value={50}>50 Users</option>
                  <option value={100}>100 Users</option>
                </select>
              </div>
            </div>
          </div>
          <button
            id="btn-run-hot-seat"
            disabled={!show || isRunning}
            onClick={runHotSeatBurst}
            className="w-full py-2 px-3 rounded-lg bg-gradient-to-r from-amber-600 to-orange-600 hover:from-amber-500 hover:to-orange-500 font-semibold text-xs text-white flex items-center justify-center gap-2 shadow-lg disabled:opacity-50 transition-all cursor-pointer"
          >
            {isRunning ? <RefreshCw className="w-3.5 h-3.5 animate-spin" /> : <Play className="w-3.5 h-3.5" />}
            Fire Hot Seat Storm
          </button>
        </div>

        {/* Scenario 2 */}
        <div className="glass-card p-4 rounded-xl flex flex-col justify-between space-y-4">
          <div>
            <div className="flex flex-wrap items-center justify-between gap-1 mb-2">
              <span className="font-semibold text-xs sm:text-sm text-white">Scenario 2: Quota Flood</span>
              <span className="text-[11px] text-purple-400 font-mono">Limit = {show?.per_user_limit || 4}</span>
            </div>
            <p className="text-xs text-gray-400 mb-3">
              A single user fires 15 parallel requests for 15 distinct seats simultaneously.
            </p>
          </div>
          <button
            id="btn-run-quota-burst"
            disabled={!show || isRunning}
            onClick={runGreedyQuotaBurst}
            className="w-full py-2 px-3 rounded-lg bg-gradient-to-r from-purple-600 to-indigo-600 hover:from-purple-500 hover:to-indigo-500 font-semibold text-xs text-white flex items-center justify-center gap-2 shadow-lg disabled:opacity-50 transition-all cursor-pointer"
          >
            {isRunning ? <RefreshCw className="w-3.5 h-3.5 animate-spin" /> : <Zap className="w-3.5 h-3.5" />}
            Fire Quota Burst
          </button>
        </div>
      </div>

      {/* Real-Time Outcome Telemetry */}
      {results && (
        <div className="border border-gray-800 rounded-xl p-4 bg-gray-900/60 animate-fadeIn">
          <div className="flex flex-wrap items-center justify-between gap-2 mb-3 border-b border-gray-800 pb-2">
            <span className="text-xs font-bold uppercase tracking-wider text-gray-400">Burst Outcome Distribution</span>
            <span className="text-xs font-mono text-emerald-400">
              Total Fired: {results.totalFired}
            </span>
          </div>

          <div className="grid grid-cols-2 sm:grid-cols-4 gap-2.5 text-center">
            <div className="p-2.5 rounded-lg bg-emerald-950/40 border border-emerald-500/40">
              <div className="flex items-center justify-center gap-1 text-emerald-400 mb-1">
                <CheckCircle2 className="w-3.5 h-3.5 shrink-0" />
                <span className="text-[11px] font-semibold">Confirmed</span>
              </div>
              <span className="text-xl sm:text-2xl font-black text-emerald-300 font-mono">{results.confirmed}</span>
              <span className="block text-[9px] text-gray-400 mt-0.5">HTTP 201</span>
            </div>

            <div className="p-2.5 rounded-lg bg-amber-950/40 border border-amber-500/40">
              <div className="flex items-center justify-center gap-1 text-amber-400 mb-1">
                <AlertTriangle className="w-3.5 h-3.5 shrink-0" />
                <span className="text-[11px] font-semibold">Seat Taken</span>
              </div>
              <span className="text-xl sm:text-2xl font-black text-amber-300 font-mono">{results.declinedSeatTaken}</span>
              <span className="block text-[9px] text-gray-400 mt-0.5">HTTP 409</span>
            </div>

            <div className="p-2.5 rounded-lg bg-purple-950/40 border border-purple-500/40">
              <div className="flex items-center justify-center gap-1 text-purple-400 mb-1">
                <ShieldAlert className="w-3.5 h-3.5 shrink-0" />
                <span className="text-[11px] font-semibold">User Limit</span>
              </div>
              <span className="text-xl sm:text-2xl font-black text-purple-300 font-mono">{results.declinedUserLimit}</span>
              <span className="block text-[9px] text-gray-400 mt-0.5">HTTP 409</span>
            </div>

            <div className="p-2.5 rounded-lg bg-red-950/40 border border-red-500/40">
              <div className="flex items-center justify-center gap-1 text-red-400 mb-1">
                <span className="text-[11px] font-semibold">5xx Errors</span>
              </div>
              <span className="text-xl sm:text-2xl font-black text-red-300 font-mono">{results.serverErrors}</span>
              <span className="block text-[9px] text-gray-400 mt-0.5">Zero Expected</span>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};
