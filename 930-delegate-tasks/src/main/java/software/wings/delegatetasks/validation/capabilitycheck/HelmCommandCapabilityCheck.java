/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks.validation.capabilitycheck;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.delegate.beans.executioncapability.CapabilityResponse;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.task.executioncapability.CapabilityCheck;
import io.harness.delegate.task.helm.HelmCommandResponse;
import io.harness.logging.CommandExecutionStatus;

import software.wings.delegatetasks.validation.capabilities.HelmCommandCapability;
import software.wings.helpers.ext.container.ContainerDeploymentDelegateHelper;
import software.wings.helpers.ext.helm.HelmDeployService;
import software.wings.helpers.ext.helm.request.HelmCommandRequest;

import com.google.inject.Inject;

@TargetModule(HarnessModule._930_DELEGATE_TASKS)
@OwnedBy(CDP)
public class HelmCommandCapabilityCheck implements CapabilityCheck {
  @Inject private HelmDeployService helmDeployService;
  @Inject private ContainerDeploymentDelegateHelper containerDeploymentDelegateHelper;

  @Override
  public CapabilityResponse performCapabilityCheck(ExecutionCapability delegateCapability) {
    HelmCommandCapability capability = (HelmCommandCapability) delegateCapability;
    HelmCommandRequest commandRequest = capability.getCommandRequest();

    if (commandRequest.getContainerServiceParams() != null) {
      String configLocation =
          containerDeploymentDelegateHelper.createAndGetKubeConfigLocation(commandRequest.getContainerServiceParams());
      commandRequest.setKubeConfigLocation(configLocation);
    }

    HelmCommandResponse helmCommandResponse = helmDeployService.ensureHelmInstalled(commandRequest);
    if (helmCommandResponse.getCommandExecutionStatus() == CommandExecutionStatus.SUCCESS) {
      return CapabilityResponse.builder().validated(true).delegateCapability(capability).build();
    }
    return CapabilityResponse.builder().validated(false).delegateCapability(capability).build();
  }
}
