import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { readErrorMessage } from '../../api/client';
import { fetchInspection, fetchRoomDamageReports } from '../../api/housekeeping';
import { fetchRoom } from '../../api/rooms';
import { assetService } from '../../services/assetApi';
import { formatClock, formatDate, formatShortDateTime } from '../../pages/rooms/format';
import { taskSourceLabel, teamNames } from '../../pages/rooms/roomLabels';
import AssetReportList from '../damage-reports/AssetReportList';
import DamageReportDialog from '../damage-reports/DamageReportDialog';

/** "12 phút", "1 giờ 5 phút", "1 ngày 2 giờ" — đã dọn bao lâu, tính từ lúc nhận / được giao. */
function elapsedLabel(fromIso, now) {
  const minutes = Math.max(0, Math.floor((now - new Date(fromIso).getTime()) / 60000));
  if (minutes < 60) return `${minutes} phút`;
  const hours = Math.floor(minutes / 60);
  if (hours < 24) return minutes % 60 ? `${hours} giờ ${minutes % 60} phút` : `${hours} giờ`;
  return hours % 24 ? `${Math.floor(hours / 24)} ngày ${hours % 24} giờ` : `${Math.floor(hours / 24)} ngày`;
}

/** Đồng hồ cập nhật mỗi phút — đủ cho "đã dọn bao lâu", không vẽ lại cả trang mỗi giây. */
function useMinuteClock() {
  const [now, setNow] = useState(() => Date.now());
  useEffect(() => {
    const id = window.setInterval(() => setNow(Date.now()), 60000);
    return () => window.clearInterval(id);
  }, []);
  return now;
}

/**
 * Chi tiết phòng đang dọn — phần chính của «Việc của tôi» (S-16, chốt 02/11/2026): người dọn đứng trong phòng
 * cần thấy đủ thông tin và thao tác ở MỘT chỗ thay vì một dòng với nút «Hoàn thành».
 *
 * Các khối, theo thứ tự cần khi đang dọn:
 *   - Số phòng cỡ lớn, loại · tầng · sức chứa; nhận lúc mấy giờ, đã dọn bao lâu; dọn cùng ai; cờ tồn đọng
 *     (backend tính — BR-HK-04).
 *   - Vì sao có việc: chờ dọn từ lúc nào, tự sinh khi khách trả phòng hay dọn lại. Dọn lại thì hiện THẲNG lý do
 *     lần trước không đạt (BR-HK-12) — không giấu sau liên kết.
 *   - Ghi chú vận hành của phòng (Quản lý / Giám đốc ghi — BR-ROOM-04).
 *   - Tài sản trong phòng, mỗi món một nút «Báo hỏng» (BR-ASSET-05); món đã có phiếu chờ xử lý thì ghi rõ để
 *     khỏi báo trùng — chỉ số phiếu, không lộ người báo.
 *   - Thanh nút dưới cùng (dính đáy màn khi cuộn trên điện thoại): «Hoàn thành», và «Trả lại» cho việc mình tự
 *     nhận (BR-HK-17) — nút phụ, nằm dưới để khó bấm nhầm.
 *
 * Mỗi khối tải riêng và lỗi riêng: thiếu tài sản hay biên bản thì khối đó báo, các thao tác chính vẫn dùng được.
 *
 * @param task        việc «Đang thực hiện» có mình trong nhóm
 * @param meId        người đang đăng nhập
 * @param onComplete  (task) => mở hộp «Báo xong»
 * @param onReturn    (task) => mở hộp «Trả lại»
 */
