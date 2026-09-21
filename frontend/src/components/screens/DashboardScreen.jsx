import React, { useState, useEffect } from 'react';
import { Button } from '../common/Button';
import { Modal } from '../common/Modal';
import { AlertTriangle } from 'lucide-react';
import { apiClient } from '../../services/apiClient';

export const DashboardScreen = ({ onNavigate, userRole }) => {
  const [isReportModalOpen, setIsReportModalOpen] = useState(false);
  const [assets, setAssets] = useState([]);
  const [loadingAssets, setLoadingAssets] = useState(false);
  
  const [fixedAssetId, setFixedAssetId] = useState('');
  const [description, setDescription] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [errorMessage, setErrorMessage] = useState(null);

  const handleOpenReport = async () => {
    setIsReportModalOpen(true);
    setFixedAssetId('');
    setDescription('');
    setErrorMessage(null);
    setLoadingAssets(true);
    try {
      // Chỉ lấy tài sản chưa thanh lý
      const res = await apiClient.get('/assets/fixed-assets?includeDisposed=false');
      setAssets(res?.content || []);
    } catch (error) {
      console.error('Error fetching assets for report:', error);
      setErrorMessage('Không thể tải danh sách tài sản');
    } finally {
      setLoadingAssets(false);
    }
  };

  const handleSubmitReport = async (e) => {
    e.preventDefault();
    if (!fixedAssetId || !description.trim()) {
      setErrorMessage('Vui lòng chọn tài sản và nhập mô tả lỗi.');
      return;
    }
    setSubmitting(true);
    setErrorMessage(null);

    try {
      await apiClient.post('/assets/damage-reports', {
        fixedAssetId,
        description: description.trim()
      });
      setIsReportModalOpen(false);
      alert('Đã gửi báo hỏng thành công!');
    } catch (error) {
      console.error('Submit report error:', error);
      setErrorMessage(error.message || 'Lỗi khi gửi báo cáo hỏng hóc');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="space-y-5 pb-8">
      <div className="text-xs text-[#5B6472] flex items-center gap-1.5">
        <span>Vận Hành Chuỗi</span>
        <span className="text-[#94A3B8]">&gt;</span>
        <span className="font-semibold text-[#1C2330]">Dashboard</span>
      </div>

      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <h2 className="text-2xl font-bold text-[#1C2330] tracking-tight">
            Tổng Quan Vận Hành Chuỗi Khách Sạn Sao Mai
          </h2>
          <p className="text-xs text-[#5B6472] mt-1">
            Chỉ số hiệu suất buồng phòng, tài sản, vật tư và nhân sự theo thời gian thực.
          </p>
        </div>
      </div>

      <div className="bg-white border border-[#DFE3E8] rounded-lg p-10 mt-6 shadow-2xs text-center">
        <h3 className="text-lg font-bold text-[#1C2330] mb-2">Chào mừng đến với Hệ thống Quản trị</h3>
        <p className="text-sm text-[#5B6472] mb-6">
          Sử dụng thanh menu bên trái để truy cập các chức năng của hệ thống.
        </p>
        
        <div className="flex items-center justify-center gap-4">
          <Button variant="primary" size="md" onClick={() => onNavigate('catalog')}>
            Đến Quản trị Danh mục ngay
          </Button>

          {userRole === 'STAFF' && (
            <Button 
              variant="secondary" 
              size="md" 
              icon={<AlertTriangle className="w-4 h-4 text-orange-500" />}
              onClick={handleOpenReport}
            >
              Báo hỏng thiết bị
            </Button>
          )}
        </div>
      </div>

      <Modal
        isOpen={isReportModalOpen}
        onClose={() => setIsReportModalOpen(false)}
        title="Báo Hỏng Thiết Bị"
        maxWidth="md"
        footer={
          <>
            <Button variant="secondary" onClick={() => setIsReportModalOpen(false)} disabled={submitting}>Hủy</Button>
            <Button variant="primary" onClick={handleSubmitReport} disabled={submitting || !fixedAssetId || !description.trim()}>
              {submitting ? 'Đang gửi...' : 'Gửi báo hỏng'}
            </Button>
          </>
        }
      >
        <div className="space-y-4">
          {errorMessage && (
            <div className="bg-red-50 text-red-600 p-2.5 rounded text-xs border border-red-200">
              {errorMessage}
            </div>
          )}
          
          <div>
            <label className="block text-sm font-medium text-[#1C2330] mb-1">
              Chọn Thiết bị / Tài sản <span className="text-red-500">*</span>
            </label>
            <select
              value={fixedAssetId}
              onChange={(e) => setFixedAssetId(e.target.value)}
              className="w-full px-3 py-2 border border-[#DFE3E8] rounded text-sm focus:outline-none focus:border-[#00375E]"
              disabled={loadingAssets}
            >
              <option value="">{loadingAssets ? 'Đang tải danh sách...' : '-- Chọn tài sản --'}</option>
              {assets.map(a => (
                <option key={a.id} value={a.id}>{a.assetCode} - {a.name}</option>
              ))}
            </select>
          </div>

          <div>
            <label className="block text-sm font-medium text-[#1C2330] mb-1">
              Mô tả chi tiết lỗi <span className="text-red-500">*</span>
            </label>
            <textarea
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              className="w-full px-3 py-2 border border-[#DFE3E8] rounded text-sm focus:outline-none focus:border-[#00375E]"
              rows={3}
              placeholder="Vui lòng mô tả chi tiết tình trạng hỏng hóc..."
            />
          </div>
        </div>
      </Modal>
    </div>
  );
};
