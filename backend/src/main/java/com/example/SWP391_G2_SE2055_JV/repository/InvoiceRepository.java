package com.example.SWP391_G2_SE2055_JV.repository;

import com.example.SWP391_G2_SE2055_JV.entity.Invoice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/** Hóa đơn nội bộ của Tenant — BR-SAAS-15. */
@Repository
public interface InvoiceRepository extends JpaRepository<Invoice, UUID> {

    /** Lịch sử hóa đơn của một Tenant, mới nhất trước. */
    List<Invoice> findByTenantIdOrderByIssuedAtDesc(UUID tenantId);
}
