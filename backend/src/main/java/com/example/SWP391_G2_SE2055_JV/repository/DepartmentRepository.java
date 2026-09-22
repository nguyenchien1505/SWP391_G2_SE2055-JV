package com.example.SWP391_G2_SE2055_JV.repository;

import com.example.SWP391_G2_SE2055_JV.entity.Department;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Phòng ban — danh mục cấp TENANT (BR-ORG-06), nên mọi truy vấn đều lọc theo tenantId.
 */
@Repository
public interface DepartmentRepository extends JpaRepository<Department, UUID> {

    /**
     * Bản không phân trang, dùng để dựng bản đồ id → tên phòng ban khi trả Position
     * (BR-ORG-07). Danh mục cấp Tenant nên tập này nhỏ.
     */
    List<Department> findByTenantIdOrderByNameAsc(UUID tenantId);

    /**
     * Thứ tự sắp xếp do {@code Pageable} quyết định nên KHÔNG đặt {@code OrderBy} vào tên
     * method — hai nguồn sắp xếp gặp nhau sẽ khiến tham số {@code sort} của client bị bỏ qua.
     */
    Page<Department> findByTenantId(UUID tenantId, Pageable pageable);

    /**
     * BR-ORG-14: danh sách chọn chỉ lấy mục đang hiện.
     *
     * <p>Tên thuộc tính là {@code active} (field), KHÔNG phải {@code isActive} (tên getter
     * Lombok sinh ra) — viết {@code IsActiveTrue} thì Spring Data không tạo được query
     * và app dừng ngay lúc khởi động.
     */
    Page<Department> findByTenantIdAndActiveTrue(UUID tenantId, Pageable pageable);

    Optional<Department> findByIdAndTenantId(UUID id, UUID tenantId);

    /** BR-ORG-13: tên Department unique trong phạm vi Tenant. */
    boolean existsByTenantIdAndName(UUID tenantId, String name);

    /** Dùng khi đổi tên: bỏ qua chính bản ghi đang sửa. */
    boolean existsByTenantIdAndNameAndIdNot(UUID tenantId, String name, UUID id);
}
