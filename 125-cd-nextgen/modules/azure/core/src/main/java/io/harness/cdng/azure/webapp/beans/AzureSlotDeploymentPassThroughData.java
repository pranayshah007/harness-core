/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.azure.webapp.beans;
import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.cdng.artifact.outcome.ArtifactOutcome;
import io.harness.cdng.infra.beans.AzureWebAppInfrastructureOutcome;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfig;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.task.azure.appservice.AzureAppServicePreDeploymentData;
import io.harness.delegate.task.azure.appservice.settings.AppSettingsFile;
import io.harness.pms.sdk.core.steps.io.PassThroughData;

import software.wings.beans.TaskType;

import java.util.Map;
import lombok.Builder;
import lombok.Value;
import org.springframework.data.annotation.TypeAlias;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_K8S})
@Value
@OwnedBy(CDP)
@Builder(toBuilder = true)
@TypeAlias("azureSlotDeploymentPassThroughData")
@RecasterAlias("io.harness.cdng.azure.webapp.beans.AzureSlotDeploymentPassThroughData")
public class AzureSlotDeploymentPassThroughData implements PassThroughData {
  AzureWebAppInfrastructureOutcome infrastructure;
  Map<String, AppSettingsFile> configs;
  Map<String, StoreConfig> unprocessedConfigs;
  AzureAppServicePreDeploymentData preDeploymentData;
  CommandUnitsProgress commandUnitsProgress;
  ArtifactOutcome primaryArtifactOutcome;
  @Builder.Default String taskType = TaskType.AZURE_WEB_APP_TASK_NG.name();
  Boolean cleanDeploymentEnabled;
}
