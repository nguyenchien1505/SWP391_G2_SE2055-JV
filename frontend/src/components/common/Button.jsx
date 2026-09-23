import React from 'react';

export const Button = ({
  children,
  variant = 'primary',
  size = 'md',
  icon,
  iconPosition = 'left',
  isLoading = false,
  className = '',
  disabled,
  ...props
}) => {
  const baseStyles =
    'inline-flex items-center justify-center font-medium rounded transition-colors duration-150 focus:outline-none focus:ring-2 focus:ring-offset-1 select-none disabled:opacity-50 disabled:cursor-not-allowed cursor-pointer';

  const sizeStyles = {
    sm: 'text-xs px-2.5 py-1.5 gap-1.5 h-8',
    md: 'text-sm px-4 py-2 gap-2 h-9.5',
    lg: 'text-base px-5 py-2.5 gap-2.5 h-11',
  };

  const variantStyles = {
    primary: 'bg-[#00375e] hover:bg-[#002845] active:bg-[#001f35] text-white focus:ring-[#00375e]/40 shadow-xs',
    secondary: 'bg-[#E5EEFF] hover:bg-[#D0E4FF] text-[#00375E] active:bg-[#B9D5FF] focus:ring-[#00375e]/30',
    outline:
      'bg-white hover:bg-[#F7F8FA] active:bg-[#EFF4FF] text-[#1C2330] border border-[#DFE3E8] hover:border-[#CBD5E1] focus:ring-[#00375e]/20 shadow-2xs',
    ghost: 'bg-transparent hover:bg-slate-100 text-[#5B6472] hover:text-[#1C2330] active:bg-slate-200 focus:ring-slate-300',
    danger: 'bg-[#D32F2F] hover:bg-[#B71C1C] text-white active:bg-[#9A0007] focus:ring-red-300 shadow-xs',
  };

  const appliedSize = sizeStyles[size] || sizeStyles.md;
  const appliedVariant = variantStyles[variant] || variantStyles.primary;

  return (
    <button
      className={`${baseStyles} ${appliedSize} ${appliedVariant} ${className}`}
      disabled={disabled || isLoading}
      {...props}
    >
      {isLoading ? (
        <svg className="animate-spin h-4 w-4 text-current" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24">
          <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4"></circle>
          <path
            className="opacity-75"
            fill="currentColor"
            d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"
          ></path>
        </svg>
      ) : (
        <>
          {icon && iconPosition === 'left' && <span className="inline-flex shrink-0">{icon}</span>}
          <span>{children}</span>
          {icon && iconPosition === 'right' && <span className="inline-flex shrink-0">{icon}</span>}
        </>
      )}
    </button>
  );
};
