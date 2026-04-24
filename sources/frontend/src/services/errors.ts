/**
 * ApiError — typed error class for HTTP wrapper.
 *
 * Source: 04-RESEARCH.md §Pattern 1 — mirrors the backend ApiErrorResponse shape
 * (identical keys on service-origin and gateway-origin per Phase 3 D-05..D-07).
 *
 * Consumers: services/http.ts throws ApiError on non-2xx; page dispatchers catch
 * and switch on err.code per UI-SPEC copywriting contract.
 */

export interface FieldError {
  field: string;
  rejectedValue?: unknown;
  message: string;
}

export class ApiError extends Error {
  constructor(
    public readonly code: string,             // 'VALIDATION_ERROR' | 'UNAUTHORIZED' | 'FORBIDDEN' | 'CONFLICT' | 'NOT_FOUND' | 'INTERNAL_ERROR'
    public readonly status: number,           // HTTP status
    message: string,
    public readonly fieldErrors: FieldError[] = [],
    public readonly traceId?: string,
    public readonly path?: string,
    public readonly details?: Record<string, unknown>, // for CONFLICT 'domainCode', 'items'
  ) {
    super(message);
    this.name = 'ApiError';
  }
}

export function isApiError(e: unknown): e is ApiError {
  return e instanceof ApiError;
}
