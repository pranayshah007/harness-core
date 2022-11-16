package io.harness.delegate.beans.connector.tasconnector;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.connector.ConnectorTaskParams;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.beans.connector.ConnectorValidationParams;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.beans.executioncapability.ExecutionCapabilityDemander;
import io.harness.expression.ExpressionEvaluator;
import io.harness.security.encryption.EncryptedDataDetail;

import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;

@Data
@EqualsAndHashCode(callSuper = true)
@SuperBuilder
@OwnedBy(HarnessTeam.CDP)
public class TasValidationParams
    extends ConnectorTaskParams implements ConnectorValidationParams, ExecutionCapabilityDemander {
  TasConnectorDTO tasConnectorDTO;
  List<EncryptedDataDetail> encryptionDetails;
  String connectorName;
  @Override
  public ConnectorType getConnectorType() {
    return ConnectorType.TAS;
  }

  @Override
  public List<ExecutionCapability> fetchRequiredExecutionCapabilities(ExpressionEvaluator maskingEvaluator) {
    return TasCapabilityHelper.fetchRequiredExecutionCapabilities(tasConnectorDTO, maskingEvaluator);
  }
}
