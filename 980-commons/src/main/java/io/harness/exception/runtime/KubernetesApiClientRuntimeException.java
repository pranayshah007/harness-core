/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.exception.runtime;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;

import lombok.Data;

@Data
@OwnedBy(CDP)
public class KubernetesApiClientRuntimeException extends RuntimeException {
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

  private KubernetesCertificateType kubernetesCertificateType;

  public KubernetesApiClientRuntimeException(String message, Throwable cause) {
    super(message, cause);
  }

  public KubernetesApiClientRuntimeException(
      String message, Throwable cause, KubernetesCertificateType kubernetesCertificateType) {
    super(message, cause);
    this.kubernetesCertificateType = kubernetesCertificateType;
  }
}
