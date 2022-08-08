/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.sm.states.azure;

import static io.harness.annotations.dev.HarnessModule._957_CG_BEANS;

import io.harness.annotations.dev.TargetModule;
import io.harness.context.ContextElementType;
import io.harness.delegate.beans.pcf.ResizeStrategy;
import io.harness.delegate.task.azure.AzureVMSSPreDeploymentData;
import io.harness.delegate.task.azure.request.AzureLoadBalancerDetailForBGDeployment;

import software.wings.sm.ContextElement;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@JsonIgnoreProperties(ignoreUnknown = true)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TargetModule(_957_CG_BEANS)
public class AzureVMSSSetupContextElement implements ContextElement {
  private String uuid;
  private String name;
  private String commandName;
  private int instanceCount;
  private String newVirtualMachineScaleSetName;
  private String oldVirtualMachineScaleSetName;
  private Integer autoScalingSteadyStateVMSSTimeout;
  private Integer maxInstances;
  private int oldDesiredCount;
  private int desiredInstances;
  private int minInstances;
  private boolean isBlueGreen;
  private String infraMappingId;
  private ResizeStrategy resizeStrategy;
  private List<String> baseVMSSScalingPolicyJSONs;
  private AzureVMSSPreDeploymentData preDeploymentData;
  private AzureLoadBalancerDetailForBGDeployment azureLoadBalancerDetail;

  @Override
  public ContextElementType getElementType() {
    return ContextElementType.AZURE_VMSS_SETUP;
  }

  @Override
  public ContextElement cloneMin() {
    return null;
  }
}
