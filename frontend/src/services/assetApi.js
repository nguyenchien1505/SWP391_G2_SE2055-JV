import { apiClient } from './apiClient';

/**
 * Dạng UUID chuẩn 8-4-4-4-12. Mọi chỗ nhận diện "id thật của backend" phải dùng CHUNG
 * hằng số này: viết lại regex ở từng hàm đã từng làm rơi mất nhóm thứ tư.
 */
const UUID_PATTERN = /^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$/;
const isUUID = (id) => UUID_PATTERN.test(String(id ?? ''));

/** Kích thước trang khi kéo trọn danh sách — dưới trần max-page-size 2000 của Spring. */
const FETCH_ALL_PAGE_SIZE = 500;

// --- CACHE ---
// Tài sản, vật tư, báo hỏng KHÔNG cache: dữ liệu vận hành phải luôn mới (cache cũ từng làm
// Dashboard hiện số liệu cũ, và lọt sang tài khoản đăng nhập sau trong cùng tab). Chỉ danh
// mục được cache NGẮN để tra tên cho cả danh sách bằng một request; cache bị xóa khi đổi
// tài khoản (clearAssetCaches, gọi từ AuthContext).
const CATEGORY_CACHE_TTL_MS = 30_000;
let categoriesCache = null; // { promise, at }

/** Xóa mọi cache của module — gọi khi đăng nhập/đăng xuất để không lộ dữ liệu người trước. */
export function clearAssetCaches() {
  categoriesCache = null;
}

/**
 * Danh mục tài sản thật của Tenant (kể cả đang ẩn — tài sản cũ vẫn phải hiện đúng tên).
 * Dùng chung một promise để N lần tra cứu song song chỉ gọi một request. Lỗi thì KHÔNG
 * giữ lại, để lần sau còn thử lại.
 */
const getCategories = () => {
  if (!categoriesCache || Date.now() - categoriesCache.at > CATEGORY_CACHE_TTL_MS) {
    const promise = apiClient
      .get('/organization/asset-categories?size=1000')
      .then((res) => res?.content || []);
    categoriesCache = { promise, at: Date.now() };
    promise.catch(() => {
      if (categoriesCache?.promise === promise) categoriesCache = null;
    });
  }
  return categoriesCache.promise;
};

const UNKNOWN_CATEGORY = { name: 'Không rõ danh mục', purpose: null };

const resolveCategory = async (categoryId) => {
  const find = (cats) => cats.find((c) => c.id === categoryId);
  try {
    const first = getCategories();
    const found = find(await first);
    if (found) return found;
    // Danh mục Giám đốc vừa tạo sau lần nạp cache — nạp lại MỘT lần. Chỉ bỏ đúng bản cache
    // vừa dùng (và không quá mới), để N lần tra cứu song song không nạp lại N lần.
    if (categoriesCache?.promise === first && Date.now() - categoriesCache.at > 1000) {
      categoriesCache = null;
    }
    return find(await getCategories()) || UNKNOWN_CATEGORY;
  } catch {
    // Tên danh mục chỉ để hiển thị — không làm hỏng cả danh sách tài sản.
    return UNKNOWN_CATEGORY;
  }
};

/** Enum AssetPurpose của backend → khóa tab và nhãn trên màn hình vật tư. */
const mapPurpose = (purpose) => {
  if (purpose === 'GUEST_USE') return { purpose: 'guest', purposeLabel: 'Dùng cho khách' };
  if (purpose === 'FACILITY_MAINTENANCE') return { purpose: 'facility', purposeLabel: 'Đồ dùng để duy trì cơ sở' };
  return { purpose: 'internal', purposeLabel: 'Nội bộ' };
};

/** Kéo trọn một danh sách phân trang qua mọi trang. `query` không kèm page/size. */
const fetchAllPages = async (path, query) => {
  const all = [];
  let page = 0;
  let totalPages = 1;
  while (page < totalPages) {
    const res = await apiClient.get(`${path}?${query}&page=${page}&size=${FETCH_ALL_PAGE_SIZE}`);
    all.push(...(res?.content || []));
    totalPages = res?.totalPages || 0;
    page += 1;
  }
  return all;
};

/** Toàn bộ tài sản cố định (cả đã thanh lý) trong phạm vi người dùng — luôn đọc mới. */
const fetchAllFixedAssetsRaw = () =>
  fetchAllPages('/assets/fixed-assets', 'includeDisposed=true&sort=assetCode,asc');

