/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.ecs.request;

import static io.harness.expression.Expression.ALLOW_SECRETS;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.task.ecs.EcsCommandTypeNG;
import io.harness.delegate.task.ecs.EcsInfraConfig;
import io.harness.delegate.task.ecs.EcsLoadBalancerConfig;
import io.harness.expression.Expression;
import io.harness.reflection.ExpressionReflectionUtils;

import java.util.List;
import lombok.Builder;
import lombok.Value;
import lombok.experimental.NonFinal;

@Value
@Builder
@OwnedBy(HarnessTeam.CDP)
public class EcsTaskArnBlueGreenCreateServiceRequest
    implements EcsCommandRequest, ExpressionReflectionUtils.NestedAnnotationResolver {
  String commandName;
  String accountId;
  CommandUnitsProgress commandUnitsProgress;
  EcsCommandTypeNG ecsCommandType;
  @NonFinal @Expression(ALLOW_SECRETS) EcsLoadBalancerConfig ecsLoadBalancerConfig;
  @NonFinal @Expression(ALLOW_SECRETS) EcsInfraConfig ecsInfraConfig;
  @NonFinal @Expression(ALLOW_SECRETS) List<String> ecsScalingPolicyManifestContentList;
  @NonFinal @Expression(ALLOW_SECRETS) Integer timeoutIntervalInMin;
  @NonFinal @Expression(ALLOW_SECRETS) String ecsTaskDefinitionArn;
  @NonFinal @Expression(ALLOW_SECRETS) String ecsServiceDefinitionManifestContent;
  String targetGroupArnKey;
  @NonFinal @Expression(ALLOW_SECRETS) List<String> ecsScalableTargetManifestContentList;
  @NonFinal @Expression(ALLOW_SECRETS) boolean sameAsAlreadyRunningInstances;
  boolean removeAutoScalingFromBlueService;
}
