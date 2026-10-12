package com.example.SWP391_G2_SE2055_JV.repository;

import com.example.SWP391_G2_SE2055_JV.entity.Invoice;
import com.example.SWP391_G2_SE2055_JV.enums.InvoiceStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Hóa đơn nội bộ của Tenant — BR-SAAS-15.
 *
 * <p>Phân biệt hóa đơn PERIODIC: MUA LẦN ĐẦU chưa có kỳ ({@code period_start} = NULL) cho tới khi
 * thanh toán thành công; GIA HẠN có kỳ ngay từ lúc tạo (bắt đầu từ ngày đến hạn của gói).
 */
@Repository
public interface InvoiceRepository extends JpaRepository<Invoice, UUID> {

    /** Lịch sử hóa đơn theo trạng thái, mới nhất trước — màn Giám đốc chỉ xem hóa đơn đã thanh toán. */
    List<Invoice> findByTenantIdAndStatusOrderByIssuedAtDesc(UUID tenantId, InvoiceStatus status);

    /** Giao dịch đang dở của một Tenant (mọi loại hóa đơn) — kiểm tra trước khi mở giao dịch mới. */
    List<Invoice> findByTenantIdAndStatus(UUID tenantId, InvoiceStatus status);

    /** Hóa đơn còn chờ, phát hành trước mốc {@code before} — đầu vào của job đối soát. */
    List<Invoice> findByStatusAndIssuedAtBefore(InvoiceStatus status, LocalDateTime before);
}
