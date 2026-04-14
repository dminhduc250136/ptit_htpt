'use client';

import React, { useState } from 'react';
import Image from 'next/image';
import styles from './page.module.css';
import Button from '@/components/ui/Button/Button';
import Input from '@/components/ui/Input/Input';
import Badge from '@/components/ui/Badge/Badge';
import { mockProducts } from '@/mock-data/products';
import { formatPrice } from '@/services/api';

export default function AdminProductsPage() {
  const [products, setProducts] = useState(mockProducts);
  const [showAddModal, setShowAddModal] = useState(false);
  const [deleteTarget, setDeleteTarget] = useState<string | null>(null);
  const [search, setSearch] = useState('');

  const filtered = products.filter(p =>
    p.name.toLowerCase().includes(search.toLowerCase()) ||
    p.category.name.toLowerCase().includes(search.toLowerCase())
  );

  const handleDelete = () => {
    if (deleteTarget) {
      setProducts(prev => prev.filter(p => p.id !== deleteTarget));
      setDeleteTarget(null);
    }
  };

  return (
    <div className={styles.page}>
      <div className={styles.header}>
        <h1 className={styles.title}>Quản lý sản phẩm</h1>
        <Button onClick={() => setShowAddModal(true)}>+ Thêm sản phẩm</Button>
      </div>

      <div className={styles.toolbar}>
        <Input placeholder="Tìm sản phẩm..." value={search} onChange={e => setSearch(e.target.value)} icon={<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/></svg>} />
        <span className={styles.count}>{filtered.length} sản phẩm</span>
      </div>

      <div className={styles.tableWrapper}>
        <table className={styles.table}>
          <thead>
            <tr>
              <th>Sản phẩm</th>
              <th>Danh mục</th>
              <th>Giá</th>
              <th>Tồn kho</th>
              <th>Trạng thái</th>
              <th>Thao tác</th>
            </tr>
          </thead>
          <tbody>
            {filtered.map(p => (
              <tr key={p.id}>
                <td>
                  <div className={styles.productCell}>
                    <div className={styles.productThumb}><Image src={p.thumbnailUrl} alt={p.name} fill sizes="48px" style={{ objectFit: 'cover' }} /></div>
                    <div><p className={styles.productName}>{p.name}</p><p className={styles.productBrand}>{p.brand}</p></div>
                  </div>
                </td>
                <td>{p.category.name}</td>
                <td className={styles.price}>{formatPrice(p.price)}</td>
                <td><span className={p.stock < 10 ? styles.lowStock : ''}>{p.stock}</span></td>
                <td><Badge variant={p.status === 'ACTIVE' ? 'new' : p.status === 'OUT_OF_STOCK' ? 'outOfStock' : 'default'}>{p.status === 'ACTIVE' ? 'Đang bán' : p.status === 'OUT_OF_STOCK' ? 'Hết hàng' : 'Ẩn'}</Badge></td>
                <td>
                  <div className={styles.actions}>
                    <button className={styles.actionBtn} title="Chỉnh sửa">✏️</button>
                    <button className={`${styles.actionBtn} ${styles.deleteBtn}`} title="Xóa" onClick={() => setDeleteTarget(p.id)}>🗑️</button>
                  </div>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {/* Modal: Add Product */}
      {showAddModal && (
        <div className={styles.overlay} onClick={() => setShowAddModal(false)}>
          <div className={styles.modal} onClick={e => e.stopPropagation()}>
            <div className={styles.modalHeader}>
              <h3>Thêm sản phẩm mới</h3>
              <button className={styles.closeBtn} onClick={() => setShowAddModal(false)}>✕</button>
            </div>
            <form className={styles.modalForm} onSubmit={e => { e.preventDefault(); alert('Thêm sản phẩm thành công! (Mock)'); setShowAddModal(false); }}>
              <Input label="Tên sản phẩm" placeholder="Nhập tên sản phẩm" fullWidth />
              <div className={styles.formRow}>
                <Input label="Giá bán" type="number" placeholder="0" fullWidth />
                <Input label="Giá gốc" type="number" placeholder="0" fullWidth />
              </div>
              <Input label="Mô tả ngắn" placeholder="Mô tả ngắn gọn sản phẩm" fullWidth />
              <div className={styles.formRow}>
                <Input label="Danh mục" placeholder="Chọn danh mục" fullWidth />
                <Input label="Thương hiệu" placeholder="Nhập thương hiệu" fullWidth />
              </div>
              <Input label="Số lượng tồn kho" type="number" placeholder="0" fullWidth />
              <Input label="URL hình ảnh" placeholder="https://..." fullWidth />
              <div className={styles.modalActions}>
                <Button variant="secondary" type="button" onClick={() => setShowAddModal(false)}>Hủy</Button>
                <Button type="submit">Thêm sản phẩm</Button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Popup: Confirm Delete */}
      {deleteTarget && (
        <div className={styles.overlay} onClick={() => setDeleteTarget(null)}>
          <div className={styles.confirmModal} onClick={e => e.stopPropagation()}>
            <div className={styles.confirmIcon}>
              <svg width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="var(--error)" strokeWidth="2">
                <circle cx="12" cy="12" r="10"/><line x1="15" y1="9" x2="9" y2="15"/><line x1="9" y1="9" x2="15" y2="15"/>
              </svg>
            </div>
            <h3 className={styles.confirmTitle}>Xác nhận xóa</h3>
            <p className={styles.confirmDesc}>Bạn có chắc chắn muốn xóa sản phẩm này? Hành động này không thể hoàn tác.</p>
            <div className={styles.confirmActions}>
              <Button variant="secondary" onClick={() => setDeleteTarget(null)}>Hủy</Button>
              <Button variant="danger" onClick={handleDelete}>Xóa sản phẩm</Button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
