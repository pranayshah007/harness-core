/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.azure.appservice.deployment.context;

import static io.harness.azure.model.AzureConstants.ARTIFACT_FILE_BLANK_ERROR_MSG;
import static io.harness.azure.model.AzureConstants.ARTIFACT_TYPE_BLANK_ERROR_MSG;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.azure.context.AzureWebClientContext;
import io.harness.azure.model.AzureAppServiceApplicationSetting;
import io.harness.azure.model.AzureAppServiceConnectionString;
import io.harness.delegate.task.azure.appservice.AzureAppServicePreDeploymentData;
import io.harness.delegate.task.azure.appservice.deployment.AzureAppServiceDeploymentService;
import io.harness.delegate.task.azure.common.AzureLogCallbackProvider;
import io.harness.delegate.task.azure.common.validator.ArtifactTypeSubset;

import software.wings.utils.ArtifactType;

import com.azure.resourcemanager.appservice.models.DeployOptions;
import com.azure.resourcemanager.appservice.models.DeployType;
import java.io.File;
import java.util.Map;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TargetModule(HarnessModule._930_DELEGATE_TASKS)
@CodePulse(
    module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_AZURE_WEBAPP})
public class AzureAppServicePackageDeploymentContext extends AzureAppServiceDeploymentContext {
  @NotNull(message = ARTIFACT_FILE_BLANK_ERROR_MSG) private File artifactFile;
  @NotNull(message = ARTIFACT_TYPE_BLANK_ERROR_MSG)
  @ArtifactTypeSubset(anyOf = {ArtifactType.ZIP, ArtifactType.WAR, ArtifactType.NUGET, ArtifactType.JAR})
  private ArtifactType artifactType;
  private boolean skipTargetSlotValidation;
  private boolean cleanDeployment;
  private boolean useNewDeployApi;

  @Builder
  public AzureAppServicePackageDeploymentContext(AzureWebClientContext azureWebClientContext,
      AzureLogCallbackProvider logCallbackProvider, Map<String, AzureAppServiceApplicationSetting> appSettingsToAdd,
      Map<String, AzureAppServiceApplicationSetting> appSettingsToRemove,
      Map<String, AzureAppServiceConnectionString> connSettingsToAdd,
      Map<String, AzureAppServiceConnectionString> connSettingsToRemove, String startupCommand, String slotName,
      String targetSlotName, File artifactFile, ArtifactType artifactType, int steadyStateTimeoutInMin,
      boolean isBasicDeployment, boolean skipTargetSlotValidation, boolean cleanDeployment, boolean useNewDeployApi) {
    super(azureWebClientContext, logCallbackProvider, appSettingsToAdd, appSettingsToRemove, connSettingsToAdd,
        connSettingsToRemove, slotName, targetSlotName, startupCommand, steadyStateTimeoutInMin, isBasicDeployment);
    this.artifactFile = artifactFile;
    this.artifactType = artifactType;
    this.skipTargetSlotValidation = skipTargetSlotValidation;
    this.cleanDeployment = cleanDeployment;
    this.useNewDeployApi = useNewDeployApi;
  }

  @Override
  public void deploy(
      AzureAppServiceDeploymentService deploymentService, AzureAppServicePreDeploymentData preDeploymentData) {
    deploymentService.deployPackage(this, preDeploymentData);
  }

  public DeployOptions toDeployOptions() {
    return new DeployOptions().withCleanDeployment(this.cleanDeployment);
  }

  public DeployType getDeployType() {
    switch (this.artifactType) {
      case JAR:
        return DeployType.JAR;
      case WAR:
        return DeployType.WAR;
      default:
        return DeployType.ZIP;
    }
  }
}
