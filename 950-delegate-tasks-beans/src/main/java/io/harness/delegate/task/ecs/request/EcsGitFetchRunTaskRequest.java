/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.ecs.request;

import static io.harness.expression.Expression.ALLOW_SECRETS;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.connector.scm.GitCapabilityHelper;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.beans.executioncapability.ExecutionCapabilityDemander;
import io.harness.delegate.beans.storeconfig.GitStoreDelegateConfig;
import io.harness.delegate.capability.EncryptedDataDetailsCapabilityHelper;
import io.harness.delegate.task.ActivityAccess;
import io.harness.delegate.task.TaskParameters;
import io.harness.delegate.task.ecs.EcsGitFetchRunTaskFileConfig;
import io.harness.expression.Expression;
import io.harness.expression.ExpressionEvaluator;
import io.harness.reflection.ExpressionReflectionUtils;

import java.util.ArrayList;
import java.util.List;
import lombok.Builder;
import lombok.Value;
import lombok.experimental.NonFinal;

@Value
@Builder
@OwnedBy(HarnessTeam.CDP)
public class EcsGitFetchRunTaskRequest implements ActivityAccess, TaskParameters, ExecutionCapabilityDemander,
                                                  ExpressionReflectionUtils.NestedAnnotationResolver {
  String executionLogName;
  String activityId;
  String accountId;

  @NonFinal @Expression(ALLOW_SECRETS) EcsGitFetchRunTaskFileConfig taskDefinitionEcsGitFetchRunTaskFileConfig;

  @NonFinal
  @Expression(ALLOW_SECRETS)
  EcsGitFetchRunTaskFileConfig ecsRunTaskRequestDefinitionEcsGitFetchRunTaskFileConfig;

  @Builder.Default boolean shouldOpenLogStream = true;
  boolean closeLogStream;

  @Override
  public List<ExecutionCapability> fetchRequiredExecutionCapabilities(ExpressionEvaluator maskingEvaluator) {
    List<ExecutionCapability> capabilities = new ArrayList<>();

    if (taskDefinitionEcsGitFetchRunTaskFileConfig != null) {
      GitStoreDelegateConfig taskDefinitionGitStoreDelegateConfig =
          taskDefinitionEcsGitFetchRunTaskFileConfig.getGitStoreDelegateConfig();
      capabilities.addAll(GitCapabilityHelper.fetchRequiredExecutionCapabilities(
          taskDefinitionGitStoreDelegateConfig, taskDefinitionGitStoreDelegateConfig.getEncryptedDataDetails()));

      capabilities.addAll(EncryptedDataDetailsCapabilityHelper.fetchExecutionCapabilitiesForEncryptedDataDetails(
          taskDefinitionGitStoreDelegateConfig.getEncryptedDataDetails(), maskingEvaluator));
    }

    if (ecsRunTaskRequestDefinitionEcsGitFetchRunTaskFileConfig != null) {
      GitStoreDelegateConfig ecsRunTaskRequestDefinitionGitStoreDelegateConfig =
          ecsRunTaskRequestDefinitionEcsGitFetchRunTaskFileConfig.getGitStoreDelegateConfig();
      capabilities.addAll(
          GitCapabilityHelper.fetchRequiredExecutionCapabilities(ecsRunTaskRequestDefinitionGitStoreDelegateConfig,
              ecsRunTaskRequestDefinitionGitStoreDelegateConfig.getEncryptedDataDetails()));

      capabilities.addAll(EncryptedDataDetailsCapabilityHelper.fetchExecutionCapabilitiesForEncryptedDataDetails(
          ecsRunTaskRequestDefinitionGitStoreDelegateConfig.getEncryptedDataDetails(), maskingEvaluator));
    }

    return capabilities;
  }
}
