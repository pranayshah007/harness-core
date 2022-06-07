/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.azure.appservice.deployment.context;

import io.harness.azure.context.AzureWebClientContext;
import io.harness.azure.model.AzureAppServiceApplicationSetting;
import io.harness.azure.model.AzureAppServiceConnectionString;
import io.harness.delegate.task.azure.appservice.AzureAppServicePreDeploymentData;
import io.harness.delegate.task.azure.appservice.deployment.AzureAppServiceDeploymentService;
import io.harness.delegate.task.azure.common.AzureLogCallbackProvider;

import java.util.Map;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class AzureAppServiceDockerDeploymentContext extends AzureAppServiceDeploymentContext {
  private String imagePathAndTag;
  private Map<String, AzureAppServiceApplicationSetting> dockerSettings;

  @Builder
  public AzureAppServiceDockerDeploymentContext(AzureWebClientContext azureWebClientContext,
      AzureLogCallbackProvider logCallbackProvider, Map<String, AzureAppServiceApplicationSetting> appSettingsToAdd,
      Map<String, AzureAppServiceApplicationSetting> appSettingsToRemove,
      Map<String, AzureAppServiceConnectionString> connSettingsToAdd,
      Map<String, AzureAppServiceConnectionString> connSettingsToRemove,
      Map<String, AzureAppServiceApplicationSetting> dockerSettings, String imagePathAndTag, String slotName,
      String targetSlotName, String startupCommand, int steadyStateTimeoutInMin) {
    super(azureWebClientContext, logCallbackProvider, appSettingsToAdd, appSettingsToRemove, connSettingsToAdd,
        connSettingsToRemove, slotName, targetSlotName, startupCommand, steadyStateTimeoutInMin);
    this.dockerSettings = dockerSettings;
    this.imagePathAndTag = imagePathAndTag;
  }

  @Override
  public void deploy(
      AzureAppServiceDeploymentService deploymentService, AzureAppServicePreDeploymentData preDeploymentData) {
    deploymentService.deployDockerImage(this, preDeploymentData);
  }
}
