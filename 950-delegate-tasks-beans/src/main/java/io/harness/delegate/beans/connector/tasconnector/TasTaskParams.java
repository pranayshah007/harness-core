package io.harness.delegate.beans.connector.tasconnector;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.connector.ConnectorTaskParams;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.beans.executioncapability.ExecutionCapabilityDemander;
import io.harness.delegate.task.TaskParameters;
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
public class TasTaskParams extends ConnectorTaskParams implements TaskParameters, ExecutionCapabilityDemander {
  TasConnectorDTO tasConnectorDTO;
  TasTaskType tasTaskType;
  List<EncryptedDataDetail> encryptionDetails;

  @Override
  public List<ExecutionCapability> fetchRequiredExecutionCapabilities(ExpressionEvaluator maskingEvaluator) {
    return TasCapabilityHelper.fetchRequiredExecutionCapabilities(tasConnectorDTO, maskingEvaluator);
  }
}
