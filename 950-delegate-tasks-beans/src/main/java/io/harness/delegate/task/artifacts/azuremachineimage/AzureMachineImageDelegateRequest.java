package io.harness.delegate.task.artifacts.azuremachineimage;

import static io.harness.delegate.beans.connector.ConnectorCapabilityBaseHelper.populateDelegateSelectorCapability;
import static io.harness.delegate.task.artifacts.ArtifactSourceType.AZURE_MACHINE_IMAGE;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.connector.azureconnector.AzureCapabilityHelper;
import io.harness.delegate.beans.connector.azureconnector.AzureConnectorDTO;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.capability.EncryptedDataDetailsCapabilityHelper;
import io.harness.delegate.task.artifacts.ArtifactSourceDelegateRequest;
import io.harness.delegate.task.artifacts.ArtifactSourceType;
import io.harness.expression.ExpressionEvaluator;
import io.harness.security.encryption.EncryptedDataDetail;

import java.util.ArrayList;
import java.util.List;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Value;
@Value
@Builder
@EqualsAndHashCode(callSuper = false)
@OwnedBy(HarnessTeam.CDC)
public class AzureMachineImageDelegateRequest implements ArtifactSourceDelegateRequest {
  AzureConnectorDTO azureConnectorDTO;
  List<EncryptedDataDetail> encryptedDataDetails;
  String subscriptionId;
  @Override
  public List<ExecutionCapability> fetchRequiredExecutionCapabilities(ExpressionEvaluator maskingEvaluator) {
    List<ExecutionCapability> capabilities =
        new ArrayList<>(EncryptedDataDetailsCapabilityHelper.fetchExecutionCapabilitiesForEncryptedDataDetails(
            encryptedDataDetails, maskingEvaluator));
    populateDelegateSelectorCapability(capabilities, azureConnectorDTO.getDelegateSelectors());
    capabilities.addAll(AzureCapabilityHelper.fetchRequiredExecutionCapabilities(azureConnectorDTO, maskingEvaluator));
    return capabilities;
  }

  @Override
  public ArtifactSourceType getSourceType() {
    return AZURE_MACHINE_IMAGE;
  }
}
