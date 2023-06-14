/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.serverless.request;

import static io.harness.expression.Expression.ALLOW_SECRETS;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.connector.awsconnector.AwsCapabilityHelper;
import io.harness.delegate.beans.connector.awsconnector.AwsConnectorDTO;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.beans.executioncapability.ExecutionCapabilityDemander;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.capability.EncryptedDataDetailsCapabilityHelper;
import io.harness.delegate.task.TaskParameters;
import io.harness.delegate.task.serverless.ServerlessAwsLambdaInfraConfig;
import io.harness.delegate.task.serverless.ServerlessAwsLambdaRollbackV2Config;
import io.harness.delegate.task.serverless.ServerlessCommandType;
import io.harness.delegate.task.serverless.ServerlessInfraConfig;
import io.harness.expression.Expression;
import io.harness.expression.ExpressionEvaluator;
import io.harness.reflection.ExpressionReflectionUtils.NestedAnnotationResolver;
import io.harness.security.encryption.EncryptedDataDetail;

import java.util.ArrayList;
import java.util.List;
import lombok.Builder;
import lombok.Value;
import lombok.experimental.NonFinal;

@Value
@Builder
@OwnedBy(HarnessTeam.CDP)
public class ServerlessRollbackV2Request
    implements NestedAnnotationResolver, ExecutionCapabilityDemander, TaskParameters {
  String accountId;
  ServerlessCommandType serverlessCommandType;
  String commandName;
  CommandUnitsProgress commandUnitsProgress;
  @NonFinal @Expression(ALLOW_SECRETS) ServerlessInfraConfig serverlessInfraConfig;
  ServerlessAwsLambdaRollbackV2Config serverlessRollbackConfig;
  Integer timeoutIntervalInMin;

  @Override
  public List<ExecutionCapability> fetchRequiredExecutionCapabilities(ExpressionEvaluator maskingEvaluator) {
    ServerlessInfraConfig serverlessInfra = getServerlessInfraConfig();
    List<EncryptedDataDetail> cloudProviderEncryptionDetails = serverlessInfra.getEncryptionDataDetails();
    List<ExecutionCapability> capabilities =
        new ArrayList<>(EncryptedDataDetailsCapabilityHelper.fetchExecutionCapabilitiesForEncryptedDataDetails(
            cloudProviderEncryptionDetails, maskingEvaluator));
    if (serverlessInfra instanceof ServerlessAwsLambdaInfraConfig) {
      AwsConnectorDTO awsConnectorDTO = ((ServerlessAwsLambdaInfraConfig) serverlessInfra).getAwsConnectorDTO();
      capabilities.addAll(AwsCapabilityHelper.fetchRequiredExecutionCapabilities(awsConnectorDTO, maskingEvaluator));
    }

    return capabilities;
  }
}
