export default function Logo({ subtitle = 'HOSPITALITY OPS' }) {
  return (
    <div className="logo">
      <span className="logo__mark" aria-hidden="true">
        ★
      </span>
      <span className="logo__text">
        <b>SAO MAI</b>
        <small>{subtitle}</small>
      </span>
    </div>
  );
}
