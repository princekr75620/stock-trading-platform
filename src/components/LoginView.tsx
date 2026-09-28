import React, { useState } from 'react';
import { api } from '../services/api';
import { User as UserType } from '../types';

interface LoginViewProps {
  onLoginSuccess: (user: UserType) => void;
}

export const LoginView: React.FC<LoginViewProps> = ({ onLoginSuccess }) => {
  const [emailOrUsername, setEmailOrUsername] = useState('trader1');
  const [password, setPassword] = useState('trader123');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleLogin = async (u: string, p: string) => {
    setLoading(true);
    setError(null);
    try {
      const res = await api.login(u, p);
      if (res.success && res.user) {
        onLoginSuccess(res.user);
      } else {
        setError('Login failed. Please check your email/username and password.');
      }
    } catch (err: any) {
      setError(err.message || 'Invalid credentials. Please try again.');
    } finally {
      setLoading(false);
    }
  };

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!emailOrUsername.trim() || !password.trim()) {
      setError('Please enter both email/username and password.');
      return;
    }
    handleLogin(emailOrUsername, password);
  };

  const setDemoAccount = (role: 'TRADER' | 'ADMIN') => {
    if (role === 'TRADER') {
      setEmailOrUsername('trader1');
      setPassword('trader123');
      handleLogin('trader1', 'trader123');
    } else {
      setEmailOrUsername('admin');
      setPassword('admin123');
      handleLogin('admin', 'admin123');
    }
  };

  return (
    <div className="min-h-screen bg-gray-50 flex flex-col justify-center items-center px-4 py-12">
      <div className="w-full max-w-md bg-white border border-gray-300 rounded-lg shadow-sm p-8">
        <div className="text-center mb-6">
          <h1 className="text-2xl font-bold text-gray-900 tracking-tight">
            Online Stock Trading Platform
          </h1>
          <p className="text-sm text-gray-600 mt-1">
            Sign in to your account
          </p>
        </div>

        {error && (
          <div className="mb-4 p-3 bg-red-50 border border-red-200 text-red-700 text-sm rounded-md">
            {error}
          </div>
        )}

        <form onSubmit={handleSubmit} className="space-y-4">
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">
              Email or Username
            </label>
            <input
              type="text"
              required
              value={emailOrUsername}
              onChange={(e) => setEmailOrUsername(e.target.value)}
              placeholder="e.g. trader1 or alex.morgan@apex.com"
              className="w-full px-3 py-2 border border-gray-300 rounded-md text-gray-900 text-sm focus:outline-none focus:ring-1 focus:ring-blue-500 focus:border-blue-500"
            />
          </div>

          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">
              Password
            </label>
            <input
              type="password"
              required
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              placeholder="••••••••"
              className="w-full px-3 py-2 border border-gray-300 rounded-md text-gray-900 text-sm focus:outline-none focus:ring-1 focus:ring-blue-500 focus:border-blue-500"
            />
          </div>

          <button
            type="submit"
            disabled={loading}
            className="w-full bg-blue-600 hover:bg-blue-700 text-white font-medium py-2 px-4 rounded-md text-sm transition disabled:opacity-50 mt-2"
          >
            {loading ? 'Signing in...' : 'Login'}
          </button>
        </form>

        <div className="mt-6 pt-6 border-t border-gray-200">
          <p className="text-xs text-gray-500 text-center mb-3">
            Quick Demo Logins (College Project Testing):
          </p>
          <div className="grid grid-cols-2 gap-2">
            <button
              type="button"
              onClick={() => setDemoAccount('TRADER')}
              className="py-1.5 px-3 border border-gray-300 rounded text-xs text-gray-700 hover:bg-gray-50 text-center font-medium"
            >
              Trader (Alex)
            </button>
            <button
              type="button"
              onClick={() => setDemoAccount('ADMIN')}
              className="py-1.5 px-3 border border-gray-300 rounded text-xs text-gray-700 hover:bg-gray-50 text-center font-medium"
            >
              Admin (System)
            </button>
          </div>
        </div>

        <div className="mt-6 text-center text-xs text-gray-500">
          Backend: Java 17 REST Engine &bull; Port 8765
        </div>
      </div>
    </div>
  );
};
