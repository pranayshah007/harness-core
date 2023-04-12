package io.harness.service.impl.stackdriver;

public enum ErrorHack {
  ERROR_IN_CALL_EXECUTION("error executing rest call");

  private String value;
  ErrorHack(String param) {
    this.value = param;
  }

  public String getValue() {
    return value;
  }
}