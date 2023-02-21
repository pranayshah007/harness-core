package io.harness.delegate.task.aws.sam.request;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.connector.awsconnector.AwsCapabilityHelper;
import io.harness.delegate.beans.connector.awsconnector.AwsConnectorDTO;
import io.harness.delegate.beans.connector.awsconnector.CrossAccountAccessDTO;
import io.harness.delegate.beans.executioncapability.AwsCliInstallationCapability;
import io.harness.delegate.beans.executioncapability.AwsSamInstallationCapability;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.beans.executioncapability.ExecutionCapabilityDemander;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.capability.EncryptedDataDetailsCapabilityHelper;
import io.harness.delegate.task.TaskParameters;
import io.harness.delegate.task.aws.sam.AwsSamInfraConfig;
import io.harness.delegate.task.aws.sam.AwsSamArtifactConfig;
import io.harness.delegate.task.aws.sam.AwsSamCommandType;
import io.harness.delegate.task.aws.sam.AwsSamS3ArtifactConfig;
import io.harness.expression.ExpressionEvaluator;
import io.harness.security.encryption.EncryptedDataDetail;

import java.util.ArrayList;
import java.util.List;

@OwnedBy(HarnessTeam.CDP)
public interface AwsSamCommandRequest extends TaskParameters, ExecutionCapabilityDemander {
  String getAccountId();
  AwsSamCommandType getAwsSamCommandType();
  CommandUnitsProgress getCommandUnitsProgress();
  AwsSamInfraConfig getAwsSamInfraConfig();
  Long getTimeoutIntervalInMillis();
  AwsSamArtifactConfig getAwsSamArtifactConfig();

  @Override
  default List<ExecutionCapability> fetchRequiredExecutionCapabilities(ExpressionEvaluator maskingEvaluator) {
    AwsSamInfraConfig awsSamInfraConfig = getAwsSamInfraConfig();
    AwsSamArtifactConfig awsSamArtifactConfig = getAwsSamArtifactConfig();

    List<EncryptedDataDetail> cloudProviderEncryptionDetails = awsSamInfraConfig.getEncryptionDataDetails();

    List<ExecutionCapability> capabilities =
            new ArrayList<>(EncryptedDataDetailsCapabilityHelper.fetchExecutionCapabilitiesForEncryptedDataDetails(
                    cloudProviderEncryptionDetails, maskingEvaluator));

    if (awsSamInfraConfig instanceof AwsSamInfraConfig) {
      AwsConnectorDTO awsConnectorDTO = awsSamInfraConfig.getAwsConnectorDTO();
      capabilities.addAll(AwsCapabilityHelper.fetchRequiredExecutionCapabilities(awsConnectorDTO, maskingEvaluator));
      CrossAccountAccessDTO crossAccountAccess = awsConnectorDTO.getCredential().getCrossAccountAccess();
      if (crossAccountAccess != null && crossAccountAccess.getCrossAccountRoleArn() != null) {
        capabilities.add(AwsCliInstallationCapability.builder().criteria("AWS CLI Installed").build());
      }
    }

    if (awsSamArtifactConfig instanceof AwsSamS3ArtifactConfig) {
      AwsConnectorDTO connectorConfigDTO = (AwsConnectorDTO) ((AwsSamS3ArtifactConfig) awsSamArtifactConfig)
              .getConnectorDTO()
              .getConnectorConfig();
      capabilities.addAll(AwsCapabilityHelper.fetchRequiredExecutionCapabilities(connectorConfigDTO, maskingEvaluator));
    }


    capabilities.add(AwsSamInstallationCapability.builder().criteria("AWS SAM Installed").build());
    return capabilities;
  }
}
