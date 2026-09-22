import React from 'react';

export const Badge = ({
  variant = 'default',
  icon,
  children,
  className = '',
}) => {
  const variantStyles = {
    default: 'bg-slate-100 text-slate-700 border-slate-200',
    code: 'bg-[#EFF4FF] text-[#00375E] border-[#D0E4FF] font-semibold font-mono tracking-tight',
    'fixed-asset': 'bg-[#EAF2FF] text-[#0E61A1] border-[#C2DBFE]',
    'consumable-linen': 'bg-[#E8F5E9] text-[#2E7D32] border-[#C8E6C9]',
    'consumable-free': 'bg-[#FFF3E0] text-[#E65100] border-[#FFE0B2]',
    'consumable-amenities': 'bg-[#FFF3E0] text-[#D84315] border-[#FFCCBC]',
    technical: 'bg-[#F3E5F5] text-[#6A1B9A] border-[#E1BEE7]',
    success: 'bg-[#E8F5E9] text-[#2E7D32] border-[#C8E6C9]',
    warning: 'bg-[#FFF8E1] text-[#F57F17] border-[#FFE082]',
    danger: 'bg-[#FFEBEE] text-[#D32F2F] border-[#FFCDD2]',
    // Room status badges
    'room-ready': 'bg-[#E8F5E9] text-[#2E7D32] border-[#C8E6C9]',
    'room-occupied': 'bg-[#E3F2FD] text-[#1565C0] border-[#BBDEFB]',
    'room-dirty': 'bg-[#FFF3E0] text-[#EF6C00] border-[#FFE0B2]',
    'room-cleaning': 'bg-[#FFFDE7] text-[#F57F17] border-[#FFF59D]',
    'room-inspecting': 'bg-[#F3E5F5] text-[#6A1B9A] border-[#E1BEE7]',
    'room-unavailable': 'bg-[#EEEEEE] text-[#616161] border-[#E0E0E0]',
  };

  const currentVariant = variantStyles[variant] || variantStyles.default;

  return (
    <span
      className={`inline-flex items-center gap-1.5 px-2.5 py-0.5 rounded text-xs font-medium border whitespace-nowrap ${currentVariant} ${className}`}
    >
      {icon && <span className="shrink-0 text-current">{icon}</span>}
      <span>{children}</span>
    </span>
  );
};
