package com.fasttest;

public class ErrorResponse {
  private int status;
  private String message;
  private long timestamp;

  public ErrorResponse(int status, String message) {
    this.status = status;
    this.message = message;
    this.timestamp = System.currentTimeMillis();
  }

  // Getters
  public int getStatus() {
    return status;
  }

  public String getMessage() {
    return message;
  }

  public long getTimestamp() {
    return timestamp;
  }

  // Getters and setters
}
