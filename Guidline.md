Đã sửa gì
#	Thay đổi
1	Đổi tên findByTenantIdAndIsActiveTrue thành findByTenantIdAndActiveTrue trong PositionRepository.java.
2	Validator dùng chung getOrCreate(), nên Tenant chưa có policy sẽ dùng bản mặc định thay vì báo lỗi. SecurityConfig cho Manager đọc (GET) Policy và Shift Template.
3	Thêm mới LocationRepository và ShiftTemplateRepository. Khi tạo user và tạo ca, hệ thống kiểm tra Location và template có thuộc Tenant không, và template còn active không. Tạo Manager thì chặn Location đã có Manager và chuyển Location sang OPERATIONAL.
4	Thêm assertCanModify: Manager chỉ sửa, cho nghỉ việc, reset mật khẩu được tài khoản STAFF.
5	Thêm mới CurrentUserRefreshFilter.java, giải thích bên dưới.
6	Thêm ShiftTimeUtils.todayInHanoi(), dùng múi giờ Asia/Ho_Chi_Minh. Chuẩn IANA không có mã riêng cho Hà Nội; cả Việt Nam dùng chung múi UTC+7 này.
7	GET /scheduling/shifts/{id}: Staff chỉ xem được ca của mình, Manager chỉ xem được ca trong Location của mình; ngoài phạm vi thì trả 404.
8	Lỗi vi phạm ràng buộc DB giờ trả 409 thay vì 500. Chặn xóa ca đã check-in. Policy có giới hạn trên: tối đa 24 giờ/ngày, 168 giờ/tuần, 99.99 giờ nghỉ giữa 2 ca (giới hạn của cột DB).
Những chỗ tôi làm khác hoặc làm thêm so với mô tả trước
Lỗi #5: dùng filter thay vì chỉ vá API đổi mật khẩu. Ở mỗi request, filter đọc lại user từ DB. Nhờ vậy sửa cùng lúc 3 việc:

mustChangePassword không còn bị cũ sau khi đổi mật khẩu;
người còn mật khẩu tạm gọi API khác sẽ nhận 403 (ép BR-USER-07 ngay ở backend);
session của người đã nghỉ việc hoặc thuộc Tenant bị Suspended bị cắt ngay, trả 401.
Cái giá là mỗi request tốn thêm 1–3 query.

maximumSessions(1) giờ mới thực sự có tác dụng, vì CustomUserDetails đã có equals theo id. Đăng nhập ở máy thứ hai thì máy cũ sẽ nhận 401. Tôi chưa kiểm tra trường hợp này với đăng nhập Google.

Lỗi #3: cho một Manager nghỉ việc thì Location quay về NOT_OPERATIONAL, để trạng thái DM-13 luôn đúng.

Lỗi #6: dùng cố định giờ Hà Nội như bạn chọn, chưa đọc cột locations.timezone.

Kiểm chứng
mvnw compile thành công.
Cả 30/30 derived query trong các repository đều qua parser của Spring Data, chạy trên các class vừa build.
5 unit test tạm cho filter đều pass: làm mới session, cắt session của người đã nghỉ việc, chặn khi còn mật khẩu tạm, vẫn cho gọi /auth/me và /auth/change-password, và equals theo id. Chạy xong tôi đã xóa các test này.


* LƯU Ý
Ai pull commit adda64d về cũng cần làm cả 2 việc:

Xóa và tạo lại database hotel_workforce.
Chạy mvnw clean (hoặc xóa thư mục backend/target) để bỏ file V2 và V3 cũ còn sót trong thư mục build.