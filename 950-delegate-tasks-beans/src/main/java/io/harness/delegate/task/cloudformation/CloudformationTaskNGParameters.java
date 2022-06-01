/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.cloudformation;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.expression.Expression.ALLOW_SECRETS;

import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.connector.awsconnector.AwsCapabilityHelper;
import io.harness.delegate.beans.connector.awsconnector.AwsConnectorDTO;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.beans.executioncapability.ExecutionCapabilityDemander;
import io.harness.delegate.task.TaskParameters;
import io.harness.expression.Expression;
import io.harness.expression.ExpressionEvaluator;
import io.harness.expression.ExpressionReflectionUtils.NestedAnnotationResolver;
import io.harness.security.encryption.EncryptedDataDetail;

import com.amazonaws.services.cloudformation.model.StackStatus;
import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.NonNull;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

@Value
@Builder
@Slf4j
@OwnedBy(CDP)
public class CloudformationTaskNGParameters
    implements TaskParameters, ExecutionCapabilityDemander, NestedAnnotationResolver {
  @NonNull String accountId;
  @NonNull CloudformationTaskType taskType;
  CloudformationCommandUnit cfCommandUnit;
  @Expression(ALLOW_SECRETS) String templateBody;
  @Expression(ALLOW_SECRETS) String templateUrl;
  @NonNull AwsConnectorDTO awsConnector;
  @NonNull List<EncryptedDataDetail> encryptedDataDetails;
  @NonNull String region;
  String cloudFormationRoleArn;
  @NonNull String stackName;
  @Expression(ALLOW_SECRETS) Map<String, String> parameters;
  List<String> capabilities;
  @Expression(ALLOW_SECRETS) String tags;
  List<StackStatus> stackStatusesToMarkAsSuccess;
  long timeoutInMs;

  @Override
  public List<ExecutionCapability> fetchRequiredExecutionCapabilities(ExpressionEvaluator maskingEvaluator) {
    log.info("Adding Required Execution Capabilities");
    return AwsCapabilityHelper.fetchRequiredExecutionCapabilities(awsConnector, maskingEvaluator);
  }
}
