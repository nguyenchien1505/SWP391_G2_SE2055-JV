package com.example.SWP391_G2_SE2055_JV.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Tenant tự đăng ký — BR-SAAS-13: đúng 5 trường.
 *
 * <p>Endpoint công khai nên DTO CỐ Ý chỉ có 5 trường này. Role, trạng thái và quota do
 * server gán cứng; nếu client gửi thêm trường lạ thì Jackson bỏ qua, không thể tự cấp
 * quyền hay quota cho mình.
 *
 * <p>Ràng buộc độ dài khớp cột DB ({@code tenants.name} VARCHAR(255),
 * {@code users.full_name} VARCHAR(255), phone VARCHAR(30)) để vi phạm trả 422 thay vì 500
 * (quy ước ở Guidline.md).
 */
@Data
public class RegisterTenantRequest {

    /** Tên công ty / chuỗi khách sạn. */
    @NotBlank(message = "Tên công ty là bắt buộc")
    @Size(max = 255, message = "Tên công ty tối đa 255 ký tự")
    private String companyName;

    /** Họ tên người đại diện — trở thành họ tên của tài khoản Giám đốc. */
    @NotBlank(message = "Họ tên người đại diện là bắt buộc")
    @Size(max = 255, message = "Họ tên tối đa 255 ký tự")
    private String representativeName;

    /** Vừa là email liên hệ Tenant vừa là username đăng nhập của Giám đốc. */
    @NotBlank(message = "Email là bắt buộc")
    @Email(message = "Email không đúng định dạng")
    @Size(max = 255, message = "Email tối đa 255 ký tự")
    private String email;

    /** 8–15 ký tự gồm chữ số, cho phép dấu + ở đầu. Định dạng do team đề xuất, BR không quy định. */
    @NotBlank(message = "Số điện thoại là bắt buộc")
    @Pattern(regexp = "^\\+?[0-9]{8,15}$", message = "Số điện thoại phải gồm 8–15 chữ số")
    private String phone;

    /**
     * Tối thiểu 8 ký tự (cùng quy tắc {@code ChangePasswordRequest}). Tối đa 72 vì BCrypt
     * chỉ dùng 72 byte đầu, mật khẩu dài hơn sẽ bị cắt âm thầm.
     */
    @NotBlank(message = "Mật khẩu là bắt buộc")
    @Size(min = 8, max = 72, message = "Mật khẩu phải từ 8 đến 72 ký tự")
    private String password;
}
