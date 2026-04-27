'use client';

import { useEffect, useRef, useState } from 'react';
import Link from 'next/link';
import styles from './AddressPicker.module.css';

interface SavedAddress {
  id: string;
  userId: string;
  fullName: string;
  phone: string;
  street: string;
  ward: string;
  district: string;
  city: string;
  isDefault: boolean;
  createdAt: string;
}

interface AddressPickerProps {
  addresses: SavedAddress[];
  loading?: boolean;
  onSelect: (address: SavedAddress) => void;
}

export default function AddressPicker({
  addresses,
  loading = false,
  onSelect,
}: AddressPickerProps) {
  const [isOpen, setIsOpen] = useState(false);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const containerRef = useRef<HTMLDivElement>(null);

  // Pre-select default address when addresses load
  useEffect(() => {
    if (addresses.length > 0) {
      const defaultAddr = addresses.find((a) => a.isDefault);
      setSelectedId(defaultAddr?.id ?? null);
    }
  }, [addresses]);

  // Click-outside close — T-11-05-03 mitigation: cleanup listener on unmount
  useEffect(() => {
    function handleClickOutside(e: MouseEvent) {
      if (containerRef.current && !containerRef.current.contains(e.target as Node)) {
        setIsOpen(false);
      }
    }
    if (isOpen) {
      document.addEventListener('mousedown', handleClickOutside);
    }
    return () => {
      document.removeEventListener('mousedown', handleClickOutside);
    };
  }, [isOpen]);

  function handleSelect(address: SavedAddress) {
    setSelectedId(address.id);
    onSelect(address);
    setIsOpen(false);
  }

  return (
    <div className={styles.container} ref={containerRef}>
      <button
        type="button"
        className={styles.trigger}
        aria-expanded={isOpen}
        aria-haspopup="listbox"
        onClick={() => setIsOpen((prev) => !prev)}
      >
        Địa chỉ đã lưu ▼
      </button>

      {isOpen && (
        <div
          role="listbox"
          className={styles.dropdown}
          aria-label="Danh sách địa chỉ đã lưu"
        >
          {loading && (
            <>
              <div className={styles.skeletonRow} aria-hidden="true" />
              <div className={styles.skeletonRow} aria-hidden="true" />
            </>
          )}

          {!loading && addresses.length === 0 && (
            <div className={styles.emptyState}>
              <p className={styles.emptyText}>Chưa có địa chỉ đã lưu.</p>
              <Link
                href="/profile/addresses"
                target="_blank"
                rel="noopener noreferrer"
                className={styles.emptyLink}
              >
                Thêm tại Sổ địa chỉ →
              </Link>
            </div>
          )}

          {!loading &&
            addresses.map((address) => {
              const isDefault = address.isDefault;
              const isSelected = selectedId === address.id;

              return (
                <div
                  key={address.id}
                  role="option"
                  aria-selected={isSelected}
                  className={[
                    styles.item,
                    isDefault ? styles.itemDefault : '',
                    isSelected ? styles.itemSelected : '',
                  ]
                    .filter(Boolean)
                    .join(' ')}
                  onClick={() => handleSelect(address)}
                  onKeyDown={(e) => {
                    if (e.key === 'Enter' || e.key === ' ') {
                      e.preventDefault();
                      handleSelect(address);
                    }
                  }}
                  tabIndex={0}
                >
                  <span className={styles.itemText}>
                    {address.fullName} — {address.street}, {address.ward},{' '}
                    {address.district}, {address.city}
                  </span>
                  {isSelected && <span className={styles.checkMark} aria-hidden="true">✓</span>}
                </div>
              );
            })}
        </div>
      )}
    </div>
  );
}

export type { AddressPickerProps, SavedAddress };
