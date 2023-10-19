/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.ecs.request;
import static io.harness.expression.Expression.ALLOW_SECRETS;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.task.ecs.EcsCommandTypeNG;
import io.harness.delegate.task.ecs.EcsInfraConfig;
import io.harness.delegate.task.ecs.EcsResizeStrategy;
import io.harness.delegate.task.ecs.EcsUpgradeContainerServiceData;
import io.harness.expression.Expression;
import io.harness.reflection.ExpressionReflectionUtils.NestedAnnotationResolver;

import java.util.List;
import lombok.Builder;
import lombok.Value;
import lombok.experimental.NonFinal;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_ECS})
@Value
@Builder
@OwnedBy(HarnessTeam.CDP)
public class EcsUpgradeContainerRequest implements EcsCommandRequest, NestedAnnotationResolver {
  String accountId;
  EcsCommandTypeNG commandType;
  String commandName;
  CommandUnitsProgress commandUnitsProgress;
  @NonFinal @Expression(ALLOW_SECRETS) EcsInfraConfig infraConfig;
  long timeoutIntervalInMillis;
  EcsResizeStrategy resizeStrategy;
  @NonFinal @Expression(ALLOW_SECRETS) EcsUpgradeContainerServiceData oldServiceData;
  @NonFinal @Expression(ALLOW_SECRETS) EcsUpgradeContainerServiceData newServiceData;
  @NonFinal @Expression(ALLOW_SECRETS) List<String> scalableTargetManifestContentList;
  @NonFinal @Expression(ALLOW_SECRETS) List<String> scalingPolicyManifestContentList;
  boolean firstTimeDeployment;

  @Override
  public EcsCommandTypeNG getEcsCommandType() {
    return commandType;
  }

  @Override
  public EcsInfraConfig getEcsInfraConfig() {
    return infraConfig;
  }
}
