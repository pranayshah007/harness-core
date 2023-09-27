/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.shell;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.delegate.beans.connector.pdcconnector.WinrmCapabilityHelper;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.capability.EncryptedDataDetailsCapabilityHelper;
import io.harness.delegate.task.ssh.WinRmInfraDelegateConfig;
import io.harness.expression.ExpressionEvaluator;

import java.util.List;
import lombok.Getter;
import lombok.Value;
import lombok.experimental.SuperBuilder;

@CodePulse(
    module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_TRADITIONAL})
@SuperBuilder
@Value
@Getter
@OwnedBy(CDP)
public class WinrmTaskParameters extends CommandTaskParameters {
  String host;
  WinRmInfraDelegateConfig winRmInfraDelegateConfig;
  boolean disableWinRMCommandEncodingFFSet;
  boolean winrmScriptCommandSplit;
  boolean useWinRMKerberosUniqueCacheFile;
  boolean disableWinRmEnvVarEscaping;

  @Override
  public void fetchInfraExecutionCapabilities(
      final List<ExecutionCapability> capabilities, ExpressionEvaluator maskingEvaluator) {
    if (winRmInfraDelegateConfig == null) {
      return;
    }

    if (winRmInfraDelegateConfig.getWinRmCredentials() != null
        && winRmInfraDelegateConfig.getWinRmCredentials().getAuth() != null) {
      capabilities.addAll(WinrmCapabilityHelper.fetchRequiredExecutionCapabilities(
          winRmInfraDelegateConfig, useWinRMKerberosUniqueCacheFile, host));
    }

    capabilities.addAll(EncryptedDataDetailsCapabilityHelper.fetchExecutionCapabilitiesForEncryptedDataDetails(
        winRmInfraDelegateConfig.getEncryptionDataDetails(), maskingEvaluator));
  }
}