/** Toàn bộ báo hỏng, cả NEW lẫn RESOLVED (`status=` rỗng = bỏ lọc, DM-16). */
const fetchAllDamageReportsRaw = () =>
  fetchAllPages('/assets/damage-reports', 'status=&sort=reportedAt,desc');

const fetchAllConsumablesRaw = () => fetchAllPages('/assets/consumables', 'sort=categoryId,asc');

const indexById = (items) => new Map(items.map((i) => [i.id, i]));

/** Thông điệp tiếng Việt cho lỗi tải — backend chỉ trả "Forbidden"/rỗng với 401/403. */
const friendlyError = (err) => {
  if (err?.status === 401) return 'Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.';
  if (err?.status === 403) return 'Bạn không có quyền xem phần này.';
  if (err?.status === 0) return 'Không kết nối được máy chủ.';
  return err?.message || 'Không tải được dữ liệu.';
};

/** Phiếu báo hỏng cho UI. `assetsById` tra mã/tên/vị trí tài sản. */
const mapDamageReport = (inc, assetsById) => {
  const asset = assetsById.get(inc.fixedAssetId);
  return {
    id: inc.id,
    shortId: String(inc.id).slice(0, 8).toUpperCase(),
    assetId: inc.fixedAssetId,
    assetCode: asset?.assetCode || 'Không rõ',
    assetName: asset?.name || 'Không rõ',
    room: asset ? asset.roomName || asset.areaName || 'Chưa xếp vị trí' : 'Chưa rõ',
    description: inc.description,
    reportedBy: inc.reporterName || inc.reporterEmail || 'Không rõ',
    reporterEmail: inc.reporterEmail,
    reportedTime: inc.reportedAt ? new Date(inc.reportedAt).toLocaleString('vi-VN') : 'N/A',
    ticketStatus: inc.status === 'NEW' ? 'New' : 'Processed',
    ticketStatusLabel: inc.status === 'NEW' ? 'Mới' : 'Đã xử lý',
  };
};

/**
 * Bản ghi backend mới nhất của một tài sản. Nhận id (đường chuẩn) hoặc mã tài sản (đường
 * cũ từ link/phiếu báo hỏng). Mã chỉ duy nhất trong một khách sạn, nên Giám đốc xem nhiều
 * khách sạn phải đi bằng id.
 */
const resolveAssetRef = async (ref) => {
  if (isUUID(ref)) {
    return apiClient.get(`/assets/fixed-assets/${ref}`);
  }
  const assets = await fetchAllFixedAssetsRaw();
  const asset = assets.find((a) => a.assetCode === ref);
  if (!asset) throw new Error(`Không tìm thấy tài sản có mã "${ref}".`);
  return asset;
};

/** Nhãn trạng thái phía UI ↔ enum FixedAssetStatus của backend. */
const STATUS_TO_BACKEND = {
  Good: 'GOOD',
  Damaged: 'BROKEN',
  Repairing: 'UNDER_REPAIR',
  Disposed: 'DISPOSED',
};

const mapAssetStatus = (status) => {
  const found = Object.entries(STATUS_TO_BACKEND).find(([ui, be]) => ui === status || be === status);
  return found ? found[0] : status;
};

/** Nhận cả nhãn UI lẫn enum backend; giá trị lạ thì báo lỗi thay vì lặng lẽ về GOOD. */
const toBackendStatus = (status) => {
  if (STATUS_TO_BACKEND[status]) return STATUS_TO_BACKEND[status];
  if (Object.values(STATUS_TO_BACKEND).includes(status)) return status;
  throw new Error(`Trạng thái tài sản không hợp lệ: ${status}`);
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
    locationId: backendAsset.locationId,
    roomId: backendAsset.roomId,
    areaId: backendAsset.areaId,
    locationType: backendAsset.roomId ? 'room' : 'area',
    location: backendAsset.roomName || backendAsset.areaName || 'Chưa xếp vị trí',
    status: mapAssetStatus(backendAsset.status),
    note: backendAsset.note,
  };
};

/** Body PUT đầy đủ từ bản ghi backend — PUT thay toàn bộ nên thiếu trường là mất dữ liệu. */
const toUpdateRequest = (asset, overrides = {}) => ({
  categoryId: asset.categoryId,
  name: asset.name,
  note: asset.note,
  roomId: asset.roomId || null,
  areaId: asset.areaId || null,
  ...overrides,
});

