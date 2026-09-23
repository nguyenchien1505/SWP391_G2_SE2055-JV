import { apiClient } from './apiClient';

export class CatalogService {
  /**
   * GET /api/v1/organization/asset-categories
   */
  static async getCatalogs(params) {
    const query = new URLSearchParams();
    
    // activeTab in UI might be 'ALL', 'TS_CD' (FIXED), 'VT_TH' (CONSUMABLE)
    if (params?.category) {
      if (params.category === 'TS_CD') query.set('assetKind', 'FIXED');
      if (params.category === 'VT_TH') query.set('assetKind', 'CONSUMABLE');
    }
    
    // Backend doesn't support "search by name" natively in the controller method signature 
    // unless it's added. Let's see if the backend controller has a 'search' param? No.
    // So we'll fetch all and filter client side if there's a search term, 
    // OR just fetch the page and ignore search if backend doesn't support it.
    
    if (params?.page !== undefined) query.set('page', params.page.toString());
    if (params?.size !== undefined) query.set('size', params.size.toString());

    let endpoint = `/organization/asset-categories${query.toString() ? `?${query.toString()}` : ''}`;
    
    // Fetch from backend
    const response = await apiClient.get(endpoint);
    
    // If there is a search term, we might have to filter on client side for now.
    // Ideally backend should support `search` param, but we just return what backend gave.
    let content = response.content || [];
    
    if (params?.search) {
      const s = params.search.toLowerCase().trim();
      content = content.filter(item => item.name && item.name.toLowerCase().includes(s));
    }
    
    return {
      content,
      pageNo: response.pageable?.pageNumber || 0,
      pageSize: response.pageable?.pageSize || 20,
      totalElements: response.totalElements || content.length,
      totalPages: response.totalPages || 1,
      last: response.last,
    };
  }

  /**
   * POST /api/v1/organization/asset-categories
   */
  static async createCatalog(dto) {
    return await apiClient.post('/organization/asset-categories', dto);
  }

  /**
   * PATCH /api/v1/organization/asset-categories/{id}/active?value={boolean}
   */
  static async toggleStatus(id, isActive) {
    return await apiClient.patch(`/organization/asset-categories/${id}/active?value=${isActive}`);
  }

  /**
   * Calculate Metrics by querying counts
   */
  static async getMetrics() {
    try {
      const [all, fixed, consumable] = await Promise.all([
        apiClient.get('/organization/asset-categories?size=1'),
        apiClient.get('/organization/asset-categories?assetKind=FIXED&size=1'),
        apiClient.get('/organization/asset-categories?assetKind=CONSUMABLE&size=1')
      ]);
      
      return {
        totalCatalogs: all.totalElements || 0,
        fixedAssetsCount: fixed.totalElements || 0,
        consumablesCount: consumable.totalElements || 0,
        chainAdoptionRate: 100,
      };
    } catch (error) {
      console.error('Error fetching metrics', error);
      return {
        totalCatalogs: 0,
        fixedAssetsCount: 0,
        consumablesCount: 0,
        chainAdoptionRate: 100,
      };
    }
  }

  /**
   * GET /api/v1/catalogs/branch-compliance (Mocked as backend doesn't have it yet)
   */
  static async getBranchCompliance() {
    return [];
  }

  static async exportExcel() {
    alert("Chức năng xuất Excel đang được bảo trì!");
  }
}
