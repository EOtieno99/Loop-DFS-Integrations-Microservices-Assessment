package com.example.loop_assessment.soap_resources;

public class UpstreamException extends RuntimeException {
  private final boolean retryable;

  public UpstreamException(String message, boolean retryable) {
    super(message);
    this.retryable = retryable;
  }

  public UpstreamException(String message, boolean retryable, Throwable cause) {
    super(message, cause);
    this.retryable = retryable;
  }

  public boolean retryable() {
    return retryable;
  }
}
