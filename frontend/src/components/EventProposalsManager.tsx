import React, { useState, useEffect } from 'react';
import {
  FileText,
  CheckCircle,
  XCircle,
  ExternalLink,
  RefreshCw,
  User,
  ShieldCheck,
  AlertCircle
} from 'lucide-react';
import {
  EventProposal,
  fetchEventProposals,
  approveEventProposal,
  rejectEventProposal
} from '../services/api';
import { showAlert } from '../utils/alert';

interface EventProposalsManagerProps {
  userToken: string;
  isAdmin: boolean;
  onShowSelect?: (showId: string) => void;
  onShowApproved?: () => void;
}

export const EventProposalsManager: React.FC<EventProposalsManagerProps> = ({
  userToken,
  isAdmin,
  onShowSelect,
  onShowApproved,
}) => {
  const [proposals, setProposals] = useState<EventProposal[]>([]);
  const [loading, setLoading] = useState<boolean>(false);
  const [statusFilter, setStatusFilter] = useState<'ALL' | 'PENDING' | 'APPROVED' | 'REJECTED'>('ALL');
  const [actionLoadingId, setActionLoadingId] = useState<string | null>(null);

  const loadProposals = async () => {
    setLoading(true);
    try {
      const data = await fetchEventProposals(userToken);
      setProposals(data);
    } catch (err: any) {
      showAlert.error('Failed to load proposals', err.message);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadProposals();
  }, [userToken, isAdmin]);

  const handleApprove = async (proposal: EventProposal) => {
    const confirmed = await showAlert.confirm(
      'Approve & Publish Event?',
      `This will immediately create and publish '${proposal.name}' with ${proposal.seats.length} seats. ${proposal.requested_by} will become the designated Event Manager.`,
      'Approve & Launch'
    );
    if (!confirmed) return;

    setActionLoadingId(proposal.id);
    try {
      const updated = await approveEventProposal(proposal.id, userToken);
      showAlert.success(
        'Event Approved & Published! 🚀',
        `'${updated.name}' is now active! Organizer '${updated.requested_by}' has full manager rights.`
      );
      await loadProposals();
      if (onShowApproved) {
        onShowApproved();
      }
      if (updated.show_id && onShowSelect) {
        onShowSelect(updated.show_id);
      }
    } catch (err: any) {
      showAlert.error('Approval Failed', err.message);
    } finally {
      setActionLoadingId(null);
    }
  };

  const handleReject = async (proposal: EventProposal) => {
    const reason = await showAlert.prompt(
      'Reject Event Proposal',
      `Please provide a reason for rejecting '${proposal.name}':`,
      'e.g. Incomplete details, venue conflict...'
    );

    if (reason === null) return; // User cancelled

    setActionLoadingId(proposal.id);
    try {
      await rejectEventProposal(proposal.id, reason || 'Rejected by administrator', userToken);
      showAlert.toast('Event proposal rejected', 'info');
      await loadProposals();
    } catch (err: any) {
      showAlert.error('Rejection Failed', err.message);
    } finally {
      setActionLoadingId(null);
    }
  };

  const filteredProposals = proposals.filter((p) => {
    if (statusFilter === 'ALL') return true;
    return p.status === statusFilter;
  });

  const pendingCount = proposals.filter((p) => p.status === 'PENDING').length;

  return (
    <div className="glass-panel p-6 rounded-2xl shadow-xl space-y-6">
      <div className="flex flex-wrap items-center justify-between gap-3 border-b border-gray-800 pb-4">
        <div className="flex items-center gap-3">
          <div className="w-10 h-10 rounded-xl bg-purple-500/20 border border-purple-500/30 flex items-center justify-center text-purple-400">
            <FileText className="w-5 h-5" />
          </div>
          <div>
            <h3 className="font-bold text-lg text-white flex items-center gap-2">
              {isAdmin ? 'Admin Event Approval Queue' : 'My Event Proposals'}
              {pendingCount > 0 && (
                <span className="px-2 py-0.5 rounded-full text-xs font-bold bg-amber-500/20 text-amber-400 border border-amber-500/30">
                  {pendingCount} Pending
                </span>
              )}
            </h3>
            <p className="text-xs text-gray-400">
              {isAdmin
                ? 'Review user-submitted event requests. Approved events grant manager privileges to the creator.'
                : 'Submit event requests for Admin review. Once approved, you become the Event Manager!'}
            </p>
          </div>
        </div>

        <button
          onClick={loadProposals}
          disabled={loading}
          className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-gray-800 hover:bg-gray-700 text-gray-300 text-xs font-semibold transition cursor-pointer"
        >
          <RefreshCw className={`w-3.5 h-3.5 ${loading ? 'animate-spin' : ''}`} />
          Refresh
        </button>
      </div>

      {/* Filter Tabs */}
      <div className="flex items-center gap-2">
        {(['ALL', 'PENDING', 'APPROVED', 'REJECTED'] as const).map((tab) => (
          <button
            key={tab}
            onClick={() => setStatusFilter(tab)}
            className={`px-3 py-1.5 rounded-lg text-xs font-bold transition cursor-pointer ${
              statusFilter === tab
                ? 'bg-purple-600 text-white shadow'
                : 'bg-gray-800/80 hover:bg-gray-800 text-gray-400 hover:text-gray-200'
            }`}
          >
            {tab}
            {tab === 'PENDING' && pendingCount > 0 && ` (${pendingCount})`}
          </button>
        ))}
      </div>

      {/* Proposals List */}
      {loading && proposals.length === 0 ? (
        <div className="text-center py-10 text-gray-400 flex flex-col items-center gap-2">
          <RefreshCw className="w-6 h-6 animate-spin text-purple-400" />
          <p className="text-sm">Fetching event proposals...</p>
        </div>
      ) : filteredProposals.length === 0 ? (
        <div className="text-center py-10 bg-gray-900/40 rounded-xl border border-dashed border-gray-800 text-gray-400 space-y-2">
          <FileText className="w-8 h-8 mx-auto text-gray-600" />
          <p className="text-sm font-medium">No event proposals found</p>
          <p className="text-xs text-gray-500">
            {isAdmin
              ? 'No proposals in this status queue.'
              : 'Submit your first event proposal using the Propose Event tab!'}
          </p>
        </div>
      ) : (
        <div className="space-y-3 max-h-[500px] overflow-y-auto pr-1">
          {filteredProposals.map((proposal) => {
            const isPending = proposal.status === 'PENDING';
            const isApproved = proposal.status === 'APPROVED';
            const isRejected = proposal.status === 'REJECTED';
            const isWorking = actionLoadingId === proposal.id;

            return (
              <div
                key={proposal.id}
                className="p-4 rounded-xl bg-gray-900/60 border border-gray-800 hover:border-gray-700 transition space-y-3"
              >
                <div className="flex flex-wrap items-start justify-between gap-2">
                  <div>
                    <div className="flex items-center gap-2">
                      <h4 className="font-bold text-white text-base">{proposal.name}</h4>
                      <span
                        className={`px-2 py-0.5 rounded-full text-xs font-bold border ${
                          isPending
                            ? 'bg-amber-500/20 text-amber-400 border-amber-500/30'
                            : isApproved
                            ? 'bg-green-500/20 text-green-400 border-green-500/30'
                            : 'bg-red-500/20 text-red-400 border-red-500/30'
                        }`}
                      >
                        {proposal.status}
                      </span>
                    </div>
                    <div className="flex flex-wrap items-center gap-3 text-xs text-gray-400 mt-1">
                      <span className="flex items-center gap-1">
                        <User className="w-3.5 h-3.5 text-purple-400" />
                        Requested by: <strong className="text-gray-200">{proposal.requested_by}</strong>
                      </span>
                      <span>•</span>
                      <span>{proposal.seats.length} seats</span>
                      <span>•</span>
                      <span>₹{(proposal.price_paise / 100).toFixed(2)}/seat</span>
                      <span>•</span>
                      <span>Max {proposal.per_user_limit} seats/user</span>
                    </div>
                  </div>

                  {/* Action Buttons for Admin or Quick Switch for Approved */}
                  <div className="flex items-center gap-2">
                    {isAdmin && isPending && (
                      <>
                        <button
                          onClick={() => handleApprove(proposal)}
                          disabled={isWorking}
                          className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-green-600 hover:bg-green-500 text-white text-xs font-bold shadow transition cursor-pointer disabled:opacity-50"
                        >
                          <CheckCircle className="w-3.5 h-3.5" />
                          {isWorking ? 'Processing...' : 'Approve & Launch'}
                        </button>
                        <button
                          onClick={() => handleReject(proposal)}
                          disabled={isWorking}
                          className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-red-600/80 hover:bg-red-600 text-white text-xs font-bold shadow transition cursor-pointer disabled:opacity-50"
                        >
                          <XCircle className="w-3.5 h-3.5" />
                          Reject
                        </button>
                      </>
                    )}

                    {isApproved && proposal.show_id && onShowSelect && (
                      <button
                        onClick={() => onShowSelect(proposal.show_id!)}
                        className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-purple-600/30 hover:bg-purple-600/50 text-purple-300 border border-purple-500/30 text-xs font-bold transition cursor-pointer"
                      >
                        <ExternalLink className="w-3.5 h-3.5" />
                        View Live Show
                      </button>
                    )}
                  </div>
                </div>

                {/* Additional Info Box for Rejection or Approval */}
                {isRejected && proposal.rejection_reason && (
                  <div className="p-2.5 rounded-lg bg-red-950/30 border border-red-800/40 text-xs text-red-300 flex items-start gap-2">
                    <AlertCircle className="w-4 h-4 text-red-400 shrink-0 mt-0.5" />
                    <div>
                      <strong>Rejection Reason:</strong> {proposal.rejection_reason}
                    </div>
                  </div>
                )}

                {isApproved && (
                  <div className="p-2 rounded-lg bg-green-950/20 border border-green-800/30 text-xs text-green-300 flex items-center justify-between">
                    <span className="flex items-center gap-1.5">
                      <ShieldCheck className="w-4 h-4 text-green-400" />
                      Approved by {proposal.reviewed_by || 'Admin'}. Event Manager:{' '}
                      <strong>{proposal.requested_by}</strong>
                    </span>
                    <span className="text-gray-400 text-[10px]">
                      {new Date(proposal.created_at).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
                    </span>
                  </div>
                )}
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
};
