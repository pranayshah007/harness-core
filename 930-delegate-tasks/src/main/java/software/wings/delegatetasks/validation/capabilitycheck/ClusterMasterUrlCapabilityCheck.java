/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks.validation.capabilitycheck;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.delegate.beans.executioncapability.CapabilityResponse;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.task.executioncapability.CapabilityCheck;
import io.harness.network.Http;

import software.wings.delegatetasks.validation.capabilities.ClusterMasterUrlValidationCapability;
import software.wings.delegatetasks.validation.container.ContainerValidationHelper;

import com.google.inject.Inject;

@CodePulse(
    module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_COMMON_STEPS})
@TargetModule(HarnessModule._930_DELEGATE_TASKS)
public class ClusterMasterUrlCapabilityCheck implements CapabilityCheck {
  @Inject ContainerValidationHelper containerValidationHelper;

  @Override
  public CapabilityResponse performCapabilityCheck(ExecutionCapability delegateCapability) {
    ClusterMasterUrlValidationCapability capability = (ClusterMasterUrlValidationCapability) delegateCapability;
    String masterUrl = containerValidationHelper.getK8sMasterUrl(capability.getContainerServiceParams());
    boolean valid = Http.connectableHttpUrl(masterUrl, false);
    return CapabilityResponse.builder().delegateCapability(capability).validated(valid).build();
  }
}
