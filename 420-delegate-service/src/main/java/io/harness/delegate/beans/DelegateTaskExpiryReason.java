package io.harness.delegate.beans;

public enum DelegateTaskExpiryReason {
  DELEGATE_DISCONNECTED("Delegate disconnected while executing the task"),
  DELEGATE_RESTARTED("Delegate restarted while executing the task");

  private String message;

  DelegateTaskExpiryReason(String message) {
    this.message = message;
  }

  public String getMessage() {
    return message;
  }
}
