import React, { forwardRef, useId } from 'react';
import styles from './Input.module.css';

interface InputProps extends React.InputHTMLAttributes<HTMLInputElement> {
  label?: string;
  error?: string;
  helperText?: string;
  icon?: React.ReactNode;
  fullWidth?: boolean;
}

const Input = forwardRef<HTMLInputElement, InputProps>(({
  label,
  error,
  helperText,
  icon,
  fullWidth = false,
  className = '',
  id,
  ...props
}, ref) => {
  const generatedId = useId();
  const inputId = id || (label ? `input-${label.toLowerCase().replace(/\s/g, '-')}` : generatedId);

  return (
    <div className={`${styles.wrapper} ${fullWidth ? styles.fullWidth : ''} ${className}`}>
      {label && (
        <label htmlFor={inputId} className={styles.label}>
          {label}
        </label>
      )}
      <div className={`${styles.inputContainer} ${error ? styles.hasError : ''}`}>
        {icon && <span className={styles.icon}>{icon}</span>}
        <input
          ref={ref}
          id={inputId}
          className={styles.input}
          {...props}
        />
      </div>
      {error && <span className={styles.error}>{error}</span>}
      {helperText && !error && <span className={styles.helper}>{helperText}</span>}
    </div>
  );
});

Input.displayName = 'Input';
export default Input;
