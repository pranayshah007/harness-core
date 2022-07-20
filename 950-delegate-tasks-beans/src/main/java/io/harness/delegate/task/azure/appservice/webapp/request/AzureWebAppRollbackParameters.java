/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.azure.appservice.webapp.request;

import static io.harness.delegate.task.azure.appservice.AzureAppServiceTaskParameters.AzureAppServiceTaskType.SLOT_ROLLBACK;
import static io.harness.delegate.task.azure.appservice.AzureAppServiceTaskParameters.AzureAppServiceType.WEB_APP;

import io.harness.delegate.task.azure.appservice.AzureAppServicePreDeploymentData;
import io.harness.delegate.task.azure.appservice.AzureAppServiceTaskParameters;

import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class AzureWebAppRollbackParameters extends AzureAppServiceTaskParameters {
  private AzureAppServicePreDeploymentData preDeploymentData;
  private boolean blueGreen;
  private boolean isBasicDeployment;

  @Builder
  public AzureWebAppRollbackParameters(String appId, String accountId, String activityId, String subscriptionId,
      String resourceGroupName, String appName, AzureAppServicePreDeploymentData preDeploymentData, String commandName,
      Integer timeoutIntervalInMin, boolean blueGreen, boolean isBasicDeployment) {
    super(appId, accountId, activityId, subscriptionId, resourceGroupName, appName, commandName, timeoutIntervalInMin,
        SLOT_ROLLBACK, WEB_APP);
    this.preDeploymentData = preDeploymentData;
    this.blueGreen = blueGreen;
    this.isBasicDeployment = isBasicDeployment;
  }
}
