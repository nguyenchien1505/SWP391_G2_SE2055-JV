import React, { useState, useEffect } from 'react';
import {
  Download,
  Plus,
  Search,
  Building2,
  MapPin,
  Snowflake,
  Tv,
  Refrigerator,
  Flame,
  Shirt,
  Droplet,
  Sparkles,
  Wind,
  Network,
  Cpu,
  PackageCheck,
  Building,
  RefreshCw,
  AlertCircle,
  Code2,
  CheckCircle2,
} from 'lucide-react';
import { Button } from '../common/Button';
import { StatCard } from '../common/StatCard';
import { Table } from '../common/Table';
import { Switch } from '../common/Switch';
import { Pagination } from '../common/Pagination';
import { Badge } from '../common/Badge';
import { CatalogService } from '../../services/catalogService';
import { AddCatalogModal } from '../modals/AddCatalogModal';

export const CatalogScreen = ({ userRole }) => {
  const [activeTab, setActiveTab] = useState('ALL');
  const [searchTerm, setSearchTerm] = useState('');
  const [currentPage, setCurrentPage] = useState(0);
  const [isLoading, setIsLoading] = useState(false);
  const [catalogs, setCatalogs] = useState([]);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [metrics, setMetrics] = useState({
    totalCatalogs: 0,
    fixedAssetsCount: 0,
    consumablesCount: 0,
    chainAdoptionRate: 0,
  });
  const [branchCompliance, setBranchCompliance] = useState([]);

  // Modals
  const [isAddModalOpen, setIsAddModalOpen] = useState(false);
  const [isComplianceModalOpen, setIsComplianceModalOpen] = useState(false);
  const [toastMessage, setToastMessage] = useState(null);

  const showToast = (text, type = 'success') => {
    setToastMessage({ text, type });
    setTimeout(() => setToastMessage(null), 3500);
  };

  // Load catalogs data
  const loadData = async () => {
    setIsLoading(true);
    try {
      const pageData = await CatalogService.getCatalogs({
        category: activeTab,
        search: searchTerm,
        page: currentPage,
        size: 8,
      });
      setCatalogs(pageData.content);
      setTotalPages(pageData.totalPages);
      setTotalElements(pageData.totalElements);

      const metricsData = await CatalogService.getMetrics();
      setMetrics(metricsData);

      const complianceData = await CatalogService.getBranchCompliance();
      setBranchCompliance(complianceData);
    } catch (err) {
      console.error('Error loading catalogs:', err);
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    loadData();
  }, [activeTab, searchTerm, currentPage]);

  const handleToggleStatus = async (item, newStatus) => {
    try {
      await CatalogService.toggleStatus(item.id, newStatus);
      setCatalogs((prev) =>
        prev.map((c) => (c.id === item.id ? { ...c, active: newStatus } : c))
      );
      showToast(
        `Đã ${newStatus ? 'Bật' : 'Tắt'} danh mục quy chuẩn ${item.name}. Dữ liệu đã đồng bộ tới các khách sạn.`
      );
    } catch (err) {
      console.error('Failed to toggle status:', err);
    }
  };

  const handleCreateCatalog = async (dto) => {
    await CatalogService.createCatalog(dto);
    showToast(`Đã thêm mới danh mục thành công và cấp mã quy chuẩn toàn chuỗi!`);
    loadData();
  };

  const handleExportExcel = async () => {
    showToast('Đang tạo và tải về file Excel quy chuẩn mã danh mục...');
    await CatalogService.exportExcel();
  };


  const columns = [
    {
      header: 'TÊN DANH MỤC',
      width: '280px',
      accessor: (item) => (
        <div className="py-0.5">
          <p
            className={`font-semibold text-sm ${
              item.active ? 'text-[#1C2330]' : 'text-[#72777F] line-through'
            }`}
          >
            {item.name}
          </p>
        </div>
      ),
    },
    {
      header: 'PHÂN LOẠI',
      width: '150px',
      accessor: (item) => (
        <span className="text-xs font-medium text-[#00375E] bg-[#EFF4FF] px-2 py-1 rounded">
          {item.assetKind || 'N/A'}
        </span>
      ),
    },
    {
      header: 'MỤC ĐÍCH SỬ DỤNG',
      width: '180px',
      accessor: (item) => (
        <span className="text-xs text-[#5B6472]">
          {item.purpose || 'N/A'}
        </span>
      ),
    },
    {
      header: 'ĐƠN VỊ TÍNH',
      width: '120px',
      accessor: (item) => (
        <span className="text-xs text-[#1C2330]">
          {item.unit || '-'}
        </span>
      ),
    },
    {
      header: 'TRẠNG THÁI',
      width: '110px',
      headerClassName: 'text-right pr-6',
      className: 'text-right pr-6',
      accessor: (item) => (
        <div className="inline-flex justify-end">
          {userRole === 'DIRECTOR' ? (
            <Switch
              checked={item.active}
              onChange={(checked) => handleToggleStatus(item, checked)}
            />
          ) : (
            <Badge variant={item.active ? "success" : "default"}>
              {item.active ? "Đang bật" : "Đã tắt"}
            </Badge>
          )}
        </div>
      ),
    },
  ];

  return (
    <div className="space-y-5 pb-8">
      {/* Toast Notification */}
      {toastMessage && (
        <div className="fixed bottom-6 right-6 z-50 bg-[#00375E] text-white px-4 py-2.5 rounded-lg shadow-xl text-xs flex items-center gap-2 border border-[#1F4E78] animate-in fade-in slide-in-from-bottom-3 duration-200">
          <CheckCircle2 className="w-4 h-4 text-emerald-400 shrink-0" />
          <span>{toastMessage.text}</span>
        </div>
      )}

      {/* Breadcrumbs */}
      <div className="text-xs text-[#5B6472] flex items-center gap-1.5">
        <span className="hover:text-[#00375E] cursor-pointer">Toàn Chuỗi Sao Mai</span>
        <span className="text-[#94A3B8]">&gt;</span>
        <span className="font-semibold text-[#1C2330]">Quản trị Danh mục</span>
      </div>

      {/* Page Header & Actions */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <h2 className="text-2xl font-bold text-[#1C2330] tracking-tight">
            Danh Mục Tài Sản & Thiết Bị
          </h2>
          <p className="text-xs text-[#5B6472] mt-1">
            Quy chuẩn mã danh mục, nhóm tài sản và vật tư tiêu hao áp dụng đồng bộ cho các khách sạn thành viên.
          </p>
        </div>

        <div className="flex items-center gap-3 shrink-0">
          <Button
            variant="outline"
            size="md"
            icon={<Download className="w-4 h-4 text-[#5B6472]" />}
            onClick={handleExportExcel}
          >
            Xuất danh mục Excel
          </Button>

          {userRole === 'DIRECTOR' && (
            <Button
              variant="primary"
              size="md"
              icon={<Plus className="w-4 h-4" />}
              onClick={() => setIsAddModalOpen(true)}
            >
              Thêm danh mục mới
            </Button>
          )}
        </div>
      </div>

      {/* 4 Metric Summary Cards */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
        <StatCard
          title="TỔNG DANH MỤC"
          value={metrics.totalCatalogs}
          icon={<Network className="w-5 h-5 text-[#00375E]" />}
          iconBg="bg-[#EFF4FF]"
          iconColor="text-[#00375E]"
        />

        <StatCard
          title="TÀI SẢN CỐ ĐỊNH (TS-CD)"
          value={metrics.fixedAssetsCount}
          icon={<Tv className="w-5 h-5 text-[#00375E]" />}
          iconBg="bg-[#EFF4FF]"
          iconColor="text-[#00375E]"
          onClick={() => setActiveTab('TS_CD')}
        />

        <StatCard
          title="VẬT TƯ TIÊU HAO (VT-TH)"
          value={metrics.consumablesCount}
          icon={<PackageCheck className="w-5 h-5 text-[#2E7D32]" />}
          iconBg="bg-[#E8F5E9]"
          iconColor="text-[#2E7D32]"
          onClick={() => setActiveTab('VT_TH')}
        />

      </div>

      {/* Filter Tabs & Search Bar */}
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-3 pt-1">
        {/* Tabs */}
        <div className="flex items-center gap-2 overflow-x-auto pb-1 sm:pb-0">
          <button
            type="button"
            onClick={() => {
              setActiveTab('ALL');
              setCurrentPage(0);
            }}
            className={`px-3.5 py-1.5 rounded text-xs font-semibold whitespace-nowrap transition-colors cursor-pointer ${
              activeTab === 'ALL'
                ? 'bg-[#00375E] text-white shadow-2xs'
                : 'bg-white text-[#1C2330] border border-[#DFE3E8] hover:bg-[#F7F8FA]'
            }`}
          >
            Tất cả danh mục
          </button>

          <button
            type="button"
            onClick={() => {
              setActiveTab('TS_CD');
              setCurrentPage(0);
            }}
            className={`px-3.5 py-1.5 rounded text-xs font-medium whitespace-nowrap transition-colors cursor-pointer ${
              activeTab === 'TS_CD'
                ? 'bg-[#00375E] text-white font-semibold shadow-2xs'
                : 'bg-white text-[#1C2330] border border-[#DFE3E8] hover:bg-[#F7F8FA]'
            }`}
          >
            Tài sản cố định (Điện tử, Nội thất)
          </button>

          <button
            type="button"
            onClick={() => {
              setActiveTab('VT_TH');
              setCurrentPage(0);
            }}
            className={`px-3.5 py-1.5 rounded text-xs font-medium whitespace-nowrap transition-colors cursor-pointer ${
              activeTab === 'VT_TH'
                ? 'bg-[#00375E] text-white font-semibold shadow-2xs'
                : 'bg-white text-[#1C2330] border border-[#DFE3E8] hover:bg-[#F7F8FA]'
            }`}
          >
            Vật tư tiêu hao (Amenities & Linen)
          </button>
        </div>

        {/* Search Box */}
        <div className="relative w-full md:w-72 shrink-0">
          <Search className="w-4 h-4 text-[#72777F] absolute left-3 top-1/2 -translate-y-1/2" />
          <input
            type="text"
            value={searchTerm}
            onChange={(e) => {
              setSearchTerm(e.target.value);
              setCurrentPage(0);
            }}
            placeholder="Tìm theo mã hoặc tên danh mục..."
            className="w-full pl-9 pr-3 py-1.5 bg-white border border-[#DFE3E8] rounded text-xs text-[#1C2330] placeholder-[#72777F] focus:border-[#00375E] focus:ring-1 focus:ring-[#00375E] outline-none"
          />
        </div>
      </div>

      {/* Main Data Table */}
      <div>
        <Table
          columns={columns}
          data={catalogs}
          keyExtractor={(item) => item.id}
          isLoading={isLoading}
        />

        {/* Pagination */}
        <Pagination
          currentPage={currentPage}
          totalPages={totalPages}
          totalElements={totalElements}
          pageSize={8}
          onPageChange={setCurrentPage}
        />
      </div>

      {/* Bottom Summary & Business Rule Cards */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-4 pt-1">
        {/* Card 1: Quy ước chuẩn hóa toàn chuỗi */}
        <div className="bg-white border border-[#DFE3E8] rounded-lg p-4 flex flex-col justify-between shadow-2xs">
          <div>
            <div className="flex items-center gap-2 mb-2">
              <div className="w-6 h-6 rounded bg-[#EFF4FF] flex items-center justify-center text-[#00375E]">
                <Code2 className="w-3.5 h-3.5" />
              </div>
              <h4 className="font-semibold text-sm text-[#1C2330]">
                Quy ước chuẩn hóa toàn chuỗi
              </h4>
            </div>

            <p className="text-xs text-[#5B6472] mb-3 leading-relaxed">
              Mã danh mục được cấp duy nhất từ hệ thống quản trị trung tâm, phân chia thành 3 phân nhóm vận hành:
            </p>

            <div className="space-y-1.5 font-mono text-xs mb-3">
              <div className="flex items-start gap-2">
                <span className="text-[#00375E] font-bold">FIXED</span>
                <span className="text-[#42474E] font-sans text-[11px]">
                  : Tài sản cố định (Điện tử, Nội thất chính)
                </span>
              </div>
              <div className="flex items-start gap-2">
                <span className="text-[#2E7D32] font-bold">CONSUMABLE</span>
                <span className="text-[#42474E] font-sans text-[11px]">
                  : Vật tư tiêu hao (Amenities, Linen, Đồ vệ sinh)
                </span>
              </div>
            </div>
          </div>

          <div className="pt-2 border-t border-[#DFE3E8]/80 flex items-center gap-2 text-[11px] text-[#00375E]">
            <RefreshCw className="w-3.5 h-3.5 text-[#00375E] animate-spin" style={{ animationDuration: '6s' }} />
            <span>Đã đồng bộ thời gian thực với PMS & ERP chuỗi.</span>
          </div>
        </div>

        {/* Card 2: Lưu ý nghiệp vụ đồng bộ */}
        <div className="bg-white border border-[#DFE3E8] rounded-lg p-4 flex flex-col justify-between shadow-2xs">
          <div>
            <div className="flex items-center gap-2 mb-2">
              <div className="w-6 h-6 rounded bg-[#FFF3E0] flex items-center justify-center text-[#E65100]">
                <AlertCircle className="w-3.5 h-3.5" />
              </div>
              <h4 className="font-semibold text-sm text-[#1C2330]">
                Lưu ý nghiệp vụ đồng bộ
              </h4>
            </div>

            <p className="text-xs text-[#5B6472] mb-2 font-medium">
              Khi Tắt một danh mục quy chuẩn:
            </p>

            <div className="bg-[#FFF1F2] border border-[#FECDD3] rounded p-2.5 text-xs text-[#9F1239] leading-relaxed mb-3">
              Tất cả khách sạn thành viên sẽ không thể khai báo mới tài sản thuộc nhóm này vào hệ thống phòng hoặc kho vật tư.
            </div>

            <p className="text-[11px] text-[#5B6472] leading-relaxed">
              Các tài sản hiện hữu đã liên kết số serial vẫn được giữ nguyên dữ liệu kiểm kê để đảm bảo lịch sử khấu hao.
            </p>
          </div>

          <div className="pt-2 border-t border-[#DFE3E8]/80 text-[11px] text-[#72777F]">
            Áp dụng lập tức trên toàn hệ thống khi thay đổi.
          </div>
        </div>

      </div>

      {/* Add Catalog Modal */}
      <AddCatalogModal
        isOpen={isAddModalOpen}
        onClose={() => setIsAddModalOpen(false)}
        onSubmit={handleCreateCatalog}
      />
    </div>
  );
};
