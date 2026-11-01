import { useEffect, useState } from 'react';
import { createPortal } from 'react-dom';
import { fetchLeaveAttachment } from '../../api/leaveRequests';

/**
 * Ảnh minh chứng kèm lý do nghỉ (chốt 01/11/2026) — hàng ảnh nhỏ, bấm để xem lớn. Người gửi và người duyệt theo cấp
 * cùng thấy; phạm vi do backend chặn. Ảnh tải qua axios (kèm cookie phiên) thành object URL và được thu hồi khi rời
 * màn, để không giữ ảnh trong bộ nhớ.
 *
 * Khung xem lớn gắn thẳng vào <body>: thẻ này còn nằm trong hộp thoại duyệt / từ chối, mà khung cố định lồng trong
 * hộp thoại khác dễ bị cắt.
 *
 * @param leave đơn có `attachments: [{ id, fileName }]`; không có ảnh thì không vẽ gì
 */
export default function LeaveAttachments({ leave }) {
  const attachments = leave.attachments ?? [];
  const ids = attachments.map((attachment) => attachment.id).join(',');
  const [urls, setUrls] = useState({}); // id → object URL, hoặc 'error'
  const [viewing, setViewing] = useState(null);

  useEffect(() => {
    if (!ids) return undefined;
    let cancelled = false;
    const created = [];
    ids.split(',').forEach((attachmentId) => {
      fetchLeaveAttachment(leave.id, attachmentId)
        .then((blob) => {
          const url = URL.createObjectURL(blob);
          if (cancelled) {
            URL.revokeObjectURL(url);
            return;
          }
          created.push(url);
          setUrls((current) => ({ ...current, [attachmentId]: url }));
        })
        .catch(() => !cancelled && setUrls((current) => ({ ...current, [attachmentId]: 'error' })));
    });
    return () => {
      cancelled = true;
      created.forEach((url) => URL.revokeObjectURL(url));
    };
  }, [leave.id, ids]);

  // Esc chỉ đóng khung ảnh, không đóng luôn hộp thoại duyệt bên dưới: bắt ở pha capture rồi chặn lại.
  useEffect(() => {
    if (!viewing) return undefined;
    const onKey = (e) => {
      if (e.key !== 'Escape') return;
      e.stopImmediatePropagation();
      setViewing(null);
    };
    window.addEventListener('keydown', onKey, true);
    return () => window.removeEventListener('keydown', onKey, true);
  }, [viewing]);

  if (attachments.length === 0) return null;

  return (
    <>
      <div className="leave-attachments" role="list" aria-label="Ảnh minh chứng">
        {attachments.map((attachment) => {
          const url = urls[attachment.id];
          const ready = url && url !== 'error';
          return (
            <button
              key={attachment.id}
              type="button"
              role="listitem"
              className="leave-attachment"
              title={attachment.fileName}
              disabled={!ready}
              onClick={() => setViewing(attachment)}
            >
              {ready
                ? <img src={url} alt={`Ảnh minh chứng ${attachment.fileName}`} />
                : <span>{url === 'error' ? 'Không tải được ảnh' : 'Đang tải…'}</span>}
            </button>
          );
        })}
      </div>

      {viewing && createPortal(
        <div className="modal-backdrop leave-viewer" role="dialog" aria-modal="true" aria-label={viewing.fileName}
             onClick={() => setViewing(null)}>
          <figure className="leave-viewer__frame" onClick={(e) => e.stopPropagation()}>
            <img src={urls[viewing.id]} alt={`Ảnh minh chứng ${viewing.fileName}`} />
            <figcaption>
              <span>{viewing.fileName}</span>
              <button type="button" className="btn btn--primary btn--sm" onClick={() => setViewing(null)} autoFocus>
                Đóng
              </button>
            </figcaption>
          </figure>
        </div>,
        document.body,
      )}
    </>
  );
}
