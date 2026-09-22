import React, { useState, useEffect } from 'react';
import { Table } from '../common/Table';
import { Badge } from '../common/Badge';
import { Button } from '../common/Button';
import { Modal } from '../common/Modal';
import { Search, QrCode, Plus, Pencil, ArrowRightLeft } from 'lucide-react';
import { apiClient } from '../../services/apiClient';

export const AssetManagementScreen = ({ userRole }) => {
  const [searchTerm, setSearchTerm] = useState('');
  const [assets, setAssets] = useState([]);
  const [categories, setCategories] = useState([]);
  const [areas, setAreas] = useState([]);
  const [loading, setLoading] = useState(true);

  // Form Modal States
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [modalMode, setModalMode] = useState('create');
  const [selectedAsset, setSelectedAsset] = useState(null);
  const [submitting, setSubmitting] = useState(false);
  const [errorMessage, setErrorMessage] = useState(null);

  // Form fields
  const [categoryId, setCategoryId] = useState('');
  const [assetCode, setAssetCode] = useState('');
  const [name, setName] = useState('');
  const [areaId, setAreaId] = useState('');
  const [note, setNote] = useState('');

  // Status Modal States
  const [isStatusModalOpen, setIsStatusModalOpen] = useState(false);
  const [newStatus, setNewStatus] = useState('GOOD');

  const loadData = async () => {
    setLoading(true);
    try {
      const [assetsData, categoriesData, areasData] = await Promise.all([
        apiClient.get('/assets/fixed-assets?includeDisposed=false'),
        apiClient.get('/organization/asset-categories?assetKind=FIXED'),
        apiClient.get('/organization/areas')
      ]);
      
      setAssets(assetsData?.content || []);
      setCategories(categoriesData?.content || []);
      setAreas(areasData?.content || []);
    } catch (error) {
      console.error('Error fetching asset data:', error);
      setErrorMessage('Lỗi khi tải dữ liệu');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadData();
  }, []);

  const getCategoryCode = (catId) => {
    const cat = categories.find(c => c.id === catId);
    return cat ? cat.name : 'UNKNOWN';
  };

  const getAreaName = (aId) => {
    if (!aId) return 'N/A';
    const a = areas.find(x => x.id === aId);
    return a ? a.name : aId;
  };

  const handleOpenCreate = () => {
    setModalMode('create');
    setSelectedAsset(null);
    setCategoryId('');
    setAssetCode('');
    setName('');
    setAreaId('');
    setNote('');
    setErrorMessage(null);
    setIsModalOpen(true);
  };

  const handleOpenEdit = (asset) => {
    setModalMode('edit');
    setSelectedAsset(asset);
    setCategoryId(asset.categoryId || '');
    setAssetCode(asset.assetCode || '');
    setName(asset.name || '');
    setAreaId(asset.areaId || '');
    setNote(asset.note || '');
    setErrorMessage(null);
    setIsModalOpen(true);
  };

  const handleOpenStatus = (asset) => {
    setSelectedAsset(asset);
    setNewStatus(asset.status || 'GOOD');
    setErrorMessage(null);
    setIsStatusModalOpen(true);
  };

  const handleSave = async (e) => {
    e.preventDefault();
    if (!categoryId || !name) {
      setErrorMessage('Vui lòng điền Danh mục và Tên tài sản');
      return;
    }
    setSubmitting(true);
    setErrorMessage(null);

    const payload = {
      categoryId,
      assetCode: assetCode.trim() || null,
      name: name.trim(),
      areaId: areaId || null,
      note: note.trim() || null
    };

    try {
      if (modalMode === 'create') {
        await apiClient.post('/assets/fixed-assets', payload);
      } else {
        await apiClient.put(`/assets/fixed-assets/${selectedAsset.id}`, payload);
      }
      setIsModalOpen(false);
      loadData();
    } catch (error) {
      console.error('Save error:', error);
      setErrorMessage(error.message || 'Lỗi khi lưu tài sản');
    } finally {
      setSubmitting(false);
    }
  };

  const handleUpdateStatus = async () => {
    setSubmitting(true);
    setErrorMessage(null);
    try {
      await apiClient.patch(`/assets/fixed-assets/${selectedAsset.id}/status`, { status: newStatus });
      setIsStatusModalOpen(false);
      loadData();
    } catch (error) {
      console.error('Status error:', error);
      setErrorMessage(error.message || 'Lỗi khi cập nhật trạng thái');
    } finally {
      setSubmitting(false);
    }
  };

  const renderStatusBadge = (status) => {
    if (status === 'GOOD') return <Badge variant="success">Hoạt động tốt</Badge>;
    if (status === 'BROKEN') return <Badge variant="error">Hỏng</Badge>;
    if (status === 'UNDER_REPAIR') return <Badge variant="warning">Đang sửa chữa</Badge>;
    if (status === 'DISPOSED') return <Badge variant="default">Đã thanh lý</Badge>;
    return <Badge variant="default">{status}</Badge>;
  };

  const columns = [
    {
      header: 'SỐ SERIAL / RFID',
      width: '180px',
      accessor: (item) => (
        <div className="flex items-center gap-1.5 font-mono text-xs font-semibold text-[#00375E]">
          <QrCode className="w-3.5 h-3.5 text-[#5B6472]" />
          <span>{item.assetCode || 'N/A'}</span>
        </div>
      ),
    },
    {
      header: 'DANH MỤC',
      width: '150px',
      accessor: (item) => <Badge variant="code">{getCategoryCode(item.categoryId)}</Badge>,
    },
    {
      header: 'TÊN THIẾT BỊ',
      accessor: (item) => (
        <div>
          <p className="font-semibold text-xs text-[#1C2330]">{item.name}</p>
          <p className="text-[10px] text-[#5B6472] mt-0.5">Vị trí: {getAreaName(item.areaId)}</p>
        </div>
      ),
    },
    {
      header: 'TRẠNG THÁI',
      width: '140px',
      accessor: (item) => renderStatusBadge(item.status),
    },
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
            title="Sửa thông tin"
          >
            <Pencil className="w-4 h-4" />
          </button>
          <button
            onClick={() => handleOpenStatus(item)}
            className="p-1.5 text-[#5B6472] hover:text-[#E65100] hover:bg-[#FFF3E0] rounded transition-colors"
            title="Cập nhật trạng thái"
          >
            <ArrowRightLeft className="w-4 h-4" />
          </button>
        </div>
      )
    });
  }

  const filtered = assets.filter((a) => {
    const searchLower = searchTerm.toLowerCase();
    const assetCodeStr = (a.assetCode || '').toLowerCase();
    const nameStr = (a.name || '').toLowerCase();
    
    return (
      assetCodeStr.includes(searchLower) ||
      nameStr.includes(searchLower)
    );
  });

  return (
    <div className="space-y-5 pb-8">
      {errorMessage && !isModalOpen && !isStatusModalOpen && (
        <div className="bg-red-50 text-red-600 p-3 rounded text-sm mb-4 border border-red-200">
          {errorMessage}
        </div>
      )}

      <div className="text-xs text-[#5B6472] flex items-center gap-1.5">
        <span>Tài Sản & Khu Vực</span>
        <span className="text-[#94A3B8]">&gt;</span>
        <span className="font-semibold text-[#1C2330]">Quản lý tài sản</span>
      </div>

      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <h2 className="text-2xl font-bold text-[#1C2330] tracking-tight">
            Quản Lý Tài Sản Cá Thể Hóa
          </h2>
          <p className="text-xs text-[#5B6472] mt-1">
            Gắn kết từng thiết bị vật lý với mã danh mục quy chuẩn của chuỗi. {userRole !== 'MANAGER' && "(Chỉ Manager mới có quyền thao tác)"}
          </p>
        </div>
        
        {userRole === 'MANAGER' && (
          <Button 
            variant="primary" 
            size="md" 
            icon={<Plus className="w-4 h-4" />}
            onClick={handleOpenCreate}
          >
            Thêm Tài Sản
          </Button>
        )}
      </div>

      {/* Filter bar */}
      <div className="flex items-center justify-between gap-3">
        <div className="relative w-full sm:w-80">
          <Search className="w-4 h-4 text-[#72777F] absolute left-3 top-1/2 -translate-y-1/2" />
          <input
            type="text"
            value={searchTerm}
            onChange={(e) => setSearchTerm(e.target.value)}
            placeholder="Tìm theo Serial, mã quy chuẩn, vị trí..."
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
        title={modalMode === 'create' ? 'Thêm Tài Sản Mới' : 'Sửa Thông Tin Tài Sản'}
        maxWidth="md"
        footer={
          <>
            <Button variant="secondary" onClick={() => setIsModalOpen(false)} disabled={submitting}>Hủy</Button>
            <Button variant="primary" onClick={handleSave} disabled={submitting || !name.trim() || !categoryId}>
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
              Danh mục <span className="text-red-500">*</span>
            </label>
            <select
              value={categoryId}
              onChange={(e) => setCategoryId(e.target.value)}
              className="w-full px-3 py-2 border border-[#DFE3E8] rounded text-sm focus:outline-none focus:border-[#00375E]"
            >
              <option value="">-- Chọn danh mục --</option>
              {categories.filter(c => c.active || c.id === categoryId).map(c => (
                <option key={c.id} value={c.id}>{c.name} {!c.active ? '(Đã tắt)' : ''}</option>
              ))}
            </select>
          </div>

          <div>
            <label className="block text-sm font-medium text-[#1C2330] mb-1">Tên thiết bị <span className="text-red-500">*</span></label>
            <input
              type="text"
              value={name}
              onChange={(e) => setName(e.target.value)}
              className="w-full px-3 py-2 border border-[#DFE3E8] rounded text-sm focus:outline-none focus:border-[#00375E]"
              placeholder="VD: Điều hòa Panasonic 12000 BTU"
            />
          </div>

          <div className="grid grid-cols-2 gap-4">
            <div>
              <label className="block text-sm font-medium text-[#1C2330] mb-1">Mã tài sản / Serial</label>
              <input
                type="text"
                value={assetCode}
                onChange={(e) => setAssetCode(e.target.value)}
                className="w-full px-3 py-2 border border-[#DFE3E8] rounded text-sm focus:outline-none focus:border-[#00375E]"
                placeholder="Để trống tự sinh"
              />
            </div>
            <div>
              <label className="block text-sm font-medium text-[#1C2330] mb-1">Vị trí (Khu vực)</label>
              <select
                value={areaId}
                onChange={(e) => setAreaId(e.target.value)}
                className="w-full px-3 py-2 border border-[#DFE3E8] rounded text-sm focus:outline-none focus:border-[#00375E]"
              >
                <option value="">-- Không chọn --</option>
                {areas.map(a => (
                  <option key={a.id} value={a.id}>{a.name}</option>
                ))}
              </select>
            </div>
          </div>

          <div>
            <label className="block text-sm font-medium text-[#1C2330] mb-1">Ghi chú</label>
            <textarea
              value={note}
              onChange={(e) => setNote(e.target.value)}
              className="w-full px-3 py-2 border border-[#DFE3E8] rounded text-sm focus:outline-none focus:border-[#00375E]"
              rows={2}
            />
          </div>
        </div>
      </Modal>

      {/* STATUS MODAL */}
      <Modal
        isOpen={isStatusModalOpen}
        onClose={() => setIsStatusModalOpen(false)}
        title="Cập nhật Trạng thái"
        maxWidth="sm"
        footer={
          <>
            <Button variant="secondary" onClick={() => setIsStatusModalOpen(false)} disabled={submitting}>Hủy</Button>
            <Button variant="primary" onClick={handleUpdateStatus} disabled={submitting}>
              {submitting ? 'Đang cập nhật...' : 'Cập nhật'}
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
          <p className="text-sm text-[#42474E]">
            Chọn trạng thái mới cho thiết bị <span className="font-bold text-[#1C2330]">{selectedAsset?.name}</span>:
          </p>
          <div>
            <select
              value={newStatus}
              onChange={(e) => setNewStatus(e.target.value)}
              className="w-full px-3 py-2 border border-[#DFE3E8] rounded text-sm focus:outline-none focus:border-[#00375E]"
            >
              <option value="GOOD">Hoạt động tốt</option>
              <option value="BROKEN">Hỏng</option>
              <option value="UNDER_REPAIR">Đang sửa chữa</option>
              <option value="DISPOSED">Thanh lý</option>
            </select>
            <p className="text-xs text-[#72777F] mt-2">
              Lưu ý: Nếu chọn "Thanh lý", tài sản sẽ bị ẩn khỏi danh sách vận hành và không thể khôi phục lại trạng thái.
            </p>
          </div>
        </div>
      </Modal>
    </div>
  );
};
