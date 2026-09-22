# DESIGN.md — Hệ thống quản lý vận hành khách sạn (SaaS đa tenant)

> Tài liệu mô tả giao diện và cảm nhận mong muốn cho sản phẩm.
> Phạm vi: Milestone 1. Nguồn nghiệp vụ: PROJECT_DECISIONS.md (134 business rule đã chốt).
> Toàn bộ giao diện dùng **tiếng Việt**.

---

## 1. Tổng quan sản phẩm

Nền tảng SaaS giúp các chuỗi khách sạn 2–3 sao quản lý vận hành hằng ngày: phòng, lịch làm việc nhân viên, công việc dọn phòng và tài sản vật chất.

Một tài khoản khách hàng (Tenant) có thể quản lý nhiều khách sạn (Location). Mỗi khách sạn có một quản lý (Manager) và các nhân viên: lễ tân, dọn dẹp, và các vị trí khác.

**Đặc điểm quyết định hình hài giao diện:**

- Đây là **công cụ làm việc**, không phải sản phẩm giải trí. Người dùng mở nó nhiều lần mỗi ngày, thao tác nhanh, cần nhìn là hiểu.
- Người dùng **không rành công nghệ**: nhân viên dọn phòng, lễ tân ca đêm. Giao diện phải đơn giản, chữ đủ lớn, nút bấm đủ to.
- Trạng thái là trung tâm: phòng có 6 trạng thái, công việc dọn có 5 trạng thái. Người dùng ra quyết định dựa trên việc **nhìn thấy trạng thái ngay lập tức**.
- Nhiều thao tác bị **chặn cứng** theo quy định (ví dụ xếp ca vượt giờ quy định). Giao diện phải giải thích rõ vì sao bị chặn, không chỉ báo lỗi chung chung.

---

## 2. Người dùng và ngữ cảnh sử dụng

| Vai trò | Thiết bị chính | Ngữ cảnh | Nhu cầu giao diện |
|---|---|---|---|
| **Admin Platform** | Máy tính | Văn phòng, quản trị nền tảng | Bảng danh sách, cấu hình, ít đồ họa |
| **Giám đốc** | Máy tính, đôi khi máy tính bảng | Xem tổng quan toàn chuỗi | Dashboard số liệu, so sánh giữa các khách sạn |
| **Manager** | Máy tính, máy tính bảng | Tại quầy, di chuyển trong khách sạn | Màn hình vận hành dày đặc thông tin, thao tác nhanh |
| **Lễ tân** | Máy tính tại quầy, điện thoại | Đứng quầy, vừa tiếp khách vừa thao tác | Cực nhanh: đổi trạng thái phòng chỉ 1–2 chạm |
| **Nhân viên dọn dẹp** | **Điện thoại** | Di chuyển giữa các tầng, tay bận | Ưu tiên tuyệt đối cho mobile: danh sách phòng cần dọn, nút hoàn thành to |

**Yêu cầu bắt buộc:** module Quản lý phòng phải tối ưu responsive đầy đủ (quy định nghiệp vụ BR-ROOM-06). Các màn hình của Lễ tân và Dọn dẹp nên thiết kế theo hướng mobile-first.

---

## 3. Phong cách hình ảnh mong muốn

**Cảm nhận chung:** sạch, sáng, chuyên nghiệp, tin cậy. Gợi liên tưởng đến ngành khách sạn (ấm áp, hiếu khách) nhưng vẫn là phần mềm nghiệp vụ nghiêm túc — không màu mè, không hiệu ứng thừa.

**Tham chiếu tinh thần:** gần với các phần mềm quản trị hiện đại (Linear, Notion) hơn là bảng điều khiển doanh nghiệp cũ kỹ. Nhiều khoảng trắng, bo góc nhẹ, đổ bóng tiết chế.

### Bảng màu đề xuất

| Vai trò màu | Mã màu | Dùng cho |
|---|---|---|
| Chính (Primary) | `#1F4E78` | Thanh điều hướng, nút chính, tiêu đề |
| Nhấn (Accent) | `#2E74B5` | Liên kết, trạng thái đang chọn |
| Nền trang | `#F7F8FA` | Nền chung |
| Nền thẻ | `#FFFFFF` | Thẻ, bảng, hộp thoại |
| Viền | `#DFE3E8` | Đường kẻ, viền ô nhập |
| Chữ chính | `#1C2330` | |
| Chữ phụ | `#5B6472` | Mô tả, chú thích |

