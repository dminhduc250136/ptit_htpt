/**
 * Shared types for chat helpers + route handlers (Wave 2).
 * Kept independent of pg / anthropic so client components can import safely if needed.
 */

export interface ChatProduct {
  id: string;
  name: string;
  price: number;
  brand: string | null;
  stock: number | null;
}

export interface ChatMessageRow {
  role: 'user' | 'assistant';
  content: string;
}
