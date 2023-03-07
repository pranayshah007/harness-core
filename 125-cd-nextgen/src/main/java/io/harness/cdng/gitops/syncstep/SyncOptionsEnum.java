package io.harness.cdng.gitops.syncstep;

public enum SyncOptionsEnum {
  VALIDATE("Validate"),
  CREATE_NAMESPACE("CreateNamespace"),
  PRUNE_LAST("pruneLast"),
  APPLY_OUT_OF_SYNC_ONLY("ApplyOutOfSyncOnly"),
  PRUNE_PROPAGATION_POLICY("PrunePropagationPolicy"),
  REPLACE("Replace");

  private final String value;

  SyncOptionsEnum(String value) {
    this.value = value;
  }

  public String getValue() {
    return value;
  }
}
