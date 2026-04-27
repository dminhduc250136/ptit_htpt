# Phase 11: Address Book + Order History Filtering - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-04-27
**Phase:** 11-address-book-order-history-filtering
**Areas discussed:** AddressPicker checkout UX, Address fields schema, Order filter page vs tab, Order filter server-side vs client-side

---

## AddressPicker checkout UX

| Option | Description | Selected |
|--------|-------------|----------|
| Snap-fill form | Giữ nguyên manual form. Thêm button/dropdown "Địa chỉ đã lưu" — chọn 1 address → auto-fill form. User vẫn edit được. | ✓ |
| Picker thay thế form | Khi có saved addresses, hiện radio cards thay form. "Nhập thủ công" cuối mới show form. | |

**User's choice:** Snap-fill form (Recommended)

---

### AddressPicker khi 0 saved addresses

| Option | Description | Selected |
|--------|-------------|----------|
| Form thuần, không picker | Ẩn button nếu 0 addresses. Không fetch API. | |
| Vẫn hiện picker, empty state | Button "Địa chỉ đã lưu" vẫn hiện, click → empty state + link sang /profile/addresses. | ✓ |

**User's choice:** Vẫn hiện picker, empty state — cross-sell sang address book

---

## Address fields schema

| Option | Description | Selected |
|--------|-------------|----------|
| Full: tên + SĐT + địa chỉ | fullName, phone, street, ward, district, city. Snap-fill đủ form checkout. | ✓ |
| Chỉ địa lý: street/ward/district/city | fullName + phone lấy từ user profile khi fill. | |

**User's choice:** Full schema (fullName, phone, street, ward, district, city)

---

### Address label/nickname

| Option | Description | Selected |
|--------|-------------|----------|
| Không cần | Hiển thị bằng nối fields. Simple. | ✓ |
| Có label tùy chọn | VARCHAR field 'label' optional ("Địa chỉ nhà", "Văn phòng"). | |

**User's choice:** Không cần label field

---

## Order filter: page vs tab

| Option | Description | Selected |
|--------|-------------|----------|
| Tạo /profile/orders/page.tsx riêng | URL riêng, shareable, URL state encoding, khớp ACCT-02. | ✓ |
| Filter bar trong tab hiện tại | Ít code hơn nhưng URL state khó implement trong tab. | |

**User's choice:** Tạo /profile/orders/page.tsx standalone

---

### Tab 'orders' trong /profile sau khi có dedicated page

| Option | Description | Selected |
|--------|-------------|----------|
| Tab 'orders' redirect sang /profile/orders | Click tab → router.push('/profile/orders'). | ✓ |
| Xóa tab 'orders' khỏi /profile | Profile chỉ còn thông tin cơ bản. | |

**User's choice:** Tab redirect → /profile/orders

---

## Order filter: server-side vs client-side

| Option | Description | Selected |
|--------|-------------|----------|
| Server-side | Backend nhận filter params, query DB. Timezone handle đúng SC-5. | ✓ |
| Client-side | Filter trên browser. Ít backend work nhưng SC-5 timezone khó đảm bảo. | |

**User's choice:** Server-side filtering

---

### Order keyword search scope

| Option | Description | Selected |
|--------|-------------|----------|
| Order ID only | Search `order.id ILIKE ?q?`. Đơn giản, index-friendly. | ✓ |
| Order ID + product name | JOIN order items. Phức tạp hơn nhưng đầy đủ theo ACCT-02. | |

**User's choice:** Order ID only — đủ cho demo scope

---

## Claude's Discretion

- Phone validation regex cho address form
- UI display format cho address trong dropdown picker
- Error handling khi fetch addresses fail ở checkout (silent vs toast)
- Backend sort order cho addresses list

## Deferred Ideas

- Label/nickname cho address ("Nhà", "Văn phòng") — not needed v1.2
- Order keyword search trên product name — deferred, order ID only đủ
- Client-side filtering — rejected (timezone correctness SC-5)
- AddressPicker thay thế form hoàn toàn — rejected (snap-fill đơn giản hơn)
