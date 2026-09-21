import React, { useEffect } from 'react';
import { X } from 'lucide-react';
import { motion, AnimatePresence } from 'motion/react';

export const Modal = ({
  isOpen,
  onClose,
  title,
  subtitle,
  children,
  footer,
  maxWidth = 'lg',
}) => {
  useEffect(() => {
    const handleKeyDown = (e) => {
      if (e.key === 'Escape' && isOpen) {
        onClose();
      }
    };
    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [isOpen, onClose]);

  const maxWidthClasses = {
    sm: 'max-w-sm',
    md: 'max-w-md',
    lg: 'max-w-lg',
    xl: 'max-w-xl',
    '2xl': 'max-w-2xl',
    '3xl': 'max-w-3xl',
  };

  const appliedMaxWidth = maxWidthClasses[maxWidth] || maxWidthClasses.lg;

  return (
    <AnimatePresence>
      {isOpen && (
        <div className="fixed inset-0 z-50 overflow-y-auto">
          {/* Backdrop */}
          <motion.div
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            transition={{ duration: 0.15 }}
            className="fixed inset-0 bg-[#001D35]/40 backdrop-blur-[2px]"
            onClick={onClose}
          />

          {/* Dialog Container */}
          <div className="min-h-screen px-4 text-center flex items-center justify-center p-4">
            <motion.div
              initial={{ opacity: 0, scale: 0.95, y: 10 }}
              animate={{ opacity: 1, scale: 1, y: 0 }}
              exit={{ opacity: 0, scale: 0.95, y: 10 }}
              transition={{ duration: 0.2, ease: 'easeOut' }}
              className={`w-full ${appliedMaxWidth} bg-white rounded-lg shadow-xl text-left overflow-hidden z-10 border border-[#DFE3E8] my-8 inline-block`}
            >
              {/* Header */}
              <div className="px-6 py-4 border-b border-[#DFE3E8] flex items-center justify-between bg-white">
                <div>
                  <h3 className="text-lg font-semibold text-[#1C2330]">{title}</h3>
                  {subtitle && <p className="text-xs text-[#5B6472] mt-0.5">{subtitle}</p>}
                </div>
                <button
                  type="button"
                  onClick={onClose}
                  className="p-1.5 rounded text-[#5B6472] hover:text-[#1C2330] hover:bg-[#F7F8FA] transition-colors focus:outline-none cursor-pointer"
                  aria-label="Đóng"
                >
                  <X className="w-5 h-5" />
                </button>
              </div>

              {/* Body */}
              <div className="px-6 py-5 max-h-[75vh] overflow-y-auto">
                {children}
              </div>

              {/* Footer */}
              {footer && (
                <div className="px-6 py-3.5 bg-[#F7F8FA] border-t border-[#DFE3E8] flex justify-end gap-3 items-center">
                  {footer}
                </div>
              )}
            </motion.div>
          </div>
        </div>
      )}
    </AnimatePresence>
  );
};
