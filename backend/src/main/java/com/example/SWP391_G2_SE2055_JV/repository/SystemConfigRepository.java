package com.example.SWP391_G2_SE2055_JV.repository;

import com.example.SWP391_G2_SE2055_JV.entity.SystemConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Cấu hình cấp hệ thống — BR-SAAS-08, BR-SAAS-10.
 *
 * <p>Bảng {@code system_config} chỉ có ĐÚNG 1 bản ghi (seed trong V1__init_schema.sql),
 * nên không có tra cứu theo id hay theo tenant.
 *
 * <p><b>Spring Data derived query:</b> Spring đọc tên method và tự sinh SQL, không cần
 * viết {@code @Query}. Tên dưới đây được tách thành: {@code findFirst} (lấy 1 dòng, thêm
 * {@code LIMIT 1}) + {@code By} + {@code OrderByCreatedAtAsc} (sắp xếp tăng dần theo cột
 * created_at). Tức là lấy dòng được tạo sớm nhất. Cách này không phụ thuộc vào UUID seed
 * cố định {@code 00000000-...-0001}, nên vẫn chạy đúng nếu ai đó dựng lại dữ liệu.
 */
@Repository
public interface SystemConfigRepository extends JpaRepository<SystemConfig, UUID> {

    Optional<SystemConfig> findFirstByOrderByCreatedAtAsc();
}
