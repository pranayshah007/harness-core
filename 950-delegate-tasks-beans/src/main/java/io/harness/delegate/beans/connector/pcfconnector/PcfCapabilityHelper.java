package io.harness.delegate.beans.connector.pcfconnector;

import io.harness.delegate.beans.connector.ConnectorCapabilityBaseHelper;
import io.harness.delegate.beans.connector.ConnectorConfigDTO;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.task.mixin.HttpConnectionExecutionCapabilityGenerator;
import io.harness.exception.UnknownEnumTypeException;
import io.harness.expression.ExpressionEvaluator;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;

@UtilityClass
public class PcfCapabilityHelper extends ConnectorCapabilityBaseHelper {
  public List<ExecutionCapability> fetchRequiredExecutionCapabilities(
      ConnectorConfigDTO connectorConfigDTO, ExpressionEvaluator maskingEvaluator) {
    List<ExecutionCapability> capabilityList = new ArrayList<>();
    PcfConnectorDTO pcfConnectorDTO = (PcfConnectorDTO) connectorConfigDTO;
    PcfCredentialDTO credential = pcfConnectorDTO.getCredential();
    if (credential.getType() == PcfCredentialType.MANUAL_CREDENTIALS) {
      PcfManualDetailsDTO pcfManualDetailsDTO = (PcfManualDetailsDTO) credential.getSpec();
      capabilityList.add(HttpConnectionExecutionCapabilityGenerator.buildHttpConnectionExecutionCapability(
          pcfManualDetailsDTO.getEndpointUrl(), maskingEvaluator));
    }
    populateDelegateSelectorCapability(capabilityList, pcfConnectorDTO.getDelegateSelectors());
    return capabilityList;
  }
}
