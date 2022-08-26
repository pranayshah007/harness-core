/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.shell;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.delegate.task.utils.PhysicalDataCenterConstants.DEFAULT_WINRM_PORT;

import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.connector.awsconnector.AwsCapabilityHelper;
import io.harness.delegate.beans.connector.azureconnector.AzureCapabilityHelper;
import io.harness.delegate.beans.connector.pdcconnector.PhysicalDataCenterConnectorCapabilityHelper;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.capability.EncryptedDataDetailsCapabilityHelper;
import io.harness.delegate.task.ssh.AwsWinrmInfraDelegateConfig;
import io.harness.delegate.task.ssh.AzureWinrmInfraDelegateConfig;
import io.harness.delegate.task.ssh.PdcWinRmInfraDelegateConfig;
import io.harness.delegate.task.ssh.WinRmInfraDelegateConfig;
import io.harness.expression.ExpressionEvaluator;

import java.util.List;
import lombok.Getter;
import lombok.Value;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@Value
@Getter
@OwnedBy(CDP)
public class WinrmTaskParameters extends CommandTaskParameters {
  String host;
  WinRmInfraDelegateConfig winRmInfraDelegateConfig;
  boolean disableWinRMCommandEncodingFFSet;
  boolean useWinRMKerberosUniqueCacheFile;

  @Override
  public void fetchInfraExecutionCapabilities(
      final List<ExecutionCapability> capabilities, ExpressionEvaluator maskingEvaluator) {
    if (winRmInfraDelegateConfig == null) {
      return;
    }

    if (winRmInfraDelegateConfig instanceof PdcWinRmInfraDelegateConfig) {
      PdcWinRmInfraDelegateConfig pdcSshInfraDelegateConfig = (PdcWinRmInfraDelegateConfig) winRmInfraDelegateConfig;
      if (pdcSshInfraDelegateConfig.getPhysicalDataCenterConnectorDTO() != null) {
        capabilities.addAll(PhysicalDataCenterConnectorCapabilityHelper.fetchRequiredExecutionCapabilities(
            pdcSshInfraDelegateConfig.getPhysicalDataCenterConnectorDTO(), maskingEvaluator,
            getDefaultPort(pdcSshInfraDelegateConfig)));
      }
      capabilities.addAll(EncryptedDataDetailsCapabilityHelper.fetchExecutionCapabilitiesForEncryptedDataDetails(
          pdcSshInfraDelegateConfig.getEncryptionDataDetails(), maskingEvaluator));
    } else if (winRmInfraDelegateConfig instanceof AzureWinrmInfraDelegateConfig) {
      AzureWinrmInfraDelegateConfig azureWinrmInfraDelegateConfig =
          (AzureWinrmInfraDelegateConfig) winRmInfraDelegateConfig;
      if (azureWinrmInfraDelegateConfig.getAzureConnectorDTO() != null) {
        capabilities.addAll(AzureCapabilityHelper.fetchRequiredExecutionCapabilities(
            azureWinrmInfraDelegateConfig.getAzureConnectorDTO(), maskingEvaluator));
      }
      capabilities.addAll(EncryptedDataDetailsCapabilityHelper.fetchExecutionCapabilitiesForEncryptedDataDetails(
          azureWinrmInfraDelegateConfig.getEncryptionDataDetails(), maskingEvaluator));
    } else if (winRmInfraDelegateConfig instanceof AwsWinrmInfraDelegateConfig) {
      AwsWinrmInfraDelegateConfig awsWinrmInfraDelegateConfig = (AwsWinrmInfraDelegateConfig) winRmInfraDelegateConfig;
      if (awsWinrmInfraDelegateConfig.getAwsConnectorDTO() != null) {
        capabilities.addAll(AwsCapabilityHelper.fetchRequiredExecutionCapabilities(
            awsWinrmInfraDelegateConfig.getAwsConnectorDTO(), maskingEvaluator));
      }
      capabilities.addAll(EncryptedDataDetailsCapabilityHelper.fetchExecutionCapabilitiesForEncryptedDataDetails(
          awsWinrmInfraDelegateConfig.getEncryptionDataDetails(), maskingEvaluator));
    }
  }

  private String getDefaultPort(PdcWinRmInfraDelegateConfig pdcSshInfraDelegateConfig) {
    return pdcSshInfraDelegateConfig.getWinRmCredentials() == null
            || pdcSshInfraDelegateConfig.getWinRmCredentials().getPort() == 0
        ? DEFAULT_WINRM_PORT
        : String.valueOf(pdcSshInfraDelegateConfig.getWinRmCredentials().getPort());
  }
}
