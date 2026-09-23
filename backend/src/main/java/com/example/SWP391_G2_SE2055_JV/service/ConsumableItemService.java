package com.example.SWP391_G2_SE2055_JV.service;

import com.example.SWP391_G2_SE2055_JV.dto.asset.AddConsumableItemRequest;
import com.example.SWP391_G2_SE2055_JV.dto.asset.ConsumableItemResponse;
import com.example.SWP391_G2_SE2055_JV.dto.asset.StockCountRequest;
import com.example.SWP391_G2_SE2055_JV.entity.AssetCategory;
import com.example.SWP391_G2_SE2055_JV.entity.ConsumableItem;
import com.example.SWP391_G2_SE2055_JV.enums.Role;
import com.example.SWP391_G2_SE2055_JV.exception.BusinessException;
import com.example.SWP391_G2_SE2055_JV.exception.ResourceNotFoundException;
import com.example.SWP391_G2_SE2055_JV.entity.User;
import com.example.SWP391_G2_SE2055_JV.repository.AssetCategoryRepository;
import com.example.SWP391_G2_SE2055_JV.repository.ConsumableItemRepository;
import com.example.SWP391_G2_SE2055_JV.repository.UserRepository;
import com.example.SWP391_G2_SE2055_JV.utils.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Tồn kho tài sản tiêu hao — BR-ASSET-04, BR-ASSET-07, BR-ASSET-10, DM-11.
 *
 * <p>Tồn kho TĨNH: Manager sửa thẳng số lượng khi kiểm kê, không có nghiệp vụ
 * xuất/nhập theo giao dịch, không lưu lịch sử chênh lệch — chỉ ghi đè và lưu mốc kiểm
 * kê gần nhất. Giám đốc chỉ xem (toàn Tenant), không kiểm kê — BR-ASSET-09 giao việc
 * quản lý từng tài sản/tồn kho cho Manager trong Location của mình.
 *
 * <p>Mỗi Location có tối đa 1 dòng cho mỗi danh mục (unique {@code location_id,
 * category_id} ở DB) — dòng chỉ được tạo khi Manager chủ động thêm danh mục vào kho
 * của mình, không tự sinh khi Giám đốc tạo danh mục mới.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConsumableItemService {

    private final ConsumableItemRepository consumableItemRepository;
    private final AssetCategoryRepository  categoryRepository;
    private final UserRepository           userRepository;

    @Transactional(readOnly = true)
    public Page<ConsumableItemResponse> getConsumables(Boolean outOfStock, Pageable pageable) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        boolean filterOutOfStock = Boolean.TRUE.equals(outOfStock);

        Page<ConsumableItem> page;
        if (isTenantWide()) {
            page = filterOutOfStock
                ? consumableItemRepository.findByTenantIdAndQuantityLessThanEqual(tenantId, BigDecimal.ZERO, pageable)
                : consumableItemRepository.findByTenantId(tenantId, pageable);
        } else {
            UUID locationId = SecurityUtils.getCurrentLocationId();
            page = filterOutOfStock
                ? consumableItemRepository.findByTenantIdAndLocationIdAndQuantityLessThanEqual(
                    tenantId, locationId, BigDecimal.ZERO, pageable)
                : consumableItemRepository.findByTenantIdAndLocationId(tenantId, locationId, pageable);
        }

        return enrichWithCategory(page);
    }

    @Transactional(readOnly = true)
    public ConsumableItemResponse getConsumableById(UUID id) {
        ConsumableItem item = getOwnedItem(id);
        User user = item.getLastCountedBy() != null ? userRepository.findById(item.getLastCountedBy()).orElse(null) : null;
        return ConsumableItemResponse.fromEntity(item, categoryRepository.findById(item.getCategoryId()).orElse(null), user);
    }

    /** Thêm một danh mục tiêu hao vào kho của Location hiện tại (BR-ASSET-04). */
    @Transactional
    public ConsumableItemResponse addConsumableItem(AddConsumableItemRequest request) {
        UUID tenantId   = SecurityUtils.getCurrentTenantId();
        UUID locationId = SecurityUtils.getCurrentLocationId();
        UUID userId     = SecurityUtils.getCurrentUserId();

        AssetCategory category = requireConsumableCategory(request.getCategoryId(), tenantId, true);

        if (consumableItemRepository.existsByLocationIdAndCategoryId(locationId, request.getCategoryId())) {
            throw new BusinessException(
                "Danh mục \"" + category.getName() + "\" đã có trong kho của khách sạn này. "
                    + "Hãy dùng chức năng kiểm kê để sửa số lượng.");
        }

        LocalDateTime now = LocalDateTime.now();
        ConsumableItem item = ConsumableItem.builder()
            .tenantId(tenantId)
            .locationId(locationId)
            .categoryId(request.getCategoryId())
            .quantity(request.getQuantity())
            .lastCountedAt(now)
            .lastCountedBy(userId)
            .build();

        User user = userRepository.findById(userId).orElse(null);
        return ConsumableItemResponse.fromEntity(consumableItemRepository.save(item), category, user);
    }

    /**
     * Kiểm kê theo đợt — BR-ASSET-07: một request ghi đè NHIỀU dòng cùng lúc, cùng một
     * mốc {@code lastCountedAt}/{@code lastCountedBy}. Một dòng sai (không thuộc
     * Location của Manager, id không tồn tại) thì rollback TOÀN BỘ đợt.
     */
    @Transactional
    public List<ConsumableItemResponse> stockCount(StockCountRequest request) {
        UUID tenantId   = SecurityUtils.getCurrentTenantId();
        UUID locationId = SecurityUtils.getCurrentLocationId();
        UUID userId     = SecurityUtils.getCurrentUserId();

        List<StockCountRequest.Line> lines = request.getLines();

        Set<UUID> requestedIds = lines.stream().map(StockCountRequest.Line::getItemId).collect(Collectors.toSet());
        if (requestedIds.size() != lines.size()) {
            throw new BusinessException("Danh sách kiểm kê có ID trùng lặp, mỗi mặt hàng chỉ được xuất hiện 1 lần.");
        }

        // Nạp một lần theo Location, tránh N+1 khi kiểm kê nhiều dòng cùng lúc.
        List<ConsumableItem> items = consumableItemRepository.findByLocationIdAndIdIn(locationId, requestedIds);
        Map<UUID, ConsumableItem> byId = items.stream()
            .collect(Collectors.toMap(ConsumableItem::getId, i -> i));

        if (byId.size() != requestedIds.size()) {
            Set<UUID> missing = new HashSet<>(requestedIds);
            missing.removeAll(byId.keySet());
            throw new BusinessException(
                "Không tìm thấy hoặc không thuộc khách sạn này " + missing.size()
                    + " dòng tồn kho: " + missing);
        }

        // Không dùng tenantId trực tiếp trong câu query trên (lọc theo locationId là đủ
        // vì locationId đã ràng buộc 1-1 với tenantId), nhưng vẫn kiểm tra lại tường
        // minh để không phụ thuộc ngầm vào việc đó.
        for (ConsumableItem item : items) {
            if (!tenantId.equals(item.getTenantId())) {
                throw new BusinessException("Dòng tồn kho không thuộc Tenant hiện tại.");
            }
        }

        LocalDateTime now = LocalDateTime.now();
        for (StockCountRequest.Line line : lines) {
            ConsumableItem item = byId.get(line.getItemId());
            item.setQuantity(line.getQuantity());
            item.setLastCountedAt(now);
            item.setLastCountedBy(userId);
        }

        List<ConsumableItem> saved = consumableItemRepository.saveAll(items);
        log.info("Kiểm kê {} dòng tồn kho tại location {} bởi {}", saved.size(), locationId, userId);

        return enrichWithCategory(saved);
    }

    /** Chỉ xóa được khi tồn kho hiện tại bằng 0 — xóa là mất dấu vĩnh viễn (BR-ASSET-07). */
    @Transactional
    public void deleteConsumableItem(UUID id) {
        ConsumableItem item = getOwnedItem(id);

        if (item.getQuantity() != null && item.getQuantity().signum() > 0) {
            throw new BusinessException(
                "Còn " + item.getQuantity() + " trong kho. "
                    + "Kiểm kê về 0 trước khi bỏ mặt hàng này khỏi kho.");
        }

        consumableItemRepository.delete(item);
        log.info("Đã xóa dòng tồn kho {} khỏi kho", id);
    }

    // ── Helper ───────────────────────────────────────────────────────────────

    /** Giám đốc đứng trên nhiều Location nên không có {@code locationId} để lọc. */
    private boolean isTenantWide() {
        return SecurityUtils.hasRole(Role.DIRECTOR);
    }

    /** Ngoài phạm vi thì trả 404 chứ không 403, để không lộ việc bản ghi có tồn tại. */
    private ConsumableItem getOwnedItem(UUID id) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        return (isTenantWide()
            ? consumableItemRepository.findByIdAndTenantId(id, tenantId)
            : consumableItemRepository.findByIdAndTenantIdAndLocationId(
                id, tenantId, SecurityUtils.getCurrentLocationId()))
            .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy dòng tồn kho"));
    }

    /**
     * Danh mục phải thuộc Tenant hiện tại và phải là loại CONSUMABLE — hai loại tài
     * sản dùng CHUNG một bảng danh mục nên rất dễ chọn nhầm (BR-ASSET-08).
     */
    private AssetCategory requireConsumableCategory(UUID categoryId, UUID tenantId, boolean requireActive) {
        AssetCategory category = categoryRepository.findByIdAndTenantId(categoryId, tenantId)
            .orElseThrow(() -> new BusinessException("Không tìm thấy danh mục tài sản với id: " + categoryId));

        if (!category.isConsumable()) {
            throw new BusinessException(
                "Danh mục \"" + category.getName() + "\" là loại cố định, không dùng cho tài sản tiêu hao.");
        }
        if (requireActive && !category.isActive()) {
            throw new BusinessException(
                "Danh mục \"" + category.getName() + "\" đang bị ẩn, không chọn được để thêm vào kho.");
        }
        return category;
    }

    /** Nạp danh mục theo lô 1 lần cho cả trang/danh sách, tránh N+1. */
    private Page<ConsumableItemResponse> enrichWithCategory(Page<ConsumableItem> page) {
        Map<UUID, AssetCategory> categoriesById = loadCategories(page.getContent());
        Map<UUID, User> usersById = loadUsers(page.getContent());
        return page.map(item -> ConsumableItemResponse.fromEntity(item, categoriesById.get(item.getCategoryId()), usersById.get(item.getLastCountedBy())));
    }

    private List<ConsumableItemResponse> enrichWithCategory(List<ConsumableItem> items) {
        Map<UUID, AssetCategory> categoriesById = loadCategories(items);
        Map<UUID, User> usersById = loadUsers(items);
        return items.stream()
            .map(item -> ConsumableItemResponse.fromEntity(item, categoriesById.get(item.getCategoryId()), usersById.get(item.getLastCountedBy())))
            .toList();
    }

    private Map<UUID, AssetCategory> loadCategories(List<ConsumableItem> items) {
        Set<UUID> categoryIds = items.stream().map(ConsumableItem::getCategoryId).collect(Collectors.toSet());
        Map<UUID, AssetCategory> result = new HashMap<>();
        categoryRepository.findAllById(categoryIds).forEach(c -> result.put(c.getId(), c));
        return result;
    }

    private Map<UUID, User> loadUsers(List<ConsumableItem> items) {
        Set<UUID> userIds = items.stream()
            .map(ConsumableItem::getLastCountedBy)
            .filter(id -> id != null)
            .collect(Collectors.toSet());
        Map<UUID, User> result = new HashMap<>();
        if (!userIds.isEmpty()) {
            userRepository.findAllById(userIds).forEach(u -> result.put(u.getId(), u));
        }
        return result;
    }
}
