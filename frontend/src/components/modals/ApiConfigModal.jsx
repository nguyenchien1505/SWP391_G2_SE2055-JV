import React, { useState } from 'react';
import { Modal } from '../common/Modal';
import { Button } from '../common/Button';
import { apiClient, API_BASE_URL } from '../../services/apiClient';
import { Server, CheckCircle2, XCircle, Code2, KeyRound } from 'lucide-react';

export const ApiConfigModal = ({ isOpen, onClose }) => {
  const [testResult, setTestResult] = useState({
    status: 'idle',
    message: '',
  });

  const handleTestConnection = async () => {
    setTestResult({ status: 'testing', message: 'Đang kiểm tra kết nối tới Spring Boot...' });
    try {
      const res = await fetch(`${API_BASE_URL}/actuator/health`, { method: 'GET' });
      if (res.ok) {
        setTestResult({
          status: 'success',
          message: `Kết nối thành công tới máy chủ Spring Boot tại ${API_BASE_URL}! Status: UP`,
        });
      } else {
        setTestResult({
          status: 'failed',
          message: `Máy chủ phản hồi với HTTP status ${res.status}. Vui lòng kiểm tra endpoint và CORS config trên Spring Boot.`,
        });
      }
    } catch (err) {
      setTestResult({
        status: 'failed',
        message: `Chưa thể kết nối tới ${API_BASE_URL}. Hãy chắc chắn bạn đã khởi động Spring Boot backend với @CrossOrigin và cổng 8080.`,
      });
    }
  };

  const endpoints = [
    { method: 'GET', path: '/api/v1/catalogs', desc: 'Lấy danh sách danh mục có phân trang, lọc theo nhóm, tìm kiếm' },
    { method: 'POST', path: '/api/v1/catalogs', desc: 'Thêm mới danh mục quy chuẩn trung tâm' },
    { method: 'PUT', path: '/api/v1/catalogs/{id}', desc: 'Cập nhật thông tin quy cách và định mức danh mục' },
    { method: 'PATCH', path: '/api/v1/catalogs/{id}/status', desc: 'Bật / Tắt trạng thái áp dụng quy chuẩn' },
    { method: 'DELETE', path: '/api/v1/catalogs/{id}', desc: 'Xóa danh mục quy chuẩn' },
    { method: 'GET', path: '/api/v1/catalogs/metrics', desc: 'Lấy 4 chỉ số KPI tổng hợp cho thẻ thống kê' },
    { method: 'GET', path: '/api/v1/catalogs/branch-compliance', desc: 'Lấy tỷ lệ đồng bộ và sai lệch từng khách sạn' },
    { method: 'GET', path: '/api/v1/catalogs/export/excel', desc: 'Xuất file Excel bảng mã quy chuẩn toàn chuỗi' },
    { method: 'GET', path: '/api/v1/rooms', desc: 'Lấy sơ đồ danh sách phòng và trạng thái 6 cấp độ' },
    { method: 'PATCH', path: '/api/v1/rooms/{roomId}/status', desc: 'Cập nhật trạng thái buồng phòng thời gian thực' },
  ];

  return (
    <Modal
      isOpen={isOpen}
      onClose={onClose}
      title="Cấu Hình REST API Spring Boot Backend"
      subtitle="Thông số kết nối Service Layer trực tiếp với Backend Spring Boot (@RestController)."
      maxWidth="2xl"
      footer={
        <Button variant="primary" size="md" onClick={onClose}>
          Đã hiểu & Đóng
        </Button>
      }
    >
      <div className="space-y-4 text-xs">
        {/* Base URL */}
        <div className="p-3 bg-[#F7F8FA] border border-[#DFE3E8] rounded space-y-2">
          <div className="flex items-center justify-between">
            <span className="font-semibold text-[#1C2330] flex items-center gap-1.5">
              <Server className="w-4 h-4 text-[#00375E]" />
              Spring Boot Base URL
            </span>
            <span className="font-mono text-[#00375E] font-semibold bg-white px-2 py-0.5 rounded border border-[#DFE3E8]">
              {API_BASE_URL}
            </span>
          </div>
          <p className="text-[11px] text-[#5B6472]">
            Cấu hình qua biến môi trường <code className="font-mono bg-white px-1 py-0.5 rounded">VITE_API_BASE_URL</code> trong file <code className="font-mono">.env</code>.
          </p>
        </div>

        {/* API Base URL config only */}
        <div className="flex justify-end">
          <Button variant="outline" size="md" onClick={handleTestConnection}>
             Test Ping Spring Boot
          </Button>
        </div>

        {/* Test Result */}
        {testResult.status !== 'idle' && (
          <div
            className={`p-3 rounded flex items-center gap-2 text-xs ${
              testResult.status === 'success'
                ? 'bg-green-50 text-green-800 border border-green-200'
                : testResult.status === 'failed'
                ? 'bg-amber-50 text-amber-900 border border-amber-200'
                : 'bg-blue-50 text-blue-800 border border-blue-200'
            }`}
          >
            {testResult.status === 'success' && <CheckCircle2 className="w-4 h-4 text-green-600 shrink-0" />}
            {testResult.status === 'failed' && <XCircle className="w-4 h-4 text-amber-600 shrink-0" />}
            <span>{testResult.message}</span>
          </div>
        )}

        {/* Endpoints Contract Table */}
        <div>
          <div className="flex items-center gap-1.5 font-semibold text-[#1C2330] mb-2">
            <Code2 className="w-4 h-4 text-[#00375E]" />
            Danh Sách Endpoint Spring Boot Đã Chuẩn Bị
          </div>
          <div className="max-h-56 overflow-y-auto border border-[#DFE3E8] rounded divide-y divide-[#DFE3E8]/80">
            {endpoints.map((ep, idx) => (
              <div key={idx} className="p-2.5 hover:bg-[#F7F8FA] flex items-start gap-3">
                <span
                  className={`px-2 py-0.5 rounded font-mono text-[10px] font-bold shrink-0 ${
                    ep.method === 'GET'
                      ? 'bg-blue-100 text-blue-800'
                      : ep.method === 'POST'
                      ? 'bg-green-100 text-green-800'
                      : ep.method === 'PUT'
                      ? 'bg-amber-100 text-amber-800'
                      : ep.method === 'PATCH'
                      ? 'bg-purple-100 text-purple-800'
                      : 'bg-red-100 text-red-800'
                  }`}
                >
                  {ep.method}
                </span>
                <div className="flex-1 min-w-0">
                  <p className="font-mono text-xs font-semibold text-[#1C2330] truncate">{ep.path}</p>
                  <p className="text-[11px] text-[#5B6472] mt-0.5">{ep.desc}</p>
                </div>
              </div>
            ))}
          </div>
        </div>
      </div>
    </Modal>
  );
};
