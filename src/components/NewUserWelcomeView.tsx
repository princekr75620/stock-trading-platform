import React from 'react';
import { User } from '../types';

interface NewUserWelcomeViewProps {
  user: User;
  onStartTrading: () => void;
  onViewPortfolio: () => void;
}

export const NewUserWelcomeView: React.FC<NewUserWelcomeViewProps> = ({
  user,
  onStartTrading,
  onViewPortfolio,
}) => {
  const balance = user.cashBalance ?? 50000.0;

  return (
    <div className="max-w-4xl mx-auto space-y-6 animate-in fade-in zoom-in-95 duration-200">
      {/* Hero Welcome Card with Indian Fintech Aesthetics */}
      <div className="bg-gradient-to-br from-slate-900 via-indigo-950 to-slate-900 text-white rounded-2xl p-6 sm:p-10 border border-slate-800 shadow-2xl relative overflow-hidden">
        {/* Tricolor accent bar */}
        <div className="absolute top-0 left-0 right-0 h-1 bg-gradient-to-r from-orange-500 via-white to-emerald-500"></div>

        <div className="relative z-10 flex flex-col md:flex-row md:items-center justify-between gap-6">
          <div className="space-y-3">
            <div className="inline-flex items-center space-x-2 px-3 py-1 rounded-full bg-orange-500/10 border border-orange-500/30 text-orange-400 text-xs font-bold">
              <span>🇮🇳 Bharat Stocks Onboarding</span>
              <span>&bull;</span>
              <span>SEBI Compliant</span>
            </div>

            <h1 className="text-2xl sm:text-4xl font-extrabold text-white tracking-tight">
              नमस्ते, {user.name}!
            </h1>

            <p className="text-slate-300 text-sm sm:text-base max-w-xl leading-relaxed">
              आपका डीमैट और ट्रेडिंग खाता सफलतापूर्वक सक्रिय हो गया है। आपका व्यक्तिगत ट्रेडिंग टर्मिनल अब लाइव है।
            </p>

            <div className="pt-2 flex flex-wrap gap-2 text-xs text-slate-400">
              <span className="px-2.5 py-1 rounded-lg bg-slate-800/80 border border-slate-700">
                Client ID: <strong className="text-white font-mono">{user.userId}</strong>
              </span>
              <span className="px-2.5 py-1 rounded-lg bg-slate-800/80 border border-slate-700">
                Email: <strong className="text-white">{user.email}</strong>
              </span>
              <span className="px-2.5 py-1 rounded-lg bg-emerald-500/10 border border-emerald-500/30 text-emerald-400 font-bold">
                Status: ACTIVE (KYC Verified)
              </span>
            </div>
          </div>

          {/* Balance Spotlight Card */}
          <div className="bg-slate-950/80 border border-slate-750 p-6 rounded-2xl shrink-0 text-center md:text-right shadow-xl">
            <span className="text-xs uppercase tracking-wider font-semibold text-slate-400">
              Credited Paper Margin
            </span>
            <div className="text-3xl sm:text-4xl font-black text-emerald-400 mt-1">
              ₹{balance.toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}
            </div>
            <p className="text-[11px] text-slate-400 mt-1.5">
              Ready for immediate order placement in NSE &amp; BSE equities
            </p>
          </div>
        </div>
      </div>

      {/* 3-Step Quick Start Guide */}
      <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
        {/* Step 1 */}
        <div className="bg-white border border-slate-200 rounded-xl p-5 shadow-xs hover:border-orange-300 transition space-y-2.5">
          <div className="w-8 h-8 rounded-lg bg-orange-100 text-orange-700 font-black flex items-center justify-center text-sm">
            1
          </div>
          <h3 className="font-bold text-slate-900 text-base">
            Explore Live Watchlist
          </h3>
          <p className="text-xs text-slate-600 leading-relaxed">
            Monitor real-time market prices of top Indian blue-chip equities: Reliance, TCS, HDFC Bank, Infosys, and Tata Motors.
          </p>
          <div className="pt-2">
            <span className="text-[11px] font-semibold text-orange-600">NSE / BSE Tickers &bull; 09:15 - 15:30 IST</span>
          </div>
        </div>

        {/* Step 2 */}
        <div className="bg-white border border-slate-200 rounded-xl p-5 shadow-xs hover:border-emerald-300 transition space-y-2.5">
          <div className="w-8 h-8 rounded-lg bg-emerald-100 text-emerald-700 font-black flex items-center justify-center text-sm">
            2
          </div>
          <h3 className="font-bold text-slate-900 text-base">
            Place Instant BUY &amp; SELL
          </h3>
          <p className="text-xs text-slate-600 leading-relaxed">
            Use our sub-second order ticket with automatic brokerage fee calculation (0.15%) and real-time margin validation.
          </p>
          <div className="pt-2">
            <span className="text-[11px] font-semibold text-emerald-600">Atomic ACID Execution &bull; Instant Confirmation</span>
          </div>
        </div>

        {/* Step 3 */}
        <div className="bg-white border border-slate-200 rounded-xl p-5 shadow-xs hover:border-blue-300 transition space-y-2.5">
          <div className="w-8 h-8 rounded-lg bg-blue-100 text-blue-700 font-black flex items-center justify-center text-sm">
            3
          </div>
          <h3 className="font-bold text-slate-900 text-base">
            Track Portfolio Returns (P&amp;L)
          </h3>
          <p className="text-xs text-slate-600 leading-relaxed">
            View your weighted average cost basis, unrealized profit &amp; loss, and asset allocation percentages dynamically in INR (₹).
          </p>
          <div className="pt-2">
            <span className="text-[11px] font-semibold text-blue-600">Weighted Average Cost &bull; Real-time Valuations</span>
          </div>
        </div>
      </div>

      {/* Action Buttons */}
      <div className="bg-slate-50 border border-slate-200 rounded-2xl p-6 flex flex-col sm:flex-row items-center justify-between gap-4">
        <div>
          <h4 className="font-bold text-slate-900 text-sm">
            Ready to make your first trade?
          </h4>
          <p className="text-xs text-slate-500 mt-0.5">
            Your ₹{balance.toLocaleString('en-IN', { maximumFractionDigits: 0 })} INR paper trading margin is loaded and ready.
          </p>
        </div>

        <div className="flex items-center space-x-3 w-full sm:w-auto">
          <button
            onClick={onViewPortfolio}
            className="flex-1 sm:flex-initial px-4 py-2.5 rounded-xl border border-slate-300 bg-white hover:bg-slate-50 text-slate-700 text-xs font-bold transition cursor-pointer shadow-xs"
          >
            View Portfolio (पोर्टफोलियो)
          </button>
          <button
            onClick={onStartTrading}
            className="flex-1 sm:flex-initial px-6 py-2.5 rounded-xl bg-gradient-to-r from-orange-500 to-amber-600 hover:from-orange-600 hover:to-amber-700 text-white text-xs font-extrabold transition shadow-md shadow-orange-500/20 flex items-center justify-center space-x-2 cursor-pointer"
          >
            <span>Start Trading (ट्रेडिंग शुरू करें)</span>
            <span>➔</span>
          </button>
        </div>
      </div>
    </div>
  );
};
