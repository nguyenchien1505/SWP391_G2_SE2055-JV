package com.example.SWP391_G2_SE2055_JV.controller;

import com.example.SWP391_G2_SE2055_JV.dto.asset.DamageReportResponse;
import com.example.SWP391_G2_SE2055_JV.dto.asset.ResolveDamageReportRequest;
import com.example.SWP391_G2_SE2055_JV.enums.DamageReportStatus;
import com.example.SWP391_G2_SE2055_JV.enums.FixedAssetStatus;
import com.example.SWP391_G2_SE2055_JV.service.DamageReportService;
import com.example.SWP391_G2_SE2055_JV.support.SecuredWebMvcTest;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phân quyền báo hỏng — BR-ASSET-05 (chỉ Lễ tân/Dọn dẹp tạo), BR-ASSET-06 (chỉ Manager đóng
 * phiếu). Nạp SecurityConfig thật ({@link SecuredWebMvcTest}); {@link DamageReportService} được
 * mock — phạm vi dữ liệu đã test ở {@code DamageReportServiceTest}.
 */
@SecuredWebMvcTest(DamageReportController.class)
class DamageReportControllerTest {

    private static final UUID REPORT_ID = UUID.randomUUID();
    private static final UUID ASSET_ID  = UUID.randomUUID();

    @Autowired MockMvc mockMvc;

    @MockBean DamageReportService damageReportService;

    @Nested
    class CreateReport {

        private final String body = """
            {"fixedAssetId": "%s", "description": "Vòi sen rỉ nước"}
            """.formatted(ASSET_ID);

        @ParameterizedTest
        @ValueSource(strings = {"ROLE_STAFF,POSITION_RECEPTION", "ROLE_STAFF,POSITION_HOUSEKEEPING"})
        void leTanVaDonDepTaoDuoc(String authorities) throws Exception {
            when(damageReportService.createDamageReport(any())).thenReturn(sample());

            mockMvc.perform(post("/assets/damage-reports").with(authorities(authorities))
                    .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
        }

        /** Manager là người nhận báo hỏng; nhân viên không tick quyền nào chỉ có quyền chung. */
        @ParameterizedTest
        @ValueSource(strings = {"ROLE_MANAGER", "ROLE_DIRECTOR", "ROLE_STAFF", "ROLE_PLATFORM_ADMIN"})
        void vaiTroKhacBiChan(String authorities) throws Exception {
            mockMvc.perform(post("/assets/damage-reports").with(authorities(authorities))
                    .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
            verifyNoInteractions(damageReportService);
        }

        @Test
        void thieuMoTaThiBaoLoiValidate() throws Exception {
            mockMvc.perform(post("/assets/damage-reports").with(authorities("ROLE_STAFF,POSITION_RECEPTION"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"fixedAssetId\": \"%s\", \"description\": \"  \"}".formatted(ASSET_ID)))
                .andExpect(status().isUnprocessableEntity());
            verifyNoInteractions(damageReportService);
        }
    }

    @Nested
    class ListReports {

        @ParameterizedTest
        @ValueSource(strings = {"ROLE_DIRECTOR", "ROLE_MANAGER", "ROLE_STAFF"})
        void caBaVaiTroDeuXemDuoc(String authorities) throws Exception {
            when(damageReportService.getDamageReports(any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(sample()), PageRequest.of(0, 20), 1));

            mockMvc.perform(get("/assets/damage-reports").with(authorities(authorities)))
                .andExpect(status().isOk());
        }

        /** DM-16: không truyền status = chỉ phiếu NEW; status rỗng = bỏ lọc. */
        @Test
        void khongTruyenStatusThiMacDinhNewConRongThiBoLoc() throws Exception {
            when(damageReportService.getDamageReports(any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

            mockMvc.perform(get("/assets/damage-reports").with(authorities("ROLE_MANAGER")))
                .andExpect(status().isOk());
            verify(damageReportService).getDamageReports(eq(DamageReportStatus.NEW), isNull(), any());

            mockMvc.perform(get("/assets/damage-reports?status=&fixedAssetId=" + ASSET_ID)
                    .with(authorities("ROLE_MANAGER")))
                .andExpect(status().isOk());
            verify(damageReportService).getDamageReports(isNull(), eq(ASSET_ID), any());
        }
    }

    @Nested
    class ResolveReport {

        @Test
        void managerDongPhieuKemTrangThaiVaGhiChu() throws Exception {
            when(damageReportService.resolveDamageReport(eq(REPORT_ID), any())).thenReturn(sample());

            mockMvc.perform(patch("/assets/damage-reports/{id}/resolve", REPORT_ID)
                    .with(authorities("ROLE_MANAGER"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"newAssetStatus\": \"UNDER_REPAIR\", \"resolutionNote\": \"Đã gọi thợ\"}"))
                .andExpect(status().isOk());

            ArgumentCaptor<ResolveDamageReportRequest> captor =
                ArgumentCaptor.forClass(ResolveDamageReportRequest.class);
            verify(damageReportService).resolveDamageReport(eq(REPORT_ID), captor.capture());
            assertThat(captor.getValue().getNewAssetStatus()).isEqualTo(FixedAssetStatus.UNDER_REPAIR);
            assertThat(captor.getValue().getResolutionNote()).isEqualTo("Đã gọi thợ");
        }

        /** Body không bắt buộc — đóng phiếu, giữ nguyên tài sản. */
        @Test
        void managerDongPhieuKhongCanBody() throws Exception {
            when(damageReportService.resolveDamageReport(eq(REPORT_ID), isNull())).thenReturn(sample());

            mockMvc.perform(patch("/assets/damage-reports/{id}/resolve", REPORT_ID)
                    .with(authorities("ROLE_MANAGER")))
                .andExpect(status().isOk());
        }

        @Test
        void ghiChuQuaDaiThiBaoLoiValidate() throws Exception {
            mockMvc.perform(patch("/assets/damage-reports/{id}/resolve", REPORT_ID)
                    .with(authorities("ROLE_MANAGER"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"resolutionNote\": \"%s\"}".formatted("x".repeat(501))))
                .andExpect(status().isUnprocessableEntity());
            verifyNoInteractions(damageReportService);
        }

        /** Giám đốc chỉ xem; nhân viên chỉ báo (BR-ASSET-06). */
        @ParameterizedTest
        @ValueSource(strings = {"ROLE_DIRECTOR", "ROLE_STAFF,POSITION_RECEPTION", "ROLE_STAFF,POSITION_HOUSEKEEPING"})
        void vaiTroKhacManagerBiChan(String authorities) throws Exception {
            mockMvc.perform(patch("/assets/damage-reports/{id}/resolve", REPORT_ID)
                    .with(authorities(authorities)))
                .andExpect(status().isForbidden());
            verifyNoInteractions(damageReportService);
        }
    }

    // ── Helper ───────────────────────────────────────────────────────────────

    private static DamageReportResponse sample() {
        return DamageReportResponse.builder()
            .id(REPORT_ID).fixedAssetId(ASSET_ID).assetCode("TS-00001").description("Vòi sen rỉ nước")
            .status(DamageReportStatus.NEW)
            .build();
    }

    private static RequestPostProcessor authorities(String commaSeparated) {
        return user("someone@test.local").authorities(
            Arrays.stream(commaSeparated.split(","))
                .map(SimpleGrantedAuthority::new)
                .toList());
    }
}
