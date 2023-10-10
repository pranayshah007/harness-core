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
import io.harness.expression.Expression;
import io.harness.reflection.ExpressionReflectionUtils.NestedAnnotationResolver;

import lombok.Builder;
import lombok.Value;
import lombok.experimental.NonFinal;

@Value
@Builder
@OwnedBy(HarnessTeam.CDP)
public class EcsBasicPrepareRollbackRequest implements EcsCommandRequest, NestedAnnotationResolver {
  String accountId;
  String commandName;
  CommandUnitsProgress commandUnitsProgress;
  EcsCommandTypeNG commandType;
  @NonFinal @Expression(ALLOW_SECRETS) String serviceDefinitionManifestContent;
  @NonFinal @Expression(ALLOW_SECRETS) EcsInfraConfig infraConfig;
  long timeoutIntervalInMillis;

  @Override
  public EcsCommandTypeNG getEcsCommandType() {
    return commandType;
  }

  @Override
  public EcsInfraConfig getEcsInfraConfig() {
    return infraConfig;
  }
}
