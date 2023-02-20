/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.aws.sam.request;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.connector.awsconnector.AwsCapabilityHelper;
import io.harness.delegate.beans.connector.awsconnector.AwsConnectorDTO;
import io.harness.delegate.beans.executioncapability.AwsCliInstallationCapability;
import io.harness.delegate.beans.executioncapability.AwsSamInstallationCapability;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.beans.executioncapability.ExecutionCapabilityDemander;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.capability.EncryptedDataDetailsCapabilityHelper;
import io.harness.delegate.task.TaskParameters;
import io.harness.delegate.task.aws.sam.AwsSamCommandType;
import io.harness.delegate.task.aws.sam.AwsSamInfraConfig;
import io.harness.expression.ExpressionEvaluator;
import io.harness.security.encryption.EncryptedDataDetail;

import java.util.ArrayList;
import java.util.List;

@OwnedBy(HarnessTeam.CDP)
public interface AwsSamCommandRequest extends TaskParameters, ExecutionCapabilityDemander {
  String getAccountId();
  AwsSamCommandType getAwsSamCommandType();
  String getCommandName();
  CommandUnitsProgress getCommandUnitsProgress();
  AwsSamInfraConfig getAwsSamInfraConfig();
  Integer getTimeoutIntervalInMin();

  @Override
  default List<ExecutionCapability> fetchRequiredExecutionCapabilities(ExpressionEvaluator maskingEvaluator) {
    AwsSamInfraConfig awsSamInfraConfig = getAwsSamInfraConfig();
    List<EncryptedDataDetail> cloudProviderEncryptionDetails = awsSamInfraConfig.getEncryptionDataDetails();

    List<ExecutionCapability> capabilities =
        new ArrayList<>(EncryptedDataDetailsCapabilityHelper.fetchExecutionCapabilitiesForEncryptedDataDetails(
            cloudProviderEncryptionDetails, maskingEvaluator));

    AwsConnectorDTO awsConnectorDTO = awsSamInfraConfig.getAwsConnectorDTO();

    capabilities.addAll(AwsCapabilityHelper.fetchRequiredExecutionCapabilities(awsConnectorDTO, maskingEvaluator));
    capabilities.add(AwsCliInstallationCapability.builder().criteria("AWS Cli Installed").build());
    capabilities.add(AwsSamInstallationCapability.builder().criteria("Aws Sam Installed").build());

    return capabilities;
  }
}
