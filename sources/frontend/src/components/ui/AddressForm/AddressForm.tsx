'use client';

import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import Button from '@/components/ui/Button/Button';
import Input from '@/components/ui/Input/Input';
import styles from './AddressForm.module.css';

const addressSchema = z.object({
  fullName: z.string().min(2, 'Tối thiểu 2 ký tự').max(100, 'Tối đa 100 ký tự'),
  phone: z
    .string()
    .regex(/^(0|\+84)[3-9]\d{8}$/, 'Số điện thoại VN không hợp lệ (VD: 0912345678)'),
  street: z.string().min(5, 'Tối thiểu 5 ký tự').max(200, 'Tối đa 200 ký tự'),
  ward: z.string().min(2, 'Tối thiểu 2 ký tự').max(100, 'Tối đa 100 ký tự'),
  district: z.string().min(2, 'Tối thiểu 2 ký tự').max(100, 'Tối đa 100 ký tự'),
  city: z.string().min(2, 'Tối thiểu 2 ký tự').max(100, 'Tối đa 100 ký tự'),
});

type AddressFormData = z.infer<typeof addressSchema>;

interface AddressBody {
  fullName: string;
  phone: string;
  street: string;
  ward: string;
  district: string;
  city: string;
  isDefault?: boolean;
}

interface AddressFormProps {
  initialValues?: Partial<AddressBody>;
  onSubmit: (data: AddressBody) => Promise<void>;
  onCancel: () => void;
  loading?: boolean;
}

export default function AddressForm({
  initialValues,
  onSubmit,
  onCancel,
  loading = false,
}: AddressFormProps) {
  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<AddressFormData>({
    resolver: zodResolver(addressSchema),
    defaultValues: {
      fullName: initialValues?.fullName ?? '',
      phone: initialValues?.phone ?? '',
      street: initialValues?.street ?? '',
      ward: initialValues?.ward ?? '',
      district: initialValues?.district ?? '',
      city: initialValues?.city ?? '',
    },
  });

  const onFormSubmit = handleSubmit(async (data: AddressFormData) => {
    await onSubmit(data);
  });

  return (
    <form onSubmit={onFormSubmit} className={styles.form} noValidate>
      <div className={styles.grid2}>
        <Input
          label="Họ và tên"
          placeholder="Nguyễn Văn A"
          error={errors.fullName?.message}
          fullWidth
          {...register('fullName')}
        />
        <Input
          label="Số điện thoại"
          placeholder="0912345678"
          type="tel"
          error={errors.phone?.message}
          fullWidth
          {...register('phone')}
        />
      </div>

      <div className={styles.spanFull}>
        <Input
          label="Địa chỉ (số nhà, đường)"
          placeholder="123 Đường Lê Lợi"
          error={errors.street?.message}
          fullWidth
          {...register('street')}
        />
      </div>

      <div className={styles.grid3}>
        <Input
          label="Phường/Xã"
          placeholder="Phường Bến Nghé"
          error={errors.ward?.message}
          fullWidth
          {...register('ward')}
        />
        <Input
          label="Quận/Huyện"
          placeholder="Quận 1"
          error={errors.district?.message}
          fullWidth
          {...register('district')}
        />
        <Input
          label="Tỉnh/Thành phố"
          placeholder="TP. Hồ Chí Minh"
          error={errors.city?.message}
          fullWidth
          {...register('city')}
        />
      </div>

      <div className={styles.actions}>
        <Button
          type="button"
          variant="secondary"
          onClick={onCancel}
          disabled={loading}
        >
          Hủy
        </Button>
        <Button type="submit" variant="primary" loading={loading}>
          Lưu địa chỉ
        </Button>
      </div>
    </form>
  );
}

export type { AddressFormProps, AddressBody, AddressFormData };
