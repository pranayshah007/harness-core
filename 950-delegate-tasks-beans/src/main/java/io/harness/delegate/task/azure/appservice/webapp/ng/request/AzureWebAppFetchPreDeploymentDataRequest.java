/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.azure.appservice.webapp.ng.request;
import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.task.azure.appservice.settings.AppSettingsFile;
import io.harness.delegate.task.azure.appservice.webapp.ng.AzureWebAppInfraDelegateConfig;
import io.harness.delegate.task.azure.appservice.webapp.ng.AzureWebAppRequestType;
import io.harness.delegate.task.azure.artifact.AzureArtifactConfig;

import java.util.Collections;
import java.util.Set;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_K8S})
@Data
@OwnedBy(CDP)
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class AzureWebAppFetchPreDeploymentDataRequest extends AbstractSlotDataRequest {
  @Builder
  public AzureWebAppFetchPreDeploymentDataRequest(String accountId, CommandUnitsProgress commandUnitsProgress,
      AzureWebAppInfraDelegateConfig infraDelegateConfig, AppSettingsFile startupCommand,
      AppSettingsFile applicationSettings, AppSettingsFile connectionStrings, AzureArtifactConfig artifact,
      Integer timeoutIntervalInMin) {
    super(accountId, commandUnitsProgress, infraDelegateConfig, startupCommand, applicationSettings, connectionStrings,
        artifact, timeoutIntervalInMin);
  }

  @Override
  public Set<String> getPrevExecUserAddedAppSettingNames() {
    return Collections.emptySet();
  }

  @Override
  public Set<String> getPrevExecUserAddedConnStringNames() {
    return Collections.emptySet();
  }

  @Override
  public boolean isPrevExecUserChangedStartupCommand() {
    return false;
  }

  @Override
  public boolean isCleanDeployment() {
    return false;
  }

  @Override
  public AzureWebAppRequestType getRequestType() {
    return AzureWebAppRequestType.FETCH_PRE_DEPLOYMENT_DATA;
  }
}
