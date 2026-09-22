import React from 'react';
import {
  LayoutGrid,
  Archive,
  FolderTree,
  Settings,
  Star,
  X,
  Building,
  Package,
  AlertTriangle,
} from 'lucide-react';

export const Sidebar = ({
  currentScreen,
  onSelectScreen,
  isOpenMobile,
  onCloseMobile,
}) => {
  const menuSections = [
    {
      sectionTitle: 'VẬN HÀNH CHUỖI',
      items: [
        { id: 'dashboard', label: 'Dashboard', icon: <LayoutGrid className="w-4 h-4" /> },
      ],
    },
    {
      sectionTitle: 'TÀI SẢN & KHU VỰC',
      items: [
        { id: 'assets', label: 'Quản lý tài sản', icon: <Archive className="w-4 h-4" /> },
        { id: 'consumables', label: 'Tồn kho tiêu hao', icon: <Package className="w-4 h-4" /> },
        { id: 'damage-reports', label: 'Quản lý báo hỏng', icon: <AlertTriangle className="w-4 h-4" /> },
        { id: 'catalog', label: 'Danh mục hệ thống', icon: <FolderTree className="w-4 h-4" /> },
        { id: 'locations', label: 'Quản lý khu vực', icon: <Building className="w-4 h-4" /> },
      ],
    },
  ];

  const handleItemClick = (id) => {
    onSelectScreen(id);
    onCloseMobile();
  };

  return (
    <>
      {/* Mobile backdrop */}
      {isOpenMobile && (
        <div
          className="fixed inset-0 bg-[#001D35]/40 backdrop-blur-xs z-40 lg:hidden"
          onClick={onCloseMobile}
        />
      )}

      {/* Sidebar Container */}
      <aside
        className={`fixed top-0 left-0 bottom-0 w-64 bg-white border-r border-[#DFE3E8] z-50 flex flex-col transition-transform duration-200 ease-in-out lg:translate-x-0 ${
          isOpenMobile ? 'translate-x-0' : '-translate-x-full'
        }`}
      >
        {/* Brand Header */}
        <div className="h-14 px-5 border-b border-[#DFE3E8] flex items-center justify-between shrink-0">
          <div className="flex items-center gap-2.5">
            <div className="w-7 h-7 rounded bg-[#00375E] text-white flex items-center justify-center shadow-xs">
              <Star className="w-4 h-4 fill-white" />
            </div>
            <div>
              <h1 className="text-xs font-bold text-[#00375E] leading-none tracking-wide">
                SAO MAI
              </h1>
              <p className="text-[9px] font-semibold text-[#5B6472] tracking-widest mt-0.5">
                HOSPITALITY OPS
              </p>
            </div>
          </div>

          <button
            type="button"
            onClick={onCloseMobile}
            className="lg:hidden p-1 rounded text-[#5B6472] hover:text-[#1C2330] hover:bg-[#F7F8FA] cursor-pointer"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Navigation items */}
        <div className="flex-1 overflow-y-auto py-3 px-3 space-y-5">
          {menuSections.map((section, sIdx) => (
            <div key={sIdx}>
              <div className="px-2.5 mb-1.5 text-[10px] font-bold uppercase tracking-wider text-[#72777F]">
                {section.sectionTitle}
              </div>
              <ul className="space-y-0.5">
                {section.items.map((item) => {
                  const isActive = currentScreen === item.id;
                  return (
                    <li key={item.id}>
                      <button
                        type="button"
                        onClick={() => handleItemClick(item.id)}
                        className={`w-full flex items-center gap-3 px-2.5 py-2 rounded text-xs transition-colors text-left cursor-pointer ${
                          isActive
                            ? 'bg-[#EFF4FF] text-[#00375E] font-semibold shadow-2xs'
                            : 'text-[#42474E] hover:bg-[#F7F8FA] hover:text-[#1C2330]'
                        }`}
                      >
                        <span className={`shrink-0 ${isActive ? 'text-[#00375E]' : 'text-[#72777F]'}`}>
                          {item.icon}
                        </span>
                        <span className="truncate">{item.label}</span>
                      </button>
                    </li>
                  );
                })}
              </ul>
            </div>
          ))}
        </div>

        {/* Sidebar Footer */}
        <div className="p-3.5 border-t border-[#DFE3E8] bg-[#FAFBFC] flex items-center justify-between text-xs text-[#5B6472] shrink-0">
          <div>
            <p className="text-[11px] font-medium text-[#1C2330]">Phiên bản Cloud</p>
            <p className="text-[10px] text-[#72777F]">v2.6 Enterprise</p>
          </div>
          <button
            type="button"
            className="p-1.5 rounded hover:bg-[#EFF4FF] text-[#72777F] hover:text-[#00375E] transition-colors cursor-pointer"
            title="Cài đặt hệ thống"
          >
            <Settings className="w-4 h-4" />
          </button>
        </div>
      </aside>
    </>
  );
};
