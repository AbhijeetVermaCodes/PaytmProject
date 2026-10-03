import React, { useEffect, useState } from 'react';
import { Activity, Database, ShieldCheck, Terminal, Server } from 'lucide-react';
import { fetchMetrics, fetchReadiness, ReadinessResponse, ShowResponse } from '../services/api';

interface ObservabilityHUDProps {
  show: ShowResponse | null;
  logs: string[];
}

export const ObservabilityHUD: React.FC<ObservabilityHUDProps> = ({ show, logs }) => {
  const [readiness, setReadiness] = useState<ReadinessResponse | null>(null);
  const [metricsText, setMetricsText] = useState<string>('');

  useEffect(() => {
    const pollHealth = async () => {
      try {
        const ready = await fetchReadiness();
        setReadiness(ready);
      } catch (e) {
        setReadiness({ status: 'DOWN', database: 'DISCONNECTED', timestamp: new Date().toISOString() });
      }

      try {
        const m = await fetchMetrics();
        setMetricsText(m);
      } catch (e) {
        // ignore
      }
    };

    pollHealth();
    const interval = setInterval(pollHealth, 5000);
    return () => clearInterval(interval);
  }, []);

  const total = show?.total_seats || 0;
  const available = show?.available_seats || 0;
  const held = show?.held_seats || 0;
  const confirmed = show?.confirmed_seats || 0;
  const invariantHolds = available + held + confirmed === total;

  return (
    <div className="glass-panel p-4 sm:p-6 rounded-2xl shadow-xl flex flex-col gap-6 w-full max-w-full overflow-hidden">
      <div className="flex flex-wrap items-center justify-between gap-2 border-b border-gray-800 pb-4">
        <div className="flex items-center gap-2">
          <Activity className="w-5 h-5 text-blue-400 animate-pulse shrink-0" />
          <h3 className="font-bold text-base sm:text-lg text-white">System Observability & Health</h3>
        </div>
        <span className="text-[11px] px-2.5 py-1 rounded-full bg-blue-500/10 border border-blue-500/30 text-blue-400 font-mono shrink-0">
          Live Telemetry
        </span>
      </div>

      {/* Status Cards */}
      <div className="grid grid-cols-1 sm:grid-cols-3 gap-3 sm:gap-4">
        {/* Database Readiness */}
        <div className="glass-card p-3 sm:p-4 rounded-xl">
          <div className="flex items-center gap-2 mb-2">
            <Database className="w-4 h-4 text-emerald-400 shrink-0" />
            <span className="text-xs text-gray-400 font-medium">DB Connection Pool</span>
          </div>
          <div className="flex items-center gap-2">
            <div className={`w-2.5 h-2.5 rounded-full shrink-0 ${readiness?.database === 'CONNECTED' ? 'bg-emerald-400 animate-ping' : 'bg-red-500'}`} />
            <span className="font-bold text-xs sm:text-sm text-white font-mono truncate">{readiness?.database || 'CHECKING...'}</span>
          </div>
        </div>

        {/* Reconciliation Invariant */}
        <div className="glass-card p-3 sm:p-4 rounded-xl">
          <div className="flex items-center gap-2 mb-2">
            <ShieldCheck className={`w-4 h-4 shrink-0 ${invariantHolds ? 'text-emerald-400' : 'text-red-400'}`} />
            <span className="text-xs text-gray-400 font-medium">Reconciliation Invariant</span>
          </div>
          <div className="flex items-center gap-2 flex-wrap">
            <span className={`text-[11px] px-2 py-0.5 rounded font-mono font-bold ${invariantHolds ? 'bg-emerald-950 text-emerald-300 border border-emerald-500/50' : 'bg-red-950 text-red-300'}`}>
              {invariantHolds ? '100% RECONCILED' : 'VIOLATION'}
            </span>
          </div>
          <span className="text-[10px] text-gray-500 mt-1 block font-mono truncate">
            {available} + {held} + {confirmed} == {total}
          </span>
        </div>

        {/* Prometheus Scrape Status */}
        <div className="glass-card p-3 sm:p-4 rounded-xl">
          <div className="flex items-center gap-2 mb-2">
            <Server className="w-4 h-4 text-purple-400 shrink-0" />
            <span className="text-xs text-gray-400 font-medium">Prometheus Metrics</span>
          </div>
          <span className="text-xs text-purple-300 font-mono font-semibold block truncate">
            {metricsText.length > 0 ? `${metricsText.split('\n').filter(l => !l.startsWith('#') && l.trim()).length} series` : '/metrics active'}
          </span>
          <span className="text-[10px] text-gray-500 mt-1 block">Live Prometheus Scrape</span>
        </div>
      </div>

      {/* Live Event Stream Console */}
      <div className="w-full">
        <div className="flex items-center gap-2 mb-2">
          <Terminal className="w-4 h-4 text-gray-400 shrink-0" />
          <span className="text-xs font-bold text-gray-300 uppercase tracking-wider">Live Transaction Log</span>
        </div>
        <div className="bg-black/70 border border-gray-800 rounded-xl p-3 h-40 overflow-y-auto font-mono text-xs text-gray-300 space-y-1 w-full max-w-full scrollbar-thin">
          {logs.length === 0 ? (
            <span className="text-gray-600 italic block">No reservation activity yet. Book a seat or trigger a burst to see real-time events.</span>
          ) : (
            logs.map((log, index) => (
              <div key={index} className="leading-relaxed break-words break-all text-[11px]">
                <span className="text-gray-500 text-[10px]">[{new Date().toLocaleTimeString()}]</span> {log}
              </div>
            ))
          )}
        </div>
      </div>
    </div>
  );
};
