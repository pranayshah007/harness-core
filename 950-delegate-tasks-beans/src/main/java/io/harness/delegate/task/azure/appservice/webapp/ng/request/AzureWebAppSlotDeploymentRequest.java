/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.azure.appservice.webapp.ng.request;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.expression.Expression.ALLOW_SECRETS;

import io.harness.annotations.dev.OwnedBy;
import io.harness.azure.model.AzureAppServiceApplicationSetting;
import io.harness.azure.model.AzureAppServiceConnectionString;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.task.azure.appservice.AzureAppServicePreDeploymentData;
import io.harness.delegate.task.azure.appservice.webapp.ng.AzureWebAppInfraDelegateConfig;
import io.harness.delegate.task.azure.appservice.webapp.ng.AzureWebAppRequestType;
import io.harness.delegate.task.azure.artifact.AzureArtifactConfig;
import io.harness.expression.Expression;

import java.util.List;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@OwnedBy(CDP)
@EqualsAndHashCode(callSuper = true)
public class AzureWebAppSlotDeploymentRequest extends AbstractWebAppTaskRequest {
  private AzureAppServicePreDeploymentData preDeploymentData;
  @Expression(ALLOW_SECRETS) private String startupCommand;
  @Expression(ALLOW_SECRETS) private List<AzureAppServiceApplicationSetting> applicationSettings;
  @Expression(ALLOW_SECRETS) private List<AzureAppServiceConnectionString> connectionStrings;
  private AzureArtifactConfig artifact;
  private Integer timeoutIntervalInMin;

  @Builder
  public AzureWebAppSlotDeploymentRequest(AzureAppServicePreDeploymentData preDeploymentData,
      CommandUnitsProgress commandUnitsProgress, AzureWebAppInfraDelegateConfig infrastructure, String startupCommand,
      List<AzureAppServiceApplicationSetting> applicationSettings,
      List<AzureAppServiceConnectionString> connectionStrings, AzureArtifactConfig artifact,
      Integer timeoutIntervalInMin) {
    super(commandUnitsProgress, infrastructure);
    this.preDeploymentData = preDeploymentData;
    this.startupCommand = startupCommand;
    this.applicationSettings = applicationSettings;
    this.connectionStrings = connectionStrings;
    this.artifact = artifact;
    this.timeoutIntervalInMin = timeoutIntervalInMin;
  }

  @Override
  public AzureWebAppRequestType getRequestType() {
    return AzureWebAppRequestType.SLOT_DEPLOYMENT;
  }
}
