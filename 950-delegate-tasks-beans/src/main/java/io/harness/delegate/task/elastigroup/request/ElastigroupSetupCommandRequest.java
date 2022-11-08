/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.elastigroup.request;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.expression.Expression.ALLOW_SECRETS;

import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.pcf.ResizeStrategy;
import io.harness.delegate.task.TaskParameters;
import io.harness.delegate.task.ecs.EcsCommandTypeNG;
import io.harness.delegate.task.ecs.EcsInfraConfig;
import io.harness.delegate.task.ecs.EcsLoadBalancerConfig;
import io.harness.delegate.task.elastigroup.response.ElastigroupCommandTypeNG;
import io.harness.delegate.task.elastigroup.response.SpotInstConfig;
import io.harness.expression.Expression;
import io.harness.expression.ExpressionReflectionUtils;
import io.harness.security.encryption.EncryptedDataDetail;

import java.util.List;
import lombok.Builder;
import lombok.Data;
import lombok.Value;
import lombok.experimental.NonFinal;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.expression.Expression.ALLOW_SECRETS;

@Value
@Data
@Builder
@OwnedBy(CDP)
public class ElastigroupSetupCommandRequest
    implements ElastigroupCommandRequest, ExpressionReflectionUtils.NestedAnnotationResolver {
  String accountId;
  ElastigroupCommandTypeNG elastigroupCommandType;
  String commandName;
  CommandUnitsProgress commandUnitsProgress;
  String elastigroupJson;
  String elastigroupNamePrefix;
  private Integer maxInstanceCount;
  private boolean useCurrentRunningInstanceCount;
  private Integer currentRunningInstanceCount;
  String startupScript;
  String image;
  boolean blueGreen;
  ResizeStrategy resizeStrategy;
  @NonFinal @Expression(ALLOW_SECRETS) Integer timeoutIntervalInMin;
  @NonFinal @Expression(ALLOW_SECRETS) SpotInstConfig spotInstConfig;
}
