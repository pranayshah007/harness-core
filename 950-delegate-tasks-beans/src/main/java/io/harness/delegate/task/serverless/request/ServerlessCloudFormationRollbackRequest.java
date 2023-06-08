/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.serverless.request;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.connector.artifactoryconnector.ArtifactoryCapabilityHelper;
import io.harness.delegate.beans.connector.awsconnector.AwsCapabilityHelper;
import io.harness.delegate.beans.connector.awsconnector.AwsConnectorDTO;
import io.harness.delegate.beans.connector.awsconnector.CrossAccountAccessDTO;
import io.harness.delegate.beans.connector.scm.GitCapabilityHelper;
import io.harness.delegate.beans.connector.scm.adapter.ScmConnectorMapper;
import io.harness.delegate.beans.executioncapability.AwsCliInstallationCapability;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.beans.executioncapability.ExecutionCapabilityDemander;
import io.harness.delegate.beans.executioncapability.ServerlessInstallationCapability;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.storeconfig.GitStoreDelegateConfig;
import io.harness.delegate.capability.EncryptedDataDetailsCapabilityHelper;
import io.harness.delegate.task.TaskParameters;
import io.harness.delegate.task.serverless.ServerlessArtifactConfig;
import io.harness.delegate.task.serverless.ServerlessArtifactoryArtifactConfig;
import io.harness.delegate.task.serverless.ServerlessAwsLambdaCloudFormationRollbackConfig;
import io.harness.delegate.task.serverless.ServerlessAwsLambdaInfraConfig;
import io.harness.delegate.task.serverless.ServerlessAwsLambdaManifestConfig;
import io.harness.delegate.task.serverless.ServerlessCommandType;
import io.harness.delegate.task.serverless.ServerlessEcrArtifactConfig;
import io.harness.delegate.task.serverless.ServerlessInfraConfig;
import io.harness.delegate.task.serverless.ServerlessManifestConfig;
import io.harness.delegate.task.serverless.ServerlessRollbackConfig;
import io.harness.delegate.task.serverless.ServerlessS3ArtifactConfig;
import io.harness.expression.Expression;
import io.harness.expression.ExpressionEvaluator;
import io.harness.reflection.ExpressionReflectionUtils.NestedAnnotationResolver;
import io.harness.security.encryption.EncryptedDataDetail;
import lombok.Builder;
import lombok.Value;
import lombok.experimental.NonFinal;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static io.harness.expression.Expression.ALLOW_SECRETS;

@Value
@Builder
@OwnedBy(HarnessTeam.CDP)
public class ServerlessCloudFormationRollbackRequest implements NestedAnnotationResolver, ExecutionCapabilityDemander, TaskParameters {
  String accountId;
  ServerlessCommandType serverlessCommandType;
  String commandName;
  CommandUnitsProgress commandUnitsProgress;
  @NonFinal @Expression(ALLOW_SECRETS) ServerlessInfraConfig serverlessInfraConfig;
  ServerlessAwsLambdaCloudFormationRollbackConfig serverlessRollbackConfig;
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
      CrossAccountAccessDTO crossAccountAccess = awsConnectorDTO.getCredential().getCrossAccountAccess();
      if (crossAccountAccess != null && crossAccountAccess.getCrossAccountRoleArn() != null) {
        capabilities.add(AwsCliInstallationCapability.builder().criteria("AWS Cli Installed").build());
      }
    }

    return capabilities;
  }
}
