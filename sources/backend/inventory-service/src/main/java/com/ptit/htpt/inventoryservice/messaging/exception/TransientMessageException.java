package com.ptit.htpt.inventoryservice.messaging.exception;

/**
 * D-08: lỗi tạm thời (DB timeout, network blip, broker tạm trục trặc).
 * Consumer throw exception này → Spring listener retry theo policy D-07 (3 lần exp backoff 1s→2s→4s).
 */
public class TransientMessageException extends RuntimeException {
  public TransientMessageException(String message) {
    super(message);
  }

  public TransientMessageException(String message, Throwable cause) {
    super(message, cause);
  }
}
