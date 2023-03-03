/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.exception;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.configuration.KubernetesCliCommandType;
import io.harness.k8s.ProcessResponse;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
@OwnedBy(CDP)
public class KubernetesCliTaskRuntimeException extends RuntimeException {
  private ProcessResponse processResponse;
  private KubernetesCliCommandType commandType;
  private String kubectlVersion;
  private String resourcesNotApplied;

  public KubernetesCliTaskRuntimeException(String message, KubernetesCliCommandType commandType) {
    this.processResponse = ProcessResponse.builder().errorMessage(message).build();
    this.commandType = commandType;
  }

  public KubernetesCliTaskRuntimeException(ProcessResponse processResponse, KubernetesCliCommandType commandType) {
    this.processResponse = processResponse;
    this.commandType = commandType;
  }
}
