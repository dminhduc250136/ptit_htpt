/* ============================================
   DATA CONTRACTS / TypeScript Interfaces
   These define the "shape" of data exchanged
   between FE and the 3 BE microservices.
   ============================================ */

// ===== USER SERVICE =====
export interface User {
  id: string;
  email: string;
  fullName: string;
  phone?: string;
  avatarUrl?: string;
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

export interface LoginRequest {
  email: string;
  password: string;
}

export interface RegisterRequest {
  email: string;
  password: string;
  fullName: string;
  phone?: string;
}

export interface AuthResponse {
  accessToken: string;
  refreshToken: string;
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
  orderCode: string;
  userId: string;
  items: OrderItem[];
  shippingAddress: Address;
  paymentMethod: 'COD' | 'BANK_TRANSFER' | 'E_WALLET';
  paymentStatus: 'PENDING' | 'PAID' | 'FAILED' | 'REFUNDED';
  orderStatus: 'PENDING' | 'CONFIRMED' | 'SHIPPING' | 'DELIVERED' | 'CANCELLED' | 'RETURNED';
  subtotal: number;
  shippingFee: number;
  discount: number;
  totalAmount: number;
  note?: string;
  createdAt: string;
  updatedAt: string;
}

export interface OrderItem {
  id: string;
  productId: string;
  productName: string;
  productImage: string;
  price: number;
  quantity: number;
  subtotal: number;
}

export interface CreateOrderRequest {
  items: { productId: string; quantity: number }[];
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
