import React from 'react';

export const StatCard = ({
  title,
  value,
  icon,
  iconBg = 'bg-[#EFF4FF]',
  iconColor = 'text-[#00375E]',
  onClick,
  className = '',
}) => {
  return (
    <div
      onClick={onClick}
      className={`bg-white rounded-lg border border-[#DFE3E8] p-4 flex items-center justify-between transition-shadow hover:shadow-xs ${
        onClick ? 'cursor-pointer' : ''
      } ${className}`}
    >
      <div>
        <p className="text-[11px] font-bold tracking-wide uppercase text-[#5B6472]">
          {title}
        </p>
        <p className="text-[28px] font-bold text-[#00375E] leading-none mt-2 tracking-tight">
          {value}
        </p>
      </div>

      <div className={`w-10 h-10 rounded flex items-center justify-center shrink-0 ${iconBg} ${iconColor}`}>
        {icon}
      </div>
    </div>
  );
};
