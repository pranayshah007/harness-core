package io.harness.cvng.beans.errortracking;

public enum CriticalEventType {
  ANY("Any"),
  CAUGHT_EXCEPTION("Caught Exceptions"),
  UNCAUGHT_EXCEPTION("Uncaught Exceptions"),
  SWALLOWED_EXCEPTION("Swallowed Exceptions"),
  LOGGED_ERROR("Logged Errors"),
  LOGGED_WARNING("Logged Warnings"),
  HTTP_ERROR("Http Errors"),
  CUSTOM_ERROR("Custom Errors");

  private final String displayName;

  CriticalEventType(String displayName) {
    this.displayName = displayName;
  }

  public String getDisplayName() {
    return this.displayName;
  }
}
