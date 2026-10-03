import React, { useState } from 'react';
import { SeatDto } from '../services/api';
import { CheckCircle2, Lock, ShieldCheck, UserCheck, ShieldAlert, Search, XCircle, Hash } from 'lucide-react';

interface SeatMapProps {
  seats: SeatDto[];
  selectedSeats: string[];
  pricePaise: number;
  perUserLimit: number;
  onToggleSeat: (seatNumber: string) => void;
  disabled?: boolean;
  isAdmin?: boolean;
  onCancelReservation?: (reservationId: string) => void;
}

export const SeatMap: React.FC<SeatMapProps> = ({
  seats,
  selectedSeats,
  pricePaise,
  perUserLimit,
  onToggleSeat,
  disabled,
  isAdmin = false,
  onCancelReservation,
}) => {
  const [hoveredSeat, setHoveredSeat] = useState<SeatDto | null>(null);
  const [inspectorSeat, setInspectorSeat] = useState<SeatDto | null>(null);
  const [rosterSearch, setRosterSearch] = useState<string>('');
  const [showRosterTable, setShowRosterTable] = useState<boolean>(true);

  // Group seats by row prefix (e.g. 'A', 'B')
  const rows: { [key: string]: SeatDto[] } = {};
  seats.forEach((seat) => {
    const row = seat.seat_number.charAt(0);
    if (!rows[row]) rows[row] = [];
    rows[row].push(seat);
  });

  const priceRupees = (pricePaise / 100).toFixed(2);

  // Filtered seats for admin roster
  const filteredRosterSeats = seats.filter((s) => {
    if (!rosterSearch) return true;
    const query = rosterSearch.toLowerCase();
    return (
      s.seat_number.toLowerCase().includes(query) ||
      (s.booked_by && s.booked_by.toLowerCase().includes(query)) ||
      (s.reservation_id && s.reservation_id.toLowerCase().includes(query)) ||
      s.status.toLowerCase().includes(query)
    );
  });

  const activeOccupiedSeat = inspectorSeat || hoveredSeat;

  return (
    <div className="space-y-6">
      {/* Main Seat Matrix Card */}
      <div className="glass-panel p-6 rounded-2xl shadow-2xl relative overflow-hidden">
        {/* Background aesthetic glow */}
        <div className="absolute top-0 left-1/2 -translate-x-1/2 w-3/4 h-24 bg-gradient-to-b from-blue-600/15 via-purple-600/5 to-transparent blur-2xl pointer-events-none" />

        {/* Stage / Screen Representation */}
        <div className="flex flex-col items-center mb-8">
          <div className="w-4/5 h-2.5 bg-gradient-to-r from-blue-500 via-purple-500 to-blue-500 rounded-full shadow-[0_0_20px_rgba(59,130,246,0.6)] mb-2" />
          <div className="flex items-center gap-2">
            <span className="text-xs uppercase tracking-widest text-gray-400 font-semibold">STAGE / SCREEN</span>
            {isAdmin && (
              <span className="inline-flex items-center gap-1 text-[10px] font-bold px-2 py-0.5 rounded-md bg-purple-500/20 text-purple-300 border border-purple-500/30">
                <ShieldCheck className="w-3 h-3 text-purple-400" /> Admin Inspector Active
              </span>
            )}
          </div>
        </div>

        {/* Seating Grid */}
        <div className="flex flex-col items-center gap-3 my-6">
          {Object.keys(rows).map((rowLetter) => (
            <div key={rowLetter} className="flex items-center gap-2">
              <span className="w-6 text-center text-xs font-bold text-gray-500">{rowLetter}</span>
              <div className="flex flex-wrap gap-2 justify-center">
                {rows[rowLetter].map((seat) => {
                  const isSelected = selectedSeats.includes(seat.seat_number);
                  const isAvailable = seat.status === 'available';
                  const isHeld = seat.status === 'held';
                  const isConfirmed = seat.status === 'confirmed';

                  let bgClass = 'bg-emerald-950/60 border-emerald-500/50 text-emerald-300 hover:bg-emerald-800/80 hover:border-emerald-400 hover:scale-105';
                  if (isSelected) {
                    bgClass = 'bg-blue-600 border-blue-400 text-white glow-blue scale-110 ring-2 ring-blue-300 animate-pulse';
                  } else if (isHeld) {
                    bgClass = 'bg-amber-950/70 border-amber-500/50 text-amber-300 cursor-pointer opacity-90 hover:ring-2 hover:ring-amber-400';
                  } else if (isConfirmed) {
                    bgClass = isAdmin
                      ? 'bg-purple-950/70 border-purple-500/50 text-purple-300 cursor-pointer hover:scale-105 hover:border-purple-400 ring-1 ring-purple-500/30 shadow-purple-500/20'
                      : 'bg-gray-800/80 border-gray-700 text-gray-500 cursor-not-allowed opacity-60';
                  }

                  const handleSeatClick = () => {
                    if (isAdmin && !isAvailable) {
                      setInspectorSeat(seat);
                    } else if (isAvailable || isSelected) {
                      onToggleSeat(seat.seat_number);
                    }
                  };

                  return (
                    <div key={seat.seat_number} className="relative group">
                      <button
                        id={`seat-btn-${seat.seat_number}`}
                        disabled={disabled || (!isAvailable && !isSelected && !isAdmin)}
                        onClick={handleSeatClick}
                        onMouseEnter={() => setHoveredSeat(seat)}
                        onMouseLeave={() => setHoveredSeat(null)}
                        className={`relative w-10 h-10 rounded-xl border flex flex-col items-center justify-center font-bold text-xs transition-all duration-200 shadow-md ${bgClass}`}
                      >
                        <span>{seat.seat_number}</span>
                        {isConfirmed && (
                          isAdmin ? (
                            <UserCheck className="w-2.5 h-2.5 mt-0.5 text-purple-300" />
                          ) : (
                            <Lock className="w-2.5 h-2.5 mt-0.5 text-gray-400" />
                          )
                        )}
                        {isSelected && <CheckCircle2 className="w-2.5 h-2.5 mt-0.5 text-white" />}
                      </button>

                      {/* Tooltip for Admin & Users */}
                      {isAdmin && seat.booked_by && (
                        <div className="absolute bottom-full left-1/2 -translate-x-1/2 mb-2 hidden group-hover:flex flex-col items-center z-50 pointer-events-none min-w-[140px]">
                          <div className="bg-gray-900 border border-purple-500/40 text-[11px] rounded-lg p-2 shadow-2xl text-center space-y-0.5">
                            <div className="font-bold text-white flex items-center justify-center gap-1">
                              <span>Seat {seat.seat_number}</span>
                              <span className={`text-[9px] uppercase px-1 rounded ${isConfirmed ? 'bg-purple-500/20 text-purple-300' : 'bg-amber-500/20 text-amber-300'}`}>
                                {seat.status}
                              </span>
                            </div>
                            <div className="text-purple-300 font-semibold flex items-center justify-center gap-1">
                              <UserCheck className="w-3 h-3" />
                              <span>{seat.booked_by}</span>
                            </div>
                            {seat.reservation_id && (
                              <div className="text-[9px] text-gray-400 font-mono">
                                ID: {seat.reservation_id.slice(0, 8)}...
                              </div>
                            )}
                          </div>
                          <div className="w-2 h-2 bg-gray-900 border-r border-b border-purple-500/40 rotate-45 -mt-1" />
                        </div>
                      )}
                    </div>
                  );
                })}
              </div>
              <span className="w-6 text-center text-xs font-bold text-gray-500">{rowLetter}</span>
            </div>
          ))}
        </div>

        {/* Legend */}
        <div className="mt-8 pt-6 border-t border-gray-800/80 flex flex-wrap items-center justify-center gap-6 text-xs text-gray-300">
          <div className="flex items-center gap-2">
            <div className="w-4 h-4 rounded-md bg-emerald-950/80 border border-emerald-500/60" />
            <span>Available</span>
          </div>
          <div className="flex items-center gap-2">
            <div className="w-4 h-4 rounded-md bg-blue-600 border border-blue-400 shadow-[0_0_8px_rgba(59,130,246,0.5)]" />
            <span>Selected</span>
          </div>
          <div className="flex items-center gap-2">
            <div className="w-4 h-4 rounded-md bg-amber-950/80 border border-amber-500/60" />
            <span>Held (Expiring)</span>
          </div>
          <div className="flex items-center gap-2">
            <div className={`w-4 h-4 rounded-md ${isAdmin ? 'bg-purple-950/80 border border-purple-500/60' : 'bg-gray-800/80 border border-gray-700'}`} />
            <span>{isAdmin ? 'Confirmed (Admin Visible)' : 'Confirmed (Sold)'}</span>
          </div>
          <div className="flex items-center gap-1 text-gray-400">
            <ShieldCheck className="w-4 h-4 text-purple-400" />
            <span>Limit: {perUserLimit} seats/user</span>
          </div>
        </div>
      </div>

      {/* Admin Quick Inspector Card */}
      {isAdmin && activeOccupiedSeat && (
        <div className="glass-panel p-4 rounded-xl border border-purple-500/40 bg-purple-950/20 flex flex-wrap items-center justify-between gap-4 animate-fadeIn">
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-lg bg-purple-600/20 border border-purple-500/40 flex items-center justify-center text-purple-300 font-extrabold text-base">
              {activeOccupiedSeat.seat_number}
            </div>
            <div>
              <div className="flex items-center gap-2">
                <span className="text-xs font-bold text-white">Seat {activeOccupiedSeat.seat_number} Audit Details</span>
                <span className={`text-[10px] uppercase font-bold px-2 py-0.5 rounded ${activeOccupiedSeat.status === 'confirmed' ? 'bg-purple-500/20 text-purple-300' : 'bg-emerald-500/20 text-emerald-300'}`}>
                  {activeOccupiedSeat.status}
                </span>
              </div>
              <div className="text-xs text-gray-300 flex items-center gap-3 pt-0.5">
                {activeOccupiedSeat.booked_by ? (
                  <span className="flex items-center gap-1 text-purple-200">
                    <UserCheck className="w-3.5 h-3.5 text-purple-400" />
                    Booked By: <strong className="font-mono">{activeOccupiedSeat.booked_by}</strong>
                  </span>
                ) : (
                  <span className="text-gray-400">Not reserved yet</span>
                )}
                {activeOccupiedSeat.reservation_id && (
                  <span className="text-gray-400 font-mono text-[11px] flex items-center gap-1">
                    <Hash className="w-3 h-3 text-gray-500" />
                    Res ID: {activeOccupiedSeat.reservation_id}
                  </span>
                )}
              </div>
            </div>
          </div>

          {activeOccupiedSeat.reservation_id && onCancelReservation && (
            <button
              onClick={() => onCancelReservation(activeOccupiedSeat.reservation_id!)}
              className="py-1.5 px-3 rounded-lg bg-red-600/80 hover:bg-red-500 text-white text-xs font-bold transition-all flex items-center gap-1 shadow-md cursor-pointer"
            >
              <XCircle className="w-3.5 h-3.5" />
              Cancel Reservation (Admin Override)
            </button>
          )}
        </div>
      )}

      {/* Admin Seat & Customer Roster Table */}
      {isAdmin && (
        <div className="glass-panel p-6 rounded-2xl space-y-4 border border-purple-500/20">
          <div className="flex flex-wrap items-center justify-between gap-4">
            <div className="flex items-center gap-2">
              <ShieldAlert className="w-5 h-5 text-purple-400" />
              <h3 className="text-base font-bold text-white">Admin Seat & Customer Audit Roster</h3>
              <span className="text-xs text-gray-400">({seats.length} total seats)</span>
            </div>

            <div className="flex items-center gap-3">
              {/* Search input */}
              <div className="relative">
                <Search className="w-3.5 h-3.5 text-gray-400 absolute left-2.5 top-1/2 -translate-y-1/2" />
                <input
                  type="text"
                  placeholder="Filter seat, user, or status..."
                  value={rosterSearch}
                  onChange={(e) => setRosterSearch(e.target.value)}
                  className="bg-gray-900 border border-gray-800 rounded-lg pl-8 pr-3 py-1.5 text-xs text-white placeholder-gray-500 focus:outline-none focus:border-purple-500 w-52"
                />
              </div>

              <button
                onClick={() => setShowRosterTable(!showRosterTable)}
                className="text-xs text-purple-400 hover:text-purple-300 font-semibold cursor-pointer"
              >
                {showRosterTable ? 'Hide Table' : 'Show Table'}
              </button>
            </div>
          </div>

          {showRosterTable && (
            <div className="overflow-x-auto rounded-xl border border-gray-800">
              <table className="w-full text-left text-xs text-gray-300">
                <thead className="bg-gray-900/90 text-gray-400 uppercase font-semibold text-[10px] border-b border-gray-800">
                  <tr>
                    <th className="px-4 py-3">Seat #</th>
                    <th className="px-4 py-3">Status</th>
                    <th className="px-4 py-3">Booked By (Customer)</th>
                    <th className="px-4 py-3">Reservation Reference</th>
                    <th className="px-4 py-3">Price</th>
                    <th className="px-4 py-3 text-right">Action</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-gray-800/60 bg-gray-950/40">
                  {filteredRosterSeats.length > 0 ? (
                    filteredRosterSeats.map((seat) => {
                      const isOccupied = seat.status !== 'available';
                      return (
                        <tr
                          key={seat.seat_number}
                          className="hover:bg-gray-900/50 transition-colors cursor-pointer"
                          onClick={() => setInspectorSeat(seat)}
                        >
                          <td className="px-4 py-3 font-bold text-white font-mono">{seat.seat_number}</td>
                          <td className="px-4 py-3">
                            <span
                              className={`px-2 py-0.5 rounded text-[10px] uppercase font-bold ${
                                seat.status === 'confirmed'
                                  ? 'bg-purple-500/20 text-purple-300 border border-purple-500/30'
                                  : seat.status === 'held'
                                  ? 'bg-amber-500/20 text-amber-300 border border-amber-500/30'
                                  : 'bg-emerald-500/20 text-emerald-300 border border-emerald-500/30'
                              }`}
                            >
                              {seat.status}
                            </span>
                          </td>
                          <td className="px-4 py-3">
                            {seat.booked_by ? (
                              <div className="flex items-center gap-1.5 font-bold text-purple-200">
                                <UserCheck className="w-3.5 h-3.5 text-purple-400" />
                                <span className="font-mono">{seat.booked_by}</span>
                              </div>
                            ) : (
                              <span className="text-gray-500 italic">— Available —</span>
                            )}
                          </td>
                          <td className="px-4 py-3 font-mono text-[11px] text-gray-400">
                            {seat.reservation_id ? seat.reservation_id : '—'}
                          </td>
                          <td className="px-4 py-3 font-mono text-emerald-400">₹{priceRupees}</td>
                          <td className="px-4 py-3 text-right">
                            {isOccupied && seat.reservation_id && onCancelReservation ? (
                              <button
                                onClick={(e) => {
                                  e.stopPropagation();
                                  onCancelReservation(seat.reservation_id!);
                                }}
                                className="px-2.5 py-1 rounded bg-red-600/20 hover:bg-red-600 text-red-300 hover:text-white text-[10px] font-bold border border-red-500/30 transition-all cursor-pointer"
                              >
                                Revoke
                              </button>
                            ) : (
                              <span className="text-gray-600 text-[10px]">—</span>
                            )}
                          </td>
                        </tr>
                      );
                    })
                  ) : (
                    <tr>
                      <td colSpan={6} className="px-4 py-6 text-center text-gray-500">
                        No seats match the filter "{rosterSearch}"
                      </td>
                    </tr>
                  )}
                </tbody>
              </table>
            </div>
          )}
        </div>
      )}
    </div>
  );
};
