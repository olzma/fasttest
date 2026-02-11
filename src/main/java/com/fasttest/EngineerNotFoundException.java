package com.fasttest;

public class EngineerNotFoundException extends RuntimeException {

  public EngineerNotFoundException(String message) {
    super("There is NO engineer available. " + message);
  }

  public EngineerNotFoundException(Integer id) {
    super("Engineer not found with id: " + id);
  }
}
