/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.shell;
import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.expression.Expression.ALLOW_SECRETS;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.delegate.beans.connector.artifactoryconnector.ArtifactoryCapabilityHelper;
import io.harness.delegate.beans.connector.awsconnector.AwsCapabilityHelper;
import io.harness.delegate.beans.connector.jenkins.JenkinsCapabilityHelper;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.beans.executioncapability.ExecutionCapabilityDemander;
import io.harness.delegate.beans.storeconfig.HarnessStoreDelegateConfig;
import io.harness.delegate.beans.storeconfig.StoreDelegateConfig;
import io.harness.delegate.capability.EncryptedDataDetailsCapabilityHelper;
import io.harness.delegate.task.TaskParameters;
import io.harness.delegate.task.ssh.NgCommandUnit;
import io.harness.delegate.task.ssh.artifact.ArtifactoryArtifactDelegateConfig;
import io.harness.delegate.task.ssh.artifact.AwsS3ArtifactDelegateConfig;
import io.harness.delegate.task.ssh.artifact.JenkinsArtifactDelegateConfig;
import io.harness.delegate.task.ssh.artifact.SshWinRmArtifactDelegateConfig;
import io.harness.delegate.task.ssh.artifact.SshWinRmArtifactType;
import io.harness.delegate.task.ssh.config.ConfigFileParameters;
import io.harness.delegate.task.ssh.config.FileDelegateConfig;
import io.harness.expression.Expression;
import io.harness.expression.ExpressionEvaluator;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.AccessLevel;
import lombok.Builder.Default;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@CodePulse(
    module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_TRADITIONAL})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SuperBuilder
@OwnedBy(CDP)
public abstract class CommandTaskParameters implements TaskParameters, ExecutionCapabilityDemander {
  String accountId;
  String executionId;
  Long sessionTimeout;
  @Default @Expression(ALLOW_SECRETS) Map<String, String> environmentVariables = new HashMap<>();
  boolean executeOnDelegate;
  @Expression(ALLOW_SECRETS) List<NgCommandUnit> commandUnits;
  SshWinRmArtifactDelegateConfig artifactDelegateConfig;
  @Expression(ALLOW_SECRETS) FileDelegateConfig fileDelegateConfig;
  @Expression(ALLOW_SECRETS) List<String> outputVariables;
  @Expression(ALLOW_SECRETS) List<String> secretOutputVariables;

  @Override
  public List<ExecutionCapability> fetchRequiredExecutionCapabilities(ExpressionEvaluator maskingEvaluator) {
    List<ExecutionCapability> capabilities = new ArrayList<>();
    if (artifactDelegateConfig != null) {
      fetchArtifactExecutionCapabilities(capabilities, maskingEvaluator);
    }

    if (fileDelegateConfig != null && isNotEmpty(fileDelegateConfig.getStores())) {
      fetchStoreExecutionCapabilities(capabilities, maskingEvaluator);
    }

    if (!executeOnDelegate) {
      fetchInfraExecutionCapabilities(capabilities, maskingEvaluator);
    }

    return capabilities;
  }

  private void fetchArtifactExecutionCapabilities(
      final List<ExecutionCapability> capabilities, ExpressionEvaluator maskingEvaluator) {
    if (SshWinRmArtifactType.ARTIFACTORY.equals(artifactDelegateConfig.getArtifactType())
        && artifactDelegateConfig instanceof ArtifactoryArtifactDelegateConfig) {
      ArtifactoryArtifactDelegateConfig artifactoryDelegateConfig =
          (ArtifactoryArtifactDelegateConfig) artifactDelegateConfig;
      capabilities.addAll(ArtifactoryCapabilityHelper.fetchRequiredExecutionCapabilities(
          artifactoryDelegateConfig.getConnectorDTO().getConnectorConfig(), maskingEvaluator));
      capabilities.addAll(EncryptedDataDetailsCapabilityHelper.fetchExecutionCapabilitiesForEncryptedDataDetails(
          artifactoryDelegateConfig.getEncryptedDataDetails(), maskingEvaluator));
    } else if (SshWinRmArtifactType.JENKINS.equals(artifactDelegateConfig.getArtifactType())
        && artifactDelegateConfig instanceof JenkinsArtifactDelegateConfig) {
      JenkinsArtifactDelegateConfig jenkinsDelegateConfig = (JenkinsArtifactDelegateConfig) artifactDelegateConfig;
      capabilities.addAll(JenkinsCapabilityHelper.fetchRequiredExecutionCapabilities(
          jenkinsDelegateConfig.getConnectorDTO().getConnectorConfig(), maskingEvaluator));
      capabilities.addAll(EncryptedDataDetailsCapabilityHelper.fetchExecutionCapabilitiesForEncryptedDataDetails(
          jenkinsDelegateConfig.getEncryptedDataDetails(), maskingEvaluator));
    } else if (SshWinRmArtifactType.AWS_S3.equals(artifactDelegateConfig.getArtifactType())
        && artifactDelegateConfig instanceof AwsS3ArtifactDelegateConfig) {
      AwsS3ArtifactDelegateConfig awsS3DelegateConfig = (AwsS3ArtifactDelegateConfig) artifactDelegateConfig;
      capabilities.addAll(AwsCapabilityHelper.fetchRequiredExecutionCapabilities(
          awsS3DelegateConfig.getAwsConnector(), maskingEvaluator));
      capabilities.addAll(EncryptedDataDetailsCapabilityHelper.fetchExecutionCapabilitiesForEncryptedDataDetails(
          awsS3DelegateConfig.getEncryptionDetails(), maskingEvaluator));
    }
  }

  private void fetchStoreExecutionCapabilities(
      final List<ExecutionCapability> capabilities, ExpressionEvaluator maskingEvaluator) {
    for (StoreDelegateConfig store : fileDelegateConfig.getStores()) {
      if (store instanceof HarnessStoreDelegateConfig) {
        HarnessStoreDelegateConfig harnessStoreDelegateConfig = (HarnessStoreDelegateConfig) store;
        for (ConfigFileParameters configFile : harnessStoreDelegateConfig.getConfigFiles()) {
          if (configFile.isEncrypted()) {
            capabilities.addAll(EncryptedDataDetailsCapabilityHelper.fetchExecutionCapabilitiesForEncryptedDataDetails(
                configFile.getEncryptionDataDetails(), maskingEvaluator));
          }
        }
      }
    }
  }

  public abstract void fetchInfraExecutionCapabilities(
      List<ExecutionCapability> capabilities, ExpressionEvaluator maskingEvaluator);
}
