import axios from 'axios';

// Cookie helper utilities for Session Cookie authentication
export function setCookie(name, value, days = 7) {
  const expires = new Date(Date.now() + days * 864e5).toUTCString();
  document.cookie = `${name}=${encodeURIComponent(value)}; expires=${expires}; path=/; SameSite=Lax`;
}

export function getCookie(name) {
  const match = document.cookie.match(new RegExp('(^| )' + name + '=([^;]+)'));
  return match ? decodeURIComponent(match[2]) : null;
}

export function eraseCookie(name) {
  document.cookie = `${name}=; Max-Age=-99999999; path=/;`;
}

// Default session data
const DEFAULT_SESSION_USER = {
  id: 'USR-MGR-001',
  name: 'Lê Hoàng Phúc',
  role: 'Quản lý khách sạn',
  roleCode: 'HOTEL_MANAGER',
  branch: 'Sao Mai Nha Trang',
  branchCode: 'SM-NHA-TRANG',
  avatar: 'https://lh3.googleusercontent.com/aida/AEtjO1UH7wexUU3fe0AiojdBASCOcLrqMAjWLGm2BEpXximteDfAtygBTvD7D_VHF9YBqtlTuoVK4IT_X1RN7itp865rs45ipaIBfripZKj5OnC8oOjig74eTwqsNqR1vVEAojRSlKfw6vN6AO6DJyQdiUHNFGP4LNZM9Vayh5M8jPqiTYJCEITimxvC1ChAtMKEXXOKcxJpr11VaAMQZllnobcd5Fv3AT7ZB2_aTqF7YMg3rSG45B_cW34AQBfw',
  sessionToken: 'sess_saomai_' + Math.random().toString(36).substring(2),
  lastLogin: '24/10/2024 · 14:32'
};

// Initialize session cookie if not present
if (!getCookie('saomai_session')) {
  setCookie('saomai_session', JSON.stringify(DEFAULT_SESSION_USER));
}

// Pre-configured Axios instance with withCredentials
export const apiClient = axios.create({
  baseURL: '/api',
  withCredentials: true,
  timeout: 10000,
  headers: {
    'Content-Type': 'application/json',
    'X-Requested-With': 'XMLHttpRequest'
  }
});

// Axios Request Interceptor: Attach Session Cookie header simulation
apiClient.interceptors.request.use((config) => {
  const sessionData = getCookie('saomai_session');
  if (sessionData) {
    try {
      const parsed = JSON.parse(sessionData);
      config.headers['Authorization'] = `Session ${parsed.sessionToken}`;
      config.headers['X-Branch-ID'] = parsed.branchCode;
    } catch (e) {
      console.warn('Invalid session cookie', e);
    }
  }
  return config;
});

// INITIAL MOCK DATA STORAGE
const STORAGE_KEYS = {
  ASSETS: 'saomai_fixed_assets_v2',
  CONSUMABLES: 'saomai_consumables_v2',
  INCIDENTS: 'saomai_incidents_v2'
};

