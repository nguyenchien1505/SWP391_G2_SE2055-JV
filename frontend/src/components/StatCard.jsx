export default function StatCard({ label, value, note, icon, tone = 'blue' }) {
  return (
    <div className="stat-card">
      <div className="stat-card__body">
        <p className="stat-card__label">{label}</p>
        <p className="stat-card__value">{value}</p>
        {note && <p className="stat-card__note">{note}</p>}
      </div>
      <span className={`stat-card__icon stat-card__icon--${tone}`} aria-hidden="true">
        {icon}
      </span>
    </div>
  );
}
