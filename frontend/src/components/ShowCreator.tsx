import React, { useState } from 'react';
import { Film, Sparkles, Send, ShieldCheck, Clock } from 'lucide-react';
import { createShow, submitEventProposal, ShowResponse, EventProposal } from '../services/api';
import { showAlert } from '../utils/alert';

interface ShowCreatorProps {
  userToken: string;
  isAdmin: boolean;
  onShowCreated: (show: ShowResponse) => void;
  onProposalSubmitted?: (proposal: EventProposal) => void;
}

export const ShowCreator: React.FC<ShowCreatorProps> = ({
  userToken,
  isAdmin,
  onShowCreated,
  onProposalSubmitted,
}) => {
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
      if (isAdmin) {
        // Direct creation by Administrator
        const created = await createShow(
          {
            name,
            seats: seatList,
            price_paise: priceRupees * 100,
            per_user_limit: perUserLimit,
          },
          userToken
        );
        showAlert.success(
          'Show Published Successfully! 🎟️',
          `'${created.name}' is now live with ${created.total_seats} seats ready for on-sale booking.`
        );
        onShowCreated(created);
      } else {
        // Regular user proposal submission
        const proposal = await submitEventProposal(
          {
            name,
            seats: seatList,
            price_paise: priceRupees * 100,
            per_user_limit: perUserLimit,
          },
          userToken
        );
        showAlert.success(
          'Event Proposal Submitted! 📋',
          `Your request for '${proposal.name}' (${proposal.seats.length} seats) has been submitted for Administrator review. Once approved, you will have full manager rights over this event!`
        );
        if (onProposalSubmitted) {
          onProposalSubmitted(proposal);
        }
      }
    } catch (err: any) {
      showAlert.error(isAdmin ? 'Show Creation Failed' : 'Proposal Submission Failed', err.message);
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div className="glass-panel p-6 rounded-2xl shadow-xl space-y-4">
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-2">
          <Film className="w-5 h-5 text-purple-400" />
          <h3 className="font-bold text-lg text-white">
            {isAdmin ? 'Direct Show Creation (Admin)' : 'Propose New Event'}
          </h3>
        </div>
        <span
          className={`px-2.5 py-1 rounded-full text-xs font-bold border flex items-center gap-1 ${
            isAdmin
              ? 'bg-purple-500/20 text-purple-300 border-purple-500/40'
              : 'bg-amber-500/20 text-amber-300 border-amber-500/40'
          }`}
        >
          {isAdmin ? (
            <>
              <ShieldCheck className="w-3.5 h-3.5 text-purple-400" />
              Instant Publish Mode
            </>
          ) : (
            <>
              <Clock className="w-3.5 h-3.5 text-amber-400" />
              Requires Admin Approval
            </>
          )}
        </span>
      </div>

      {!isAdmin && (
        <div className="p-3 rounded-xl bg-purple-950/20 border border-purple-800/30 text-xs text-purple-300">
          💡 <strong>Role Note:</strong> Regular users submit proposals. Once approved by an Admin,
          your event goes live and you automatically become the <strong>Event Manager</strong> with
          seat roster inspection and booking management privileges!
        </div>
      )}

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
          className={`w-full py-2.5 px-4 rounded-xl font-bold text-sm text-white flex items-center justify-center gap-2 shadow-lg disabled:opacity-50 transition-all cursor-pointer ${
            isAdmin
              ? 'bg-gradient-to-r from-purple-600 to-blue-600 hover:from-purple-500 hover:to-blue-500'
              : 'bg-gradient-to-r from-amber-600 to-purple-600 hover:from-amber-500 hover:to-purple-500'
          }`}
        >
          {isAdmin ? <Sparkles className="w-4 h-4" /> : <Send className="w-4 h-4" />}
          {isSubmitting
            ? isAdmin
              ? 'Publishing...'
              : 'Submitting Proposal...'
            : isAdmin
            ? `Publish Show Directly (${rows * seatsPerRow} Seats)`
            : `Submit Event Request (${rows * seatsPerRow} Seats)`}
        </button>
      </form>
    </div>
  );
};
