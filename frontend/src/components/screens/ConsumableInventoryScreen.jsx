import React, { useState, useEffect } from 'react';
import { Table } from '../common/Table';
import { Button } from '../common/Button';
import { Modal } from '../common/Modal';
import { Badge } from '../common/Badge';
import { Search, Plus, Trash2, Edit3, Save, X } from 'lucide-react';
import { apiClient } from '../../services/apiClient';

export const ConsumableInventoryScreen = ({ userRole }) => {
  const [searchTerm, setSearchTerm] = useState('');
  const [items, setItems] = useState([]);
  const [categories, setCategories] = useState([]);
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [errorMessage, setErrorMessage] = useState(null);

  // Edit inline (Stock count)
  const [editingId, setEditingId] = useState(null);
  const [editQuantity, setEditQuantity] = useState('');

  // Add Item Modal
  const [isAddModalOpen, setIsAddModalOpen] = useState(false);
  const [categoryId, setCategoryId] = useState('');
  const [initQuantity, setInitQuantity] = useState('0');

  const loadData = async () => {
    setLoading(true);
    setErrorMessage(null);
    try {
      const [itemsData, categoriesData] = await Promise.all([
        apiClient.get('/assets/consumables'),
        apiClient.get('/organization/asset-categories?assetKind=CONSUMABLE')
      ]);
      setItems(itemsData?.content || []);
      setCategories(categoriesData?.content || []);
    } catch (error) {
      console.error('Error fetching inventory:', error);
      setErrorMessage('Lỗi khi tải dữ liệu tồn kho');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadData();
  }, []);

  const handleOpenAdd = () => {
    setCategoryId('');
    setInitQuantity('0');
    setErrorMessage(null);
    setIsAddModalOpen(true);
  };

  const handleAddItem = async (e) => {
    e.preventDefault();
    if (!categoryId) return;
    setSubmitting(true);
    setErrorMessage(null);

    try {
      await apiClient.post('/assets/consumables', {
        categoryId,
        quantity: parseFloat(initQuantity) || 0
      });
      setIsAddModalOpen(false);
      loadData();
    } catch (error) {
      console.error('Add item error:', error);
      setErrorMessage(error.message || 'Lỗi khi thêm danh mục vào kho');
    } finally {
      setSubmitting(false);
    }
  };

  const handleDelete = async (id) => {
    if (!window.confirm('Bạn có chắc muốn xóa mặt hàng này khỏi kho? Chỉ xóa được khi tồn kho bằng 0.')) return;
    setSubmitting(true);
    try {
      await apiClient.delete(`/assets/consumables/${id}`);
      loadData();
    } catch (error) {
      console.error('Delete error:', error);
      setErrorMessage(error.message || 'Không thể xóa khi tồn kho lớn hơn 0');
    } finally {
      setSubmitting(false);
    }
  };

  const handleStartEdit = (item) => {
    setEditingId(item.id);
    setEditQuantity(item.quantity?.toString() || '0');
  };

  const handleCancelEdit = () => {
    setEditingId(null);
    setEditQuantity('');
  };

  const handleSaveEdit = async (item) => {
    const qty = parseFloat(editQuantity);
    if (isNaN(qty) || qty < 0) {
      alert('Số lượng không hợp lệ');
      return;
    }
    setSubmitting(true);
    setErrorMessage(null);

    try {
      await apiClient.put('/assets/consumables/stock-count', {
        lines: [{ itemId: item.id, quantity: qty }]
      });
      setEditingId(null);
      loadData();
    } catch (error) {
      console.error('Stock count error:', error);
      setErrorMessage(error.message || 'Lỗi khi cập nhật tồn kho');
    } finally {
      setSubmitting(false);
    }
  };

  const columns = [
    {
      header: 'TÊN VẬT TƯ (DANH MỤC)',
      accessor: (item) => (
        <div>
          <p className="font-semibold text-sm text-[#1C2330]">{item.categoryName || 'Unknown'}</p>
          <p className="text-[10px] text-[#5B6472]">Mục đích: {item.categoryPurpose === 'GUEST_USE' ? 'Dành cho khách' : 'Bảo trì, dọn dẹp'}</p>
        </div>
      ),
    },
    {
      header: 'ĐƠN VỊ TÍNH',
      width: '120px',
      accessor: (item) => <span className="text-xs text-[#5B6472]">{item.categoryUnit || '-'}</span>,
    },
    {
      header: 'TỒN KHO THỰC TẾ',
      width: '150px',
      accessor: (item) => {
        if (editingId === item.id) {
          return (
            <input
              type="number"
              min="0"
              step="any"
              value={editQuantity}
              onChange={(e) => setEditQuantity(e.target.value)}
              className="w-20 px-2 py-1 border border-[#00375E] rounded text-sm outline-none"
              autoFocus
            />
          );
        }
        return (
          <Badge variant={item.quantity > 0 ? "success" : "error"}>
            {item.quantity}
          </Badge>
        );
      },
    },
    {
      header: 'KIỂM KÊ LẦN CUỐI',
      width: '160px',
      accessor: (item) => {
        if (!item.lastCountedAt) return '-';
        const date = new Date(item.lastCountedAt);
        return <span className="text-xs text-[#5B6472]">{date.toLocaleString('vi-VN')}</span>;
      }
    }
  ];

  if (userRole === 'MANAGER') {
    columns.push({
      header: 'THAO TÁC (KIỂM KÊ)',
      width: '140px',
      accessor: (item) => {
        if (editingId === item.id) {
          return (
            <div className="flex items-center gap-2">
              <button onClick={() => handleSaveEdit(item)} disabled={submitting} className="p-1.5 text-green-600 hover:bg-green-50 rounded" title="Lưu số lượng">
                <Save className="w-4 h-4" />
              </button>
              <button onClick={handleCancelEdit} disabled={submitting} className="p-1.5 text-red-600 hover:bg-red-50 rounded" title="Hủy">
                <X className="w-4 h-4" />
              </button>
            </div>
          );
        }
        return (
          <div className="flex items-center gap-2">
            <button onClick={() => handleStartEdit(item)} className="p-1.5 text-[#00375E] hover:bg-[#EFF4FF] rounded" title="Cập nhật số lượng (Kiểm kê)">
              <Edit3 className="w-4 h-4" />
            </button>
            <button onClick={() => handleDelete(item.id)} className="p-1.5 text-red-600 hover:bg-red-50 rounded" title="Xóa khỏi kho (Yêu cầu tồn kho = 0)">
              <Trash2 className="w-4 h-4" />
            </button>
          </div>
        );
      }
    });
  }

  const filtered = items.filter((i) => {
    const searchLower = searchTerm.toLowerCase();
    const nameStr = (i.categoryName || '').toLowerCase();
    return nameStr.includes(searchLower);
  });

  return (
    <div className="space-y-5 pb-8">
      {errorMessage && !isAddModalOpen && (
        <div className="bg-red-50 text-red-600 p-3 rounded text-sm border border-red-200">
          {errorMessage}
        </div>
      )}

      <div className="text-xs text-[#5B6472] flex items-center gap-1.5">
        <span>Tài Sản & Khu Vực</span>
        <span className="text-[#94A3B8]">&gt;</span>
        <span className="font-semibold text-[#1C2330]">Tồn kho tiêu hao</span>
      </div>

      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <h2 className="text-2xl font-bold text-[#1C2330] tracking-tight">
            Quản Lý Tồn Kho Tiêu Hao
          </h2>
          <p className="text-xs text-[#5B6472] mt-1">
            Quản lý số lượng tồn kho của Amenities, Linen... {userRole !== 'MANAGER' && "(Chỉ Manager mới có quyền thêm/kiểm kê)"}
          </p>
        </div>
        
        {userRole === 'MANAGER' && (
          <Button variant="primary" size="md" icon={<Plus className="w-4 h-4" />} onClick={handleOpenAdd}>
            Thêm vào kho
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
            placeholder="Tìm theo tên vật tư..."
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

      {/* ADD ITEM MODAL */}
      <Modal
        isOpen={isAddModalOpen}
        onClose={() => setIsAddModalOpen(false)}
        title="Thêm Vật Tư Vào Kho"
        maxWidth="sm"
        footer={
          <>
            <Button variant="secondary" onClick={() => setIsAddModalOpen(false)} disabled={submitting}>Hủy</Button>
            <Button variant="primary" onClick={handleAddItem} disabled={submitting || !categoryId}>
              {submitting ? 'Đang thêm...' : 'Lưu lại'}
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
              Danh mục Tiêu hao <span className="text-red-500">*</span>
            </label>
            <select
              value={categoryId}
              onChange={(e) => setCategoryId(e.target.value)}
              className="w-full px-3 py-2 border border-[#DFE3E8] rounded text-sm focus:outline-none focus:border-[#00375E]"
            >
              <option value="">-- Chọn danh mục --</option>
              {categories.filter(c => c.active).map(c => (
                <option key={c.id} value={c.id}>{c.name} ({c.unit || '-'})</option>
              ))}
            </select>
          </div>

          <div>
            <label className="block text-sm font-medium text-[#1C2330] mb-1">Số lượng ban đầu <span className="text-red-500">*</span></label>
            <input
              type="number"
              min="0"
              step="any"
              value={initQuantity}
              onChange={(e) => setInitQuantity(e.target.value)}
              className="w-full px-3 py-2 border border-[#DFE3E8] rounded text-sm focus:outline-none focus:border-[#00375E]"
            />
          </div>
        </div>
      </Modal>
    </div>
  );
};
