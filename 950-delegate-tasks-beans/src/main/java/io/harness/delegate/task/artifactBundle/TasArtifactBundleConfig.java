/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.artifactBundle;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.delegate.beans.connector.artifactoryconnector.ArtifactoryCapabilityHelper;
import io.harness.delegate.beans.connector.awsconnector.AwsCapabilityHelper;
import io.harness.delegate.beans.connector.azureartifacts.AzureArtifactsCapabilityHelper;
import io.harness.delegate.beans.connector.bamboo.BambooCapabilityHelper;
import io.harness.delegate.beans.connector.gcp.GcpCapabilityHelper;
import io.harness.delegate.beans.connector.jenkins.JenkinsCapabilityHelper;
import io.harness.delegate.beans.connector.nexusconnector.NexusCapabilityHelper;
import io.harness.delegate.beans.connector.nexusconnector.NexusConnectorDTO;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.capability.EncryptedDataDetailsCapabilityHelper;
import io.harness.delegate.task.pcf.artifact.TasArtifactConfig;
import io.harness.delegate.task.pcf.artifact.TasArtifactType;
import io.harness.delegate.task.pcf.artifact.TasPackageArtifactConfig;
import io.harness.delegate.task.pcf.manifest.TasManifestDelegateConfig;
import io.harness.expression.ExpressionEvaluator;
import io.harness.security.encryption.EncryptedDataDetail;

import java.util.ArrayList;
import java.util.List;
import lombok.Builder;
import lombok.Data;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PCF})
@OwnedBy(CDP)
@Data
@Builder
public class TasArtifactBundleConfig implements ArtifactBundleConfig {
  TasArtifactConfig tasArtifactConfig;
  TasManifestDelegateConfig tasManifestDelegateConfig;

  @Override
  public List<ExecutionCapability> fetchRequiredExecutionCapabilities(ExpressionEvaluator maskingEvaluator) {
    List<EncryptedDataDetail> artifactEncryptionDataDetails = tasArtifactConfig.getEncryptedDataDetails();

    List<ExecutionCapability> capabilities =
        new ArrayList<>(EncryptedDataDetailsCapabilityHelper.fetchExecutionCapabilitiesForEncryptedDataDetails(
            artifactEncryptionDataDetails, maskingEvaluator));
    populateRequestCapabilities(capabilities, maskingEvaluator);
    return capabilities;
  }

  public void populateRequestCapabilities(
      List<ExecutionCapability> capabilities, ExpressionEvaluator maskingEvaluator) {
    if (tasArtifactConfig == null || (tasArtifactConfig.getArtifactType() == TasArtifactType.CONTAINER)) {
      return;
    }
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
