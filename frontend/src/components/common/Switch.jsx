import React from 'react';

export const Switch = ({
  checked,
  onChange,
  disabled = false,
  labelOn = 'Bật',
  labelOff = 'Tắt',
  id,
}) => {
  const handleClick = (e) => {
    e.stopPropagation();
    if (!disabled) {
      onChange(!checked);
    }
  };

  return (
    <div 
      className={`inline-flex items-center gap-2 select-none ${disabled ? 'opacity-50 cursor-not-allowed' : 'cursor-pointer'}`}
      onClick={handleClick}
      id={id}
    >
      <button
        type="button"
        role="switch"
        aria-checked={checked}
        disabled={disabled}
        className={`relative inline-flex h-5 w-9 shrink-0 cursor-pointer rounded-full border-2 border-transparent transition-colors duration-200 ease-in-out focus:outline-none focus:ring-2 focus:ring-[#00375e]/20 ${
          checked ? 'bg-[#2E7D32]' : 'bg-[#CBD5E1]'
        }`}
      >
        <span
          aria-hidden="true"
          className={`pointer-events-none inline-block h-4 w-4 transform rounded-full bg-white shadow-xs ring-0 transition duration-200 ease-in-out ${
            checked ? 'translate-x-4' : 'translate-x-0'
          }`}
        />
      </button>
      <span className={`text-xs font-medium ${checked ? 'text-[#2E7D32]' : 'text-[#94A3B8]'}`}>
        {checked ? labelOn : labelOff}
      </span>
    </div>
  );
};
