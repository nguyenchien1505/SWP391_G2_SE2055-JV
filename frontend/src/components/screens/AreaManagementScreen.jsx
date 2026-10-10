import React, { useState, useEffect } from 'react';
import { Table } from '../common/Table';
import { Button } from '../common/Button';
import { Modal } from '../common/Modal';
import { apiClient } from '../../services/apiClient';
import { assetService } from '../../services/assetApi';
import { useMediaQuery } from '../../hooks/useMediaQuery';
import { formatDateTime } from '../../pages/rooms/format';
import AreaPanel from '../AreaPanel';
import AssetAuditModal from '../AssetAuditModal';
import { BatchCreateAssetsModal } from '../modals/BatchCreateAssetsModal';
import { DamageReportModal } from '../modals/DamageReportModal';
import { groupReportsBy } from '../damage-reports/ViewReportButton';
// Bảng chi tiết dùng chung khung `board-panel` / `board-overlay` với sơ đồ phòng.
import '../../pages/rooms/rooms.css';

/** Từ khổ này bảng chi tiết nằm HẲN bên phải danh sách; nhỏ hơn thì mở đè lên — giống sơ đồ phòng. */
const WIDE_QUERY = '(min-width: 1200px)';

/**
 * Quản lý khu vực chung của khách sạn (BR-ORG-12). Bấm một khu vực → bảng chi tiết bên cạnh (cùng
 * cấu trúc với sơ đồ phòng): thông tin, tài sản trong khu vực và các thao tác của Manager — thêm
 * tài sản (màn thêm hàng loạt, chọn sẵn khu vực), kiểm kê, sửa tên, xóa.
 */
