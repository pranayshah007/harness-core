/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.elastigroup.request;

import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.beans.executioncapability.ExecutionCapabilityDemander;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.task.TaskParameters;
import io.harness.delegate.task.ecs.EcsCommandTypeNG;
import io.harness.delegate.task.ecs.EcsInfraConfig;
import io.harness.delegate.task.ecs.EcsLoadBalancerConfig;
import io.harness.delegate.task.elastigroup.response.ElastigroupCommandTypeNG;
import io.harness.delegate.task.spotinst.request.SpotInstTaskParameters;
import io.harness.expression.Expression;
import io.harness.expression.ExpressionEvaluator;
import io.harness.expression.ExpressionReflectionUtils;
import io.harness.security.encryption.EncryptedDataDetail;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.NonFinal;
import software.wings.beans.AwsConfig;
import software.wings.beans.SpotInstConfig;
import software.wings.delegatetasks.delegatecapability.CapabilityHelper;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.expression.Expression.ALLOW_SECRETS;

@Data
@Builder
@OwnedBy(CDP)
public class ElastigroupSetupCommandRequest implements ElastigroupCommandRequest, ExpressionReflectionUtils.NestedAnnotationResolver {

  String accountId;
  ElastigroupCommandTypeNG ecsCommandType;
  String commandName;
  CommandUnitsProgress commandUnitsProgress;
  String awsRegion;
  String elastiGroupJson;
  String elastigroupNamePrefix;
  boolean blueGreen;
  String image;
  String resizeStrategy;
  String userData;
  @NonFinal
  @Expression(ALLOW_SECRETS)
  EcsInfraConfig ecsInfraConfig;
  @NonFinal @Expression(ALLOW_SECRETS) Integer timeoutIntervalInMin;
  @NonFinal @Expression(ALLOW_SECRETS) AwsConfig awsConfig;
  @NonFinal @Expression(ALLOW_SECRETS) SpotInstConfig spotInstConfig;
  @NonFinal @Expression(ALLOW_SECRETS) List<EncryptedDataDetail> awsEncryptionDetails;
  @NonFinal @Expression(ALLOW_SECRETS) List<EncryptedDataDetail> spotinstEncryptionDetails;

  @Override
  public List<ExecutionCapability> fetchRequiredExecutionCapabilities(ExpressionEvaluator maskingEvaluator) {
    Set<ExecutionCapability> executionCapabilities = new HashSet<>();
    executionCapabilities.addAll(
        CapabilityHelper.generateDelegateCapabilities(awsConfig, awsEncryptionDetails, maskingEvaluator));
    executionCapabilities.addAll(
        CapabilityHelper.generateDelegateCapabilities(spotInstConfig, spotinstEncryptionDetails, maskingEvaluator));
    return new ArrayList<>(executionCapabilities);
  }
}
