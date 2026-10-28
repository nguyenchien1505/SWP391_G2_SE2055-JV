import { addDays, hhmm } from '../scheduling/scheduleFormat';
import { formatClock, todayIso } from './format';

/**
 * "Ngày làm hiện tại" phía giao diện — BR-SCH-03: ca qua đêm thuộc ngày BẮT ĐẦU ca, nên ca 22:00–06:00
 * hôm qua vẫn là ca đang làm lúc 02:00 hôm nay, cho tới đúng giờ kết thúc (chốt 28/10/2026).
 *
 * PHẢI khớp `ShiftTimeUtils.isInCurrentWorkDay` ở backend. Chỉ dùng để HIỂN THỊ (khối "Ca hôm nay"); mọi
 * quyết định giao / nhận việc, và cờ «Tồn đọng», đều do backend tính.
 */
export function isInCurrentWorkDay(shift, now = new Date()) {
  const today = todayIso(now);
  if (shift.shiftDate === today) return true;
  return shift.shiftDate === yesterdayIso(now)
    && Boolean(shift.overnight)
    && formatClock(now) < hhmm(shift.endTime);
}

/** Hôm qua dạng "YYYY-MM-DD" — ngày bắt đầu của ca đêm còn đang làm. */
export function yesterdayIso(now = new Date()) {
  return addDays(todayIso(now), -1);
}