export default function CleaningTaskDetail({ task, meId, onComplete, onReturn }) {
  const now = useMinuteClock();
  const [room, setRoom] = useState(null);
  const [previous, setPrevious] = useState(null);         // biên bản lần trước — chỉ việc dọn lại
  const [previousError, setPreviousError] = useState('');
  const [assets, setAssets] = useState(null);             // null = đang tải
  const [assetsError, setAssetsError] = useState('');
  const [damageByAsset, setDamageByAsset] = useState({}); // { [assetId]: { openReports, reportedByMe } }
  const [assetsKey, setAssetsKey] = useState(0);
  const [reportingAsset, setReportingAsset] = useState(null);
  const [reportNotice, setReportNotice] = useState('');

  const teammates = teamNames(task, meId);
  const selfClaimed = task.assignmentSource === 'SELF_CLAIM' && task.assignedBy === meId;

  // Ghi chú vận hành và sức chứa nằm trên phòng, không có trong việc dọn.
  useEffect(() => {
    let cancelled = false;
    fetchRoom(task.roomId)
      .then((data) => !cancelled && setRoom(data))
      .catch(() => !cancelled && setRoom(null));
    return () => {
      cancelled = true;
    };
  }, [task.roomId]);

  useEffect(() => {
    setPrevious(null);
    setPreviousError('');
    if (!task.parentTaskId) return undefined;
    let cancelled = false;
    fetchInspection(task.parentTaskId)
      .then((data) => !cancelled && setPrevious(data))
      .catch((err) => !cancelled
        && setPreviousError(readErrorMessage(err, 'Không tải được biên bản của lần kiểm tra trước.')));
    return () => {
      cancelled = true;
    };
  }, [task.parentTaskId]);

  // Tài sản và báo hỏng đang chờ tải cùng lúc; báo hỏng lỗi thì vẫn hiện tài sản, chỉ thiếu dòng "đã có người báo".
  useEffect(() => {
    let cancelled = false;
    setAssetsError('');
    Promise.all([
      assetService.getReportableAssets({ roomId: task.roomId }),
      fetchRoomDamageReports(task.id).catch(() => []),
    ])
      .then(([rows, damage]) => {
        if (cancelled) return;
        setAssets(rows);
        setDamageByAsset(Object.fromEntries(damage.map((item) => [item.fixedAssetId, item])));
      })
      .catch((err) => !cancelled && setAssetsError(err?.message || 'Không tải được tài sản trong phòng.'));
    return () => {
      cancelled = true;
    };
  }, [task.roomId, task.id, assetsKey]);

  // Đổi sang phòng khác thì bỏ câu báo của phòng cũ.
  useEffect(() => {
    setReportNotice('');
  }, [task.id]);

  function finishDamageReport(report) {
    setReportingAsset(null);
    setReportNotice(`Đã gửi báo hỏng ${report.assetName} (${report.assetCode}) cho quản lý.`);
    setAssetsKey((key) => key + 1);
  }


  return (
    <section className="panel cleaning-now" aria-labelledby={`cleaning-now-${task.id}`}>
      <header className="cleaning-now__head">
        <p className="cleaning-now__eyebrow">Đang dọn</p>
        <h2 className="cleaning-now__room" id={`cleaning-now-${task.id}`}>
          {task.roomNumber ?? '—'}
          <small>
            {[task.roomTypeName, `Tầng ${task.floor ?? '—'}`, room && `${room.capacity} người`].filter(Boolean).join(' · ')}
          </small>
        </h2>
        {task.assignedAt && (
          <p className="cleaning-now__meta">
            Nhận lúc {formatClock(new Date(task.assignedAt))} · đã dọn {elapsedLabel(task.assignedAt, now)}
          </p>
        )}
        {teammates && <p className="cleaning-now__meta">Dọn cùng: {teammates}</p>}
        {/* BR-HK-04: việc của hôm trước chưa xong vẫn phải làm. Cờ do backend tính (ca qua đêm). */}
        {task.overdue && <p className="task-card__flag">Tồn đọng từ {formatDate(task.assignedDate)}</p>}
      </header>

      {/* Màn rộng: thông tin phòng bên trái, tài sản bên phải; điện thoại: xếp chồng theo đúng thứ tự này. */}
      <div className="cleaning-now__body">
        <div className="cleaning-now__info">
          <p className="cleaning-now__why">
            Chờ dọn từ {formatShortDateTime(task.createdAt)} · {taskSourceLabel(task.createdSource)}
          </p>

          {/* BR-HK-12: dọn lại thì người dọn cần biết lần trước hỏng ở đâu — hiện thẳng, không qua liên kết. */}
          {task.parentTaskId && (
            <div className="alert alert--warn cleaning-now__redo">
              <b>Lần kiểm tra trước không đạt</b>
              {previous && (
                <p>
                  {previous.reason ?? 'Không ghi lý do.'}
                  <small> · {formatShortDateTime(previous.inspectedAt)}</small>
                </p>
              )}
              {!previous && !previousError && <p>Đang tải lý do…</p>}
              {previousError && <p>{previousError}</p>}
            </div>
          )}

          {room?.note && (
            <div className="readonly-box">
              <b>Ghi chú phòng</b>
              <p>{room.note}</p>
            </div>
          )}
        </div>

        <section className="cleaning-now__assets" aria-label="Tài sản trong phòng">
          <h3 className="board-panel__heading">Tài sản trong phòng</h3>
          {reportNotice && (
            <div className="alert alert--success" role="status">
              {reportNotice}{' '}
              <Link className="inline-link" to="/bao-hong/cua-toi">Xem báo hỏng của tôi</Link>
              <button type="button" className="alert__close" onClick={() => setReportNotice('')} aria-label="Đóng">
                ×
              </button>
            </div>
          )}
          {assetsError && <p className="muted">{assetsError}</p>}
          {!assetsError && assets === null && <p className="muted">Đang tải tài sản…</p>}
          {!assetsError && assets && (
            <AssetReportList
              assets={assets}
              compact
              onReport={(asset) => {
                setReportNotice('');
                setReportingAsset(asset);
              }}
              nameAddon={(asset) => damageByAsset[asset.id] && (
                <span className="badge badge--orange cleaning-now__reported">
                  {damageByAsset[asset.id].reportedByMe ? 'Bạn đã báo · chờ xử lý' : 'Đã có người báo · chờ xử lý'}
                </span>
              )}
              emptyText="Phòng này chưa có tài sản cố định nào."
            />
          )}
        </section>
      </div>

      <div className="cleaning-now__actions">
        <button type="button" className="btn btn--primary" onClick={() => onComplete(task)}>
          Hoàn thành
        </button>
        {/* BR-HK-17: chỉ người tự nhận trả lại được — ẩn cho dễ dùng, backend vẫn kiểm lại. */}
        {selfClaimed && (
          <button type="button" className="btn btn--ghost" onClick={() => onReturn(task)}>
            Trả lại
          </button>
        )}
      </div>

      {reportingAsset && (
        <DamageReportDialog
          asset={reportingAsset}
          onClose={() => setReportingAsset(null)}
          onSubmitted={finishDamageReport}
        />
      )}
    </section>
  );
}
