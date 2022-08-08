/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.sm.states.azure.appservices;

import static io.harness.annotations.dev.HarnessModule._957_CG_BEANS;

import io.harness.annotations.dev.TargetModule;
import io.harness.beans.SweepingOutput;
import io.harness.context.ContextElementType;
import io.harness.delegate.task.azure.appservice.AzureAppServicePreDeploymentData;

import software.wings.sm.ContextElement;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonTypeName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@JsonIgnoreProperties(ignoreUnknown = true)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonTypeName("azureAppServiceSlotSetupContextElement")
@TargetModule(_957_CG_BEANS)
public class AzureAppServiceSlotSetupContextElement implements ContextElement, SweepingOutput {
  private String uuid;
  private String name;
  private String commandName;
  private String subscriptionId;
  private String resourceGroup;
  private String webApp;
  private String deploymentSlot;
  private String targetSlot;
  private Integer appServiceSlotSetupTimeOut;
  private String infraMappingId;
  private AzureAppServicePreDeploymentData preDeploymentData;
  public static final String SWEEPING_OUTPUT_APP_SERVICE = "setupSweepingOutputAppService";

  @Override
  public ContextElementType getElementType() {
    return ContextElementType.AZURE_WEBAPP_SETUP;
  }

  @Override
  public ContextElement cloneMin() {
    return null;
  }

  @Override
  public String getType() {
    return "azureAppServiceSlotSetupContextElement";
  }
}
