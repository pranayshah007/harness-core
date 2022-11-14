package io.harness.delegate.task.pcf.request;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.connector.pcfconnector.PcfCapabilityHelper;
import io.harness.delegate.beans.connector.pcfconnector.PcfConnectorDTO;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.beans.executioncapability.ExecutionCapabilityDemander;
import io.harness.delegate.capability.EncryptedDataDetailsCapabilityHelper;
import io.harness.delegate.task.TaskParameters;
import io.harness.delegate.task.pcf.response.PcfInfraConfig;
import io.harness.expression.ExpressionEvaluator;
import io.harness.security.encryption.EncryptedDataDetail;

import java.util.ArrayList;
import java.util.List;

@OwnedBy(HarnessTeam.CDP)
public interface CfCommandRequestNG extends TaskParameters, ExecutionCapabilityDemander {
  PcfInfraConfig getPcfInfraConfig();
  @Override
  default List<ExecutionCapability> fetchRequiredExecutionCapabilities(ExpressionEvaluator maskingEvaluator) {
    PcfInfraConfig pcfInfraConfig = getPcfInfraConfig();
    List<EncryptedDataDetail> infraConfigEncryptionDataDetails = pcfInfraConfig.getEncryptionDataDetails();

    List<ExecutionCapability> capabilities =
        new ArrayList<>(EncryptedDataDetailsCapabilityHelper.fetchExecutionCapabilitiesForEncryptedDataDetails(
            infraConfigEncryptionDataDetails, maskingEvaluator));

    PcfConnectorDTO pcfConnectorDTO = pcfInfraConfig.getPcfConnectorDTO();
    capabilities.addAll(PcfCapabilityHelper.fetchRequiredExecutionCapabilities(pcfConnectorDTO, maskingEvaluator));
    return capabilities;
  }
}
