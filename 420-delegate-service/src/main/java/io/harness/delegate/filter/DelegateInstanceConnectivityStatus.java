package io.harness.delegate.filter;

// Todo: Temporarily added fields after DISCONNECTED to make backend compatible with current UI. Anupam to remove this
// once UI changes are updated.
public enum DelegateInstanceConnectivityStatus {
  CONNECTED,
  DISCONNECTED,
  ENABLED,
  WAITING_FOR_APPROVAL,
  @Deprecated DISABLED,
  DELETED
}