const INITIAL_FIXED_ASSETS = [
  {
    code: 'TS-310-AC-01',
    name: 'Daikin Inverter 1.5HP',
    category: 'Điều hòa',
    categoryCode: 'ac',
    categoryFullName: 'Điều hòa nhiệt độ Daikin Inverter 2.0HP',
    model: 'FTKF50XVMV',
    purpose: 'Dùng cho khách',
    purposeCode: 'guest',
    locationType: 'room',
    location: 'Phòng 310',
    locationSub: 'Deluxe Hướng Biển · Tầng 3',
    status: 'Good',
    statusLabel: 'Tốt (Good)',
    lastUpdate: '22/10/2024 · 09:15',
    updatedBy: 'Lê Văn Hùng (Kỹ thuật)',
    createdAt: '12/03/2024',
    createdBy: 'Lê Hoàng Phúc (Manager)',
    image: 'https://lh3.googleusercontent.com/aida-public/AB6AXuD73B-7sxokHUMITEtlnX8FwmkJtLh2N1vcnsTu-0sfBGL0IrWR8bAcpf4Ilh3962zGfSB-QA9_JtZwrT2lqQ2ncTY-XP9qbs72otJJjqWBXdkgvejOo860klwujXoHiVkIsiKsVjGv1XlhFGJO85fIR8VW_9PanEbZIijmIBl0TO_r3lUulDF0nYMaVuNUopltkKE9BToovm_vBYM-ta5EgKG_3pvjMs9JEZY6cud15wbVWse-r6PB8Q',
    bookValue: '15,850,000 đ',
    warranty: 'Đến 12/2025'
  },
  {
    code: 'TS-205-TV-02',
    name: 'Samsung 55 Inch 4K',
    category: 'Smart TV',
    categoryCode: 'tv',
    categoryFullName: 'Smart TV Samsung 55 Inch Crystal UHD',
    model: 'UA55CU8000',
    purpose: 'Dùng cho khách',
    purposeCode: 'guest',
    locationType: 'room',
    location: 'Phòng 205',
    locationSub: 'Superior Đôi · Tầng 2',
    status: 'Damaged',
    statusLabel: 'Bị hỏng (Damaged)',
    lastUpdate: '24/10/2024 · 11:40',
    updatedBy: 'Trần Mai Anh (Buồng phòng)',
    createdAt: '15/01/2024',
    createdBy: 'Lê Hoàng Phúc (Manager)',
    image: 'https://lh3.googleusercontent.com/aida-public/AB6AXuDVXDVrwDIklZ91EMotOJm6Tt1N1T95YLwQxYN2itXrtHaxjmG1U62jSQHV8Trpqc1F54mvR39QgwloOG_7ijMgDQnAnDK9I6_-JsL4JIwgchybfxnHy9cPu7PvIXdjKj7xaUZ2H1HoWU5jgopJ2FGBnXeHyZYRED2CLH8E42rnfbQ9x9gkZ5VuagN2YQTHguqURpYNQNYuMN0SVsuxdiN3V77v1Notod-AmmR1MAKkW_S0oIdsaxZSZA',
    bookValue: '12,500,000 đ',
    warranty: 'Đến 06/2025'
  },
  {
    code: 'TS-LBY-AC-03',
    name: 'Panasonic Cassette 5.0HP',
    category: 'Điều hòa âm trần',
    categoryCode: 'ac',
    categoryFullName: 'Điều hòa âm trần cassette Panasonic Inverter',
    model: 'S-3448PU3H',
    purpose: 'Duy trì cơ sở',
    purposeCode: 'facility',
    locationType: 'area',
    location: 'Sảnh chính (Lobby)',
    locationSub: 'Khu vực Lễ tân · Tầng 1',
    status: 'Repairing',
    statusLabel: 'Đang sửa chữa (Repairing)',
    lastUpdate: '23/10/2024 · 16:20',
    updatedBy: 'Bảo trì Điện lạnh Phú Thịnh',
    createdAt: '10/05/2023',
    createdBy: 'Lê Hoàng Phúc (Manager)',
    image: 'https://lh3.googleusercontent.com/aida-public/AB6AXuD73B-7sxokHUMITEtlnX8FwmkJtLh2N1vcnsTu-0sfBGL0IrWR8bAcpf4Ilh3962zGfSB-QA9_JtZwrT2lqQ2ncTY-XP9qbs72otJJjqWBXdkgvejOo860klwujXoHiVkIsiKsVjGv1XlhFGJO85fIR8VW_9PanEbZIijmIBl0TO_r3lUulDF0nYMaVuNUopltkKE9BToovm_vBYM-ta5EgKG_3pvjMs9JEZY6cud15wbVWse-r6PB8Q',
    bookValue: '38,200,000 đ',
    warranty: 'Hết hạn bảo hành'
  },
  {
    code: 'TS-104-RF-01',
    name: 'Electrolux 45 Lít',
    category: 'Tủ lạnh mini',
    categoryCode: 'fridge',
    categoryFullName: 'Tủ lạnh mini quầy bar Electrolux 45L',
    model: 'EUM0500SB',
    purpose: 'Dùng cho khách',
    purposeCode: 'guest',
    locationType: 'room',
    location: 'Phòng 104',
    locationSub: 'Standard Đơn · Tầng 1',
    status: 'Good',
    statusLabel: 'Tốt (Good)',
    lastUpdate: '20/10/2024 · 14:10',
    updatedBy: 'Võ Minh Quân (Buồng phòng)',
    createdAt: '18/02/2024',
    createdBy: 'Lê Hoàng Phúc (Manager)',
    image: 'https://lh3.googleusercontent.com/aida-public/AB6AXuD73B-7sxokHUMITEtlnX8FwmkJtLh2N1vcnsTu-0sfBGL0IrWR8bAcpf4Ilh3962zGfSB-QA9_JtZwrT2lqQ2ncTY-XP9qbs72otJJjqWBXdkgvejOo860klwujXoHiVkIsiKsVjGv1XlhFGJO85fIR8VW_9PanEbZIijmIBl0TO_r3lUulDF0nYMaVuNUopltkKE9BToovm_vBYM-ta5EgKG_3pvjMs9JEZY6cud15wbVWse-r6PB8Q',
    bookValue: '3,200,000 đ',
    warranty: 'Đến 02/2026'
  },
  {
    code: 'TS-502-SF-01',
    name: 'Yale Electronic YSEB/200',
    category: 'Két sắt điện tử',
    categoryCode: 'safe',
    categoryFullName: 'Két sắt mini điện tử an toàn khách sạn Yale',
    model: 'YSEB/200/EB1',
    purpose: 'Dùng cho khách',
    purposeCode: 'guest',
    locationType: 'room',
    location: 'Phòng 502',
    locationSub: 'Executive Suite · Tầng 5',
    status: 'Good',
    statusLabel: 'Tốt (Good)',
    lastUpdate: '19/10/2024 · 10:00',
    updatedBy: 'Lê Hoàng Phúc (Quản lý)',
    createdAt: '01/04/2023',
    createdBy: 'Lê Hoàng Phúc (Manager)',
    image: 'https://lh3.googleusercontent.com/aida-public/AB6AXuD73B-7sxokHUMITEtlnX8FwmkJtLh2N1vcnsTu-0sfBGL0IrWR8bAcpf4Ilh3962zGfSB-QA9_JtZwrT2lqQ2ncTY-XP9qbs72otJJjqWBXdkgvejOo860klwujXoHiVkIsiKsVjGv1XlhFGJO85fIR8VW_9PanEbZIijmIBl0TO_r3lUulDF0nYMaVuNUopltkKE9BToovm_vBYM-ta5EgKG_3pvjMs9JEZY6cud15wbVWse-r6PB8Q',
    bookValue: '4,500,000 đ',
    warranty: 'Đến 04/2025'
  },
  {
    code: 'TS-GYM-AC-02',
    name: 'LG Standing Inverter 3.0HP',
    category: 'Điều hòa cây',
    categoryCode: 'ac',
    categoryFullName: 'Điều hòa tủ đứng LG Inverter 3.0HP',
    model: 'APNQ30GR5A4',
    purpose: 'Duy trì cơ sở',
    purposeCode: 'facility',
    locationType: 'area',
    location: 'Phòng Gym & Spa',
    locationSub: 'Khu tiện ích · Tầng M',
    status: 'Good',
    statusLabel: 'Tốt (Good)',
    lastUpdate: '18/10/2024 · 08:30',
    updatedBy: 'Lê Văn Hùng (Kỹ thuật)',
    createdAt: '22/06/2023',
    createdBy: 'Lê Hoàng Phúc (Manager)',
    image: 'https://lh3.googleusercontent.com/aida-public/AB6AXuD73B-7sxokHUMITEtlnX8FwmkJtLh2N1vcnsTu-0sfBGL0IrWR8bAcpf4Ilh3962zGfSB-QA9_JtZwrT2lqQ2ncTY-XP9qbs72otJJjqWBXdkgvejOo860klwujXoHiVkIsiKsVjGv1XlhFGJO85fIR8VW_9PanEbZIijmIBl0TO_r3lUulDF0nYMaVuNUopltkKE9BToovm_vBYM-ta5EgKG_3pvjMs9JEZY6cud15wbVWse-r6PB8Q',
    bookValue: '28,900,000 đ',
    warranty: 'Đến 06/2025'
  },
  {
    code: 'TS-312-WH-01',
    name: 'Ariston Andris2 RS 30L',
    category: 'Máy nước nóng',
    categoryCode: 'heater',
    categoryFullName: 'Máy nước nóng gián tiếp Ariston 30L',
    model: 'ANDRIS2 30RS',
    purpose: 'Dùng cho khách',
    purposeCode: 'guest',
    locationType: 'room',
    location: 'Phòng 312',
    locationSub: 'Deluxe Twin · Tầng 3',
    status: 'Good',
    statusLabel: 'Tốt (Good)',
    lastUpdate: '15/10/2024 · 11:15',
    updatedBy: 'Lê Văn Hùng (Kỹ thuật)',
    createdAt: '11/02/2023',
    createdBy: 'Lê Hoàng Phúc (Manager)',
    image: 'https://lh3.googleusercontent.com/aida-public/AB6AXuD73B-7sxokHUMITEtlnX8FwmkJtLh2N1vcnsTu-0sfBGL0IrWR8bAcpf4Ilh3962zGfSB-QA9_JtZwrT2lqQ2ncTY-XP9qbs72otJJjqWBXdkgvejOo860klwujXoHiVkIsiKsVjGv1XlhFGJO85fIR8VW_9PanEbZIijmIBl0TO_r3lUulDF0nYMaVuNUopltkKE9BToovm_vBYM-ta5EgKG_3pvjMs9JEZY6cud15wbVWse-r6PB8Q',
    bookValue: '3,850,000 đ',
    warranty: 'Đến 02/2025'
  },
  {
    code: 'TS-405-HD-01',
    name: 'Philips BHD004/00',
    category: 'Máy sấy tóc',
    categoryCode: 'dryer',
    categoryFullName: 'Máy sấy tóc ion Philips Essential Care',
    model: 'BHD004',
    purpose: 'Dùng cho khách',
    purposeCode: 'guest',
    locationType: 'room',
    location: 'Phòng 405',
    locationSub: 'Superior Đơn · Tầng 4',
    status: 'Good',
    statusLabel: 'Tốt (Good)',
    lastUpdate: '12/10/2024 · 15:45',
    updatedBy: 'Trần Mai Anh (Buồng phòng)',
    createdAt: '10/01/2024',
    createdBy: 'Lê Hoàng Phúc (Manager)',
    image: 'https://lh3.googleusercontent.com/aida-public/AB6AXuD73B-7sxokHUMITEtlnX8FwmkJtLh2N1vcnsTu-0sfBGL0IrWR8bAcpf4Ilh3962zGfSB-QA9_JtZwrT2lqQ2ncTY-XP9qbs72otJJjqWBXdkgvejOo860klwujXoHiVkIsiKsVjGv1XlhFGJO85fIR8VW_9PanEbZIijmIBl0TO_r3lUulDF0nYMaVuNUopltkKE9BToovm_vBYM-ta5EgKG_3pvjMs9JEZY6cud15wbVWse-r6PB8Q',
    bookValue: '850,000 đ',
    warranty: 'Đến 01/2026'
  },
  {
    code: 'TS-RST-TV-01',
    name: 'LG Commercial 65 Inch',
    category: 'Smart TV',
    categoryCode: 'tv',
    categoryFullName: 'Smart TV LG Commercial 65 Inch 4K',
    model: '65UT640S',
    purpose: 'Duy trì cơ sở',
    purposeCode: 'facility',
    locationType: 'area',
    location: 'Nhà hàng Sao Mai',
    locationSub: 'Khu Buffet Sáng · Tầng 2',
    status: 'Good',
    statusLabel: 'Tốt (Good)',
    lastUpdate: '10/10/2024 · 09:30',
    updatedBy: 'Lê Hoàng Phúc (Quản lý)',
    createdAt: '15/04/2023',
    createdBy: 'Lê Hoàng Phúc (Manager)',
    image: 'https://lh3.googleusercontent.com/aida-public/AB6AXuD73B-7sxokHUMITEtlnX8FwmkJtLh2N1vcnsTu-0sfBGL0IrWR8bAcpf4Ilh3962zGfSB-QA9_JtZwrT2lqQ2ncTY-XP9qbs72otJJjqWBXdkgvejOo860klwujXoHiVkIsiKsVjGv1XlhFGJO85fIR8VW_9PanEbZIijmIBl0TO_r3lUulDF0nYMaVuNUopltkKE9BToovm_vBYM-ta5EgKG_3pvjMs9JEZY6cud15wbVWse-r6PB8Q',
    bookValue: '21,500,000 đ',
    warranty: 'Đến 04/2025'
  },
  {
    code: 'TS-OLD-TV-99',
    name: 'Sony Bravia 43 Inch (Hỏng main)',
    category: 'Smart TV',
    categoryCode: 'tv',
    categoryFullName: 'Smart TV Sony Bravia 43W660F',
    model: 'KDL-43W660F',
    purpose: 'Dùng cho khách',
    purposeCode: 'guest',
    locationType: 'area',
    location: 'Kho tổng tầng hầm',
    locationSub: 'Chờ thanh lý phế liệu',
    status: 'Disposed',
    statusLabel: 'Đã thanh lý (Disposed)',
    lastUpdate: '05/10/2024 · 16:00',
    updatedBy: 'Lê Hoàng Phúc (Quản lý)',
    createdAt: '10/08/2021',
    createdBy: 'Lê Hoàng Phúc (Manager)',
    image: 'https://lh3.googleusercontent.com/aida-public/AB6AXuD73B-7sxokHUMITEtlnX8FwmkJtLh2N1vcnsTu-0sfBGL0IrWR8bAcpf4Ilh3962zGfSB-QA9_JtZwrT2lqQ2ncTY-XP9qbs72otJJjqWBXdkgvejOo860klwujXoHiVkIsiKsVjGv1XlhFGJO85fIR8VW_9PanEbZIijmIBl0TO_r3lUulDF0nYMaVuNUopltkKE9BToovm_vBYM-ta5EgKG_3pvjMs9JEZY6cud15wbVWse-r6PB8Q',
    bookValue: '0 đ',
    warranty: 'Đã thanh lý'
  }
];

