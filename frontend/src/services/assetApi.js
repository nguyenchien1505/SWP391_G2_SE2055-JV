import { apiClient } from './apiClient';

let localMockAssets = [];

/**
 * Dạng UUID chuẩn 8-4-4-4-12. Mọi chỗ nhận diện "id thật của backend" phải dùng CHUNG
 * hằng số này: viết lại regex ở từng hàm đã từng làm rơi mất nhóm thứ tư, khiến id thật
 * bị coi là id giả nên tài sản mới chỉ nằm trong mock trong RAM, không xuống DB.
 */
const UUID_PATTERN = /^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$/;
const isUUID = (id) => UUID_PATTERN.test(String(id ?? ''));


// --- CACHE FOR LOOKUPS ---
let categoriesCache = null;
let fixedAssetsCache = null;

const MOCK_CATEGORIES = [
  { id: 'AC', name: 'Điều hòa nhiệt độ (AC)', prefix: 'TS-AC', purpose: 'GUEST_USE', assetKind: 'FIXED' },
  { id: 'TV', name: 'Smart TV màn hình phẳng (TV)', prefix: 'TS-TV', purpose: 'GUEST_USE', assetKind: 'FIXED' },
  { id: 'RF', name: 'Tủ lạnh mini quầy bar (RF)', prefix: 'TS-RF', purpose: 'GUEST_USE', assetKind: 'FIXED' },
  { id: 'SF', name: 'Két sắt mini điện tử (SF)', prefix: 'TS-SF', purpose: 'GUEST_USE', assetKind: 'FIXED' },
  { id: 'WH', name: 'Máy nước nóng gián tiếp (WH)', prefix: 'TS-WH', purpose: 'GUEST_USE', assetKind: 'FIXED' },
  { id: 'HD', name: 'Máy sấy tóc ion cao cấp (HD)', prefix: 'TS-HD', purpose: 'GUEST_USE', assetKind: 'FIXED' },
  { id: 'CS', name: 'Điều hòa âm trần Cassette', prefix: 'TS-CS', purpose: 'FACILITY_MAINTENANCE', assetKind: 'FIXED' },
  { id: 'ST', name: 'Điều hòa tủ đứng công suất lớn', prefix: 'TS-ST', purpose: 'FACILITY_MAINTENANCE', assetKind: 'FIXED' },
  { id: 'PC', name: 'Máy tính để bàn lễ tân', prefix: 'TS-PC', purpose: 'INTERNAL_OPS', assetKind: 'FIXED' }
];

const getCategories = async () => {
  if (!categoriesCache) {
    try {
      const res = await apiClient.get('/organization/asset-categories?size=1000');
      categoriesCache = res.content || [];
    } catch(e) { categoriesCache = []; }
    
    // Inject mock categories to ensure dropdowns have options
    const existingIds = new Set(categoriesCache.map(c => c.id));
    for (const mc of MOCK_CATEGORIES) {
      if (!existingIds.has(mc.id)) {
        categoriesCache.push(mc);
      }
    }
  }
  return categoriesCache;
};

const resolveCategory = async (categoryId) => {
  const cats = await getCategories();
  const cat = cats.find(c => c.id === categoryId);
  return cat || { name: 'Unknown', purpose: 'Unknown' };
};

const getFixedAssetsMap = async () => {
  if (!fixedAssetsCache) {
    try {
      const res = await apiClient.get('/assets/fixed-assets?size=1000&includeDisposed=true');
      fixedAssetsCache = res.content || [];
    } catch(e) { fixedAssetsCache = []; }
  }
  return [...localMockAssets, ...fixedAssetsCache];
}

const resolveFixedAsset = async (assetId) => {
  const assets = await getFixedAssetsMap();
  const asset = assets.find(a => a.id === assetId);
  return asset || null;
}

const mapAssetStatus = (status) => {
  switch (status) {
    case 'GOOD': case 'Good': return 'Good';
    case 'BROKEN': case 'Damaged': return 'Damaged';
    case 'UNDER_REPAIR': case 'Repairing': return 'Repairing';
    case 'DISPOSED': case 'Disposed': return 'Disposed';
    default: return 'Good';
  }
};