### Màu trạng thái phòng (rất quan trọng — dùng nhất quán toàn hệ thống)

| Trạng thái | Màu | Mã màu | Ghi chú |
|---|---|---|---|
| Trống / Sẵn sàng | Xanh lá | `#2E7D32` | Phòng sạch, bán được |
| Đang sử dụng | Xanh dương | `#1565C0` | Có khách |
| Chờ dọn | Cam | `#EF6C00` | Cần xử lý — nổi bật |
| Đang dọn | Vàng | `#F9A825` | Đang có người làm |
| Chờ kiểm tra | Tím | `#6A1B9A` | Chờ Manager duyệt |
| Không khả dụng | Xám đậm | `#616161` | Bảo trì hoặc khóa phòng |

Trạng thái hiển thị dạng **huy hiệu (badge) có nền màu nhạt và chữ màu đậm**, không chỉ dùng chấm tròn màu — vì có người dùng khó phân biệt màu, cần kèm chữ.

### Chữ

- Font: **Be Vietnam Pro** hoặc **Inter**. Bắt buộc hỗ trợ đầy đủ dấu tiếng Việt (ặ, ữ, ỡ, ể…). Kiểm tra kỹ phần dấu không bị cắt hoặc chồng.
- Cỡ chữ cơ sở: 15–16px trên máy tính, không nhỏ hơn 16px trên điện thoại.
- Với màn hình của nhân viên dọn dẹp: cỡ chữ lớn hơn một bậc, vùng bấm tối thiểu 48×48px.

### Khoảng cách và bo góc

- Đơn vị khoảng cách: bội số của 4px.
- Bo góc: 8px cho thẻ và hộp thoại, 6px cho nút và ô nhập.
- Đổ bóng: rất nhẹ, chỉ dùng cho thẻ nổi và menu thả xuống.

---

## 4. Danh sách màn hình cần thiết kế

### 4.1. Chung (không đăng nhập)
1. **Đăng nhập** — 1 ô email + mật khẩu. Không có chọn khách sạn/công ty (email là duy nhất toàn hệ thống).
2. **Đổi mật khẩu lần đầu** — bắt buộc với tài khoản mới được cấp mật khẩu tạm.
3. **Đăng ký Tenant** — 5 trường: tên công ty/chuỗi khách sạn, họ tên người đại diện, email, số điện thoại, mật khẩu.
4. **Chọn gói dịch vụ** — tự tùy chỉnh số lượng: số khách sạn, số nhân viên, số phòng. Giá hiện lên theo thời gian thực khi kéo/nhập số lượng. Hiển thị rõ "dùng thử miễn phí 1 tháng".
5. **Trang thanh toán** — giả lập, có nút "Thanh toán thành công" và "Giả lập thất bại" để thử nghiệm.
6. **Màn hình chặn** — khi tài khoản bị tạm ngưng: thông báo rõ lý do (hết hạn dùng thử / chưa thanh toán / bị khóa) và cách liên hệ.

### 4.2. Admin Platform
7. **Danh sách Tenant** — bảng: tên, trạng thái, gói hiện tại, ngày đến hạn. Lọc theo trạng thái.
8. **Chi tiết Tenant** — thông tin, lịch sử hóa đơn, nút mở khóa tài khoản bị tạm ngưng.
9. **Cấu hình hệ thống** — đơn giá theo khách sạn/nhân viên/phòng, số ngày dùng thử, số ngày gia hạn.

### 4.3. Giám đốc (cấp toàn chuỗi)
10. **Dashboard tổng quan** — số khách sạn theo trạng thái, tổng nhân viên, tổng phòng theo từng trạng thái, bảng so sánh nhanh giữa các khách sạn.
11. **Danh sách khách sạn** + form thêm/sửa khách sạn.
12. **Danh sách quản lý (Manager)** + form tạo, kèm màn hình hiển thị mật khẩu tạm (chỉ hiện đúng 1 lần, có nút sao chép).
13. **Quản lý danh mục** — phòng ban, vị trí công việc, loại phòng, danh mục tài sản. Dạng bảng có công tắc bật/tắt.
14. **Quy định xếp ca** — form cấu hình 6 tham số: giờ tối đa/ngày, giờ tối đa/tuần, số ca liên tiếp tối đa, giờ nghỉ tối thiểu giữa 2 ca, ngày nghỉ tối thiểu/tuần, thời gian chờ phản hồi đổi ca.
15. **Mẫu ca làm việc** — danh sách ca sáng/chiều/đêm, thêm sửa.
16. **Duyệt điều chuyển nhân sự** — danh sách yêu cầu chờ duyệt.
17. **Quản lý gói dịch vụ** — xem gói hiện tại, tăng/giảm số lượng.

