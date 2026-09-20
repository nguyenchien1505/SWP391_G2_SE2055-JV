package com.example.SWP391_G2_SE2055_JV.repository;

import com.example.SWP391_G2_SE2055_JV.entity.Tenant;
import com.example.SWP391_G2_SE2055_JV.enums.TenantStatus;
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
public interface TenantRepository extends JpaRepository<Tenant, UUID> {

    Optional<Tenant> findByContactEmail(String contactEmail);

    boolean existsByContactEmail(String contactEmail);

    /** Tập ứng viên cho các job BR-SAAS-09 (hết trial) và BR-SAAS-10 (hết ân hạn). */
    List<Tenant> findByStatus(TenantStatus status);

    /**
     * Danh sách Tenant cho Admin Platform, lọc TÙY CHỌN: tham số nào là {@code null} thì bỏ
     * qua điều kiện đó ({@code :status IS NULL OR ...}). Derived query không diễn đạt được
     * kiểu "lọc tùy chọn + tìm ở hai cột" nên dùng {@code @Query} (JPQL).
     *
     * <p>{@code Pageable} khiến Spring sinh thêm câu COUNT để trả tổng số trang, và tự nối
     * ORDER BY theo {@code Sort} của {@code Pageable}.
     *
     * <p>Lưu ý: {@code %} hoặc {@code _} trong {@code keyword} được hiểu là ký tự đại diện
     * của SQL LIKE. Chấp nhận được với công cụ nội bộ của Admin.
     */
    @Query("""
        SELECT t FROM Tenant t
        WHERE (:status IS NULL OR t.status = :status)
          AND (:keyword IS NULL
               OR LOWER(t.name)         LIKE LOWER(CONCAT('%', :keyword, '%'))
               OR LOWER(t.contactEmail) LIKE LOWER(CONCAT('%', :keyword, '%')))
        """)
    Page<Tenant> search(@Param("status") TenantStatus status,
                        @Param("keyword") String keyword,
                        Pageable pageable);
}