const INITIAL_CONSUMABLES = [
  {
    id: 'c1',
    name: 'Nước suối Sao Mai 350ml',
    description: 'Đồ uống phòng Deluxe & Standard',
    purpose: 'guest',
    purposeLabel: 'Guest Use',
    unit: 'Chai',
    quantity: 1250,
    lastAuditDate: '24/10/2024 10:15',
    lastAuditUser: 'Lê Hoàng Phúc',
    icon: 'local_drink'
  },
  {
    id: 'c2',
    name: 'Khăn tắm cotton 70x140cm',
    description: 'Đồ vải thay buồng phòng',
    purpose: 'guest',
    purposeLabel: 'Guest Use',
    unit: 'Chiếc',
    quantity: 480,
    lastAuditDate: '23/10/2024 16:40',
    lastAuditUser: 'Trần Thị Mai Lan',
    icon: 'dry_cleaning'
  },
  {
    id: 'c3',
    name: 'Dầu gội & Sữa tắm mini 45ml',
    description: 'Amenities phòng tắm cao cấp',
    purpose: 'guest',
    purposeLabel: 'Guest Use',
    unit: 'Bộ',
    quantity: 860,
    lastAuditDate: '24/10/2024 09:00',
    lastAuditUser: 'Lê Hoàng Phúc',
    icon: 'soap'
  },
  {
    id: 'c4',
    name: 'Giấy vệ sinh cuộn lớn',
    description: 'Dùng cho phòng khách & WC sảnh',
    purpose: 'guest',
    purposeLabel: 'Guest Use',
    unit: 'Cuộn',
    quantity: 315,
    lastAuditDate: '21/10/2024 14:10',
    lastAuditUser: 'Nguyễn Văn Tuấn',
    icon: 'receipt_long'
  },
  {
    id: 'c5',
    name: 'Nước lau sàn hương sả chanh (Can 5L)',
    description: 'Hóa chất làm sạch buồng phòng & hành lang',
    purpose: 'facility',
    purposeLabel: 'Facility Maintenance',
    unit: 'Can',
    quantity: 42,
    lastAuditDate: '22/10/2024 15:30',
    lastAuditUser: 'Lê Hoàng Phúc',
    icon: 'sanitizer'
  },
  {
    id: 'c6',
    name: 'Túi rác sinh học đen (Gói 1kg)',
    description: 'Dùng lót thùng rác phòng và khu vực chung',
    purpose: 'facility',
    purposeLabel: 'Facility Maintenance',
    unit: 'Gói',
    quantity: 95,
    lastAuditDate: '20/10/2024 11:20',
    lastAuditUser: 'Trần Thị Mai Lan',
    icon: 'delete_sweep'
  }
];

