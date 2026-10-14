import { hhmm } from '../../pages/scheduling/scheduleFormat';

/**
 * "06:00 – 14:00" xuống dòng gọn trong ô hẹp (bảng tuần trên máy tính bảng): chỉ ngắt sau dấu gạch
 * ("06:00 –" / "14:00"), không bao giờ để dấu gạch đứng một dòng riêng.
 */
export default function TimeRangeText({ start, end }) {
  return (
    <>
      <span className="time-range__part">{hhmm(start)} –</span>{' '}
      <span className="time-range__part">{hhmm(end)}</span>
    </>
  );
}
