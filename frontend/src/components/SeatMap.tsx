import React from 'react';
import { SeatDto } from '../services/api';
import { CheckCircle2, Lock, ShieldCheck } from 'lucide-react';

interface SeatMapProps {
  seats: SeatDto[];
  selectedSeats: string[];
  pricePaise: number;
  perUserLimit: number;
  onToggleSeat: (seatNumber: string) => void;
  disabled?: boolean;
}

export const SeatMap: React.FC<SeatMapProps> = ({
  seats,
  selectedSeats,
  pricePaise,
  perUserLimit,
  onToggleSeat,
  disabled,
}) => {
  // Group seats by row prefix (e.g. 'A', 'B')
  const rows: { [key: string]: SeatDto[] } = {};
  seats.forEach((seat) => {
    const row = seat.seat_number.charAt(0);
    if (!rows[row]) rows[row] = [];
    rows[row].push(seat);
  });

  const priceRupees = (pricePaise / 100).toFixed(2);

  return (
    <div className="glass-panel p-6 rounded-2xl shadow-2xl relative overflow-hidden">
      {/* Background aesthetic glow */}
      <div className="absolute top-0 left-1/2 -translate-x-1/2 w-3/4 h-24 bg-gradient-to-b from-blue-600/15 via-purple-600/5 to-transparent blur-2xl pointer-events-none" />

      {/* Stage / Screen Representation */}
      <div className="flex flex-col items-center mb-8">
        <div className="w-4/5 h-2.5 bg-gradient-to-r from-blue-500 via-purple-500 to-blue-500 rounded-full shadow-[0_0_20px_rgba(59,130,246,0.6)] mb-2" />
        <span className="text-xs uppercase tracking-widest text-gray-400 font-semibold">STAGE / SCREEN</span>
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
                  bgClass = 'bg-amber-950/70 border-amber-500/50 text-amber-300 cursor-not-allowed opacity-90';
                } else if (isConfirmed) {
                  bgClass = 'bg-gray-800/80 border-gray-700 text-gray-500 cursor-not-allowed opacity-60';
                }

                return (
                  <button
                    key={seat.seat_number}
                    id={`seat-btn-${seat.seat_number}`}
                    disabled={disabled || (!isAvailable && !isSelected)}
                    onClick={() => onToggleSeat(seat.seat_number)}
                    title={`Seat ${seat.seat_number} — ${seat.status.toUpperCase()} (₹${priceRupees})`}
                    className={`relative w-10 h-10 rounded-xl border flex flex-col items-center justify-center font-bold text-xs transition-all duration-200 shadow-md ${bgClass}`}
                  >
                    <span>{seat.seat_number}</span>
                    {isConfirmed && <Lock className="w-2.5 h-2.5 mt-0.5 text-gray-400" />}
                    {isSelected && <CheckCircle2 className="w-2.5 h-2.5 mt-0.5 text-white" />}
                  </button>
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
          <div className="w-4 h-4 rounded-md bg-gray-800/80 border border-gray-700" />
          <span>Confirmed (Sold)</span>
        </div>
        <div className="flex items-center gap-1 text-gray-400">
          <ShieldCheck className="w-4 h-4 text-purple-400" />
          <span>Max Limit: {perUserLimit} seats/user</span>
        </div>
      </div>
    </div>
  );
};