const mapFixedAsset = async (backendAsset) => {
  const cat = await resolveCategory(backendAsset.categoryId);
  return {
    id: backendAsset.id,
    code: backendAsset.assetCode,
    name: backendAsset.name,
    categoryId: backendAsset.categoryId,
    category: cat.name,
    categoryFullName: cat.name,
    purpose: cat.purpose,
    locationType: backendAsset.roomId ? 'room' : 'area',
    location: backendAsset.roomName || backendAsset.areaName || backendAsset.roomId || backendAsset.areaId || 'Chưa xếp vị trí',
    status: mapAssetStatus(backendAsset.status),
    note: backendAsset.note
  };
};

const mapConsumable = async (backendConsumable) => {
  const cat = await resolveCategory(backendConsumable.categoryId);
  return {
    id: backendConsumable.id,
    categoryId: backendConsumable.categoryId,
    categoryCode: cat.name, 
    name: cat.name,
    description: cat.name, // Fallback since backend category doesn't have description
    icon: 'inventory_2',
    unit: cat.unit || 'Lít',
    quantity: backendConsumable.quantity || 0,
    lastAuditDate: backendConsumable.lastCountedAt ? new Date(backendConsumable.lastCountedAt).toLocaleString('vi-VN') : 'Chưa kiểm kê',
    lastAuditUser: backendConsumable.lastCountedByEmail || backendConsumable.lastCountedBy || 'N/A',
    status: (backendConsumable.quantity > 0) ? 'In Stock' : 'Out of Stock',
    statusLabel: (backendConsumable.quantity > 0) ? 'Còn hàng' : 'Hết hàng',
    purpose: cat.purpose === 'GUEST_USE' ? 'guest' : (cat.purpose === 'FACILITY_OPS' ? 'facility' : 'internal'),
    purposeLabel: cat.purpose === 'GUEST_USE' ? 'Dùng cho khách' : (cat.purpose === 'FACILITY_OPS' ? 'Đồ dùng để duy trì cơ sở' : 'Nội bộ')
  };
};

