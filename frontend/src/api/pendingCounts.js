import { fetchAdjustmentReviewQueue } from './attendanceAdjustments';
import { fetchLeaveReviewQueue } from './leaveRequests';
import { fetchSwapRequests } from './scheduling';

// Số việc đang chờ người xem duyệt — dùng chung cho tab «Chờ duyệt» của 3 màn duyệt và thẻ «Đơn chờ duyệt» trên
// dashboard của Manager, để hai nơi luôn ra cùng một số. Chỉ cần tổng nên lấy trang 1 phần tử, đọc totalElements.

const totalOf = (page) => page.totalElements ?? 0;

/** Đơn nghỉ chờ mình duyệt: Manager — đơn nhân viên khách sạn mình; Giám đốc — đơn của các Manager (BR-SCH-08). */
export function countPendingLeave() {
  return fetchLeaveReviewQueue({ status: 'PENDING', size: 1 }).then(totalOf);
}

/** Yêu cầu đổi ca đồng nghiệp đã đồng ý, chờ Manager duyệt (chỉ Manager). */
export function countPendingSwaps() {
  return fetchSwapRequests({ box: 'LOCATION', statuses: ['PENDING_MANAGER'], size: 1 }).then(totalOf);
}

/** Đơn điều chỉnh công chờ Manager duyệt (chỉ Manager). */
export function countPendingAdjustments() {
  return fetchAdjustmentReviewQueue({ statuses: ['PENDING'], size: 1 }).then(totalOf);
}