### 4.4. Manager (cấp một khách sạn)
18. **Dashboard vận hành** — số phòng theo từng trạng thái (dạng thẻ màu, bấm vào lọc được), số nhân viên có ca hôm nay, số việc dọn theo trạng thái, số yêu cầu chờ duyệt.
19. **Danh sách phòng** ⭐ *(bắt buộc responsive hoàn chỉnh)* — dạng lưới thẻ trên điện thoại, dạng bảng trên máy tính. Mỗi phòng: số phòng, tầng, loại phòng, huy hiệu trạng thái. Lọc theo trạng thái và tầng.
20. **Chi tiết phòng** — thông tin, trạng thái hiện tại, lịch sử đổi trạng thái, danh sách tài sản trong phòng, nút đổi trạng thái.
21. **Bảng xếp lịch làm việc** ⭐ *(màn hình phức tạp nhất)* — dạng lưới: hàng là nhân viên, cột là 7 ngày trong tuần. Ô trống bấm để thêm ca. Ca chưa có người phân công hiển thị khác biệt rõ (viền đứt nét, nền nhạt).
22. **Hộp thoại thêm ca** — chọn mẫu ca có sẵn hoặc nhập giờ tự do. Nếu vi phạm quy định thì hiện thông báo chặn, nêu rõ vi phạm điều nào.
23. **Danh sách việc dọn phòng** — nhóm theo: chưa phân công / đang làm / chờ kiểm tra. Kéo thả hoặc bấm để gán người.
24. **Màn hình kiểm tra phòng** — sau khi nhân viên báo dọn xong: nút Đạt / Không đạt. Chọn Không đạt thì bắt buộc nhập lý do.
25. **Duyệt đơn nghỉ và đổi ca** — danh sách chờ duyệt, xem chi tiết, duyệt hoặc từ chối.
26. **Danh sách nhân viên** + form tạo nhân viên (10 trường, có tải ảnh đại diện).
27. **Quản lý tài sản** — danh sách tài sản cố định (mã, tên, vị trí phòng/khu vực, trạng thái), danh sách vật tư tiêu hao (tên, số lượng tồn, lần kiểm kê gần nhất).
28. **Danh sách báo hỏng** — nhóm Mới / Đã xử lý.
29. **Quản lý khu vực** — sảnh, hành lang, kho…

### 4.5. Lễ tân
30. **Sơ đồ phòng** ⭐ — màn hình chính, xem toàn bộ phòng dưới dạng lưới thẻ màu theo trạng thái. Chạm vào phòng để đổi trạng thái khi khách nhận phòng hoặc trả phòng. Tối ưu cho thao tác nhanh.
31. **Lịch làm việc cá nhân** — xem ca của mình, nút chấm công vào/ra ca.
32. **Gửi đơn nghỉ / đổi ca**.
33. **Báo hỏng tài sản** — chọn phòng, chọn tài sản, mô tả.

### 4.6. Nhân viên dọn dẹp (ưu tiên điện thoại)
34. **Danh sách phòng cần dọn hôm nay** ⭐ — danh sách dọc, mỗi dòng là một phòng với số phòng cỡ lớn, nút "Hoàn thành" to rõ. Đây là màn hình được dùng nhiều nhất trong toàn hệ thống.
35. **Lịch làm việc cá nhân** + chấm công vào/ra ca.
36. **Gửi đơn nghỉ / đổi ca**.
37. **Báo hỏng tài sản**.

---

## 5. Thành phần giao diện cần có

