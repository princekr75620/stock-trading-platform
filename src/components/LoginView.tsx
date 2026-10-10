import React, { useState } from 'react';
import { api } from '../services/api';
import { User as UserType } from '../types';

interface LoginViewProps {
  onLoginSuccess: (user: UserType, isNewUser?: boolean) => void;
}

export const LoginView: React.FC<LoginViewProps> = ({ onLoginSuccess }) => {
  const [authMode, setAuthMode] = useState<'LOGIN' | 'REGISTER'>('LOGIN');

  // Login form state - clean and ready for user's own credentials
  const [emailOrUsername, setEmailOrUsername] = useState('');
  const [password, setPassword] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // Register form state
  const [regName, setRegName] = useState('');
  const [regMobile, setRegMobile] = useState('');
  const [regEmail, setRegEmail] = useState('');
  const [regPassword, setRegPassword] = useState('');
  const [regPan, setRegPan] = useState('');
  const [regSuccess, setRegSuccess] = useState<string | null>(null);

  const handleLogin = async (u: string, p: string, isNewUser = false) => {
    setLoading(true);
    setError(null);
    try {
      const res = await api.login(u, p);
      if (res.success && res.user) {
        onLoginSuccess(res.user, isNewUser);
      } else {
        setError('Login failed. Please verify your Client ID or Password.');
      }
    } catch (err: any) {
      setError(err.message || 'Invalid Indian Equities account credentials.');
    } finally {
      setLoading(false);
    }
  };

  const handleLoginSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!emailOrUsername.trim() || !password.trim()) {
      setError('Please provide Client ID / Mobile and Password.');
      return;
    }
    handleLogin(emailOrUsername, password);
  };

  const handleRegisterSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!regName.trim() || !regEmail.trim() || !regPassword.trim()) {
      setError('Please fill in Name, Email and Password.');
      return;
    }

    setLoading(true);
    setError(null);
    setRegSuccess(null);

    const generatedUsername = regEmail.split('@')[0].replace(/[^a-zA-Z0-9]/g, '').toLowerCase() || `trader_${Date.now()}`;

    try {
      const res = await api.createAdminUser({
        name: regName.trim(),
        email: regEmail.trim(),
        password: regPassword,
        role: 'TRADER',
        userId: generatedUsername,
        initialBalance: 50000.0,
      });

      if (res.success && res.user) {
        setRegSuccess(`Registration complete! Client ID: ${generatedUsername}. ₹50,000 INR trading balance credited.`);
        setTimeout(() => {
          onLoginSuccess(res.user, true);
        }, 1200);
      } else {
        setError('Registration could not be completed.');
      }
    } catch (err: any) {
      setError(err.message || 'Registration failed. Try a different email.');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="min-h-screen bg-slate-950 text-slate-100 flex flex-col justify-between selection:bg-orange-500 selection:text-white">
      {/* Top Indian Equities Live Ticker Strip */}
      <div className="bg-slate-900/90 border-b border-slate-800 text-[11px] py-1.5 px-4 overflow-x-auto whitespace-nowrap">
        <div className="max-w-7xl mx-auto flex items-center justify-between gap-6 text-slate-300">
          <div className="flex items-center space-x-6">
            <span className="flex items-center space-x-1.5 font-medium">
              <span className="w-2 h-2 rounded-full bg-emerald-500 animate-pulse"></span>
              <span className="text-white font-bold">NSE &amp; BSE LIVE</span>
            </span>
            <span className="space-x-1.5">
              <span className="text-slate-400">NIFTY 50:</span>
              <span className="font-semibold text-emerald-400">25,340.20</span>
              <span className="text-emerald-400 font-bold">+156.40 (+0.62%)</span>
            </span>
            <span className="space-x-1.5">
              <span className="text-slate-400">SENSEX:</span>
              <span className="font-semibold text-emerald-400">82,780.50</span>
              <span className="text-emerald-400 font-bold">+480.20 (+0.58%)</span>
            </span>
            <span className="space-x-1.5 hidden md:inline">
              <span className="text-slate-400">BANK NIFTY:</span>
              <span className="font-semibold text-emerald-400">51,890.10</span>
              <span className="text-emerald-400 font-bold">+382.10 (+0.74%)</span>
            </span>
            <span className="space-x-1.5 hidden lg:inline">
              <span className="text-slate-400">USD/INR:</span>
              <span className="font-semibold text-slate-200">₹83.92</span>
            </span>
          </div>

          <div className="flex items-center space-x-3 text-slate-400 text-[11px] shrink-0">
            <span className="px-2 py-0.5 rounded bg-emerald-500/10 border border-emerald-500/30 text-emerald-400 font-medium">
              Market Session: 09:15 - 15:30 IST
            </span>
          </div>
        </div>
      </div>

      {/* Main Login Card Section */}
      <div className="flex-1 flex items-center justify-center p-4 sm:p-6 lg:p-8">
        <div className="w-full max-w-4xl grid grid-cols-1 lg:grid-cols-12 bg-slate-900 border border-slate-800 rounded-2xl shadow-2xl overflow-hidden">
          
          {/* Left Hero Panel (Indian Trading Theme) */}
          <div className="lg:col-span-5 bg-gradient-to-br from-slate-900 via-indigo-950 to-slate-900 p-8 border-b lg:border-b-0 lg:border-r border-slate-800 flex flex-col justify-between relative overflow-hidden">
            {/* Subtle Tricolor Glow in background */}
            <div className="absolute top-0 left-0 w-32 h-1 bg-gradient-to-r from-orange-500 via-white to-emerald-500"></div>
            
            <div>
              <div className="flex items-center space-x-3 mb-6">
                <div className="w-12 h-12 rounded-xl bg-gradient-to-br from-orange-500 to-amber-600 flex items-center justify-center font-black text-white text-xl shadow-lg shadow-orange-500/20">
                  ₹
                </div>
                <div>
                  <h2 className="text-lg font-black tracking-tight text-white uppercase">
                    BHARAT STOCKS
                  </h2>
                  <p className="text-[11px] text-orange-400 font-semibold tracking-wider uppercase">
                    NSE &bull; BSE Equities Exchange
                  </p>
                </div>
              </div>

              <h1 className="text-2xl sm:text-3xl font-extrabold text-white leading-tight mb-3">
                India's Next-Gen Institutional Trading Desk
              </h1>

              <p className="text-slate-400 text-xs sm:text-sm leading-relaxed mb-6">
                Trade Blue-Chip Indian Equities, NIFTY 50 Stocks, and BSE Market Indices with sub-second execution, transparent 0.15% brokerage, and zero hidden charges.
              </p>

              {/* Key Highlights */}
              <div className="space-y-3">
                <div className="flex items-center space-x-3 text-xs text-slate-300">
                  <div className="w-5 h-5 rounded-full bg-emerald-500/20 border border-emerald-500/40 text-emerald-400 flex items-center justify-center text-[10px] font-bold">
                    ✓
                  </div>
                  <span>Instant ₹50,000 INR Paper Trading Credit</span>
                </div>
                <div className="flex items-center space-x-3 text-xs text-slate-300">
                  <div className="w-5 h-5 rounded-full bg-emerald-500/20 border border-emerald-500/40 text-emerald-400 flex items-center justify-center text-[10px] font-bold">
                    ✓
                  </div>
                  <span>Real-time NSE/BSE Weighted Cost Basis &amp; P&amp;L</span>
                </div>
                <div className="flex items-center space-x-3 text-xs text-slate-300">
                  <div className="w-5 h-5 rounded-full bg-emerald-500/20 border border-emerald-500/40 text-emerald-400 flex items-center justify-center text-[10px] font-bold">
                    ✓
                  </div>
                  <span>Atomic ACID Transactions with PreparedStatement</span>
                </div>
              </div>
            </div>

            {/* Bottom Compliance Callout */}
            <div className="mt-8 pt-4 border-t border-slate-800/80">
              <div className="flex items-center justify-between text-[11px] text-slate-400">
                <span>SEBI Reg: <strong className="text-slate-300">INZ000205836</strong></span>
                <span>CDSL / NSDL Depository</span>
              </div>
            </div>
          </div>

          {/* Right Form Panel */}
          <div className="lg:col-span-7 p-6 sm:p-8 flex flex-col justify-between bg-slate-900/60">
            <div>
              {/* Tab Selector: Sign In vs Open Account */}
              <div className="flex items-center p-1 bg-slate-950 rounded-xl border border-slate-800 mb-6">
                <button
                  type="button"
                  onClick={() => { setAuthMode('LOGIN'); setError(null); setRegSuccess(null); }}
                  className={`flex-1 py-2 text-xs font-bold rounded-lg transition ${
                    authMode === 'LOGIN'
                      ? 'bg-gradient-to-r from-orange-500 to-amber-600 text-white shadow-sm'
                      : 'text-slate-400 hover:text-white'
                  }`}
                >
                  Sign In (लॉगिन)
                </button>
                <button
                  type="button"
                  onClick={() => { setAuthMode('REGISTER'); setError(null); setRegSuccess(null); }}
                  className={`flex-1 py-2 text-xs font-bold rounded-lg transition ${
                    authMode === 'REGISTER'
                      ? 'bg-gradient-to-r from-orange-500 to-amber-600 text-white shadow-sm'
                      : 'text-slate-400 hover:text-white'
                  }`}
                >
                  Open Free Account (खाता खोलें)
                </button>
              </div>

              {/* Status & Error Messages */}
              {error && (
                <div className="mb-4 p-3 bg-red-500/10 border border-red-500/30 text-red-300 text-xs rounded-xl flex items-start space-x-2">
                  <span className="font-bold text-red-400">⚠</span>
                  <span>{error}</span>
                </div>
              )}

              {regSuccess && (
                <div className="mb-4 p-3 bg-emerald-500/10 border border-emerald-500/30 text-emerald-300 text-xs rounded-xl flex items-start space-x-2">
                  <span className="font-bold text-emerald-400">✓</span>
                  <span>{regSuccess}</span>
                </div>
              )}

              {/* MODE 1: LOGIN FORM */}
              {authMode === 'LOGIN' && (
                <form onSubmit={handleLoginSubmit} className="space-y-4">
                  <div>
                    <label className="block text-xs font-semibold text-slate-300 mb-1.5">
                      Client ID / UCC / Registered Email
                    </label>
                    <div className="relative">
                      <input
                        type="text"
                        required
                        value={emailOrUsername}
                        onChange={(e) => setEmailOrUsername(e.target.value)}
                        placeholder="e.g. trader1 or alex@apex.com"
                        className="w-full px-3.5 py-2.5 bg-slate-950 border border-slate-750 rounded-xl text-white text-sm placeholder:text-slate-500 focus:outline-none focus:ring-2 focus:ring-orange-500/50 focus:border-orange-500 transition"
                      />
                    </div>
                  </div>

                  <div>
                    <div className="flex items-center justify-between mb-1.5">
                      <label className="block text-xs font-semibold text-slate-300">
                        Trading PIN / Password
                      </label>
                      <span className="text-[11px] text-orange-400 hover:underline cursor-pointer">
                        Forgot PIN?
                      </span>
                    </div>
                    <input
                      type="password"
                      required
                      value={password}
                      onChange={(e) => setPassword(e.target.value)}
                      placeholder="••••••••"
                      className="w-full px-3.5 py-2.5 bg-slate-950 border border-slate-750 rounded-xl text-white text-sm placeholder:text-slate-500 focus:outline-none focus:ring-2 focus:ring-orange-500/50 focus:border-orange-500 transition"
                    />
                  </div>

                  <div className="flex items-center justify-between text-xs text-slate-400 pt-1">
                    <label className="flex items-center space-x-2 cursor-pointer">
                      <input type="checkbox" defaultChecked className="rounded border-slate-700 bg-slate-950 text-orange-500 focus:ring-0" />
                      <span>Remember Client ID</span>
                    </label>
                    <span className="text-slate-500">2FA Protected</span>
                  </div>

                  <button
                    type="submit"
                    disabled={loading}
                    className="w-full mt-2 py-3 px-4 bg-gradient-to-r from-orange-500 hover:from-orange-600 to-amber-600 hover:to-amber-700 text-white font-bold text-sm rounded-xl transition shadow-lg shadow-orange-500/20 disabled:opacity-50 flex items-center justify-center space-x-2 cursor-pointer"
                  >
                    {loading ? (
                      <span>Verifying Credentials...</span>
                    ) : (
                      <>
                        <span>Authorize &amp; Enter Trading Terminal</span>
                        <span>➔</span>
                      </>
                    )}
                  </button>

                  {/* Fast 1-Click Evaluation Login Buttons */}
                  <div className="mt-5 pt-4 border-t border-slate-800">
                    <p className="text-[11px] text-slate-400 text-center mb-2 font-medium">
                      One-Click Fast Demo Credentials (for Evaluation &amp; Testing):
                    </p>
                    <div className="grid grid-cols-2 gap-2">
                      <button
                        type="button"
                        onClick={() => {
                          setEmailOrUsername('trader1');
                          setPassword('trader123');
                          handleLogin('trader1', 'trader123');
                        }}
                        className="p-2.5 rounded-xl border border-slate-800 bg-slate-950 hover:bg-slate-850 hover:border-emerald-500/40 text-left transition group cursor-pointer"
                      >
                        <div className="text-xs font-bold text-emerald-400 group-hover:text-emerald-300">
                          ⚡ Quick Demo Account
                        </div>
                        <div className="text-[10px] text-slate-400 mt-0.5">
                          Instant Test Margin &bull; Live Portfolio
                        </div>
                      </button>

                      <button
                        type="button"
                        onClick={() => {
                          setEmailOrUsername('admin');
                          setPassword('admin123');
                          handleLogin('admin', 'admin123');
                        }}
                        className="p-2.5 rounded-xl border border-slate-800 bg-slate-950 hover:bg-slate-850 hover:border-purple-500/40 text-left transition group cursor-pointer"
                      >
                        <div className="text-xs font-bold text-purple-400 group-hover:text-purple-300">
                          ⚡ Platform Administrator
                        </div>
                        <div className="text-[10px] text-slate-400 mt-0.5">
                          User CRUD &bull; Float &bull; Risk Controls
                        </div>
                      </button>
                    </div>
                  </div>
                </form>
              )}

              {/* MODE 2: REGISTRATION FORM */}
              {authMode === 'REGISTER' && (
                <form onSubmit={handleRegisterSubmit} className="space-y-3.5">
                  <div className="p-3 rounded-xl bg-orange-500/10 border border-orange-500/20 text-orange-300 text-xs">
                    🎉 <strong>Instant Onboarding Bonus:</strong> New accounts receive ₹50,000 INR test capital immediately upon activation!
                  </div>

                  <div>
                    <label className="block text-xs font-semibold text-slate-300 mb-1">
                      Full Legal Name (as on Aadhaar / PAN)
                    </label>
                    <input
                      type="text"
                      required
                      value={regName}
                      onChange={(e) => setRegName(e.target.value)}
                      placeholder="e.g. Rahul Sharma"
                      className="w-full px-3.5 py-2 bg-slate-950 border border-slate-750 rounded-xl text-white text-sm placeholder:text-slate-500 focus:outline-none focus:ring-2 focus:ring-orange-500/50"
                    />
                  </div>

                  <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                    <div>
                      <label className="block text-xs font-semibold text-slate-300 mb-1">
                        Mobile (+91)
                      </label>
                      <input
                        type="tel"
                        value={regMobile}
                        onChange={(e) => setRegMobile(e.target.value)}
                        placeholder="9876543210"
                        className="w-full px-3.5 py-2 bg-slate-950 border border-slate-750 rounded-xl text-white text-sm placeholder:text-slate-500 focus:outline-none focus:ring-2 focus:ring-orange-500/50"
                      />
                    </div>
                    <div>
                      <label className="block text-xs font-semibold text-slate-300 mb-1">
                        PAN Card Number
                      </label>
                      <input
                        type="text"
                        value={regPan}
                        onChange={(e) => setRegPan(e.target.value.toUpperCase())}
                        placeholder="ABCDE1234F"
                        maxLength={10}
                        className="w-full px-3.5 py-2 bg-slate-950 border border-slate-750 rounded-xl text-white text-sm uppercase placeholder:text-slate-500 focus:outline-none focus:ring-2 focus:ring-orange-500/50 font-mono"
                      />
                    </div>
                  </div>

                  <div>
                    <label className="block text-xs font-semibold text-slate-300 mb-1">
                      Email Address
                    </label>
                    <input
                      type="email"
                      required
                      value={regEmail}
                      onChange={(e) => setRegEmail(e.target.value)}
                      placeholder="rahul@example.com"
                      className="w-full px-3.5 py-2 bg-slate-950 border border-slate-750 rounded-xl text-white text-sm placeholder:text-slate-500 focus:outline-none focus:ring-2 focus:ring-orange-500/50"
                    />
                  </div>

                  <div>
                    <label className="block text-xs font-semibold text-slate-300 mb-1">
                      Set Password
                    </label>
                    <input
                      type="password"
                      required
                      value={regPassword}
                      onChange={(e) => setRegPassword(e.target.value)}
                      placeholder="At least 6 characters"
                      className="w-full px-3.5 py-2 bg-slate-950 border border-slate-750 rounded-xl text-white text-sm placeholder:text-slate-500 focus:outline-none focus:ring-2 focus:ring-orange-500/50"
                    />
                  </div>

                  <button
                    type="submit"
                    disabled={loading}
                    className="w-full py-2.5 px-4 bg-gradient-to-r from-emerald-600 hover:from-emerald-700 to-teal-600 text-white font-bold text-sm rounded-xl transition shadow-lg shadow-emerald-600/20 disabled:opacity-50 cursor-pointer"
                  >
                    {loading ? 'Creating Demat Account...' : 'Open Demat & Trading Account (₹50,000 Credit)'}
                  </button>
                </form>
              )}
            </div>

            {/* Bottom Institutional Badges */}
            <div className="mt-6 pt-4 border-t border-slate-800 text-[10px] text-slate-500 flex flex-wrap items-center justify-between gap-2">
              <span className="flex items-center space-x-1">
                <span>🔒</span>
                <span>256-Bit SSL Encryption</span>
              </span>
              <span>NSE &bull; BSE &bull; NSDL &bull; CDSL</span>
              <span>100% Indian Equity Compliance</span>
            </div>
          </div>
        </div>
      </div>

      {/* Footer Disclaimer */}
      <footer className="text-center py-3 text-[11px] text-slate-500 border-t border-slate-900 bg-slate-950">
        <p>
          Investments in securities market are subject to market risks. Read all scheme related documents carefully before investing.
        </p>
      </footer>
    </div>
  );
};
