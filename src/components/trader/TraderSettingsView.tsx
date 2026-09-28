import React, { useState } from 'react';
import { User } from '../../types';

interface TraderSettingsViewProps {
  currentUser: User;
}

export const TraderSettingsView: React.FC<TraderSettingsViewProps> = ({ currentUser }) => {
  const [emailAlerts, setEmailAlerts] = useState(true);
  const [orderConfirmation, setOrderConfirmation] = useState(true);
  const [priceVolatilityThreshold, setPriceVolatilityThreshold] = useState('3.0');
  const [savedSuccess, setSavedSuccess] = useState(false);

  const [currentPass, setCurrentPass] = useState('');
  const [newPass, setNewPass] = useState('');
  const [confirmPass, setConfirmPass] = useState('');
  const [passMsg, setPassMsg] = useState<string | null>(null);

  const handleSavePreferences = (e: React.FormEvent) => {
    e.preventDefault();
    setSavedSuccess(true);
    setTimeout(() => setSavedSuccess(false), 3000);
  };

  const handlePasswordChange = (e: React.FormEvent) => {
    e.preventDefault();
    if (newPass !== confirmPass) {
      setPassMsg('New passwords do not match.');
      return;
    }
    if (newPass.length < 6) {
      setPassMsg('Password must be at least 6 characters.');
      return;
    }
    setPassMsg('Password updated successfully.');
    setCurrentPass('');
    setNewPass('');
    setConfirmPass('');
    setTimeout(() => setPassMsg(null), 3500);
  };

  return (
    <div className="space-y-6 max-w-4xl">
      {/* Header */}
      <div className="pb-4 border-b border-gray-200">
        <h1 className="text-2xl font-bold text-gray-900">Settings</h1>
        <p className="text-sm text-gray-600">
          Account details, trading preferences, and security credentials.
        </p>
      </div>

      {/* Profile & Account Information */}
      <div className="bg-white border border-gray-200 rounded-md p-5 shadow-sm space-y-4">
        <h2 className="text-base font-semibold text-gray-900 pb-2 border-b border-gray-100">
          Trader Profile
        </h2>

        <div className="grid grid-cols-1 sm:grid-cols-2 gap-4 text-sm">
          <div>
            <span className="text-xs text-gray-500 font-medium">Full Name</span>
            <div className="font-semibold text-gray-900 mt-0.5">{currentUser.name}</div>
          </div>
          <div>
            <span className="text-xs text-gray-500 font-medium">Email Address</span>
            <div className="font-semibold text-gray-900 mt-0.5">{currentUser.email}</div>
          </div>
          <div>
            <span className="text-xs text-gray-500 font-medium">User ID</span>
            <div className="font-mono text-gray-900 mt-0.5">{currentUser.userId}</div>
          </div>
          <div>
            <span className="text-xs text-gray-500 font-medium">Role &amp; Status</span>
            <div className="text-gray-900 mt-0.5">
              <span className="inline-block bg-blue-50 text-blue-700 px-2 py-0.5 rounded text-xs font-semibold mr-2">
                {currentUser.role}
              </span>
              <span className="inline-block bg-green-50 text-green-700 px-2 py-0.5 rounded text-xs font-semibold">
                {currentUser.status || 'ACTIVE'}
              </span>
            </div>
          </div>
        </div>
      </div>

      {/* Trading & Notification Preferences */}
      <div className="bg-white border border-gray-200 rounded-md p-5 shadow-sm space-y-4">
        <h2 className="text-base font-semibold text-gray-900 pb-2 border-b border-gray-100">
          Trading &amp; Notification Preferences
        </h2>

        {savedSuccess && (
          <div className="p-3 bg-green-50 border border-green-200 text-green-700 text-xs rounded">
            Preferences saved successfully.
          </div>
        )}

        <form onSubmit={handleSavePreferences} className="space-y-4 text-sm">
          <div className="flex items-center justify-between py-2 border-b border-gray-100">
            <div>
              <span className="font-medium text-gray-900">Trade Confirmations</span>
              <p className="text-xs text-gray-500">
                Display pre-trade verification modal before routing order to Java engine.
              </p>
            </div>
            <input
              type="checkbox"
              checked={orderConfirmation}
              onChange={(e) => setOrderConfirmation(e.target.checked)}
              className="h-4 w-4 text-blue-600 rounded border-gray-300 focus:ring-blue-500"
            />
          </div>

          <div className="flex items-center justify-between py-2 border-b border-gray-100">
            <div>
              <span className="font-medium text-gray-900">Price Volatility Alerts</span>
              <p className="text-xs text-gray-500">
                Receive notifications when stock prices fluctuate beyond threshold.
              </p>
            </div>
            <input
              type="checkbox"
              checked={emailAlerts}
              onChange={(e) => setEmailAlerts(e.target.checked)}
              className="h-4 w-4 text-blue-600 rounded border-gray-300 focus:ring-blue-500"
            />
          </div>

          <div className="flex items-center justify-between py-2">
            <div>
              <span className="font-medium text-gray-900">Volatility Threshold (%)</span>
              <p className="text-xs text-gray-500">
                Trigger alerts when a single stock tick moves higher than:
              </p>
            </div>
            <select
              value={priceVolatilityThreshold}
              onChange={(e) => setPriceVolatilityThreshold(e.target.value)}
              className="px-3 py-1.5 border border-gray-300 rounded text-sm text-gray-900 focus:ring-1 focus:ring-blue-500"
            >
              <option value="1.0">1.0%</option>
              <option value="2.0">2.0%</option>
              <option value="3.0">3.0%</option>
              <option value="5.0">5.0%</option>
            </select>
          </div>

          <div className="pt-2">
            <button
              type="submit"
              className="px-4 py-2 bg-blue-600 hover:bg-blue-700 text-white rounded text-sm font-medium transition"
            >
              Save Preferences
            </button>
          </div>
        </form>
      </div>

      {/* Change Password */}
      <div className="bg-white border border-gray-200 rounded-md p-5 shadow-sm space-y-4">
        <h2 className="text-base font-semibold text-gray-900 pb-2 border-b border-gray-100">
          Change Password
        </h2>

        {passMsg && (
          <div
            className={`p-3 text-xs rounded border ${
              passMsg.includes('success')
                ? 'bg-green-50 border-green-200 text-green-700'
                : 'bg-red-50 border-red-200 text-red-700'
            }`}
          >
            {passMsg}
          </div>
        )}

        <form onSubmit={handlePasswordChange} className="space-y-3 max-w-md text-sm">
          <div>
            <label className="block text-xs font-medium text-gray-700 mb-1">Current Password</label>
            <input
              type="password"
              required
              value={currentPass}
              onChange={(e) => setCurrentPass(e.target.value)}
              className="w-full px-3 py-1.5 border border-gray-300 rounded text-sm focus:ring-1 focus:ring-blue-500"
            />
          </div>
          <div>
            <label className="block text-xs font-medium text-gray-700 mb-1">New Password</label>
            <input
              type="password"
              required
              value={newPass}
              onChange={(e) => setNewPass(e.target.value)}
              className="w-full px-3 py-1.5 border border-gray-300 rounded text-sm focus:ring-1 focus:ring-blue-500"
            />
          </div>
          <div>
            <label className="block text-xs font-medium text-gray-700 mb-1">Confirm New Password</label>
            <input
              type="password"
              required
              value={confirmPass}
              onChange={(e) => setConfirmPass(e.target.value)}
              className="w-full px-3 py-1.5 border border-gray-300 rounded text-sm focus:ring-1 focus:ring-blue-500"
            />
          </div>

          <div className="pt-2">
            <button
              type="submit"
              className="px-4 py-2 border border-gray-300 hover:bg-gray-50 text-gray-800 rounded text-sm font-medium transition"
            >
              Update Password
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};