export const AreaManagementScreen = ({ userRole }) => {
  const wide = useMediaQuery(WIDE_QUERY);
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

  // Tài sản cố định (chưa thanh lý) theo khu vực: areaId → danh sách. null = đang tải.
  const [assetsByArea, setAssetsByArea] = useState(null);
  const [assetsError, setAssetsError] = useState('');
  const [selectedId, setSelectedId] = useState(null);
  const [auditingArea, setAuditingArea] = useState(null);
  const [addingAssetTo, setAddingAssetTo] = useState(null);
  // Báo hỏng theo khu vực: areaId → danh sách (đang chờ trước). null = đang tải.
  const [reportsByArea, setReportsByArea] = useState(null);
  const [openReportId, setOpenReportId] = useState(null);
  const [banner, setBanner] = useState('');

  const loadData = async () => {
    setLoading(true);
    setErrorMessage(null);
    try {
      const response = await apiClient.get('/organization/areas?size=200');
      setAreas(response?.content || []);
    } catch (error) {
      console.error('Error fetching areas:', error);
      setErrorMessage('Không thể tải danh sách khu vực');
    } finally {
      setLoading(false);
    }
  };

  /** Một lần gọi lấy toàn bộ tài sản của khách sạn rồi chia theo khu vực — không gọi từng dòng. */
  const loadAssets = async () => {
    setAssetsError('');
    try {
      const assets = await assetService.getReportableAssets();
      const grouped = {};
      for (const asset of assets) {
        if (asset.areaId) (grouped[asset.areaId] ??= []).push(asset);
      }
      setAssetsByArea(grouped);
    } catch (error) {
      console.error('Error fetching assets:', error);
      setAssetsError(error.message || 'Không tải được tài sản của các khu vực');
      setAssetsByArea({});
    }
  };

  /** Một lần gọi lấy toàn bộ báo hỏng của khách sạn rồi chia theo khu vực của tài sản. */
  const loadReports = async () => {
    try {
      setReportsByArea(groupReportsBy(await assetService.getAllDamageReports(), 'areaId'));
    } catch (error) {
      console.error('Error fetching damage reports:', error);
      setReportsByArea({}); // Thông tin phụ — không chặn cả màn hình.
    }
  };

  useEffect(() => {
    loadData();
    loadAssets();
    loadReports();
  }, []);

  const assetsOf = (area) => assetsByArea?.[area.id] ?? [];

  const handleAddAsset = (area) => setAddingAssetTo(area);

  const handleOpenAudit = (area) => {
    setBanner('');
    setAuditingArea(area);
  };

  const handleAuditSaved = (changedCount) => {
    setBanner(`Đã lưu kiểm kê khu vực ${auditingArea.name}: cập nhật tình trạng ${changedCount} tài sản.`);
    setAuditingArea(null);
    loadAssets();
  };

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
      if (areaToDelete.id === selectedId) setSelectedId(null);
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
          <span className="material-symbols-outlined text-[16px] leading-none text-[#5B6472]">apartment</span>
          <span>{item.name}</span>
        </div>
      ),
    },
    {
      header: 'TÀI SẢN',
      width: '120px',
      accessor: (item) => (
        <span className="text-xs font-semibold text-[#0e61a1]">
          {assetsByArea === null ? 'Đang tải…' : `${assetsOf(item).length} tài sản`}
        </span>
      ),
    },
    {
      header: 'CẬP NHẬT',
      width: '150px',
      accessor: (item) => <span className="text-xs text-[#5B6472]">{formatDateTime(item.updatedAt)}</span>,
    },
  ];

  if (userRole === 'MANAGER') {
    columns.push({
      header: 'THAO TÁC',
      width: '96px',
      // stopPropagation: bấm Sửa / Xóa không mở bảng chi tiết của dòng.
      accessor: (item) => (
        <div className="flex items-center gap-1" onClick={(e) => e.stopPropagation()}>
          <button
            type="button"
            onClick={() => handleOpenEdit(item)}
            className="p-1.5 text-[#5B6472] hover:text-[#00375E] hover:bg-[#EFF4FF] rounded transition-colors cursor-pointer"
            title="Sửa tên"
            aria-label={`Sửa tên ${item.name}`}
          >
            <span className="material-symbols-outlined text-[18px] leading-none">edit</span>
          </button>
          <button
            type="button"
            onClick={() => handleOpenDelete(item)}
            className="p-1.5 text-[#5B6472] hover:text-red-600 hover:bg-red-50 rounded transition-colors cursor-pointer"
            title="Xóa khu vực"
            aria-label={`Xóa ${item.name}`}
          >
            <span className="material-symbols-outlined text-[18px] leading-none">delete</span>
          </button>
        </div>
      ),
    });
  }

  const detailArea = areas.find((area) => area.id === selectedId) ?? null;
  const modalOpen = isModalOpen || isDeleteModalOpen || Boolean(auditingArea || addingAssetTo || openReportId);

  // Esc đóng bảng chi tiết khi nó đang đè lên trang — trừ lúc có hộp thoại mở (Esc là của hộp thoại).
  useEffect(() => {
    if (wide || !detailArea || modalOpen) return undefined;
    const onKey = (e) => e.key === 'Escape' && setSelectedId(null);
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [wide, detailArea, modalOpen]);

  const panelProps = detailArea && {
    area: detailArea,
    assets: assetsByArea === null ? null : assetsOf(detailArea),
    canManage: userRole === 'MANAGER',
    onClose: () => setSelectedId(null),
    onAddAsset: handleAddAsset,
    onAudit: handleOpenAudit,
    reports: reportsByArea === null ? null : reportsByArea[detailArea.id] ?? [],
    onOpenReport: (report) => setOpenReportId(report.id),
  };

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

      {banner && (
        <div className="alert alert--success" role="status">
          {banner}
          <button type="button" className="alert__close" onClick={() => setBanner('')} aria-label="Đóng">
            ×
          </button>
        </div>
      )}
      {assetsError && (
        <div className="bg-red-50 text-red-600 p-3 rounded text-sm border border-red-200">{assetsError}</div>
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
            icon={<span className="material-symbols-outlined text-[16px] leading-none">add</span>}
            onClick={handleOpenCreate}
          >
            Thêm Khu Vực
          </Button>
        )}
      </div>

      <div className="flex items-center justify-between gap-3">
        <div className="relative w-full sm:w-80">
          <span className="material-symbols-outlined text-[16px] leading-none text-[#72777F] absolute left-3 top-1/2 -translate-y-1/2">search</span>
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
        <div className={`board-layout ${wide ? 'board-layout--with-panel board-layout--area' : ''}`}>
          <div className="board-main">
            <Table
              columns={columns}
              data={filtered}
              keyExtractor={(item) => item.id}
              onRowClick={(item) => setSelectedId(item.id)}
              isRowSelected={(item) => item.id === selectedId}
              minWidthClass="min-w-[480px]"
              emptyMessage={searchTerm ? 'Không có khu vực nào khớp từ khóa.' : 'Chưa có khu vực nào.'}
            />
          </div>

          {wide && (detailArea ? (
            <AreaPanel {...panelProps} />
          ) : (
            <aside className="board-panel board-panel--empty">
              <span className="material-symbols-outlined" aria-hidden="true">touch_app</span>
              <p>Chọn một khu vực để xem chi tiết và thao tác.</p>
            </aside>
          ))}
        </div>
      )}

      {!wide && detailArea && (
        <div className="board-overlay" onClick={() => setSelectedId(null)}>
          <div
            className="board-overlay__sheet board-overlay__sheet--wide"
            role="dialog"
            aria-modal="true"
            aria-labelledby="area-panel-title"
            onClick={(e) => e.stopPropagation()}
          >
            <AreaPanel {...panelProps} autoFocusClose />
          </div>
        </div>
      )}

      {openReportId && (
        <DamageReportModal
          incidentId={openReportId}
          onClose={() => setOpenReportId(null)}
          onResolved={() => {
            // Đóng phiếu có thể đổi trạng thái tài sản → nạp lại cả hai.
            loadReports();
            loadAssets();
          }}
        />
      )}

      {addingAssetTo && (
        <BatchCreateAssetsModal
          initialAreaId={addingAssetTo.id}
          onClose={() => setAddingAssetTo(null)}
          onCreated={loadAssets}
        />
      )}

      {auditingArea && (
        <AssetAuditModal
          scope={{ areaId: auditingArea.id }}
          title={`Kiểm kê tài sản khu vực ${auditingArea.name}`}
          emptyText="Khu vực này chưa có tài sản cố định nào."
          onClose={() => setAuditingArea(null)}
          onSaved={handleAuditSaved}
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
