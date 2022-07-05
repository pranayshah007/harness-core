package io.harness.delegate.beans;

public enum DelegateTaskExpiryReason {
  DELEGATE_DISCONNECTED("Delegate disconnected while executing the task"),
  DELEGATE_RESTARTED("Delegate restarted while executing the task"),
  REBROADCAST_LIMIT_REACHED("No delegate acquired the task within the broadcast limit");

  private String message;

  DelegateTaskExpiryReason(String message) {
    this.message = message;
  }

  public String getMessage() {
    return message;
  }
}