export const assetService = {
  // ---------------------------------
  // FIXED ASSETS
  // ---------------------------------
  getFixedAssets: async (options = {}) => {
      try {
        const { includeDisposed = false, hideDisposed, page = 1, limit = 10, search, category, purpose, status, location } = options;
        const actualIncludeDisposed = hideDisposed !== undefined ? !hideDisposed : includeDisposed;
        
        const query = new URLSearchParams({
          includeDisposed: actualIncludeDisposed.toString(),
          page: (page - 1).toString(), 
          size: limit.toString(),
          sort: 'createdAt,desc'
        }).toString();
    
        const response = await apiClient.get(`/assets/fixed-assets?${query}`);
        let mappedItems = await Promise.all((response.content || []).map(mapFixedAsset));
  
        if (search) {
          const s = search.toLowerCase();
          mappedItems = mappedItems.filter(a => (a.code || '').toLowerCase().includes(s) || (a.name || '').toLowerCase().includes(s));
        }
        if (status) {
          mappedItems = mappedItems.filter(a => (a.status || '').toLowerCase() === status.toLowerCase());
        }

        // INTERCEPT MOCK
        let filteredMock = localMockAssets.filter(a => {
          if (!actualIncludeDisposed && a.status === 'DISPOSED') return false;
          if (search && !(a.assetCode || a.code || '').toLowerCase().includes(search.toLowerCase()) && 
!(a.name || '').toLowerCase().includes(search.toLowerCase())) return false;
          if (status && (a.status || '').toLowerCase() !== status.toLowerCase()) return false;
          return true;
        });
    
        return {
          items: [...filteredMock, ...mappedItems],
          total: (response.totalElements || 0) + filteredMock.length,
          totalPages: Math.max(response.totalPages || 1, Math.ceil(((response.totalElements || 0) + 
filteredMock.length) / limit))
        };
      } catch (err) {
        console.error("GET_FIXED_ASSETS_ERROR", err);
        throw err;
      }
    },

  getAssetDetail: async (id) => {
    return assetService.getFixedAssetById(id);
  },

  getFixedAssetById: async (id) => {
    const looksLikeBackendId = isUUID(id);
    let backendAsset;

    // Check local mocks first if not UUID
    if (!looksLikeBackendId) {
      const mockAsset = localMockAssets.find(a => a.code === id || a.assetCode === id);
      if (mockAsset) return mockAsset;
    }

    if (looksLikeBackendId) {
      backendAsset = await apiClient.get(`/assets/fixed-assets/${id}`);
    } else {
      const assets = await getFixedAssetsMap();
      backendAsset = assets.find(a => a.assetCode === id || a.code === id);
      if (!backendAsset) throw new Error("Asset not found by code: " + id);
    }
    
    // If it's a mock asset that leaked in here, return it directly
    if (backendAsset.id && backendAsset.id.toString().startsWith('mock-')) {
      return backendAsset;
    }
    
    return mapFixedAsset(backendAsset);
  },

  
  createBatchAssets: async (data) => {
    let count = 0;
    const catId = data.category.id || data.category.value;
    const posId = data.position?.id;
    const hasValidUUIDs = isUUID(catId) && (!posId || isUUID(posId));

    for (let i = 1; i <= data.quantity; i++) {
      const seq = String(i).padStart(3, '0');
      const fullCode = `${data.category.prefix || 'TS'}-${data.position.code}-${seq}`;
      
      if (!hasValidUUIDs) {
        // Fallback to mock
        localMockAssets.unshift({
           id: `mock-${Date.now()}-${i}`,
           assetCode: fullCode,
           code: fullCode,
           name: data.category.name,
           categoryId: catId,
           category: data.category.name,
           purpose: data.category.purpose || 'Internal',
           location: data.position.label || data.position.code,
           status: 'Good',
           createdAt: new Date().toISOString()
        });
        count++;
        continue;
      }

      const req = {
        categoryId: catId,
        name: data.category.name,
        note: "Thêm tự động hàng loạt",
        roomId: data.positionType === 'ROOM' ? posId : null,
        areaId: data.positionType === 'AREA' ? posId : null,
        assetCode: fullCode
      };
      try {
        await apiClient.post('/assets/fixed-assets', req);
        count++;
      } catch (e) {
        console.error('Failed to create asset in batch:', e);
        // If it fails on the first one, throw an error to the UI
        if (i === 1) throw new Error('Lỗi khi tạo tài sản: ' + (e.response?.data?.message || e.message));
      }
    }
    fixedAssetsCache = null;
    return { count };
  },
  createFixedAsset: async (data) => {
    const hasValidUUIDs = isUUID(data.categoryId) && (!data.locationId || isUUID(data.locationId));

    if (!hasValidUUIDs) {
        const mock = {
           id: `mock-${Date.now()}`,
           assetCode: `TS-${data.categoryId}-${Date.now().toString().slice(-4)}`,
           code: `TS-${data.categoryId}-${Date.now().toString().slice(-4)}`,
           name: data.name,
           categoryId: data.categoryId,
           category: 'Mock Category',
           purpose: 'Internal',
           location: data.locationId || 'Chưa rõ',
           status: 'Good',
           note: data.note,
           createdAt: new Date().toISOString()
        };
        localMockAssets.unshift(mock);
        return mock;
    }

    const req = {
      categoryId: data.categoryId,
      name: data.name,
      note: data.note,
      roomId: data.locationType === 'room' ? data.locationId : null,
      areaId: data.locationType === 'area' ? data.locationId : null,
    };
    const response = await apiClient.post('/assets/fixed-assets', req);
    fixedAssetsCache = null;
    return mapFixedAsset(response);
  },

  updateFixedAssetInfo: async (code, data) => {
    const assets = await getFixedAssetsMap();
    const asset = assets.find(a => a.assetCode === code || a.code === code);
    if (!asset) throw new Error("Not found");
    
    if (asset.id && asset.id.toString().startsWith('mock-')) {
       asset.name = data.name;
       asset.note = data.note;
       if (data.categoryId) {
         asset.categoryId = data.categoryId;
         const cats = await getCategories();
         const cat = cats.find(c => c.id === data.categoryId);
         if (cat) {
           asset.category = cat.name;
           asset.categoryFullName = cat.name;
           asset.purpose = cat.purpose;
         }
       }
       return asset;
    }

    const req = {
      categoryId: data.categoryId || asset.categoryId,
      name: data.name,
      note: data.note,
      roomId: asset.roomId || null,
      areaId: asset.areaId || null,
    };
    
    const updated = await apiClient.put(`/assets/fixed-assets/${asset.id}`, req);
    fixedAssetsCache = null;
    return mapFixedAsset(updated);
  },

  deleteFixedAsset: async (code) => {
    const assets = await getFixedAssetsMap();
    const assetIndex = assets.findIndex(a => a.assetCode === code || a.code === code);
    if (assetIndex === -1) throw new Error("Not found");
    const asset = assets[assetIndex];

    if (asset.id && asset.id.toString().startsWith('mock-')) {
       localMockAssets = localMockAssets.filter(a => a.id !== asset.id);
       return true;
    }

    await apiClient.delete(`/assets/fixed-assets/${asset.id}`);
    fixedAssetsCache = null;
    return true;
  },

  updateAssetLocation: async (code, newLocationId, newLocationType, newLocationName) => {
    const assets = await getFixedAssetsMap();
    const asset = assets.find(a => a.assetCode === code || a.code === code);
    if (!asset) throw new Error("Not found");
    
    // Mock handling
    if (asset.id && asset.id.toString().startsWith('mock-')) {
       asset.location = newLocationName || newLocationId;
       return asset;
    }
    
    try {
      const req = { 
        categoryId: asset.categoryId,
        name: asset.name,
        note: asset.note,
        roomId: newLocationType === 'ROOM' ? newLocationId : null, 
        areaId: newLocationType === 'AREA' ? newLocationId : null,
        assetCode: asset.assetCode
      };
      const updated = await apiClient.put(`/assets/fixed-assets/${asset.id}`, req);
      fixedAssetsCache = null;
      return mapFixedAsset(updated);
    } catch (e) {
      console.warn('Backend update failed for location (likely due to mock string UUID). Updating local cache instead.');
      asset.location = newLocationName || `Mock-${newLocationType}-${newLocationId}`;
      return asset;
    }
  },

  updateAssetStatus: async (code, newStatus) => {
    const assets = await getFixedAssetsMap();
    const asset = assets.find(a => a.assetCode === code || a.code === code);
    if (!asset) throw new Error("Not found");

    // Mock handling
    if (asset.id && asset.id.toString().startsWith('mock-')) {
       asset.status = newStatus;
       return asset;
    }

    let backStatus = 'GOOD';
    if (newStatus === 'Repairing') backStatus = 'UNDER_REPAIR';
    if (newStatus === 'Good') backStatus = 'GOOD';
    if (newStatus === 'Damaged') backStatus = 'BROKEN';
    if (newStatus === 'Disposed') backStatus = 'DISPOSED';
    const req = { status: backStatus };
    const updated = await apiClient.patch(`/assets/fixed-assets/${asset.id}/status`, req);
    fixedAssetsCache = null;
    return mapFixedAsset(updated);
  },

  // ---------------------------------
  // CONSUMABLES
  // ---------------------------------
  getConsumables: async (options = {}) => {
    const { page = 1, limit = 10, search, purpose } = options;
    const params = new URLSearchParams();
    params.append('page', (page - 1).toString());
    params.append('size', limit.toString());

    const response = await apiClient.get(`/assets/consumables?${params.toString()}`);
    let mappedItems = await Promise.all((response.content || []).map(mapConsumable));
    
    if (purpose && purpose !== 'all') {
      mappedItems = mappedItems.filter(i => i.purpose === purpose);
    }
    if (search) {
      const lowerSearch = search.toLowerCase();
      mappedItems = mappedItems.filter(i => (i.name || '').toLowerCase().includes(lowerSearch));
    }
    
    return {
      items: mappedItems,
      total: response.totalElements || 0,
      totalPages: response.totalPages || 0
    };
  },

  addConsumableItem: async (data) => {
    return apiClient.post('/assets/consumables', data);
  },

  stockCount: async (items) => {
    return apiClient.put('/assets/consumables/stock-count', { lines: items });
  },

  updateConsumableInventory: async (id, quantity) => {
    return assetService.stockCount([{ itemId: id, quantity }]);
  },

  // ---------------------------------
  // DAMAGE REPORTS
  // ---------------------------------
  getDamageReports: async (options = {}) => {
    const { status, page = 1, limit = 10 } = options;
    const params = new URLSearchParams();
    if (status) params.append('status', status.toUpperCase());
    params.append('page', (page - 1).toString());
    params.append('size', limit.toString());

    const response = await apiClient.get(`/assets/damage-reports?${params.toString()}`);
    
    const mappedItems = await Promise.all((response.content || []).map(async inc => {
      let assetCode = 'Unknown';
      let assetName = 'Unknown';
      let room = 'Chưa rõ';
      
      const assetRes = await resolveFixedAsset(inc.fixedAssetId);
      if (assetRes) {
         assetCode = assetRes.assetCode;
         assetName = assetRes.name;
         room = assetRes.roomId || assetRes.areaId || 'Chưa rõ';
      }

      return {
        id: inc.id,
        assetCode,
        assetName,
        room,
        description: inc.description,
        reportedBy: inc.reporterId,
        reportedRole: 'Nhân viên',
        reportedTime: inc.reportedAt ? new Date(inc.reportedAt).toLocaleString('vi-VN') : 'N/A',
        ticketStatus: inc.status === 'NEW' ? 'New' : 'Processed',
        ticketStatusLabel: inc.status === 'NEW' ? 'Mới' : 'Đã xử lý'
      };
    }));

    return {
      items: mappedItems,
      total: response.totalElements || 0,
      totalPages: response.totalPages || 0
    };
  },

  getIncident: async (id) => {
     const res = await assetService.getDamageReports({ limit: 1000, status: '' });
     return res.items.find(i => i.id === id);
  },
  
  getDamageReportById: async (id) => {
     return assetService.getIncident(id);
  },

  resolveDamageReport: async (id) => {
    return apiClient.patch(`/assets/damage-reports/${id}/resolve`);
  },

  resolveIncident: async (id, data) => {
    const inc = await assetService.getIncident(id);
    if (data?.newAssetStatus) {
       let backStatus = 'GOOD';
       if (data.newAssetStatus === 'Repairing') backStatus = 'UNDER_REPAIR';
       if (data.newAssetStatus === 'Good') backStatus = 'GOOD';
       if (data.newAssetStatus === 'Disposed') backStatus = 'DISPOSED';
       try {
           await assetService.updateAssetStatus(inc.assetCode, backStatus);
       } catch(e) { console.error('Failed to update asset status', e); }
    }
    await assetService.resolveDamageReport(id);
    const updated = await assetService.getIncident(id);
    updated.managerNote = data?.processingNote || '';
    return updated;
  },

  // ---------------------------------
  // ASSET CATEGORIES
  // ---------------------------------
  getAssetCategories: getCategories,

  // ---------------------------------
  // OVERVIEW STATS
  // ---------------------------------
  getOverviewStats: async () => {
    const fixedAssetsData = await getFixedAssetsMap();
    
    // Consumables limit 1000
    let consumablesData = [];
    try {
      const coRes = await apiClient.get('/assets/consumables?size=1000');
      consumablesData = coRes.content || [];
    } catch(e) {}
    
    let incidentsData = [];
    try {
      const inRes = await assetService.getDamageReports({ limit: 1000, status: '' });
      incidentsData = inRes.items || [];
    } catch(e) {}
    
    const goodCount = fixedAssetsData.filter((a) => mapAssetStatus(a.status) === 'Good').length;
    const damagedCount = fixedAssetsData.filter((a) => mapAssetStatus(a.status) === 'Damaged').length;
    const repairingCount = fixedAssetsData.filter((a) => mapAssetStatus(a.status) === 'Repairing').length;
    const disposedCount = fixedAssetsData.filter((a) => mapAssetStatus(a.status) === 'Disposed').length;

    let maxAudit = null;
    let totalStock = 0;
    let guestCategories = new Set();
    let facilityCategories = new Set();
    
    for (const c of consumablesData) {
       totalStock += (c.quantity || 0);
       if (c.lastCountedAt) {
          const d = new Date(c.lastCountedAt);
          if (!maxAudit || d > maxAudit) maxAudit = d;
       }
       try {
         const category = await resolveCategory(c.categoryId);
         if (category.purpose === 'GUEST_USE') guestCategories.add(category.id);
         if (category.purpose === 'FACILITY_OPS') facilityCategories.add(category.id);
       } catch(e) {}
    }
    const lastAuditStr = maxAudit ? maxAudit.toLocaleString('vi-VN') : 'Chưa kiểm kê';
    
    const pendingIncidents = incidentsData.filter((i) => i.ticketStatus === 'New').length;
    
    return {
       fixedAssets: {
          total: fixedAssetsData.length,
          good: goodCount,
          damaged: damagedCount,
          repairing: repairingCount,
          disposed: disposedCount
       },
       consumables: {
          totalStock: totalStock,
          categoriesCount: new Set(consumablesData.map(c => c.categoryId)).size,
          guestCount: guestCategories.size,
          facilityCount: facilityCategories.size,
          lastAudit: lastAuditStr
       },
       incidents: {
          pending: pendingIncidents,
          resolved: incidentsData.length - pendingIncidents,
          damageTotal: incidentsData.length,
          lostTotal: 0,
          top5: incidentsData.filter((i) => i.ticketStatus === 'New').slice(0, 5)
       }
    };
  }
};
