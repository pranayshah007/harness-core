/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.azure.arm;

import io.harness.delegate.beans.DelegateMetaInfo;
import io.harness.delegate.beans.logstreaming.UnitProgressData;
import io.harness.logging.CommandExecutionStatus;

import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class AzureARMTaskNGResponse extends AzureResourceCreationTaskNGResponse {
  private String outputs;
  private AzureARMPreDeploymentData preDeploymentData;

  @Builder
  public AzureARMTaskNGResponse(CommandExecutionStatus commandExecutionStatus, UnitProgressData unitProgressData,
      DelegateMetaInfo delegateMetaInfo, String errorMsg, String outputs,
      AzureARMPreDeploymentData azureARMPreDeploymentData) {
    super(commandExecutionStatus, unitProgressData, delegateMetaInfo, errorMsg);
    this.outputs = outputs;
    this.preDeploymentData = azureARMPreDeploymentData;
  }
}
