/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.k8s.model;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

@OwnedBy(HarnessTeam.CDP)
public enum ServiceHookContext {
  MANIFEST_FILES_DIRECTORY("MANIFEST_FILES_DIRECTORY"),
  WORKLOADS_LIST("WORKLOADS_LIST"),
  MANAGED_WORKLOADS("MANAGED_WORKLOADS"),
  CUSTOM_WORKLOADS("CUSTOM_WORKLOADS"),
  GOOGLE_APPLICATION_CREDENTIALS("GOOGLE_APPLICATION_CREDENTIALS"),
  KUBE_CONFIG("KUBE_CONFIG");
  private String name;

  ServiceHookContext(String hookContext) {
    this.name = hookContext;
  }

  public String getContextName() {
    return name;
  }
}
