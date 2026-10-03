import React, { useState } from 'react';
import { Film, Sparkles } from 'lucide-react';
import { createShow, ShowResponse } from '../services/api';

interface ShowCreatorProps {
  onShowCreated: (show: ShowResponse) => void;
}

export const ShowCreator: React.FC<ShowCreatorProps> = ({ onShowCreated }) => {
  const [name, setName] = useState('Friday Night Rock Concert');
  const [rows, setRows] = useState(4);
  const [seatsPerRow, setSeatsPerRow] = useState(10);
  const [priceRupees, setPriceRupees] = useState(250);
  const [perUserLimit, setPerUserLimit] = useState(4);
  const [isSubmitting, setIsSubmitting] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setIsSubmitting(true);

    const seatList: string[] = [];
    const rowLetters = ['A', 'B', 'C', 'D', 'E', 'F', 'G', 'H'];
    for (let r = 0; r < Math.min(rows, rowLetters.length); r++) {
      for (let s = 1; s <= seatsPerRow; s++) {
        seatList.push(`${rowLetters[r]}${s}`);
      }
    }

    try {
      const created = await createShow({
        name,
        seats: seatList,
        price_paise: priceRupees * 100,
        per_user_limit: perUserLimit,
      });
      onShowCreated(created);
    } catch (err: any) {
      alert(`Failed to create show: ${err.message}`);
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div className="glass-panel p-6 rounded-2xl shadow-xl">
      <div className="flex items-center gap-2 mb-4">
        <Film className="w-5 h-5 text-purple-400" />
        <h3 className="font-bold text-lg text-white">Create New Event / Show</h3>
      </div>

      <form onSubmit={handleSubmit} className="space-y-4">
        <div>
          <label className="block text-xs font-semibold text-gray-400 mb-1">Event Name</label>
          <input
            type="text"
            required
            value={name}
            onChange={(e) => setName(e.target.value)}
            className="w-full px-3 py-2 bg-gray-900 border border-gray-700 rounded-lg text-sm text-white focus:outline-none focus:border-purple-500"
          />
        </div>

        <div className="grid grid-cols-2 gap-3">
          <div>
            <label className="block text-xs font-semibold text-gray-400 mb-1">Rows (A-H)</label>
            <input
              type="number"
              min="1"
              max="8"
              value={rows}
              onChange={(e) => setRows(Number(e.target.value))}
              className="w-full px-3 py-2 bg-gray-900 border border-gray-700 rounded-lg text-sm text-white focus:outline-none focus:border-purple-500"
            />
          </div>
          <div>
            <label className="block text-xs font-semibold text-gray-400 mb-1">Seats / Row</label>
            <input
              type="number"
              min="1"
              max="20"
              value={seatsPerRow}
              onChange={(e) => setSeatsPerRow(Number(e.target.value))}
              className="w-full px-3 py-2 bg-gray-900 border border-gray-700 rounded-lg text-sm text-white focus:outline-none focus:border-purple-500"
            />
          </div>
        </div>

        <div className="grid grid-cols-2 gap-3">
          <div>
            <label className="block text-xs font-semibold text-gray-400 mb-1">Price (₹ INR)</label>
            <input
              type="number"
              min="1"
              value={priceRupees}
              onChange={(e) => setPriceRupees(Number(e.target.value))}
              className="w-full px-3 py-2 bg-gray-900 border border-gray-700 rounded-lg text-sm text-white focus:outline-none focus:border-purple-500"
            />
          </div>
          <div>
            <label className="block text-xs font-semibold text-gray-400 mb-1">Per-User Limit</label>
            <input
              type="number"
              min="1"
              max="10"
              value={perUserLimit}
              onChange={(e) => setPerUserLimit(Number(e.target.value))}
              className="w-full px-3 py-2 bg-gray-900 border border-gray-700 rounded-lg text-sm text-white focus:outline-none focus:border-purple-500"
            />
          </div>
        </div>

        <button
          type="submit"
          disabled={isSubmitting}
          className="w-full py-2.5 px-4 rounded-xl bg-gradient-to-r from-purple-600 to-blue-600 hover:from-purple-500 hover:to-blue-500 font-bold text-sm text-white flex items-center justify-center gap-2 shadow-lg disabled:opacity-50 transition-all cursor-pointer"
        >
          <Sparkles className="w-4 h-4" />
          {isSubmitting ? 'Creating...' : `Publish Show (${rows * seatsPerRow} Seats)`}
        </button>
      </form>
    </div>
  );
};
