/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package software.wings.helpers.ext.cloudformation.request;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.beans.executioncapability.ExecutionCapabilityDemander;
import io.harness.expression.ExpressionEvaluator;

import software.wings.beans.AwsConfig;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.hibernate.validator.constraints.NotEmpty;

@Data
@AllArgsConstructor
@TargetModule(HarnessModule._950_DELEGATE_TASKS_BEANS)
@OwnedBy(CDP)
public class CloudFormationCommandRequest implements ExecutionCapabilityDemander {
  @NotEmpty private CloudFormationCommandType commandType;
  private String accountId;
  private String appId;
  private String activityId;
  private String commandName;
  private AwsConfig awsConfig;
  private int timeoutInMs;
  private String region;
  private String cloudFormationRoleArn;
  private boolean skipWaitForResources;
  private boolean timeoutSupported;

  @Override
  public List<ExecutionCapability> fetchRequiredExecutionCapabilities(ExpressionEvaluator maskingEvaluator) {
    return awsConfig.fetchRequiredExecutionCapabilities(maskingEvaluator);
  }

  public enum CloudFormationCommandType { CREATE_STACK, GET_STACKS, DELETE_STACK, UNKNOWN_REQUEST }
}
