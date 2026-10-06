package com.example.SWP391_G2_SE2055_JV.repository;

import com.example.SWP391_G2_SE2055_JV.entity.ShiftTemplate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ShiftTemplateRepository extends JpaRepository<ShiftTemplate, UUID> {

    /** Template là danh mục cấp Tenant — không được tham chiếu template của Tenant khác. */
    Optional<ShiftTemplate> findByIdAndTenantId(UUID id, UUID tenantId);

    /**
     * Thứ tự sắp xếp do {@code Pageable} quyết định nên KHÔNG đặt {@code OrderBy} vào tên
     * method — hai nguồn sắp xếp gặp nhau sẽ khiến tham số {@code sort} của client bị bỏ qua.
     */
    Page<ShiftTemplate> findByTenantId(UUID tenantId, Pageable pageable);

    /**
     * BR-SCH-22: mẫu đã vô hiệu hóa chỉ còn để tra ca lịch sử, không hiện ở màn hình xếp ca.
     *
     * <p>Tên thuộc tính là {@code active} (field), không phải {@code isActive} (getter Lombok).
     */
    Page<ShiftTemplate> findByTenantIdAndActiveTrue(UUID tenantId, Pageable pageable);

    /**
     * Một BỘ mẫu: {@code locationId = null} là bộ mẫu chung, có giá trị là bộ mẫu riêng của chi
     * nhánh đó (V6 — mỗi chi nhánh dùng đúng một bộ).
     */
    @Query("""
        select t from ShiftTemplate t
        where t.tenantId = :tenantId
          and ((:locationId is null and t.locationId is null) or t.locationId = :locationId)
          and (:includeInactive = true or t.active = true)
        """)
    Page<ShiftTemplate> findInSet(@Param("tenantId") UUID tenantId,
                                  @Param("locationId") UUID locationId,
                                  @Param("includeInactive") boolean includeInactive,
                                  Pageable pageable);

    /**
     * Tên đã có trong CÙNG bộ chưa — khớp unique {@code (tenant_id, scope_key, name)} của V5.
     * {@code excludeId} để đổi tên không tự đụng chính mình (null khi tạo mới).
     */
    @Query("""
        select case when count(t) > 0 then true else false end from ShiftTemplate t
        where t.tenantId = :tenantId and t.name = :name
          and ((:locationId is null and t.locationId is null) or t.locationId = :locationId)
          and (:excludeId is null or t.id <> :excludeId)
        """)
    boolean existsNameInSet(@Param("tenantId") UUID tenantId, @Param("locationId") UUID locationId,
                            @Param("name") String name, @Param("excludeId") UUID excludeId);

    /** Mẫu chung đang dùng — nguồn để sao chép sang bộ riêng của một chi nhánh. */
    List<ShiftTemplate> findByTenantIdAndLocationIdIsNullAndActiveTrueOrderByStartTimeAsc(UUID tenantId);

    /** Số mẫu riêng đang dùng của một chi nhánh — bộ rỗng thì không bật được. */
    long countByTenantIdAndLocationIdAndActiveTrue(UUID tenantId, UUID locationId);
}
