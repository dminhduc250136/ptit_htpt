/* ============================================
   DATA CONTRACTS / TypeScript Interfaces
   These define the "shape" of data exchanged
   between FE and the 3 BE microservices.
   ============================================

   Phase 4-03 audit note — LEGACY hand-written types retained intentionally.

   The backend-derived DTOs below (User, Address, Product, Category, Specification,
   Order, OrderItem, CreateOrderRequest, LoginRequest, RegisterRequest, AuthResponse)
   look like duplicates of `src/types/api/*.generated.ts` but they are NOT removable
   today because:

     1. Pitfall 7 / 04-01 Deviation 2: springdoc emits `never` for every response body
        wrapped by `ApiResponseAdvice` (~1,238 `: never;` occurrences across the 6
        generated files). Every `services/*.ts` module hand-narrows to the types below
        (e.g. `httpGet<PaginatedResponse<Product>>`). Deleting them breaks the build.

     2. `/auth/*` endpoints are not shipped yet (04-WAVE-STATUS.md §Blocker). The
        generated `users.generated.ts` has no `/auth/login`, `/auth/register`,
        `/auth/refresh` paths — LoginRequest / RegisterRequest / AuthResponse can
        ONLY come from here until backend publishes the routes.

   UI-owned types (NOT backend-derived) that stay here permanently:
     - ProductFilter        (FE query-shape for filter UI state)
     - PaginatedResponse<T> (matches Phase 2 D-05 shape exactly; shared across services)
     - CartItem (below) is currently defined here but services/cart.ts writes a FLAT
       shape ({ productId, name, thumbnailUrl, price, quantity }). Phase 5 cleanup:
       either update CartItem below to match the flat shape or move flat shape into
       services/cart.ts as its own exported type.

   Phase 5 cleanup candidates (DO NOT remove now):
     - Backend-derived DTOs collapse to `paths[...]['responses'][...]['content']['application/json']`
       imports once every backend controller gains `@ApiResponse(content=@Content(schema=@Schema(...)))`
       annotations — at that point the generated types stop being `never` and become authoritative.
     - `getFeaturedProducts` / `getNewProducts` / mock-backed getters in services/api.ts can be
       deleted once admin/* pages move off mocks.
   ============================================ */

// ===== USER SERVICE =====
export interface User {
  id: string;
  email: string;
  username?: string;   // thêm — backend UserDto trả username (Phase 6)
  roles?: string;      // thêm — per D-08, middleware đọc user_role cookie từ đây
  fullName: string;    // giữ — có thể dùng bởi profile pages phase khác
  phone?: string;
  avatarUrl?: string;
  hasAvatar?: boolean; // Phase 10 — luôn false ở Phase 10, true khi avatar wire-up Phase 12+
  role: 'CUSTOMER' | 'ADMIN';
  address?: Address;
  createdAt: string;
  updatedAt: string;
}

export interface Address {
  street: string;
  ward: string;
  district: string;
  city: string;
  zipCode?: string;
}

// ===== ADDRESS BOOK (Phase 11 / ACCT-05) =====
/**
 * SavedAddress: persisted address book entry cho user.
 * Khác với Address (checkout snapshot) — có id, userId, fullName, phone, isDefault.
 */
export interface SavedAddress {
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

/** Body để create / update một saved address. */
export interface AddressBody {
  fullName: string;
  phone: string;
  street: string;
  ward: string;
  district: string;
  city: string;
  isDefault?: boolean;
}

export interface LoginRequest {
  email: string;
  password: string;
}

export interface RegisterRequest {
  username: string;   // thêm mới — per D-01
  email: string;
  password: string;
  // fullName và phone REMOVED per D-01 / CONTEXT.md Deferred
}

export interface AuthResponse {
  accessToken: string;
  refreshToken?: string;   // optional — backend Phase 6 không trả field này (deferred)
  user: User;
}


// ===== PRODUCT SERVICE =====
export interface Product {
  id: string;
  name: string;
  slug: string;
  description: string;
  shortDescription: string;
  price: number;
  originalPrice?: number;
  discount?: number;
  images: string[];
  thumbnailUrl: string;
  category: Category;
  brand?: string;
  specifications?: Specification[];
  rating: number;
  reviewCount: number;
  stock: number;
  status: 'ACTIVE' | 'INACTIVE' | 'OUT_OF_STOCK';
  tags?: string[];
  createdAt: string;
  updatedAt: string;
}

export interface Category {
  id: string;
  name: string;
  slug: string;
  description?: string;
  imageUrl?: string;
  parentId?: string;
}

export interface Specification {
  label: string;
  value: string;
}

export interface ProductFilter {
  categoryId?: string;
  keyword?: string;
  minPrice?: number;
  maxPrice?: number;
  sortBy?: 'price_asc' | 'price_desc' | 'newest' | 'popular' | 'rating';
  page?: number;
  size?: number;
}

export interface PaginatedResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  currentPage: number;
  pageSize: number;
  isFirst: boolean;
  isLast: boolean;
}


// ===== ORDER SERVICE =====
export interface CartItem {
  id: string;
  product: Product;
  quantity: number;
  selectedVariant?: string;
}

export interface Cart {
  id: string;
  userId: string;
  items: CartItem[];
  totalAmount: number;
  totalItems: number;
}

export interface Order {
  id: string;
  orderCode?: string;      // FE legacy — backend dùng id làm orderCode
  userId: string;
  items: OrderItem[];
  shippingAddress: Address;
  paymentMethod: 'COD' | 'BANK_TRANSFER' | 'E_WALLET' | string;
  paymentStatus?: 'PENDING' | 'PAID' | 'FAILED' | 'REFUNDED';
  orderStatus?: string;    // optional — backend trả 'status' không phải 'orderStatus'
  status?: string;         // D-10: backend field name
  subtotal?: number;
  shippingFee?: number;
  discount?: number;
  totalAmount?: number;    // FE legacy alias cho total
  total?: number;          // D-10: backend field name
  note?: string;
  createdAt: string;
  updatedAt: string;
}

export interface OrderItem {
  id: string;
  productId: string;
  productName: string;
  productImage?: string;   // optional — backend không trả, FE có thể bỏ trống
  price: number;           // alias cho unitPrice — giữ cho compat
  unitPrice?: number;      // D-10: backend OrderItemDto trả unitPrice
  quantity: number;
  subtotal: number;        // alias cho lineTotal — giữ cho compat
  lineTotal?: number;      // D-10: backend OrderItemDto trả lineTotal
}

export interface CreateOrderRequest {
  // Phase 4-06: per-item unitPrice required by backend CreateOrderCommand (04-05).
  // Cart already carries `price` per item — checkout passes it through as a snapshot
  // so the backend can compute totalAmount server-side.
  items: { productId: string; productName: string; quantity: number; unitPrice: number }[];   // D-06: productName snapshot
  shippingAddress: Address;
  paymentMethod: 'COD' | 'BANK_TRANSFER' | 'E_WALLET';
  note?: string;
}


// ===== REVIEW (Part of Product Service) =====
export interface Review {
  id: string;
  userId: string;
  userName: string;
  userAvatar?: string;
  productId: string;
  rating: number;
  comment: string;
  images?: string[];
  createdAt: string;
}