const INITIAL_INCIDENTS = [
  {
    id: 'RP-2024-089',
    ticketStatus: 'New',
    ticketStatusLabel: 'Mới tiếp nhận (New)',
    type: 'Damage',
    typeLabel: 'Hỏng block lạnh',
    assetCode: 'TS-205-AC-01',
    assetName: 'Điều hòa Daikin 1.5HP',
    categoryName: 'Điều hòa nhiệt độ Daikin Inverter 2.0HP (FTKF50XVMV)',
    room: 'Phòng 205',
    roomSub: 'Đôi Standard · Tầng 2',
    reportedBy: 'Lê Thị Hoa',
    reportedRole: 'Buồng phòng · Ca sáng',
    reportedTime: '14:10 · Hôm nay',
    description: 'Điều hòa chảy nước ở máng xả lạnh xuống tường thảm phòng khách, quạt dàn lạnh phát ra tiếng kêu rít to bất thường và phả gió không mát. Đã tắt CB điện tạm thời để tránh chập cháy chập mạch điện khu vực phòng 205.',
    photos: [
      {
        url: 'https://lh3.googleusercontent.com/aida-public/AB6AXuDVXDVrwDIklZ91EMotOJm6Tt1N1T95YLwQxYN2itXrtHaxjmG1U62jSQHV8Trpqc1F54mvR39QgwloOG_7ijMgDQnAnDK9I6_-JsL4JIwgchybfxnHy9cPu7PvIXdjKj7xaUZ2H1HoWU5jgopJ2FGBnXeHyZYRED2CLH8E42rnfbQ9x9gkZ5VuagN2YQTHguqURpYNQNYuMN0SVsuxdiN3V77v1Notod-AmmR1MAKkW_S0oIdsaxZSZA',
        title: 'Rò rỉ máng thoát nước phòng 205'
      },
      {
        url: 'https://lh3.googleusercontent.com/aida-public/AB6AXuCFSzwBHJYE3GQZnK12Ifih0p4oDRawqTRcmdC7ljlL__B0CEQhkbZ3qWtIN7zIR99mpMV-B-3MCDsfXotXQKSk5HXliwTO8bBkA2zQbN5kzr4d2_pDzaZua4k1a8ofQT1wEFjmHSLjbkuWYOsatAmRKfQ3KdHffOADKuCGw_pGDdBt4TnmXyzUbaVCkpffBPSA76yEUYO-QYxUyGchYLx-qYBjO8LsufFvsNpn57A_IfOzUm0l8Zn8Ug',
        title: 'CB điện buồng máy đã tắt an toàn'
      }
    ],
    managerNote: '',
    resolvedAt: null,
    resolvedBy: null
  },
  {
    id: 'RP-2024-088',
    ticketStatus: 'New',
    ticketStatusLabel: 'Mới tiếp nhận (New)',
    type: 'Lost',
    typeLabel: 'Báo mất đồ',
    assetCode: 'AST-HD-312',
    assetName: 'Máy sấy tóc Panasonic',
    categoryName: 'Máy sấy tóc ion cao cấp 1800W',
    room: 'Phòng 312',
    roomSub: 'Deluxe Twin · Tầng 3',
    reportedBy: 'Trần Mai Anh',
    reportedRole: 'Buồng phòng',
    reportedTime: '13:45 · Hôm nay',
    description: 'Khách trả phòng kiểm tra không thấy máy sấy tóc trong tủ quần áo hay kệ phòng tắm.',
    photos: []
  },
  {
    id: 'RP-2024-087',
    ticketStatus: 'New',
    ticketStatusLabel: 'Mới tiếp nhận (New)',
    type: 'Damage',
    typeLabel: 'Màn hình sọc',
    assetCode: 'AST-TV-401',
    assetName: 'Smart TV Samsung 43"',
    categoryName: 'Smart TV Samsung 43 Inch',
    room: 'Phòng 401',
    roomSub: 'Suite Hướng Biển · Tầng 4',
    reportedBy: 'Võ Minh Quân',
    reportedRole: 'Buồng phòng',
    reportedTime: '11:20 · Hôm nay',
    description: 'Bật tivi màn hình xuất hiện các sọc kẻ dọc nhiều màu, mất hình một nửa bên phải.',
    photos: []
  },
  {
    id: 'RP-2024-086',
    ticketStatus: 'New',
    ticketStatusLabel: 'Mới tiếp nhận (New)',
    type: 'Damage',
    typeLabel: 'Đứt dây công tắc',
    assetCode: 'AST-LP-108',
    assetName: 'Đèn đọc sách đầu giường',
    categoryName: 'Đèn ngủ đầu giường gắn tường',
    room: 'Phòng 108',
    roomSub: 'Standard King · Tầng 1',
    reportedBy: 'Lê Thị Hoa',
    reportedRole: 'Buồng phòng',
    reportedTime: '09:30 · Hôm nay',
    description: 'Dây cấp nguồn công tắc đèn đọc sách bị đứt vỏ bọc, hở lõi đồng nguy hiểm.',
    photos: []
  },
  {
    id: 'RP-2024-085',
    ticketStatus: 'New',
    ticketStatusLabel: 'Mới tiếp nhận (New)',
    type: 'Damage',
    typeLabel: 'Liệt bàn phím mã',
    assetCode: 'AST-SF-502',
    assetName: 'Két sắt mini điện tử',
    categoryName: 'Két sắt an toàn mini',
    room: 'Phòng 502',
    roomSub: 'Executive Suite · Tầng 5',
    reportedBy: 'Lê Hoàng Phúc',
    reportedRole: 'Quản lý',
    reportedTime: '08:15 · Hôm nay',
    description: 'Bàn phím bấm số 3 và 7 không nhận tín hiệu tiếng bíp, không thể đổi mật khẩu cho khách mới.',
    photos: []
  }
];

