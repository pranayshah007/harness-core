/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.pcf.request;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.expression.Expression.ALLOW_SECRETS;

import static java.lang.String.format;

import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.connector.artifactoryconnector.ArtifactoryCapabilityHelper;
import io.harness.delegate.beans.connector.awsconnector.AwsCapabilityHelper;
import io.harness.delegate.beans.connector.azureartifacts.AzureArtifactsCapabilityHelper;
import io.harness.delegate.beans.connector.azureconnector.AzureCapabilityHelper;
import io.harness.delegate.beans.connector.bamboo.BambooCapabilityHelper;
import io.harness.delegate.beans.connector.docker.DockerCapabilityHelper;
import io.harness.delegate.beans.connector.gcp.GcpCapabilityHelper;
import io.harness.delegate.beans.connector.jenkins.JenkinsCapabilityHelper;
import io.harness.delegate.beans.connector.nexusconnector.NexusCapabilityHelper;
import io.harness.delegate.beans.connector.nexusconnector.NexusConnectorDTO;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.beans.executioncapability.PcfAutoScalarCapability;
import io.harness.delegate.beans.executioncapability.PcfInstallationCapability;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.task.pcf.CfCommandTypeNG;
import io.harness.delegate.task.pcf.artifact.TasArtifactConfig;
import io.harness.delegate.task.pcf.artifact.TasArtifactType;
import io.harness.delegate.task.pcf.artifact.TasContainerArtifactConfig;
import io.harness.delegate.task.pcf.artifact.TasPackageArtifactConfig;
import io.harness.delegate.task.pcf.response.TasInfraConfig;
import io.harness.expression.Expression;
import io.harness.expression.ExpressionEvaluator;
import io.harness.pcf.model.CfCliVersion;

import java.util.List;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@OwnedBy(CDP)
@EqualsAndHashCode(callSuper = true)
public class CfBlueGreenSetupRequestNG extends AbstractTasTaskRequest {
  @Expression(ALLOW_SECRETS) String releaseNamePrefix;
  TasArtifactConfig tasArtifactConfig;
  Integer olderActiveVersionCountToKeep;
  Integer maxCount;
  Integer currentRunningCount;
  boolean useCurrentCount;
  @Expression(ALLOW_SECRETS) List<String> tempRoutes;
  @Expression(ALLOW_SECRETS) List<String> routeMaps;
  boolean useAppAutoScalar;
  @Expression(ALLOW_SECRETS) TasManifestsPackage tasManifestsPackage;

  @Builder
  public CfBlueGreenSetupRequestNG(String accountId, CfCommandTypeNG cfCommandTypeNG, String commandName,
      CommandUnitsProgress commandUnitsProgress, TasInfraConfig tasInfraConfig, boolean useCfCLI,
      CfCliVersion cfCliVersion, Integer timeoutIntervalInMin, String releaseNamePrefix,
      TasArtifactConfig tasArtifactConfig, Integer olderActiveVersionCountToKeep, Integer maxCount,
      Integer currentRunningCount, boolean useCurrentCount, List<String> routeMaps, boolean useAppAutoScalar,
      TasManifestsPackage tasManifestsPackage, List<String> tempRoutes) {
    super(timeoutIntervalInMin, accountId, commandName, cfCommandTypeNG, commandUnitsProgress, tasInfraConfig, useCfCLI,
        cfCliVersion);
    this.releaseNamePrefix = releaseNamePrefix;
    this.tasArtifactConfig = tasArtifactConfig;
    this.olderActiveVersionCountToKeep = olderActiveVersionCountToKeep;
    this.maxCount = maxCount;
    this.currentRunningCount = currentRunningCount;
    this.useCurrentCount = useCurrentCount;
    this.routeMaps = routeMaps;
    this.useAppAutoScalar = useAppAutoScalar;
    this.tasManifestsPackage = tasManifestsPackage;
    this.tempRoutes = tempRoutes;
  }

