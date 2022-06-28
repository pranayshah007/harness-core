/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.shell;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.connector.artifactoryconnector.ArtifactoryCapabilityHelper;
import io.harness.delegate.beans.connector.pdcconnector.PhysicalDataCenterConnectorCapabilityHelper;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.beans.executioncapability.ExecutionCapabilityDemander;
import io.harness.delegate.beans.storeconfig.HarnessStoreDelegateConfig;
import io.harness.delegate.beans.storeconfig.StoreDelegateConfig;
import io.harness.delegate.capability.EncryptedDataDetailsCapabilityHelper;
import io.harness.delegate.task.ssh.PdcSshInfraDelegateConfig;
import io.harness.delegate.task.ssh.SshInfraDelegateConfig;
import io.harness.delegate.task.ssh.artifact.ArtifactoryArtifactDelegateConfig;
import io.harness.delegate.task.ssh.artifact.SshWinRmArtifactType;
import io.harness.delegate.task.ssh.config.ConfigFileParameters;
import io.harness.expression.ExpressionEvaluator;

import java.util.ArrayList;
import java.util.List;
import lombok.Value;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@Value
@OwnedBy(CDP)
public class SshCommandTaskParameters extends CommandTaskParameters implements ExecutionCapabilityDemander {
  SshInfraDelegateConfig sshInfraDelegateConfig;
  String host;

  @Override
  public List<ExecutionCapability> fetchRequiredExecutionCapabilities(ExpressionEvaluator maskingEvaluator) {
    List<ExecutionCapability> capabilities = new ArrayList<>();
    if (sshInfraDelegateConfig != null) {
      fetchInfraExecutionCapabilities(capabilities, maskingEvaluator);
    }

    if (artifactDelegateConfig != null) {
      fetchArtifactExecutionCapabilities(capabilities, maskingEvaluator);
    }

    if (fileDelegateConfig != null && isNotEmpty(fileDelegateConfig.getStores())) {
      fetchStoreExecutionCapabilities(capabilities, maskingEvaluator);
    }

    return capabilities;
  }

  private void fetchInfraExecutionCapabilities(
      final List<ExecutionCapability> capabilities, ExpressionEvaluator maskingEvaluator) {
    if (sshInfraDelegateConfig instanceof PdcSshInfraDelegateConfig) {
      PdcSshInfraDelegateConfig pdcSshInfraDelegateConfig = (PdcSshInfraDelegateConfig) sshInfraDelegateConfig;
      if (pdcSshInfraDelegateConfig.getPhysicalDataCenterConnectorDTO() != null) {
        capabilities.addAll(PhysicalDataCenterConnectorCapabilityHelper.fetchRequiredExecutionCapabilities(
            pdcSshInfraDelegateConfig.getPhysicalDataCenterConnectorDTO(), maskingEvaluator));
      }
      capabilities.addAll(EncryptedDataDetailsCapabilityHelper.fetchExecutionCapabilitiesForEncryptedDataDetails(
          pdcSshInfraDelegateConfig.getEncryptionDataDetails(), maskingEvaluator));
    }
  }

  private void fetchArtifactExecutionCapabilities(
      final List<ExecutionCapability> capabilities, ExpressionEvaluator maskingEvaluator) {
    if (SshWinRmArtifactType.ARTIFACTORY.equals(artifactDelegateConfig.getArtifactType())) {
      ArtifactoryArtifactDelegateConfig artifactoryDelegateConfig =
          (ArtifactoryArtifactDelegateConfig) artifactDelegateConfig;
      capabilities.addAll(ArtifactoryCapabilityHelper.fetchRequiredExecutionCapabilities(
          artifactoryDelegateConfig.getConnectorDTO().getConnectorConfig(), maskingEvaluator));
      capabilities.addAll(EncryptedDataDetailsCapabilityHelper.fetchExecutionCapabilitiesForEncryptedDataDetails(
          artifactoryDelegateConfig.getEncryptedDataDetails(), maskingEvaluator));
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
}