| Thành phần | Yêu cầu |
|---|---|
| **Huy hiệu trạng thái** | 6 màu phòng + trạng thái công việc, đơn nghỉ, tài sản. Luôn có chữ kèm màu |
| **Thẻ số liệu** | Dùng trên dashboard: số lớn + nhãn + biểu tượng. Bấm được để lọc |
| **Bảng dữ liệu** | Có lọc, sắp xếp, phân trang. Trên điện thoại chuyển thành danh sách thẻ |
| **Lưới xếp lịch** | Dạng lịch tuần, ô bấm được, cuộn ngang trên màn hình nhỏ |
| **Hộp thoại xác nhận** | Cho mọi thao tác không hoàn tác được (xóa, cho nghỉ việc, thanh lý tài sản) |
| **Thông báo chặn** | Khi vi phạm quy định: nền đỏ nhạt, nêu rõ vi phạm điều nào, không cho lưu |
| **Trạng thái rỗng** | Khi chưa có dữ liệu: hình minh họa nhẹ + câu hướng dẫn việc cần làm tiếp |
| **Hiển thị mật khẩu tạm** | Hộp thoại đặc biệt, có cảnh báo "chỉ hiện một lần", nút sao chép |
| **Bộ chọn số lượng gói** | Thanh trượt hoặc ô nhập cho 3 loại, giá cập nhật theo thời gian thực |

---

## 6. Quy ước hiển thị

- **Ngày:** dd/MM/yyyy (ví dụ 19/09/2026). **Giờ:** HH:mm định dạng 24 giờ.
- **Tiền:** VND, số nguyên, có dấu phân cách hàng nghìn (ví dụ 2.400.000 ₫).
- **Ca qua đêm:** hiển thị rõ là thuộc về ngày bắt đầu, ví dụ "19/09 · 22:00 – 06:00 (qua đêm)".
- **Không có trung tâm thông báo** trong phiên bản này. Việc cần xử lý hiển thị trực tiếp trên dashboard dưới dạng số đếm.

---

## 7. Dữ liệu mẫu (dùng cho bản thiết kế)

Dùng dữ liệu tiếng Việt thật để bản thiết kế sát thực tế:

- **Chuỗi khách sạn:** Sao Mai Hotels
- **Khách sạn:** Sao Mai Nha Trang, Sao Mai Đà Lạt, Sao Mai Vũng Tàu
- **Phòng:** 101, 102, 205, 310, G01 (tầng để dạng chữ: G, M, B1)
- **Loại phòng:** Đơn, Đôi, Gia đình, Hạng sang
- **Phòng ban:** Tiền sảnh, Buồng phòng, Kỹ thuật
- **Vị trí:** Lễ tân, Nhân viên dọn phòng, Bảo vệ
- **Tên nhân viên:** Nguyễn Thị Lan, Trần Văn Hùng, Phạm Minh Tuấn, Lê Thị Hoa
- **Tài sản:** Điều hòa, Tivi, Tủ lạnh mini, Máy nước nóng
- **Vật tư tiêu hao:** Khăn tắm, Dầu gội, Nước suối, Giấy vệ sinh

---

## 8. Ngoài phạm vi — không cần thiết kế

Các phần sau **không** có trong phiên bản này, tránh thiết kế thừa:

- Tính lương, chấm công tự động (đi muộn, về sớm), hợp đồng lao động
- Tuyển dụng, hồ sơ ứng viên
- Đánh giá hiệu suất (KPI), phản hồi khách hàng
- Đặt phòng, quản lý khách lưu trú, thanh toán tiền phòng
- Trung tâm thông báo, chuông thông báo
- Quản lý kho theo phiếu nhập/xuất
- Cảnh báo sắp hết vật tư
- Quy trình bảo trì tài sản chi tiết
- Báo cáo nâng cao, biểu đồ phân tích chuyên sâu

---

## 9. Lưu ý

- Danh sách màn hình ở mục 4 do đội phân tích nghiệp vụ tổng hợp từ các quy định đã chốt; chưa qua bước duyệt chính thức về giao diện. Nếu công cụ thiết kế thấy cần tách hoặc gộp màn hình cho hợp lý hơn, có thể đề xuất.
- Bảng màu và font ở mục 3 là **đề xuất**, chưa phải quy chuẩn thương hiệu đã chốt. Có thể điều chỉnh miễn giữ được tinh thần ở đầu mục 3 và giữ nguyên hệ màu trạng thái phòng (vì màu trạng thái gắn với nghiệp vụ, không chỉ là thẩm mỹ).
- Ưu tiên thiết kế trước các màn hình đánh dấu ⭐: đây là những màn hình được dùng nhiều nhất và phức tạp nhất.
