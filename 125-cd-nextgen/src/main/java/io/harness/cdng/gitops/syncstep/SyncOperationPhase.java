/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */
package io.harness.cdng.gitops.syncstep;

public enum SyncOperationPhase {
  RUNNING("Running"),
  FAILED("Failed"),
  ERROR("Error"),
  SUCCEEDED("Succeeded"),
  TERMINATING("Terminating");

  private final String value;

  SyncOperationPhase(String value) {
    this.value = value;
  }

  public String getValue() {
    return value;
  }
}
