# Script sinh dữ liệu demo

Script `generate-demo-seed.js` sinh ra 6 file `src/main/resources/db/seed/R__seed_demo_0X_*.sql`:

| File | Nội dung |
|---|---|
| 01_platform_billing | 6 tenant (chuỗi chính Sen Việt + 5 tenant phụ), gói dịch vụ, hóa đơn đã thanh toán, 1 mốc giá cũ |
| 02_organization | khách sạn, phòng ban, vị trí, loại phòng, khu vực, mẫu ca, tài khoản, quyền, mạng chấm công |
| 03_rooms_assets | phòng, tài sản cố định, vật tư, báo hỏng |
| 04_schedule_attendance | ca làm từ d-28 đến d+7, lượt chấm công, đơn điều chỉnh công, đổi ca |
| 05_leave | đơn nghỉ, 1 ảnh đính kèm |
| 06_housekeeping | việc dọn, người dọn, kết quả kiểm tra, lượt trả việc, lịch sử trạng thái phòng |

Đầu mỗi file có bảng tóm tắt các tình huống. Ví dụ: ai đổi ca với ai, phòng nào đang ở trạng thái gì.

## Chạy

```
node backend/scripts/demo-seed/generate-demo-seed.js
```

Chỉ cần Node 18 trở lên, không phải cài thêm package nào. Chạy lại cho ra đúng cùng nội dung, vì script không dùng số ngẫu nhiên.

**Đừng sửa tay các file SQL.** Hãy sửa script rồi sinh lại.

Sau khi sinh lại, nội dung file đổi nên Flyway sẽ chạy lại các file `R__` đó. Tuy nhiên mọi câu INSERT đều bỏ qua
dòng đã có, nên muốn áp đúng dữ liệu mới thì xóa và tạo lại database.

## Script tự kiểm tra gì

Trước khi ghi file, script kiểm tra các điều sau. Chỉ cần sai một điều là dừng và báo lỗi, không ghi file nào.

- **Policy ca mặc định:**
  - tối đa 8 giờ mỗi ngày;
  - tối đa 48 giờ và ít nhất 1 ngày nghỉ trong mọi khoảng 7 ngày;
  - không quá 6 ngày làm liên tiếp;
  - nghỉ ít nhất 12 giờ giữa hai ca;
  - không có ca nào rơi vào ngày nghỉ phép đã duyệt.
- **Lễ tân:** mỗi ca theo mẫu đã có người phải có ít nhất một lễ tân.
- **Quota:** số khách sạn, nhân viên và phòng không vượt quota của từng tenant.
- **Manager:** mỗi khách sạn tối đa một Manager. Trạng thái hoạt động của khách sạn phải khớp với ngày vào làm của Manager.
- **Phòng:**
  - trạng thái chỉ chuyển theo các bước hợp lệ (như `RoomTransitionPolicy`);
  - lịch sử phòng theo đúng thứ tự thời gian;
  - trạng thái phòng khớp với việc dọn đang mở.
- **Người dọn và người kiểm tra:**
  - người được giao dọn có quyền dọn, có ca, đã vào ca, và không phải lễ tân duy nhất của ca;
  - người tự nhận việc không đang giữ việc khác;
  - người kiểm tra không nằm trong nhóm dọn của phòng đó.
- **Đổi ca đang chờ:**
  - hai người cùng vị trí và cùng quyền;
  - lịch của cả hai sau khi đổi vẫn đúng policy, nên khi demo bấm duyệt sẽ thành công;
  - ca đổi là ca tương lai.
- **Đơn nghỉ:** đủ lý do, phép năm báo trước ít nhất 3 ngày, không ai tự duyệt đơn của mình.
- **Hóa đơn:** không có hóa đơn nào có kỳ bắt đầu sau `current_period_start`. Nếu có, job gia hạn sẽ ghi đè quota.

## Lưu ý khi viết thêm dữ liệu

**Lịch ca:** mỗi người làm cố định một loại ca và nghỉ một ngày mỗi 7 ngày. Vì vậy, đổi hai ca **cùng loại** ở hai ngày
khác nhau luôn vi phạm luật "tối đa 6 ngày liên tiếp".

Cặp đổi ca hợp lệ là trong **cùng một ngày**, giữa:
- một người làm ca chiều và được nghỉ hôm trước;
- một người làm ca sáng và được nghỉ hôm sau.

**Không seed hóa đơn PENDING.** Job đối soát VNPay sẽ xóa các hóa đơn này sau 60 phút.

**Giờ "sáng nay":** mọi sự kiện hôm nay đều xảy ra trước 11:00. Vì vậy, nên tạo lại database sau giờ đó để không có sự kiện nào nằm ở tương lai.
