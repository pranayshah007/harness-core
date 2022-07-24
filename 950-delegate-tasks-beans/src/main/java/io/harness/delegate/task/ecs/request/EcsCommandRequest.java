package io.harness.delegate.task.ecs.request;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.connector.awsconnector.AwsCapabilityHelper;
import io.harness.delegate.beans.connector.awsconnector.AwsConnectorDTO;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.beans.executioncapability.ExecutionCapabilityDemander;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.capability.EncryptedDataDetailsCapabilityHelper;
import io.harness.delegate.task.TaskParameters;
import io.harness.delegate.task.ecs.EcsCommandTypeNG;
import io.harness.delegate.task.ecs.EcsInfraConfig;
import io.harness.expression.ExpressionEvaluator;
import io.harness.security.encryption.EncryptedDataDetail;
import org.hibernate.validator.constraints.NotEmpty;

import java.util.ArrayList;
import java.util.List;

@OwnedBy(HarnessTeam.CDP)
public interface EcsCommandRequest extends TaskParameters, ExecutionCapabilityDemander {
  String getAccountId();
  @NotEmpty EcsCommandTypeNG getEcsCommandType();
  String getCommandName();
  CommandUnitsProgress getCommandUnitsProgress();
  EcsInfraConfig getEcsInfraConfig();
  Integer getTimeoutIntervalInMin();

  @Override
  default List<ExecutionCapability> fetchRequiredExecutionCapabilities(ExpressionEvaluator maskingEvaluator) {
    EcsInfraConfig ecsInfraConfig = getEcsInfraConfig();
    List<EncryptedDataDetail> infraConfigEncryptionDataDetails = ecsInfraConfig.getEncryptionDataDetails();

    List<ExecutionCapability> capabilities =
            new ArrayList<>(EncryptedDataDetailsCapabilityHelper.fetchExecutionCapabilitiesForEncryptedDataDetails(
                    infraConfigEncryptionDataDetails, maskingEvaluator));

    AwsConnectorDTO awsConnectorDTO = ecsInfraConfig.getAwsConnectorDTO();
    capabilities.addAll(AwsCapabilityHelper.fetchRequiredExecutionCapabilities(awsConnectorDTO, maskingEvaluator));
    return capabilities;
  }
}
