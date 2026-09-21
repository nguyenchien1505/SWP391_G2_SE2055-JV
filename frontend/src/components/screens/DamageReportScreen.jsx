import React, { useState, useEffect } from 'react';
import { Table } from '../common/Table';
import { Button } from '../common/Button';
import { Badge } from '../common/Badge';
import { Search, CheckCircle, Clock } from 'lucide-react';
import { apiClient } from '../../services/apiClient';

export const DamageReportScreen = ({ userRole }) => {
  const [searchTerm, setSearchTerm] = useState('');
  const [reports, setReports] = useState([]);
  const [loading, setLoading] = useState(true);
  const [statusFilter, setStatusFilter] = useState('NEW'); // NEW | RESOLVED | ALL
  const [submitting, setSubmitting] = useState(false);
  const [errorMessage, setErrorMessage] = useState(null);

  const [assets, setAssets] = useState([]);

  const loadData = async () => {
    setLoading(true);
    setErrorMessage(null);
    try {
      const url = statusFilter === 'ALL' 
        ? '/assets/damage-reports?status=' 
        : `/assets/damage-reports?status=${statusFilter}`;
        
      const [res, assetsRes] = await Promise.all([
        apiClient.get(url),
        apiClient.get('/assets/fixed-assets?includeDisposed=true')
      ]);
      setReports(res?.content || []);
      setAssets(assetsRes?.content || []);
    } catch (error) {
      console.error('Error fetching damage reports:', error);
      setErrorMessage('Lỗi khi tải dữ liệu báo hỏng');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadData();
  }, [statusFilter]);

  const getAssetCode = (id) => {
    const asset = assets.find(a => a.id === id);
    return asset ? asset.assetCode : id;
  };

  const getAssetName = (id) => {
    const asset = assets.find(a => a.id === id);
    return asset ? asset.name : 'Unknown Asset';
  };

  const handleResolve = async (id) => {
    if (!window.confirm('Xác nhận đã xử lý báo hỏng này? (Hành động này không tự động đổi trạng thái tài sản)')) return;
    setSubmitting(true);
    try {
      await apiClient.patch(`/assets/damage-reports/${id}/resolve`);
      loadData();
    } catch (error) {
      console.error('Resolve error:', error);
      setErrorMessage(error.message || 'Lỗi khi xử lý báo hỏng');
    } finally {
      setSubmitting(false);
    }
  };

  const columns = [
    {
      header: 'NGÀY BÁO',
      width: '140px',
      accessor: (item) => {
        const date = new Date(item.reportedAt);
        return (
          <div className="flex items-center gap-1.5 text-xs text-[#5B6472]">
            <Clock className="w-3.5 h-3.5" />
            <span>{date.toLocaleString('vi-VN')}</span>
          </div>
        );
      }
    },
    {
      header: 'TÀI SẢN',
      width: '200px',
      accessor: (item) => (
        <div>
          <p className="font-semibold text-xs text-[#1C2330]">{getAssetCode(item.fixedAssetId)}</p>
          <p className="text-[10px] text-[#5B6472] mt-0.5">{getAssetName(item.fixedAssetId)}</p>
        </div>
      ),
    },
    {
      header: 'MÔ TẢ LỖI',
      accessor: (item) => (
        <p className="text-sm text-[#42474E] max-w-md truncate" title={item.description}>
          {item.description}
        </p>
      ),
    },
    {
      header: 'TRẠNG THÁI',
      width: '140px',
      accessor: (item) => {
        if (item.status === 'NEW') return <Badge variant="warning">Chờ xử lý</Badge>;
        if (item.status === 'RESOLVED') return <Badge variant="success">Đã xử lý</Badge>;
        return <Badge variant="default">{item.status}</Badge>;
      },
    },
  ];

  if (userRole === 'MANAGER') {
    columns.push({
      header: 'THAO TÁC',
      width: '120px',
      accessor: (item) => {
        if (item.status === 'NEW') {
          return (
            <button
              onClick={() => handleResolve(item.id)}
              disabled={submitting}
              className="flex items-center gap-1.5 px-2 py-1 bg-green-50 text-green-700 hover:bg-green-100 rounded text-xs font-medium transition-colors"
            >
              <CheckCircle className="w-3.5 h-3.5" />
              Đã xử lý
            </button>
          );
        }
        return <span className="text-[10px] text-[#94A3B8]">Hoàn tất lúc {new Date(item.resolvedAt).toLocaleTimeString('vi-VN')}</span>;
      }
    });
  }

  const filtered = reports.filter((r) => {
    const searchLower = searchTerm.toLowerCase();
    const assetCodeStr = (getAssetCode(r.fixedAssetId) || '').toLowerCase();
    const descStr = (r.description || '').toLowerCase();
    return assetCodeStr.includes(searchLower) || descStr.includes(searchLower);
  });

  return (
    <div className="space-y-5 pb-8">
      {errorMessage && (
        <div className="bg-red-50 text-red-600 p-3 rounded text-sm mb-4 border border-red-200">
          {errorMessage}
        </div>
      )}

      <div className="text-xs text-[#5B6472] flex items-center gap-1.5">
        <span>Tài Sản & Khu Vực</span>
        <span className="text-[#94A3B8]">&gt;</span>
        <span className="font-semibold text-[#1C2330]">Quản lý báo hỏng</span>
      </div>

      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <h2 className="text-2xl font-bold text-[#1C2330] tracking-tight">
            Quản Lý Báo Hỏng Tài Sản
          </h2>
          <p className="text-xs text-[#5B6472] mt-1">
            Theo dõi và xử lý các báo cáo hỏng hóc từ nhân viên vận hành. Việc xử lý không tự động đổi trạng thái tài sản.
          </p>
        </div>
      </div>

      <div className="flex flex-col sm:flex-row items-center justify-between gap-3">
        <div className="flex gap-2">
          <button 
            onClick={() => setStatusFilter('NEW')}
            className={`px-3 py-1.5 text-xs font-medium rounded transition-colors ${statusFilter === 'NEW' ? 'bg-[#00375E] text-white' : 'bg-white border border-[#DFE3E8] text-[#5B6472] hover:bg-[#F7F8FA]'}`}
          >
            Chờ xử lý
          </button>
          <button 
            onClick={() => setStatusFilter('RESOLVED')}
            className={`px-3 py-1.5 text-xs font-medium rounded transition-colors ${statusFilter === 'RESOLVED' ? 'bg-[#00375E] text-white' : 'bg-white border border-[#DFE3E8] text-[#5B6472] hover:bg-[#F7F8FA]'}`}
          >
            Đã xử lý
          </button>
          <button 
            onClick={() => setStatusFilter('ALL')}
            className={`px-3 py-1.5 text-xs font-medium rounded transition-colors ${statusFilter === 'ALL' ? 'bg-[#00375E] text-white' : 'bg-white border border-[#DFE3E8] text-[#5B6472] hover:bg-[#F7F8FA]'}`}
          >
            Tất cả
          </button>
        </div>
        <div className="relative w-full sm:w-80">
          <Search className="w-4 h-4 text-[#72777F] absolute left-3 top-1/2 -translate-y-1/2" />
          <input
            type="text"
            value={searchTerm}
            onChange={(e) => setSearchTerm(e.target.value)}
            placeholder="Tìm theo mã tài sản, mô tả..."
            className="w-full pl-9 pr-3 py-1.5 bg-white border border-[#DFE3E8] rounded text-xs text-[#1C2330] outline-none focus:border-[#00375E]"
          />
        </div>
      </div>

      {loading ? (
        <div className="flex justify-center p-12">
          <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-[#00375E]"></div>
        </div>
      ) : (
        <Table columns={columns} data={filtered} keyExtractor={(item) => item.id} />
      )}
    </div>
  );
};
