import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext';
import { canReportDamage } from '../../permissions';
import { assetService } from '../../services/assetApi';
import AssetReportList from './AssetReportList';
import DamageReportDialog from './DamageReportDialog';

/**
 * Tab "Tài sản trong phòng" của chi tiết phòng (S-04) — nơi nhân viên Lễ tân / Dọn dẹp báo hỏng
 * tài sản trong phòng (BR-ASSET-05). Giám đốc/Manager chỉ xem danh sách; Manager xử lý báo hỏng ở
 * màn "Báo hỏng".
 *
 * Tài sản đã thanh lý không hiện (BR-ASSET-14) — cũng không báo hỏng được (BR-ASSET-11).
 */
export default function RoomAssetsPanel({ roomId }) {
  const { user } = useAuth();
  const canReport = canReportDamage(user);

  const [assets, setAssets] = useState([]);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState('');
  const [reporting, setReporting] = useState(null);
  const [banner, setBanner] = useState('');

  const load = useCallback(async () => {
    setLoading(true);
    setLoadError('');
    try {
      setAssets(await assetService.getReportableAssets({ roomId }));
    } catch (err) {
      setLoadError(err?.message || 'Không tải được danh sách tài sản.');
    } finally {
      setLoading(false);
    }
  }, [roomId]);

  useEffect(() => {
    load();
  }, [load]);

  function handleSubmitted(report) {
    setReporting(null);
    setBanner(`Đã gửi báo hỏng ${report.assetName} (${report.assetCode}) cho quản lý.`);
  }

  return (
    <>
      {banner && (
        <div className="alert alert--success" role="status">
          {banner}{' '}
          <Link className="inline-link" to="/bao-hong/cua-toi">Xem báo hỏng của tôi</Link>
          <button type="button" className="alert__close" onClick={() => setBanner('')} aria-label="Đóng">
            ×
          </button>
        </div>
      )}
      {loadError && (
        <div className="alert alert--error" role="alert">
          {loadError}
        </div>
      )}
      {loading && <p className="state">Đang tải dữ liệu…</p>}

      {!loading && !loadError && (
        <AssetReportList
          assets={assets}
          onReport={canReport ? (asset) => { setBanner(''); setReporting(asset); } : undefined}
          emptyText="Phòng này chưa có tài sản cố định nào."
        />
      )}

      {reporting && (
        <DamageReportDialog asset={reporting} onClose={() => setReporting(null)} onSubmitted={handleSubmitted} />
      )}
    </>
  );
}
