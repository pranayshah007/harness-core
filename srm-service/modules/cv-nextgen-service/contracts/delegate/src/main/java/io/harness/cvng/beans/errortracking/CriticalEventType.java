package io.harness.cvng.beans.errortracking;

public enum CriticalEventType {
  ANY,
  CAUGHT_EXCEPTION,
  UNCAUGHT_EXCEPTION,
  SWALLOWED_EXCEPTION,
  LOGGED_ERROR,
  LOGGED_WARNING,
  HTTP_ERROR,
  CUSTOM_ERROR,
}
