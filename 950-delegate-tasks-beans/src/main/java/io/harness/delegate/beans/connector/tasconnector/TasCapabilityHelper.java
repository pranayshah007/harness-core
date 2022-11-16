package io.harness.delegate.beans.connector.tasconnector;

import io.harness.delegate.beans.connector.ConnectorCapabilityBaseHelper;
import io.harness.delegate.beans.connector.ConnectorConfigDTO;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.task.mixin.HttpConnectionExecutionCapabilityGenerator;
import io.harness.expression.ExpressionEvaluator;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;

@UtilityClass
public class TasCapabilityHelper extends ConnectorCapabilityBaseHelper {
  public List<ExecutionCapability> fetchRequiredExecutionCapabilities(
      ConnectorConfigDTO connectorConfigDTO, ExpressionEvaluator maskingEvaluator) {
    List<ExecutionCapability> capabilityList = new ArrayList<>();
    TasConnectorDTO tasConnectorDTO = (TasConnectorDTO) connectorConfigDTO;
    TasCredentialDTO credential = tasConnectorDTO.getCredential();
    if (credential.getType() == TasCredentialType.MANUAL_CREDENTIALS) {
      TasManualDetailsDTO tasManualDetailsDTO = (TasManualDetailsDTO) credential.getSpec();
      capabilityList.add(HttpConnectionExecutionCapabilityGenerator.buildHttpConnectionExecutionCapability(
          tasManualDetailsDTO.getEndpointUrl(), maskingEvaluator));
    }
    populateDelegateSelectorCapability(capabilityList, tasConnectorDTO.getDelegateSelectors());
    return capabilityList;
  }
}
