# SWP391_G2_SE2055-JV

```
backend/    Spring Boot REST API (Java 17, MySQL, Flyway)
frontend/   Giao diện người dùng
docs/       ERD và sơ đồ nghiệp vụ
```

## Chạy backend

Chạy từ trong thư mục `backend/` (file `.env` nằm ở đây). Lần đầu: copy
`backend/.env.example` thành `backend/.env` rồi điền giá trị thật (DB, Google OAuth).
Gửi email mật khẩu tạm là tùy chọn, mặc định tắt — bật bằng `MAIL_ENABLED=true` kèm
Gmail + App Password.

```
cd backend
./mvnw spring-boot:run
```

## Dữ liệu demo

Flyway tự nạp dữ liệu mẫu từ `backend/src/main/resources/db/seed`. Có hai bộ dữ liệu độc lập:

- `R__seed_test_*`: tài khoản cho System Test (`manager@swp391.test`, `letan@swp391.test`…).
- `R__seed_demo_*`: bộ dữ liệu để demo. Gồm chuỗi **Sen Việt** với 3 khách sạn, khoảng 30 nhân viên, lịch ca
  4 tuần, chấm công, đổi ca, đơn nghỉ, dọn phòng, tài sản và hóa đơn. Có thêm 5 tenant phụ ở các trạng thái khác nhau.

Mọi tài khoản đều dùng mật khẩu `Test@123`. Danh sách đầy đủ có ở đầu file `R__seed_demo_02_organization.sql`.

| Tài khoản | Dùng để demo |
|---|---|
| `admin@swp391.test` | Quản trị nền tảng: tenant ở đủ 4 trạng thái, lịch sử bảng giá |
| `director.demo@swp391.test` | Giám đốc: tổng quan 3 khách sạn, 4 hóa đơn, duyệt đơn nghỉ của Manager, tổng kết chấm công |
| `manager.hn@swp391.test` | Manager Hà Nội: lịch ca, duyệt đổi ca / đơn nghỉ / điều chỉnh công, bảng dọn phòng |
| `manager.dn@swp391.test` | Manager Đà Nẵng: bộ mẫu ca riêng 07–15 / 15–23 / 23–07 |
| `letan.hn1@…`, `buongphong.hn2@…`, `giamsat.hn1@…` | Nhân viên: lịch cá nhân, chấm công (mạng `127.0.0.1` khớp IP khách sạn), nhận đổi ca, việc dọn |
| `buongphong.dn5@swp391.test` | Nhân viên mới, bị buộc đổi mật khẩu khi đăng nhập lần đầu |
| `manager.duphong@…`, `manager.hl@…` | Manager dự bị, và khách sạn Hạ Long chưa hoạt động |
| `director.maytrang@…`, `director.doithong@…` | Tenant hết dùng thử (chỉ xem) và tenant bị Admin khóa |

**Ngày tháng tính theo ngày Flyway chạy file seed.** Để dữ liệu khớp đúng hôm nay, vào **sáng ngày demo,
sau 11:00**, xóa database rồi chạy lại backend:

```
DROP DATABASE hotel_workforce; CREATE DATABASE hotel_workforce;
```

Một số dữ liệu tự đổi theo thời gian:
- Yêu cầu đổi ca chờ người nhận hết hạn sau 20 giờ.
- Tenant "Phố Cổ" bị khóa sau 4 ngày.

Muốn sửa nội dung demo thì sửa script `backend/scripts/demo-seed/generate-demo-seed.js`, rồi sinh lại các file SQL
(xem `backend/scripts/demo-seed/README.md`).

Cấu trúc package trong `backend/src/main/java/com/example/SWP391_G2_SE2055_JV`:

```
config/       Security, JPA, Web config, OAuth2 handlers
controller/   REST controllers
service/      Business logic
repository/   Spring Data JPA repositories
entity/       JPA entities
dto/          Request / response objects
enums/        Enum trạng thái và role
exception/    Exception + GlobalExceptionHandler
utils/        Helper dùng chung
```
