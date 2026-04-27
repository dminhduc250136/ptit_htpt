'use client';

import Button from '@/components/ui/Button/Button';
import styles from './AddressCard.module.css';

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

interface AddressCardProps {
  address: SavedAddress;
  onEdit: (address: SavedAddress) => void;
  onDelete: (address: SavedAddress) => void;
  onSetDefault: (address: SavedAddress) => void;
  settingDefault?: boolean;
}

export default function AddressCard({
  address,
  onEdit,
  onDelete,
  onSetDefault,
  settingDefault = false,
}: AddressCardProps) {
  const { fullName, phone, street, ward, district, city, isDefault } = address;

  return (
    <div
      className={[
        styles.card,
        isDefault ? styles.cardDefault : '',
        settingDefault ? styles.loadingCard : '',
      ]
        .filter(Boolean)
        .join(' ')}
    >
      <div className={styles.header}>
        <div className={styles.nameRow}>
          {isDefault && (
            <span
              className={styles.badge}
              aria-label="Địa chỉ mặc định"
            >
              Mặc định
            </span>
          )}
          <span className={styles.fullName}>{fullName}</span>
        </div>
        <span className={styles.phone}>{phone}</span>
      </div>

      <p className={styles.addressText}>
        {street}, {ward}, {district}, {city}
      </p>

      <div className={styles.actions}>
        {!isDefault && (
          <Button
            variant="secondary"
            size="sm"
            onClick={() => onSetDefault(address)}
            loading={settingDefault}
            disabled={settingDefault}
          >
            Đặt làm mặc định
          </Button>
        )}
        <Button
          variant="secondary"
          size="sm"
          onClick={() => onEdit(address)}
          aria-label={`Sửa địa chỉ ${fullName}`}
          disabled={settingDefault}
        >
          Sửa
        </Button>
        <Button
          variant="danger"
          size="sm"
          onClick={() => onDelete(address)}
          aria-label={`Xóa địa chỉ ${fullName}`}
          disabled={settingDefault}
        >
          Xóa
        </Button>
      </div>
    </div>
  );
}

export type { AddressCardProps, SavedAddress };
