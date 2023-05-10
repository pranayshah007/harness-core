/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.exception.runtime;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.runtime.utils.KubernetesCertificateType;

import lombok.Data;

@Data
@OwnedBy(CDP)
public class KubernetesApiClientRuntimeException extends RuntimeException {
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
