/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.exception.runtime.utils;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;

@OwnedBy(CDP)
public enum KubernetesCertificateType {
  NONE("No Certificate"),
  CA_CERTIFICATE("CA Certificate"),
  CLIENT_CERTIFICATE("Client Certificate"),
  BOTH_CA_AND_CLIENT_CERTIFICATE("CA / Client Certificate");

  private String name;

  KubernetesCertificateType(String name) {
    this.name = name;
  }

  public String getName() {
    return name;
  }
}
