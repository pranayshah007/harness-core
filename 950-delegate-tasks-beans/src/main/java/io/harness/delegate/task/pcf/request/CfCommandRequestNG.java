package io.harness.delegate.task.pcf.request;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.connector.tasconnector.TasCapabilityHelper;
import io.harness.delegate.beans.connector.tasconnector.TasConnectorDTO;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.beans.executioncapability.ExecutionCapabilityDemander;
import io.harness.delegate.capability.EncryptedDataDetailsCapabilityHelper;
import io.harness.delegate.task.TaskParameters;
import io.harness.delegate.task.pcf.response.TasInfraConfig;
import io.harness.expression.ExpressionEvaluator;
import io.harness.security.encryption.EncryptedDataDetail;

import java.util.ArrayList;
import java.util.List;

@OwnedBy(HarnessTeam.CDP)
public interface CfCommandRequestNG extends TaskParameters, ExecutionCapabilityDemander {
  TasInfraConfig getTasInfraConfig();
  @Override
  default List<ExecutionCapability> fetchRequiredExecutionCapabilities(ExpressionEvaluator maskingEvaluator) {
    TasInfraConfig tasInfraConfig = getTasInfraConfig();
    List<EncryptedDataDetail> infraConfigEncryptionDataDetails = tasInfraConfig.getEncryptionDataDetails();

    List<ExecutionCapability> capabilities =
        new ArrayList<>(EncryptedDataDetailsCapabilityHelper.fetchExecutionCapabilitiesForEncryptedDataDetails(
            infraConfigEncryptionDataDetails, maskingEvaluator));

    TasConnectorDTO tasConnectorDTO = tasInfraConfig.getTasConnectorDTO();
    capabilities.addAll(TasCapabilityHelper.fetchRequiredExecutionCapabilities(tasConnectorDTO, maskingEvaluator));
    return capabilities;
  }
}
