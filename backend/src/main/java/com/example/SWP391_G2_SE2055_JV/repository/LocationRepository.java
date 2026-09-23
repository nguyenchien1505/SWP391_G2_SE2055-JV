package com.example.SWP391_G2_SE2055_JV.repository;

import com.example.SWP391_G2_SE2055_JV.entity.Location;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface LocationRepository extends JpaRepository<Location, UUID> {

    /**
     * Chốt chặn cách ly Tenant cho mọi {@code locationId} nhận từ client: khóa ngoại chỉ
     * đảm bảo Location TỒN TẠI, không đảm bảo nó thuộc cùng Tenant với người thao tác.
     */
    Optional<Location> findByIdAndTenantId(UUID id, UUID tenantId);

    /**
     * Thứ tự sắp xếp do {@code Pageable} quyết định nên KHÔNG đặt {@code OrderBy} vào tên
     * method — hai nguồn sắp xếp gặp nhau sẽ khiến tham số {@code sort} của client bị bỏ qua.
     *
     * <p>Màn hình cần đủ danh sách để đổ dropdown thì gọi với {@code size} lớn; số Location
     * vốn bị chặn bởi quota gói dịch vụ (BR-SAAS-02) nên luôn là tập nhỏ.
     */
    Page<Location> findByTenantId(UUID tenantId, Pageable pageable);

    /**
     * BR-SAAS-02: số Location đang chiếm quota. Location xóa là xóa cứng (không có cột xóa
     * mềm) nên đếm toàn bộ bản ghi của Tenant.
     */
    long countByTenantId(UUID tenantId);
}
