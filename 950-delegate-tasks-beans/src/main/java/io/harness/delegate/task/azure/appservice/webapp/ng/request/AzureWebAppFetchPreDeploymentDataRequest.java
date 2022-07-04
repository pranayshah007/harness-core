/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.azure.appservice.webapp.ng.request;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.azure.model.AzureAppServiceApplicationSetting;
import io.harness.azure.model.AzureAppServiceConnectionString;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.task.azure.appservice.webapp.ng.AzureWebAppInfraDelegateConfig;
import io.harness.delegate.task.azure.appservice.webapp.ng.AzureWebAppRequestType;
import io.harness.delegate.task.azure.artifact.AzureArtifactConfig;

import java.util.List;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@OwnedBy(CDP)
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class AzureWebAppFetchPreDeploymentDataRequest extends AbstractSlotDataRequest {
  @Builder
  public AzureWebAppFetchPreDeploymentDataRequest(CommandUnitsProgress commandUnitsProgress,
      AzureWebAppInfraDelegateConfig infraDelegateConfig, String startupCommand,
      List<AzureAppServiceApplicationSetting> applicationSettings,
      List<AzureAppServiceConnectionString> connectionStrings, AzureArtifactConfig artifact,
      Integer timeoutIntervalInMin) {
    super(commandUnitsProgress, infraDelegateConfig, startupCommand, applicationSettings, connectionStrings, artifact,
        timeoutIntervalInMin);
  }

  @Override
  public AzureWebAppRequestType getRequestType() {
    return AzureWebAppRequestType.FETCH_PRE_DEPLOYMENT_DATA;
  }
}
