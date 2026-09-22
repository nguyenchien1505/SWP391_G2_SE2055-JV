package com.example.SWP391_G2_SE2055_JV.service;

import com.example.SWP391_G2_SE2055_JV.dto.asset.AssetCategoryResponse;
import com.example.SWP391_G2_SE2055_JV.dto.asset.CreateAssetCategoryRequest;
import com.example.SWP391_G2_SE2055_JV.dto.asset.UpdateAssetCategoryRequest;
import com.example.SWP391_G2_SE2055_JV.entity.AssetCategory;
import com.example.SWP391_G2_SE2055_JV.enums.AssetKind;
import com.example.SWP391_G2_SE2055_JV.exception.BusinessException;
import com.example.SWP391_G2_SE2055_JV.exception.ResourceNotFoundException;
import com.example.SWP391_G2_SE2055_JV.repository.AssetCategoryRepository;
import com.example.SWP391_G2_SE2055_JV.repository.ConsumableItemRepository;
import com.example.SWP391_G2_SE2055_JV.repository.FixedAssetRepository;
import com.example.SWP391_G2_SE2055_JV.utils.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Danh mục tài sản — BR-ASSET-08, BR-ASSET-09.
 *
 * <p>Danh mục cấp TENANT, dùng chung mọi Location, chỉ Giám đốc quản lý. Một bảng duy
 * nhất cho cả hai loại tài sản, phân biệt bằng {@code assetKind}.
 *
 * <p>Ba rule về vòng đời danh mục dễ lẫn nhau nên ghi rõ ở đây:
 * <ul>
 *   <li>{@code is_active = false} (BR-ORG-14) là ẩn TẠM khỏi danh sách chọn, bật lại
 *       được bất cứ lúc nào — không phải xóa.</li>
 *   <li>Xóa cứng chỉ cho phép khi KHÔNG còn tài sản cố định hay dòng tồn kho nào trỏ
 *       vào, cùng tinh thần BR-ORG-10 với Department/Position.</li>
 *   <li>{@code assetKind} bất biến sau khi tạo.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AssetCategoryService {

    private final AssetCategoryRepository categoryRepository;
    private final FixedAssetRepository    fixedAssetRepository;
    private final ConsumableItemRepository consumableItemRepository;

    /**
     * @param assetKind lọc theo loại tài sản, bỏ trống để lấy cả hai
     * @param active    bỏ trống để lấy cả danh mục đang ẩn — Giám đốc cần thấy chúng
     *                  thì mới bật lại được (BR-ORG-14)
     */
    @Transactional(readOnly = true)
    public Page<AssetCategoryResponse> getCategories(AssetKind assetKind, Boolean active, Pageable pageable) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();

        Page<AssetCategory> page;
        if (assetKind != null && active != null) {
            page = categoryRepository.findByTenantIdAndAssetKindAndActive(tenantId, assetKind, active, pageable);
        } else if (assetKind != null) {
            page = categoryRepository.findByTenantIdAndAssetKind(tenantId, assetKind, pageable);
        } else if (active != null) {
            page = categoryRepository.findByTenantIdAndActive(tenantId, active, pageable);
        } else {
            page = categoryRepository.findByTenantId(tenantId, pageable);
        }
        return page.map(AssetCategoryResponse::fromEntity);
    }

    @Transactional(readOnly = true)
    public AssetCategoryResponse getCategoryById(UUID id) {
        return AssetCategoryResponse.fromEntity(getOwnedCategory(id));
    }

    @Transactional
    public AssetCategoryResponse createCategory(CreateAssetCategoryRequest request) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();

        assertNameAvailable(tenantId, request.getName(), null);
        String unit = normalizeUnit(request.getAssetKind(), request.getUnit());

        AssetCategory category = AssetCategory.builder()
            .tenantId(tenantId)
            .name(request.getName())
            .assetKind(request.getAssetKind())
            .purpose(request.getPurpose())
            .unit(unit)
            .active(true)
            .build();

        return AssetCategoryResponse.fromEntity(categoryRepository.save(category));
    }

    /** {@code assetKind} không nằm trong request nên không có đường nào đổi được — BR-ASSET-08. */
    @Transactional
    public AssetCategoryResponse updateCategory(UUID id, UpdateAssetCategoryRequest request) {
        AssetCategory category = getOwnedCategory(id);

        assertNameAvailable(category.getTenantId(), request.getName(), id);

        category.setName(request.getName());
        category.setPurpose(request.getPurpose());
        category.setUnit(normalizeUnit(category.getAssetKind(), request.getUnit()));

        return AssetCategoryResponse.fromEntity(categoryRepository.save(category));
    }

    /**
     * Ẩn hoặc hiện lại danh mục — BR-ORG-14. Ẩn KHÔNG ảnh hưởng tới tài sản đã tạo,
     * chỉ khiến danh mục không còn được chọn khi tạo mới.
     */
    @Transactional
    public AssetCategoryResponse setActive(UUID id, boolean active) {
        AssetCategory category = getOwnedCategory(id);
        category.setActive(active);
        return AssetCategoryResponse.fromEntity(categoryRepository.save(category));
    }

    /**
     * Xóa cứng, chặn khi còn tham chiếu. Giám đốc muốn dọn danh sách chọn mà vẫn giữ
     * dữ liệu lịch sử thì dùng {@link #setActive} thay vì xóa.
     */
    @Transactional
    public void deleteCategory(UUID id) {
        AssetCategory category = getOwnedCategory(id);

        if (fixedAssetRepository.existsByCategoryId(id)) {
            throw new BusinessException(
                "Không xóa được danh mục đang có tài sản cố định thuộc về nó. "
                    + "Hãy ẩn danh mục thay vì xóa (BR-ORG-14).");
        }
        if (consumableItemRepository.existsByCategoryId(id)) {
            throw new BusinessException(
                "Không xóa được danh mục đang có tồn kho tiêu hao thuộc về nó. "
                    + "Hãy ẩn danh mục thay vì xóa (BR-ORG-14).");
        }

        categoryRepository.delete(category);
        log.info("Đã xóa danh mục tài sản {} ({})", category.getName(), id);
    }

    /** KHÔNG lọc theo {@code active} — xem javadoc của AssetCategoryRepository. */
    private AssetCategory getOwnedCategory(UUID id) {
        return categoryRepository.findByIdAndTenantId(id, SecurityUtils.getCurrentTenantId())
            .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy danh mục tài sản"));
    }

    /**
     * BR-ASSET-08: đơn vị tính chỉ có nghĩa với loại tiêu hao. Trả về giá trị đã chuẩn
     * hóa thay vì sửa đè lên request, để method kiểm tra không có tác dụng phụ.
     */
    private String normalizeUnit(AssetKind assetKind, String unit) {
        if (assetKind == AssetKind.CONSUMABLE) {
            if (unit == null || unit.isBlank()) {
                throw new BusinessException("Tài sản tiêu hao bắt buộc phải có đơn vị tính (BR-ASSET-08).");
            }
            return unit.trim();
        }
        return null;
    }

    /**
     * BR-ORG-13: tên unique trong phạm vi Tenant. Tính CẢ danh mục đang ẩn, vì ràng
     * buộc uk_asset_categories_tenant_name ở DB không quan tâm cờ is_active.
     */
    private void assertNameAvailable(UUID tenantId, String name, UUID idToExclude) {
        boolean duplicated = idToExclude == null
            ? categoryRepository.existsByTenantIdAndName(tenantId, name)
            : categoryRepository.existsByTenantIdAndNameAndIdNot(tenantId, name, idToExclude);

        if (duplicated) {
            throw new BusinessException(
                "Tên danh mục đã tồn tại trong hệ thống của bạn: " + name
                    + ". Lưu ý tên của danh mục đang ẩn vẫn chiếm chỗ.");
        }
    }
}
