/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */
package io.harness.delegate.task.pcf.request;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.delegate.beans.connector.tasconnector.TasCapabilityHelper;
import io.harness.delegate.beans.connector.tasconnector.TasConnectorDTO;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.beans.executioncapability.ExecutionCapabilityDemander;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.capability.EncryptedDataDetailsCapabilityHelper;
import io.harness.delegate.task.TaskParameters;
import io.harness.delegate.task.pcf.CfCommandTypeNG;
import io.harness.delegate.task.pcf.response.TasInfraConfig;
import io.harness.expression.ExpressionEvaluator;
import io.harness.pcf.model.CfCliVersion;
import io.harness.security.encryption.EncryptedDataDetail;

import java.util.ArrayList;
import java.util.List;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = false, components = {HarnessModuleComponent.CDS_PCF})
@OwnedBy(HarnessTeam.CDP)
public interface CfCommandRequestNG extends TaskParameters, ExecutionCapabilityDemander {
  TasInfraConfig getTasInfraConfig();
  CfCommandTypeNG getCfCommandTypeNG();
  String getCommandName();
  CommandUnitsProgress getCommandUnitsProgress();
  CfCliVersion getCfCliVersion();
  @Override
  default List<ExecutionCapability> fetchRequiredExecutionCapabilities(ExpressionEvaluator maskingEvaluator) {
    TasInfraConfig tasInfraConfig = getTasInfraConfig();
    List<EncryptedDataDetail> infraConfigEncryptionDataDetails = tasInfraConfig.getEncryptionDataDetails();

    List<ExecutionCapability> capabilities =
        new ArrayList<>(EncryptedDataDetailsCapabilityHelper.fetchExecutionCapabilitiesForEncryptedDataDetails(
            infraConfigEncryptionDataDetails, maskingEvaluator));
    TasConnectorDTO tasConnectorDTO = tasInfraConfig.getTasConnectorDTO();
    capabilities.addAll(TasCapabilityHelper.fetchRequiredExecutionCapabilities(tasConnectorDTO, maskingEvaluator));
    populateRequestCapabilities(capabilities, maskingEvaluator);
    return capabilities;
  }

  default void populateRequestCapabilities(
      List<ExecutionCapability> capabilities, ExpressionEvaluator maskingEvaluator) {
    // used for request specific additional capabilities
  }
}
