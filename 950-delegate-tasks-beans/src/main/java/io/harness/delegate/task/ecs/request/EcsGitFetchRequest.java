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
import io.harness.delegate.task.ecs.EcsGitFetchFileConfig;
import io.harness.expression.Expression;
import io.harness.expression.ExpressionEvaluator;
import io.harness.reflection.ExpressionReflectionUtils;

import java.util.ArrayList;
import java.util.List;
import lombok.Builder;
import lombok.Value;
import lombok.experimental.NonFinal;
import org.apache.commons.collections.CollectionUtils;

@Value
@Builder
@OwnedBy(HarnessTeam.CDP)
public class EcsGitFetchRequest implements ActivityAccess, TaskParameters, ExecutionCapabilityDemander,
                                           ExpressionReflectionUtils.NestedAnnotationResolver {
  String executionLogName;
  String activityId;
  String accountId;

  @NonFinal @Expression(ALLOW_SECRETS) EcsGitFetchFileConfig ecsTaskDefinitionGitFetchFileConfig;

  @NonFinal @Expression(ALLOW_SECRETS) EcsGitFetchFileConfig ecsServiceDefinitionGitFetchFileConfig;

  @NonFinal @Expression(ALLOW_SECRETS) List<EcsGitFetchFileConfig> ecsScalableTargetGitFetchFileConfigs;

  @NonFinal @Expression(ALLOW_SECRETS) List<EcsGitFetchFileConfig> ecsScalingPolicyGitFetchFileConfigs;

  @Builder.Default boolean shouldOpenLogStream = true;
  boolean closeLogStream;

  @Override
  public List<ExecutionCapability> fetchRequiredExecutionCapabilities(ExpressionEvaluator maskingEvaluator) {
    List<ExecutionCapability> capabilities = new ArrayList<>();

    List<EcsGitFetchFileConfig> allEcsGitFetchFileConfigs = new ArrayList<>();

    if (ecsTaskDefinitionGitFetchFileConfig != null) {
      allEcsGitFetchFileConfigs.add(ecsTaskDefinitionGitFetchFileConfig);
    }

    if (ecsServiceDefinitionGitFetchFileConfig != null) {
      allEcsGitFetchFileConfigs.add(ecsServiceDefinitionGitFetchFileConfig);
    }

    if (CollectionUtils.isNotEmpty(ecsScalableTargetGitFetchFileConfigs)) {
      allEcsGitFetchFileConfigs.addAll(ecsScalableTargetGitFetchFileConfigs);
    }

    if (CollectionUtils.isNotEmpty(ecsScalingPolicyGitFetchFileConfigs)) {
      allEcsGitFetchFileConfigs.addAll(ecsScalingPolicyGitFetchFileConfigs);
    }

    for (EcsGitFetchFileConfig ecsGitFetchFileConfig : allEcsGitFetchFileConfigs) {
      GitStoreDelegateConfig gitStoreDelegateConfig = ecsGitFetchFileConfig.getGitStoreDelegateConfig();

      capabilities.addAll(GitCapabilityHelper.fetchRequiredExecutionCapabilities(
          gitStoreDelegateConfig, gitStoreDelegateConfig.getEncryptedDataDetails()));

      capabilities.addAll(EncryptedDataDetailsCapabilityHelper.fetchExecutionCapabilitiesForEncryptedDataDetails(
          gitStoreDelegateConfig.getEncryptedDataDetails(), maskingEvaluator));
    }

    return capabilities;
  }
}