const mapConsumable = async (backendConsumable) => {
  const cat = await resolveCategory(backendConsumable.categoryId);
  return {
    id: backendConsumable.id,
    categoryId: backendConsumable.categoryId,
    categoryCode: backendConsumable.categoryName || cat.name,
    name: backendConsumable.categoryName || cat.name,
    description: backendConsumable.categoryName || cat.name, // Danh mục backend không có mô tả
    icon: 'inventory_2',
    unit: backendConsumable.unit || cat.unit || '',
    quantity: Number(backendConsumable.quantity) || 0,
    lastAuditDate: backendConsumable.lastCountedAt ? new Date(backendConsumable.lastCountedAt).toLocaleString('vi-VN') : 'Chưa kiểm kê',
    lastAuditUser: backendConsumable.lastCountedByEmail || backendConsumable.lastCountedBy || 'N/A',
    status: (backendConsumable.quantity > 0) ? 'In Stock' : 'Out of Stock',
    statusLabel: (backendConsumable.quantity > 0) ? 'Còn hàng' : 'Hết hàng',
    ...mapPurpose(cat.purpose),
  };
};

export const assetService = {
  // ---------------------------------
  // FIXED ASSETS
  // ---------------------------------

  /**
   * Toàn bộ tài sản cố định trong phạm vi người dùng (cả đã thanh lý). Backend chưa hỗ trợ
   * tìm kiếm/lọc theo trạng thái, nên màn danh sách lọc và phân trang ở client — lọc trên
   * từng trang server sẽ bỏ sót tài sản ở các trang khác.
   */
  getAllFixedAssets: async () => {
    const raw = await fetchAllFixedAssetsRaw();
    return Promise.all(raw.map(mapFixedAsset));
  },

  getAssetDetail: async (ref) => {
    return assetService.getFixedAssetById(ref);
  },

  getFixedAssetById: async (ref) => {
    return mapFixedAsset(await resolveAssetRef(ref));
  },

  /**
   * STT kế tiếp cho các mã dạng `${base}NNN` trong phạm vi người dùng, tính từ số LỚN NHẤT
   * đang dùng (cả tài sản đã thanh lý — mã của chúng vẫn chiếm chỗ trong ràng buộc unique).
   */
  getNextBatchSequence: async (base) => {
    const assets = await fetchAllFixedAssetsRaw();
    let max = 0;
    for (const a of assets) {
      const code = a.assetCode || '';
      if (!code.startsWith(base)) continue;
      const rest = code.slice(base.length);
      if (/^\d+$/.test(rest)) max = Math.max(max, parseInt(rest, 10));
    }
    return max + 1;
  },

  /**
   * Tạo lần lượt từng tài sản. Gặp lỗi thì DỪNG và báo rõ đã tạo được bao nhiêu — không bỏ
   * qua lỗi rồi báo thành công một con số sai.
   *
   * @param data { categoryId, name, codeBase, positionType: 'ROOM'|'AREA', positionId, quantity }
   * @returns { count, codes }
   */
  createBatchAssets: async (data) => {
    if (!isUUID(data.categoryId)) throw new Error('Vui lòng chọn danh mục tài sản hợp lệ.');
    if (!isUUID(data.positionId)) throw new Error('Vui lòng chọn Phòng hoặc Khu vực hợp lệ.');
    if (data.positionType !== 'ROOM' && data.positionType !== 'AREA') {
      throw new Error('Loại vị trí không hợp lệ.');
    }

    const start = await assetService.getNextBatchSequence(data.codeBase);
    const codes = [];
    try {
      for (let i = 0; i < data.quantity; i++) {
        const assetCode = `${data.codeBase}${String(start + i).padStart(3, '0')}`;
        await apiClient.post('/assets/fixed-assets', {
          categoryId: data.categoryId,
          name: data.name,
          note: 'Thêm tự động hàng loạt',
          roomId: data.positionType === 'ROOM' ? data.positionId : null,
          areaId: data.positionType === 'AREA' ? data.positionId : null,
          assetCode,
        });
        codes.push(assetCode);
      }
    } catch (e) {
      const done = codes.length > 0
        ? `Đã tạo ${codes.length}/${data.quantity} tài sản (${codes[0]} → ${codes[codes.length - 1]}) rồi dừng. `
        : '';
      throw new Error(`${done}Lỗi khi tạo tài sản: ${e.message}`);
    }
    return { count: codes.length, codes };
  },

  createFixedAsset: async (data) => {
    if (!isUUID(data.categoryId) || !isUUID(data.locationId)) {
      throw new Error('Danh mục hoặc vị trí không hợp lệ.');
    }
    const response = await apiClient.post('/assets/fixed-assets', {
      categoryId: data.categoryId,
      name: data.name,
      note: data.note,
      roomId: data.locationType === 'room' ? data.locationId : null,
      areaId: data.locationType === 'area' ? data.locationId : null,
    });
    return mapFixedAsset(response);
  },

  updateFixedAssetInfo: async (ref, data) => {
    const asset = await resolveAssetRef(ref);
    const updated = await apiClient.put(
      `/assets/fixed-assets/${asset.id}`,
      toUpdateRequest(asset, {
        categoryId: data.categoryId || asset.categoryId,
        name: data.name,
        note: data.note,
      })
    );
    return mapFixedAsset(updated);
  },

  deleteFixedAsset: async (ref) => {
    const asset = await resolveAssetRef(ref);
    await apiClient.delete(`/assets/fixed-assets/${asset.id}`);
    return true;
  },

  /** Đổi vị trí trong cùng khách sạn — BR-ASSET-13. Lỗi backend được ném ra nguyên văn. */
  updateAssetLocation: async (ref, newLocationId, newLocationType) => {
    if (newLocationType !== 'ROOM' && newLocationType !== 'AREA') {
      throw new Error('Loại vị trí không hợp lệ.');
    }
    if (!isUUID(newLocationId)) throw new Error('Vui lòng chọn Phòng hoặc Khu vực hợp lệ.');

    const asset = await resolveAssetRef(ref);
    const updated = await apiClient.put(
      `/assets/fixed-assets/${asset.id}`,
      toUpdateRequest(asset, {
        roomId: newLocationType === 'ROOM' ? newLocationId : null,
        areaId: newLocationType === 'AREA' ? newLocationId : null,
      })
    );
    return mapFixedAsset(updated);
  },

  /** `newStatus` nhận cả nhãn UI (Good/Damaged/Repairing/Disposed) lẫn enum backend. */
  updateAssetStatus: async (ref, newStatus) => {
    const status = toBackendStatus(newStatus);
    const asset = await resolveAssetRef(ref);
    const updated = await apiClient.patch(`/assets/fixed-assets/${asset.id}/status`, { status });
    return mapFixedAsset(updated);
  },

  // ---------------------------------
  // CONSUMABLES
  // ---------------------------------
  /**
   * Toàn bộ tồn kho trong phạm vi người dùng. Backend chưa hỗ trợ tìm theo tên/mục đích,
   * nên màn hình lọc ở client — lọc trên một trang server sẽ bỏ sót mặt hàng ở trang khác.
   */
  getAllConsumables: async () => {
    const raw = await fetchAllConsumablesRaw();
    return Promise.all(raw.map(mapConsumable));
  },

  /**
   * Danh mục tiêu hao đang hoạt động của Tenant (do Giám đốc tạo) — kèm `purpose`/`purposeLabel`
   * đã đổi sang khóa của màn hình vật tư; enum gốc giữ ở `purposeCode`.
   */
  getActiveConsumableCategories: async () => {
    const res = await apiClient.get(
      '/organization/asset-categories?assetKind=CONSUMABLE&active=true&size=1000&sort=name,asc'
    );
    return (res?.content || []).map((c) => ({ ...c, purposeCode: c.purpose, ...mapPurpose(c.purpose) }));
  },

  /** body: { categoryId, quantity } — Location lấy từ phiên đăng nhập (BR-ASSET-13). */
  addConsumableItem: async (data) => {
    return apiClient.post('/assets/consumables', data);
  },

  /** Backend chỉ cho xóa khi tồn kho bằng 0. */
  deleteConsumableItem: async (id) => {
    return apiClient.delete(`/assets/consumables/${id}`);
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
    // Không truyền `status` → backend mặc định chỉ trả NEW; `status=''` (gửi rỗng) mới là
    // bỏ lọc, lấy cả RESOLVED (DM-16). Bỏ mất tham số rỗng thì phiếu vừa đóng "biến mất".
    if (status !== undefined && status !== null) params.append('status', status.toUpperCase());
    params.append('page', (page - 1).toString());
    params.append('size', limit.toString());

    const [response, assets] = await Promise.all([
      apiClient.get(`/assets/damage-reports?${params.toString()}`),
      fetchAllFixedAssetsRaw(),
    ]);
    const assetsById = indexById(assets);

    return {
      items: (response.content || []).map((inc) => mapDamageReport(inc, assetsById)),
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

  /**
   * Cập nhật trạng thái tài sản TRƯỚC rồi mới đóng phiếu: nếu bước đầu lỗi thì phiếu vẫn
   * mở, người dùng thấy lỗi và làm lại — thay vì phiếu đã đóng mà tài sản sai trạng thái.
   */
  resolveIncident: async (id, data) => {
    const inc = await assetService.getIncident(id);
    if (!inc) throw new Error('Không tìm thấy phiếu báo hỏng.');
    if (data?.newAssetStatus) {
      await assetService.updateAssetStatus(inc.assetId || inc.assetCode, data.newAssetStatus);
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

  /**
   * Số liệu Dashboard — luôn đọc mới từ backend. Ba phần tải độc lập: phần nào lỗi thì
   * trả `null` kèm thông điệp trong `errors`, KHÔNG thay bằng số 0 (0 trông như số liệu
   * thật). Cả ba cùng lỗi thì ném lỗi để màn hình hiện thông báo tải thất bại.
   *
   * @param includeConsumables false cho Staff — API vật tư chỉ mở cho Giám đốc/Manager.
   */
  getOverviewStats: async ({ includeConsumables = true } = {}) => {
    const [assetsRes, consumablesRes, reportsRes] = await Promise.allSettled([
      fetchAllFixedAssetsRaw(),
      includeConsumables ? fetchAllConsumablesRaw() : Promise.resolve(null),
      fetchAllDamageReportsRaw(),
    ]);

    const errors = {};
    const failed = (res, key) => {
      if (res.status === 'rejected') errors[key] = friendlyError(res.reason);
      return res.status === 'rejected';
    };

    let fixedAssets = null;
    if (!failed(assetsRes, 'fixedAssets')) {
      const count = (st) => assetsRes.value.filter((a) => a.status === st).length;
      fixedAssets = {
        total: assetsRes.value.length,
        good: count('GOOD'),
        damaged: count('BROKEN'),
        repairing: count('UNDER_REPAIR'),
        disposed: count('DISPOSED'),
      };
    }

    let consumables = null;
    if (!failed(consumablesRes, 'consumables') && consumablesRes.value) {
      const items = consumablesRes.value;
      let maxAudit = null;
      let totalStock = 0;
      const guestCategories = new Set();
      const facilityCategories = new Set();
      for (const c of items) {
        totalStock += Number(c.quantity) || 0;
        if (c.lastCountedAt) {
          const d = new Date(c.lastCountedAt);
          if (!maxAudit || d > maxAudit) maxAudit = d;
        }
        const category = await resolveCategory(c.categoryId);
        if (category.purpose === 'GUEST_USE') guestCategories.add(c.categoryId);
        if (category.purpose === 'FACILITY_MAINTENANCE') facilityCategories.add(c.categoryId);
      }
      consumables = {
        totalStock,
        categoriesCount: new Set(items.map((c) => c.categoryId)).size,
        outOfStockCount: items.filter((c) => c.outOfStock || !(Number(c.quantity) > 0)).length,
        guestCount: guestCategories.size,
        facilityCount: facilityCategories.size,
        lastAudit: maxAudit ? maxAudit.toLocaleString('vi-VN') : 'Chưa kiểm kê',
      };
    }

    let incidents = null;
    if (!failed(reportsRes, 'incidents')) {
      // Tài sản chỉ để tra tên/vị trí — thiếu thì phiếu vẫn hiện, ghi "Không rõ".
      const assetsById = indexById(assetsRes.status === 'fulfilled' ? assetsRes.value : []);
      const reports = reportsRes.value.map((r) => mapDamageReport(r, assetsById));
      const pendingList = reports.filter((i) => i.ticketStatus === 'New');
      incidents = {
        pending: pendingList.length,
        resolved: reports.length - pendingList.length,
        total: reports.length,
        top5: pendingList.slice(0, 5),
      };
    }

    if (!fixedAssets && !incidents && (!includeConsumables || !consumables)) {
      throw new Error(Object.values(errors)[0] || 'Không tải được dữ liệu tổng quan.');
    }

    return { fixedAssets, consumables, incidents, errors, loadedAt: new Date() };
  }
};
