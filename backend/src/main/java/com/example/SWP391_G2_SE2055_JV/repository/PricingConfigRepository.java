package com.example.SWP391_G2_SE2055_JV.repository;

import com.example.SWP391_G2_SE2055_JV.entity.PricingConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Bảng đơn giá chung — BR-SAAS-02.
 *
 * <p>Mỗi dòng có một {@code effective_from} (unique). Bảng giá đang áp dụng tại một ngày
 * là dòng có {@code effective_from} lớn nhất mà không vượt quá ngày đó.
 */
@Repository
public interface PricingConfigRepository extends JpaRepository<PricingConfig, UUID> {

    /**
     * Bảng giá đang hiệu lực tại {@code date}. Spring sinh:
     * {@code WHERE effective_from <= ? ORDER BY effective_from DESC LIMIT 1}.
     */
    Optional<PricingConfig> findFirstByEffectiveFromLessThanEqualOrderByEffectiveFromDesc(LocalDate date);

    /** Kiểm tra trước khi thêm để trả thông báo rõ ràng, thay vì đợi DB báo lỗi UNIQUE. */
    boolean existsByEffectiveFrom(LocalDate effectiveFrom);

    /** Lịch sử bảng giá, mới nhất trước. */
    List<PricingConfig> findAllByOrderByEffectiveFromDesc();
}
