package com.example.loop_assessment.service;

import org.springframework.http.HttpStatus;

public class ServiceException extends RuntimeException {
  private final HttpStatus status;
  private final String code;

  public ServiceException(HttpStatus status, String code, String message) {
    super(message);
    this.status = status;
    this.code = code;
  }

  public HttpStatus status() {
    return status;
  }

  public String code() {
    return code;
  }
}
