import React, { useState } from 'react';
import { apiClient } from '../../services/apiClient';
import { Button } from '../common/Button';

export const ChangePasswordScreen = ({ onPasswordChanged }) => {
  const [currentPassword, setCurrentPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [error, setError] = useState(null);
  const [loading, setLoading] = useState(false);

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError(null);

    if (newPassword !== confirmPassword) {
      setError('Mật khẩu mới không khớp.');
      return;
    }

    setLoading(true);

    try {
      await apiClient.post('/auth/change-password', {
        currentPassword,
        newPassword,
      });
      
      // Success, call callback
      onPasswordChanged();
    } catch (err) {
      setError(err.message || 'Không thể đổi mật khẩu. Vui lòng kiểm tra lại.');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="min-h-screen bg-[#F7F8FA] flex items-center justify-center p-4">
      <div className="bg-white p-8 rounded-lg shadow-md w-full max-w-md">
        <div className="text-center mb-8">
          <h1 className="text-2xl font-bold text-[#1C2330]">Đổi mật khẩu bảo mật</h1>
          <p className="text-sm text-red-600 mt-2 font-medium">
            Bạn đang sử dụng mật khẩu tạm thời. Vui lòng đổi mật khẩu mới để tiếp tục sử dụng hệ thống.
          </p>
        </div>

        {error && (
          <div className="bg-red-50 text-red-600 p-3 rounded text-sm mb-4">
            {error}
          </div>
        )}

        <form onSubmit={handleSubmit} className="space-y-4">
          <div>
            <label className="block text-sm font-medium text-[#1C2330] mb-1">
              Mật khẩu hiện tại (Mật khẩu tạm)
            </label>
            <input
              type="password"
              required
              value={currentPassword}
              onChange={(e) => setCurrentPassword(e.target.value)}
              className="w-full px-3 py-2 border border-[#DFE3E8] rounded text-sm focus:outline-none focus:border-[#00375E]"
            />
          </div>

          <div>
            <label className="block text-sm font-medium text-[#1C2330] mb-1">
              Mật khẩu mới
            </label>
            <input
              type="password"
              required
              value={newPassword}
              onChange={(e) => setNewPassword(e.target.value)}
              className="w-full px-3 py-2 border border-[#DFE3E8] rounded text-sm focus:outline-none focus:border-[#00375E]"
              minLength={6}
            />
          </div>

          <div>
            <label className="block text-sm font-medium text-[#1C2330] mb-1">
              Nhập lại mật khẩu mới
            </label>
            <input
              type="password"
              required
              value={confirmPassword}
              onChange={(e) => setConfirmPassword(e.target.value)}
              className="w-full px-3 py-2 border border-[#DFE3E8] rounded text-sm focus:outline-none focus:border-[#00375E]"
              minLength={6}
            />
          </div>

          <Button
            type="submit"
            variant="primary"
            className="w-full justify-center mt-6"
            disabled={loading}
          >
            {loading ? 'Đang cập nhật...' : 'Đổi mật khẩu và Tiếp tục'}
          </Button>
        </form>
      </div>
    </div>
  );
};
