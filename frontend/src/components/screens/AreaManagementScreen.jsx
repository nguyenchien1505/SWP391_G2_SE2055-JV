import React, { useState, useEffect } from 'react';
import { Table } from '../common/Table';
import { Button } from '../common/Button';
import { Modal } from '../common/Modal';
import { Search, Building, Plus, Pencil, Trash2 } from 'lucide-react';
import { apiClient } from '../../services/apiClient';

export const AreaManagementScreen = ({ userRole }) => {
  const [areas, setAreas] = useState([]);
  const [loading, setLoading] = useState(true);
  const [searchTerm, setSearchTerm] = useState('');

  // Modal states
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [modalMode, setModalMode] = useState('create'); // 'create' | 'edit'
  const [selectedArea, setSelectedArea] = useState(null);
  const [areaName, setAreaName] = useState('');

  // Delete confirm states
  const [isDeleteModalOpen, setIsDeleteModalOpen] = useState(false);
  const [areaToDelete, setAreaToDelete] = useState(null);

  // Error state
  const [errorMessage, setErrorMessage] = useState(null);
  const [submitting, setSubmitting] = useState(false);

  const loadData = async () => {
    setLoading(true);
    setErrorMessage(null);
    try {
      const response = await apiClient.get('/organization/areas');
      setAreas(response?.content || []);
    } catch (error) {
      console.error('Error fetching areas:', error);
      setErrorMessage('Không thể tải danh sách khu vực');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadData();
  }, []);

  const handleOpenCreate = () => {
    setModalMode('create');
    setAreaName('');
    setSelectedArea(null);
    setErrorMessage(null);
    setIsModalOpen(true);
  };

  const handleOpenEdit = (area) => {
    setModalMode('edit');
    setAreaName(area.name);
    setSelectedArea(area);
    setErrorMessage(null);
    setIsModalOpen(true);
  };

  const handleOpenDelete = (area) => {
    setAreaToDelete(area);
    setErrorMessage(null);
    setIsDeleteModalOpen(true);
  };

  const handleSave = async (e) => {
    e.preventDefault();
    if (!areaName.trim()) return;

    setSubmitting(true);
    setErrorMessage(null);

    try {
      if (modalMode === 'create') {
        await apiClient.post('/organization/areas', { name: areaName.trim() });
      } else {
        await apiClient.put(`/organization/areas/${selectedArea.id}`, { name: areaName.trim() });
      }
      setIsModalOpen(false);
      loadData();
    } catch (error) {
      console.error('Save error:', error);
      setErrorMessage(error.message || 'Lỗi khi lưu khu vực (Bạn có quyền Manager không?)');
    } finally {
      setSubmitting(false);
    }
  };

  const handleDelete = async () => {
    if (!areaToDelete) return;

    setSubmitting(true);
    setErrorMessage(null);

    try {
      await apiClient.delete(`/organization/areas/${areaToDelete.id}`);
      setIsDeleteModalOpen(false);
      loadData();
    } catch (error) {
      console.error('Delete error:', error);
      setErrorMessage(error.message || 'Không thể xóa vì khu vực đang có tài sản cố định');
    } finally {
      setSubmitting(false);
    }
  };

  const columns = [
    {
      header: 'TÊN KHU VỰC',
      accessor: (item) => (
        <div className="flex items-center gap-1.5 text-sm font-semibold text-[#1C2330]">
          <Building className="w-4 h-4 text-[#5B6472]" />
          <span>{item.name}</span>
        </div>
      ),
    },
    {
      header: 'ID KHU VỰC',
      width: '300px',
      accessor: (item) => <span className="text-xs text-[#5B6472]">{item.id}</span>,
    }
  ];

  if (userRole === 'MANAGER') {
    columns.push({
      header: 'THAO TÁC',
      width: '120px',
      accessor: (item) => (
        <div className="flex items-center gap-2">
          <button
            onClick={() => handleOpenEdit(item)}
            className="p-1.5 text-[#5B6472] hover:text-[#00375E] hover:bg-[#EFF4FF] rounded transition-colors"
            title="Sửa tên"
          >
            <Pencil className="w-4 h-4" />
          </button>
          <button
            onClick={() => handleOpenDelete(item)}
            className="p-1.5 text-[#5B6472] hover:text-red-600 hover:bg-red-50 rounded transition-colors"
            title="Xóa khu vực"
          >
            <Trash2 className="w-4 h-4" />
          </button>
        </div>
      ),
    });
  }

  const filtered = areas.filter((a) => {
    const searchLower = searchTerm.toLowerCase();
    return (a.name || '').toLowerCase().includes(searchLower);
  });

  return (
    <div className="space-y-5 pb-8">
      {errorMessage && !isModalOpen && !isDeleteModalOpen && (
        <div className="bg-red-50 text-red-600 p-3 rounded text-sm mb-4 border border-red-200">
          {errorMessage}
        </div>
      )}

      <div className="text-xs text-[#5B6472] flex items-center gap-1.5">
        <span>Tài Sản & Khu Vực</span>
        <span className="text-[#94A3B8]">&gt;</span>
        <span className="font-semibold text-[#1C2330]">Quản lý khu vực</span>
      </div>

      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <h2 className="text-2xl font-bold text-[#1C2330] tracking-tight">
            Quản Lý Khu Vực (Areas)
          </h2>
          <p className="text-xs text-[#5B6472] mt-1">
            Danh sách các khu vực chung thuộc chi nhánh hiện tại. {userRole !== 'MANAGER' && "(Chỉ Manager mới có quyền Thêm/Sửa/Xóa)"}
          </p>
        </div>

        {userRole === 'MANAGER' && (
          <Button
            variant="primary"
            size="md"
            icon={<Plus className="w-4 h-4" />}
            onClick={handleOpenCreate}
          >
            Thêm Khu Vực
          </Button>
        )}
      </div>

      <div className="flex items-center justify-between gap-3">
        <div className="relative w-full sm:w-80">
          <Search className="w-4 h-4 text-[#72777F] absolute left-3 top-1/2 -translate-y-1/2" />
          <input
            type="text"
            value={searchTerm}
            onChange={(e) => setSearchTerm(e.target.value)}
            placeholder="Tìm theo tên khu vực..."
            className="w-full pl-9 pr-3 py-1.5 bg-white border border-[#DFE3E8] rounded text-xs text-[#1C2330] outline-none focus:border-[#00375E]"
          />
        </div>
      </div>

      {loading ? (
        <div className="flex justify-center p-12">
          <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-[#00375E]"></div>
        </div>
      ) : (
        <Table
          columns={columns}
          data={filtered}
          keyExtractor={(item) => item.id}
        />
      )}

      {/* CREATE / EDIT MODAL */}
      <Modal
        isOpen={isModalOpen}
        onClose={() => setIsModalOpen(false)}
        title={modalMode === 'create' ? 'Thêm Khu Vực Mới' : 'Sửa Tên Khu Vực'}
        maxWidth="sm"
        footer={
          <>
            <Button variant="secondary" onClick={() => setIsModalOpen(false)} disabled={submitting}>Hủy</Button>
            <Button variant="primary" onClick={handleSave} disabled={submitting || !areaName.trim()}>
              {submitting ? 'Đang lưu...' : 'Lưu lại'}
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
              Tên khu vực <span className="text-red-500">*</span>
            </label>
            <input
              type="text"
              value={areaName}
              onChange={(e) => setAreaName(e.target.value)}
              className="w-full px-3 py-2 border border-[#DFE3E8] rounded text-sm focus:outline-none focus:border-[#00375E]"
              placeholder="VD: Sảnh A, Hành lang tầng 1..."
              autoFocus
            />
          </div>
        </div>
      </Modal>

      {/* DELETE CONFIRM MODAL */}
      <Modal
        isOpen={isDeleteModalOpen}
        onClose={() => setIsDeleteModalOpen(false)}
        title="Xác nhận Xóa"
        maxWidth="sm"
        footer={
          <>
            <Button variant="secondary" onClick={() => setIsDeleteModalOpen(false)} disabled={submitting}>Hủy</Button>
            <Button
              variant="primary"
              onClick={handleDelete}
              disabled={submitting}
              className="!bg-red-600 hover:!bg-red-700 !border-red-600"
            >
              {submitting ? 'Đang xử lý...' : 'Xóa khu vực'}
            </Button>
          </>
        }
      >
        <div className="space-y-3">
          {errorMessage && (
            <div className="bg-red-50 text-red-600 p-2.5 rounded text-xs border border-red-200">
              {errorMessage}
            </div>
          )}
          <p className="text-sm text-[#42474E]">
            Bạn có chắc chắn muốn xóa khu vực <span className="font-bold text-[#1C2330]">{areaToDelete?.name}</span> không?
          </p>
          <p className="text-xs text-red-600">
            Lưu ý: Không thể xóa nếu khu vực này đang có tài sản cố định được bố trí.
          </p>
        </div>
      </Modal>
    </div>
  );
};
