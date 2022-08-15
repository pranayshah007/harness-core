/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.shell;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.connector.awsconnector.AwsCapabilityHelper;
import io.harness.delegate.beans.connector.azureconnector.AzureCapabilityHelper;
import io.harness.delegate.beans.connector.pdcconnector.PhysicalDataCenterConnectorCapabilityHelper;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.capability.EncryptedDataDetailsCapabilityHelper;
import io.harness.delegate.task.ssh.AwsSshInfraDelegateConfig;
import io.harness.delegate.task.ssh.AzureSshInfraDelegateConfig;
import io.harness.delegate.task.ssh.PdcSshInfraDelegateConfig;
import io.harness.delegate.task.ssh.SshInfraDelegateConfig;
import io.harness.expression.ExpressionEvaluator;

import java.util.List;
import lombok.Value;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@Value
@OwnedBy(CDP)
public class SshCommandTaskParameters extends CommandTaskParameters {
  SshInfraDelegateConfig sshInfraDelegateConfig;
  String host;

  @Override
  public void fetchInfraExecutionCapabilities(
      final List<ExecutionCapability> capabilities, ExpressionEvaluator maskingEvaluator) {
    if (sshInfraDelegateConfig == null) {
      return;
    }

    if (sshInfraDelegateConfig instanceof PdcSshInfraDelegateConfig) {
      PdcSshInfraDelegateConfig pdcSshInfraDelegateConfig = (PdcSshInfraDelegateConfig) sshInfraDelegateConfig;
      if (pdcSshInfraDelegateConfig.getPhysicalDataCenterConnectorDTO() != null) {
        capabilities.addAll(PhysicalDataCenterConnectorCapabilityHelper.fetchRequiredExecutionCapabilities(
            pdcSshInfraDelegateConfig.getPhysicalDataCenterConnectorDTO(), maskingEvaluator));
      }
      capabilities.addAll(EncryptedDataDetailsCapabilityHelper.fetchExecutionCapabilitiesForEncryptedDataDetails(
          pdcSshInfraDelegateConfig.getEncryptionDataDetails(), maskingEvaluator));
    } else if (sshInfraDelegateConfig instanceof AzureSshInfraDelegateConfig) {
      AzureSshInfraDelegateConfig azureSshInfraDelegateConfig = (AzureSshInfraDelegateConfig) sshInfraDelegateConfig;
      if (azureSshInfraDelegateConfig.getAzureConnectorDTO() != null) {
        capabilities.addAll(AzureCapabilityHelper.fetchRequiredExecutionCapabilities(
            azureSshInfraDelegateConfig.getAzureConnectorDTO(), maskingEvaluator));
      }
      capabilities.addAll(EncryptedDataDetailsCapabilityHelper.fetchExecutionCapabilitiesForEncryptedDataDetails(
          azureSshInfraDelegateConfig.getEncryptionDataDetails(), maskingEvaluator));
    } else if (sshInfraDelegateConfig instanceof AwsSshInfraDelegateConfig) {
      AwsSshInfraDelegateConfig awsSshInfraDelegateConfig = (AwsSshInfraDelegateConfig) sshInfraDelegateConfig;
      if (awsSshInfraDelegateConfig.getAwsConnectorDTO() != null) {
        capabilities.addAll(AwsCapabilityHelper.fetchRequiredExecutionCapabilities(
            awsSshInfraDelegateConfig.getAwsConnectorDTO(), maskingEvaluator));
      }
      capabilities.addAll(EncryptedDataDetailsCapabilityHelper.fetchExecutionCapabilitiesForEncryptedDataDetails(
          awsSshInfraDelegateConfig.getEncryptionDataDetails(), maskingEvaluator));
    }
  }
}