  @Override
  public void populateRequestCapabilities(
      List<ExecutionCapability> capabilities, ExpressionEvaluator maskingEvaluator) {
    if (useCfCLI || useAppAutoScalar) {
      capabilities.add(PcfInstallationCapability.builder()
                           .criteria(format("Checking that CF CLI version: %s is installed", cfCliVersion))
                           .version(cfCliVersion)
                           .build());
    }
    if (useAppAutoScalar) {
      capabilities.add(PcfAutoScalarCapability.builder()
                           .version(cfCliVersion)
                           .criteria("Checking that App Autoscaler plugin is installed")
                           .build());
    }
    if (tasArtifactConfig != null) {
      if (tasArtifactConfig.getArtifactType() == TasArtifactType.CONTAINER) {
        TasContainerArtifactConfig azureContainerArtifactConfig = (TasContainerArtifactConfig) tasArtifactConfig;
        switch (azureContainerArtifactConfig.getRegistryType()) {
          case DOCKER_HUB_PUBLIC:
          case DOCKER_HUB_PRIVATE:
            capabilities.addAll(DockerCapabilityHelper.fetchRequiredExecutionCapabilities(
                azureContainerArtifactConfig.getConnectorConfig(), maskingEvaluator));
            break;
          case ARTIFACTORY_PRIVATE_REGISTRY:
            capabilities.addAll(ArtifactoryCapabilityHelper.fetchRequiredExecutionCapabilities(
                azureContainerArtifactConfig.getConnectorConfig(), maskingEvaluator));
            break;
          case ACR:
            capabilities.addAll(AzureCapabilityHelper.fetchRequiredExecutionCapabilities(
                azureContainerArtifactConfig.getConnectorConfig(), maskingEvaluator));
            break;
          default:
            // no capabilities to add
        }
      } else if (tasArtifactConfig.getArtifactType() == TasArtifactType.PACKAGE) {
        TasPackageArtifactConfig azurePackageArtifactConfig = (TasPackageArtifactConfig) tasArtifactConfig;
        switch (azurePackageArtifactConfig.getSourceType()) {
          case ARTIFACTORY_REGISTRY:
            capabilities.addAll(ArtifactoryCapabilityHelper.fetchRequiredExecutionCapabilities(
                azurePackageArtifactConfig.getConnectorConfig(), maskingEvaluator));
            break;
          case AMAZONS3:
            capabilities.addAll(AwsCapabilityHelper.fetchRequiredExecutionCapabilities(
                azurePackageArtifactConfig.getConnectorConfig(), maskingEvaluator));
            break;
          case NEXUS3_REGISTRY:
            capabilities.addAll(NexusCapabilityHelper.fetchRequiredExecutionCapabilities(
                maskingEvaluator, (NexusConnectorDTO) azurePackageArtifactConfig.getConnectorConfig()));
            break;
          case JENKINS:
            capabilities.addAll(JenkinsCapabilityHelper.fetchRequiredExecutionCapabilities(
                azurePackageArtifactConfig.getConnectorConfig(), maskingEvaluator));
            break;
          case AZURE_ARTIFACTS:
            capabilities.addAll(AzureArtifactsCapabilityHelper.fetchRequiredExecutionCapabilities(
                azurePackageArtifactConfig.getConnectorConfig(), maskingEvaluator));
            break;
          case BAMBOO:
            capabilities.addAll(BambooCapabilityHelper.fetchRequiredExecutionCapabilities(
                azurePackageArtifactConfig.getConnectorConfig(), maskingEvaluator));
            break;
          case GOOGLE_CLOUD_STORAGE_ARTIFACT:
            capabilities.addAll(GcpCapabilityHelper.fetchRequiredExecutionCapabilities(
                azurePackageArtifactConfig.getConnectorConfig(), maskingEvaluator));
            break;
          default:
            // no capabilities to add
        }
      }
    }
  }
}