function getStored(key, fallback) {
  try {
    const data = localStorage.getItem(key);
    return data ? JSON.parse(data) : fallback;
  } catch (e) {
    return fallback;
  }
}

function saveStored(key, val) {
  try {
    localStorage.setItem(key, JSON.stringify(val));
  } catch (e) {
    console.error('Storage error', e);
  }
}

// Initial hydration
if (!localStorage.getItem(STORAGE_KEYS.ASSETS)) {
  saveStored(STORAGE_KEYS.ASSETS, INITIAL_FIXED_ASSETS);
}
if (!localStorage.getItem(STORAGE_KEYS.CONSUMABLES)) {
  saveStored(STORAGE_KEYS.CONSUMABLES, INITIAL_CONSUMABLES);
}
if (!localStorage.getItem(STORAGE_KEYS.INCIDENTS)) {
  saveStored(STORAGE_KEYS.INCIDENTS, INITIAL_INCIDENTS);
}

// API Service Layer (using Axios conventions)
export const assetService = {
  // Session & Auth
  async getCurrentUser() {
    const raw = getCookie('saomai_session');
    if (!raw) return null;
    try {
      return JSON.parse(raw);
    } catch {
      return null;
    }
  },

  async login(username, password) {
    // Session Cookie Authentication login
    await new Promise((r) => setTimeout(r, 400));
    const user = {
      ...DEFAULT_SESSION_USER,
      name: username === 'admin' ? 'Nguyễn Tổng Giám Đốc' : 'Lê Hoàng Phúc',
      role: username === 'admin' ? 'Giám đốc Vận hành' : 'Quản lý khách sạn',
      sessionToken: 'sess_' + Math.random().toString(36).substring(2),
      lastLogin: new Date().toLocaleDateString('vi-VN') + ' · ' + new Date().toLocaleTimeString('vi-VN', { hour: '2-digit', minute: '2-digit' })
    };
    setCookie('saomai_session', JSON.stringify(user));
    return user;
  },

  async logout() {
    await new Promise((r) => setTimeout(r, 200));
    eraseCookie('saomai_session');
    return true;
  },

  // Overview Stats
  async getOverviewStats() {
    await new Promise((r) => setTimeout(r, 250));
    const assets = getStored(STORAGE_KEYS.ASSETS, INITIAL_FIXED_ASSETS);
    const consumables = getStored(STORAGE_KEYS.CONSUMABLES, INITIAL_CONSUMABLES);
    const incidents = getStored(STORAGE_KEYS.INCIDENTS, INITIAL_INCIDENTS);

    const goodCount = assets.filter((a) => a.status === 'Good').length;
    const damagedCount = assets.filter((a) => a.status === 'Damaged').length;
    const repairingCount = assets.filter((a) => a.status === 'Repairing').length;
    const disposedCount = assets.filter((a) => a.status === 'Disposed').length;

    const totalStock = consumables.reduce((sum, item) => sum + item.quantity, 0);
    const pendingIncidents = incidents.filter((i) => i.ticketStatus === 'New').length;

    return {
      fixedAssets: {
        total: 450 + (assets.length - INITIAL_FIXED_ASSETS.length),
        good: 395 + (goodCount - 7),
        damaged: 18 + (damagedCount - 1),
        repairing: 25 + (repairingCount - 1),
        disposed: 12 + (disposedCount - 1)
      },
      consumables: {
        categoriesCount: consumables.length + 10,
        totalStock: 3420 + (totalStock - 3042),
        lastAudit: '23/10/2024 · 18:00',
        complianceRate: '94.8%'
      },
      incidents: {
        pending: pendingIncidents,
        resolved: 42,
        damageTotal: 31,
        lostTotal: 18,
        top5: incidents.slice(0, 5)
      }
    };
  },

  // Fixed Assets Query
  async getFixedAssets({ search = '', category = '', purpose = '', status = '', location = '', hideDisposed = true, page = 1, limit = 10 } = {}) {
    await new Promise((r) => setTimeout(r, 200));
    let list = getStored(STORAGE_KEYS.ASSETS, INITIAL_FIXED_ASSETS);

    if (hideDisposed) {
      list = list.filter((a) => a.status !== 'Disposed');
    }

    if (search.trim()) {
      const q = search.toLowerCase();
      list = list.filter((a) => a.code.toLowerCase().includes(q) || a.name.toLowerCase().includes(q) || a.location.toLowerCase().includes(q));
    }

    if (category) {
      list = list.filter((a) => a.categoryCode === category || a.category.toLowerCase().includes(category.toLowerCase()));
    }

    if (purpose) {
      list = list.filter((a) => a.purposeCode === purpose || (purpose === 'guest' && a.purpose.includes('khách')) || (purpose === 'facility' && a.purpose.includes('cơ sở')));
    }

    if (status) {
      list = list.filter((a) => a.status.toLowerCase() === status.toLowerCase());
    }

    if (location) {
      list = list.filter((a) => a.locationType === location);
    }

    const total = list.length;
    const startIndex = (page - 1) * limit;
    const items = list.slice(startIndex, startIndex + limit);

    return {
      items,
      total,
      page,
      limit,
      totalPages: Math.ceil(total / limit) || 1
    };
  },

  // Single Asset Detail
  async getAssetDetail(code) {
    await new Promise((r) => setTimeout(r, 150));
    const list = getStored(STORAGE_KEYS.ASSETS, INITIAL_FIXED_ASSETS);
    const found = list.find((a) => a.code === code) || list[0];
    return found;
  },

  // Update Asset Location
  async updateAssetLocation(code, newLocation, note = '') {
    await new Promise((r) => setTimeout(r, 300));
    const list = getStored(STORAGE_KEYS.ASSETS, INITIAL_FIXED_ASSETS);
    const updated = list.map((a) => {
      if (a.code === code) {
        const parts = newLocation.split(' (');
        return {
          ...a,
          location: parts[0],
          locationSub: parts[1] ? parts[1].replace(')', '') : 'Nội bộ khách sạn Sao Mai Nha Trang',
          lastUpdate: `${new Date().toLocaleTimeString('vi-VN', { hour: '2-digit', minute: '2-digit' })} · Hôm nay`,
          updatedBy: 'Lê Hoàng Phúc (Manager)'
        };
      }
      return a;
    });
    saveStored(STORAGE_KEYS.ASSETS, updated);
    return updated.find((a) => a.code === code);
  },

  // Update Asset Status
  async updateAssetStatus(code, newStatus, reason = '') {
    await new Promise((r) => setTimeout(r, 300));
    const list = getStored(STORAGE_KEYS.ASSETS, INITIAL_FIXED_ASSETS);
    const statusLabels = {
      Good: 'Tốt (Good)',
      Damaged: 'Bị hỏng (Damaged)',
      Repairing: 'Đang sửa (Repairing)',
      Disposed: 'Đã thanh lý (Disposed)'
    };
    const updated = list.map((a) => {
      if (a.code === code) {
        return {
          ...a,
          status: newStatus,
          statusLabel: statusLabels[newStatus] || newStatus,
          lastUpdate: `${new Date().toLocaleTimeString('vi-VN', { hour: '2-digit', minute: '2-digit' })} · Hôm nay`,
          updatedBy: 'Lê Hoàng Phúc (Manager)'
        };
      }
      return a;
    });
    saveStored(STORAGE_KEYS.ASSETS, updated);
    return updated.find((a) => a.code === code);
  },

  // Batch Create Fixed Assets
  async createBatchAssets({ category, positionType, position, quantity, status = 'Good' }) {
    await new Promise((r) => setTimeout(r, 600));
    const list = getStored(STORAGE_KEYS.ASSETS, INITIAL_FIXED_ASSETS);
    const newItems = [];
    const now = new Date();
    const dateStr = `${now.getDate().toString().padStart(2, '0')}/${(now.getMonth() + 1).toString().padStart(2, '0')}/${now.getFullYear()}`;

    for (let i = 1; i <= quantity; i++) {
      const seq = String(i).padStart(3, '0');
      const fullCode = `${category.prefix}-${position.code}-${seq}`;
      const item = {
        code: fullCode,
        name: `${category.name} #${i}`,
        category: category.name,
        categoryCode: category.value.toLowerCase(),
        categoryFullName: category.name,
        model: category.name,
        purpose: category.purpose === 'Guest Use' ? 'Dùng cho khách' : 'Duy trì cơ sở',
        purposeCode: category.purpose === 'Guest Use' ? 'guest' : 'facility',
        locationType: positionType === 'ROOM' ? 'room' : 'area',
        location: position.label.split(' (')[0],
        locationSub: position.label.includes('(') ? position.label.split('(')[1].replace(')', '') : 'Khu vực quản lý',
        status: status,
        statusLabel: 'Tốt (Good)',
        lastUpdate: `${dateStr} · Vừa tạo`,
        updatedBy: 'Lê Hoàng Phúc (Quản lý)',
        createdAt: dateStr,
        createdBy: 'Lê Hoàng Phúc (Manager)',
        image: 'https://lh3.googleusercontent.com/aida-public/AB6AXuD73B-7sxokHUMITEtlnX8FwmkJtLh2N1vcnsTu-0sfBGL0IrWR8bAcpf4Ilh3962zGfSB-QA9_JtZwrT2lqQ2ncTY-XP9qbs72otJJjqWBXdkgvejOo860klwujXoHiVkIsiKsVjGv1XlhFGJO85fIR8VW_9PanEbZIijmIBl0TO_r3lUulDF0nYMaVuNUopltkKE9BToovm_vBYM-ta5EgKG_3pvjMs9JEZY6cud15wbVWse-r6PB8Q',
        bookValue: 'Theo danh mục cấp',
        warranty: '24 tháng'
      };
      newItems.push(item);
    }

    const updated = [...newItems, ...list];
    saveStored(STORAGE_KEYS.ASSETS, updated);
    return { count: newItems.length, items: newItems };
  },

  // Consumables Query
  async getConsumables({ search = '', purpose = 'all' } = {}) {
    await new Promise((r) => setTimeout(r, 200));
    let list = getStored(STORAGE_KEYS.CONSUMABLES, INITIAL_CONSUMABLES);
    if (purpose && purpose !== 'all') {
      list = list.filter((c) => c.purpose === purpose);
    }
    if (search.trim()) {
      const q = search.toLowerCase();
      list = list.filter((c) => c.name.toLowerCase().includes(q) || c.description.toLowerCase().includes(q));
    }
    return list;
  },

  // Update Consumable Inventory Count
  async updateConsumableInventory(name, actualQty, checker = 'Lê Hoàng Phúc') {
    await new Promise((r) => setTimeout(r, 300));
    const list = getStored(STORAGE_KEYS.CONSUMABLES, INITIAL_CONSUMABLES);
    const now = new Date();
    const dateStr = `${now.getDate().toString().padStart(2, '0')}/${(now.getMonth() + 1).toString().padStart(2, '0')}/${now.getFullYear()} ${now.getHours().toString().padStart(2, '0')}:${now.getMinutes().toString().padStart(2, '0')}`;

    const updated = list.map((c) => {
      if (c.name === name) {
        return {
          ...c,
          quantity: parseInt(actualQty, 10),
          lastAuditDate: dateStr,
          lastAuditUser: checker
        };
      }
      return c;
    });

    saveStored(STORAGE_KEYS.CONSUMABLES, updated);
    return updated.find((c) => c.name === name);
  },

  // Get Incident Report
  async getIncident(id) {
    await new Promise((r) => setTimeout(r, 150));
    const list = getStored(STORAGE_KEYS.INCIDENTS, INITIAL_INCIDENTS);
    return list.find((i) => i.id === id) || list[0];
  },

  // Resolve Incident Report & Mandatory Asset Status Update
  async resolveIncident(id, { newAssetStatus, processingNote }) {
    await new Promise((r) => setTimeout(r, 400));
    const incidents = getStored(STORAGE_KEYS.INCIDENTS, INITIAL_INCIDENTS);
    let targetTicket = null;

    const updatedIncidents = incidents.map((i) => {
      if (i.id === id) {
        targetTicket = {
          ...i,
          ticketStatus: 'Processed',
          ticketStatusLabel: 'Đã xử lý (Processed)',
          managerNote: processingNote,
          resolvedAt: `${new Date().toLocaleDateString('vi-VN')} · ${new Date().toLocaleTimeString('vi-VN', { hour: '2-digit', minute: '2-digit' })}`,
          resolvedBy: 'Lê Hoàng Phúc (Quản lý)'
        };
        return targetTicket;
      }
      return i;
    });

    saveStored(STORAGE_KEYS.INCIDENTS, updatedIncidents);

    // Also update corresponding Fixed Asset status in the database!
    if (targetTicket && targetTicket.assetCode) {
      await this.updateAssetStatus(targetTicket.assetCode, newAssetStatus, processingNote);
    }

    return targetTicket;
  }
};
