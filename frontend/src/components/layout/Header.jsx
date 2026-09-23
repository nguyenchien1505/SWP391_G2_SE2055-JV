import React, { useState, useRef, useEffect } from 'react';
import {
  Building2,
  Clock,
  Bell,
  ChevronDown,
  Menu,
  Check,
  Server,
  LogOut,
  Shield,
} from 'lucide-react';
import { HOTEL_BRANCHES } from '../../services/roomService';

export const Header = ({
  onToggleMobileMenu,
  selectedHotelId,
  onSelectHotel,
  onOpenApiConfig,
  user,
  onLogout,
}) => {
  const [isHotelDropdownOpen, setIsHotelDropdownOpen] = useState(false);
  const [isProfileDropdownOpen, setIsProfileDropdownOpen] = useState(false);
  const [isNotificationsOpen, setIsNotificationsOpen] = useState(false);

  const hotelDropdownRef = useRef(null);
  const profileDropdownRef = useRef(null);
  const notifDropdownRef = useRef(null);

  const currentHotel =
    HOTEL_BRANCHES.find((h) => h.id === selectedHotelId) || HOTEL_BRANCHES[1];

  // Close dropdowns on outside click
  useEffect(() => {
    const handleClickOutside = (event) => {
      if (
        hotelDropdownRef.current &&
        !hotelDropdownRef.current.contains(event.target)
      ) {
        setIsHotelDropdownOpen(false);
      }
      if (
        profileDropdownRef.current &&
        !profileDropdownRef.current.contains(event.target)
      ) {
        setIsProfileDropdownOpen(false);
      }
      if (
        notifDropdownRef.current &&
        !notifDropdownRef.current.contains(event.target)
      ) {
        setIsNotificationsOpen(false);
      }
    };
    document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, []);

  const notifications = [
    {
      id: 'n1',
      title: 'Đồng bộ quy chuẩn toàn chuỗi',
      time: '10 phút trước',
      desc: 'Giám đốc đã cập nhật danh mục TS-CD-04 và VT-TH-01 cho 3 khách sạn.',
      unread: true,
    },
    {
      id: 'n2',
      title: 'Cảnh báo lệch chuẩn chi nhánh',
      time: '1 giờ trước',
      desc: 'Sao Mai Resort Nha Trang phát hiện 4 mục chưa khớp chuẩn trung tâm.',
      unread: false,
    },
    {
      id: 'n3',
      title: 'Kiểm kê tài sản phòng 205',
      time: 'Hôm nay 08:30',
      desc: 'Hoàn tất bàn giao ca kiểm tra phòng Executive.',
      unread: false,
    },
  ];

  return (
    <header className="h-14 bg-white border-b border-[#DFE3E8] px-4 lg:px-6 flex items-center justify-between sticky top-0 z-30 shrink-0">
      {/* Left side: Hamburger & Hotel Selector */}
      <div className="flex items-center gap-3">
        <button
          type="button"
          onClick={onToggleMobileMenu}
          className="lg:hidden p-2 rounded text-[#5B6472] hover:text-[#1C2330] hover:bg-[#F7F8FA] cursor-pointer"
          aria-label="Mở menu"
        >
          <Menu className="w-5 h-5" />
        </button>

        {/* Hotel Branch Selector Dropdown */}
        <div className="relative" ref={hotelDropdownRef}>
          <button
            type="button"
            onClick={() => setIsHotelDropdownOpen(!isHotelDropdownOpen)}
            className="flex items-center gap-2 px-2.5 py-1.5 rounded hover:bg-[#F7F8FA] border border-transparent hover:border-[#DFE3E8] transition-colors text-xs font-semibold text-[#00375E] cursor-pointer"
          >
            <Building2 className="w-4 h-4 text-[#00375E] shrink-0" />
            <span className="max-w-[180px] sm:max-w-[280px] truncate">
              {currentHotel.name}
            </span>
            <ChevronDown className="w-3.5 h-3.5 text-[#5B6472] shrink-0" />
          </button>

          {/* Dropdown Menu */}
          {isHotelDropdownOpen && (
            <div className="absolute left-0 mt-1.5 w-72 bg-white rounded-lg shadow-lg border border-[#DFE3E8] py-1.5 z-40 text-xs">
              <div className="px-3 py-1.5 text-[10px] font-bold text-[#72777F] uppercase tracking-wider border-b border-[#DFE3E8]">
                Đơn vị khách sạn thành viên
              </div>
              {HOTEL_BRANCHES.map((hotel) => (
                <button
                  key={hotel.id}
                  type="button"
                  onClick={() => {
                    onSelectHotel(hotel.id);
                    setIsHotelDropdownOpen(false);
                  }}
                  className={`w-full flex items-center justify-between px-3 py-2 text-left hover:bg-[#F7F8FA] transition-colors cursor-pointer ${
                    hotel.id === selectedHotelId
                      ? 'bg-[#EFF4FF] text-[#00375E] font-semibold'
                      : 'text-[#1C2330]'
                  }`}
                >
                  <div className="truncate">
                    <p className="truncate">{hotel.name}</p>
                    <p className="text-[10px] text-[#72777F]">{hotel.city} · {hotel.roomCount} phòng</p>
                  </div>
                  {hotel.id === selectedHotelId && (
                    <Check className="w-4 h-4 text-[#00375E] shrink-0 ml-2" />
                  )}
                </button>
              ))}
            </div>
          )}
        </div>
      </div>

      {/* Right side: Time, Notifications, User Profile */}
      <div className="flex items-center gap-2 sm:gap-4">
        {/* Date & Time pill */}
        <div className="hidden md:flex items-center gap-1.5 px-3 py-1 rounded bg-[#F7F8FA] border border-[#DFE3E8] text-xs text-[#5B6472]">
          <Clock className="w-3.5 h-3.5 text-[#72777F]" />
          <span>Hôm nay: Thứ Năm, 24/10/2024 · 09:30</span>
        </div>

        {/* Notifications */}
        <div className="relative" ref={notifDropdownRef}>
          <button
            type="button"
            onClick={() => setIsNotificationsOpen(!isNotificationsOpen)}
            className="p-2 rounded-full text-[#5B6472] hover:text-[#1C2330] hover:bg-[#F7F8FA] relative transition-colors cursor-pointer"
            aria-label="Thông báo"
          >
            <Bell className="w-4.5 h-4.5" />
            <span className="absolute top-1.5 right-1.5 w-2 h-2 bg-red-500 rounded-full ring-2 ring-white"></span>
          </button>

          {isNotificationsOpen && (
            <div className="absolute right-0 mt-2 w-80 bg-white rounded-lg shadow-xl border border-[#DFE3E8] py-2 z-40 text-xs">
              <div className="px-4 py-2 border-b border-[#DFE3E8] flex items-center justify-between">
                <span className="font-semibold text-[#1C2330]">Thông báo hệ thống</span>
                <span className="text-[10px] bg-red-50 text-red-600 px-1.5 py-0.5 rounded font-medium">1 mới</span>
              </div>
              <div className="max-h-64 overflow-y-auto divide-y divide-[#DFE3E8]/60">
                {notifications.map((n) => (
                  <div
                    key={n.id}
                    className={`p-3 hover:bg-[#F7F8FA] transition-colors cursor-pointer ${
                      n.unread ? 'bg-[#F0F5FF]' : ''
                    }`}
                  >
                    <div className="flex justify-between items-start gap-1">
                      <p className="font-semibold text-[#1C2330] text-xs">{n.title}</p>
                      <span className="text-[10px] text-[#72777F] shrink-0">{n.time}</span>
                    </div>
                    <p className="text-[11px] text-[#5B6472] mt-1 leading-relaxed">{n.desc}</p>
                  </div>
                ))}
              </div>
            </div>
          )}
        </div>

        {/* User Profile */}
        <div className="relative" ref={profileDropdownRef}>
          <button
            type="button"
            onClick={() => setIsProfileDropdownOpen(!isProfileDropdownOpen)}
            className="flex items-center gap-2.5 pl-1.5 pr-2 py-1 rounded-full hover:bg-[#F7F8FA] transition-colors cursor-pointer"
          >
            <div className="w-8 h-8 rounded-full bg-[#00375E] text-white flex items-center justify-center text-xs font-bold ring-1 ring-[#DFE3E8] overflow-hidden relative">
              <img
                src="https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=150&auto=format&fit=crop&q=80"
                alt="Lê Hoàng Phúc"
                className="w-full h-full object-cover"
                onError={(e) => {
                  e.currentTarget.style.display = 'none';
                }}
              />
              <span className="absolute">
                {user?.email ? user.email.charAt(0).toUpperCase() : 'U'}
              </span>
            </div>

            <div className="hidden sm:block text-left">
              <p className="text-xs font-semibold text-[#1C2330] leading-tight truncate max-w-[120px]">
                {user?.email || 'Người dùng'}
              </p>
              <p className="text-[11px] text-[#5B6472] leading-tight">
                {user?.role || 'Nhân viên'}
              </p>
            </div>

            <ChevronDown className="w-3.5 h-3.5 text-[#5B6472] shrink-0" />
          </button>

          {isProfileDropdownOpen && (
            <div className="absolute right-0 mt-2 w-64 bg-white rounded-lg shadow-xl border border-[#DFE3E8] py-1.5 z-40 text-xs">
              <div className="px-4 py-2.5 border-b border-[#DFE3E8] sm:hidden">
                <p className="font-semibold text-[#1C2330]">Lê Hoàng Phúc</p>
                <p className="text-[11px] text-[#5B6472]">Giám đốc vận hành</p>
              </div>

              <div className="py-1">
                <button
                  type="button"
                  onClick={() => {
                    onOpenApiConfig();
                    setIsProfileDropdownOpen(false);
                  }}
                  className="w-full flex items-center gap-2 px-4 py-2 text-left text-[#1C2330] hover:bg-[#F7F8FA] hover:text-[#00375E] cursor-pointer"
                >
                  <Server className="w-4 h-4 text-[#00375E]" />
                  <span>Cấu hình REST API Spring Boot</span>
                </button>

                <div className="w-full flex items-center gap-2 px-4 py-2 text-left text-[#1C2330] hover:bg-[#F7F8FA]">
                  <Shield className="w-4 h-4 text-[#5B6472]" />
                  <span>Quyền hạn: Quản trị viên cấp cao</span>
                </div>
              </div>

              <div className="border-t border-[#DFE3E8] pt-1">
                <button
                  type="button"
                  onClick={onLogout}
                  className="w-full flex items-center gap-2 px-4 py-2 text-left text-red-600 hover:bg-red-50 cursor-pointer"
                >
                  <LogOut className="w-4 h-4" />
                  <span>Đăng xuất</span>
                </button>
              </div>
            </div>
          )}
        </div>
      </div>
    </header>
  );
};
