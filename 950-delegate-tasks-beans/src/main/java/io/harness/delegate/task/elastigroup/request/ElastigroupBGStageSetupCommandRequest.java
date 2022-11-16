/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.elastigroup.request;

import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.pcf.ResizeStrategy;
import io.harness.delegate.task.aws.LoadBalancerDetailsForBGDeployment;
import io.harness.delegate.task.elastigroup.response.SpotInstConfig;
import io.harness.expression.Expression;
import io.harness.expression.ExpressionReflectionUtils;
import io.harness.spotinst.model.ElastiGroup;
import lombok.Builder;
import lombok.Data;
import lombok.Value;
import lombok.experimental.NonFinal;

import java.util.List;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.expression.Expression.ALLOW_SECRETS;

@Data
@Builder
@OwnedBy(CDP)
public class ElastigroupBGStageSetupCommandRequest
    implements ElastigroupCommandRequest, ExpressionReflectionUtils.NestedAnnotationResolver {
  String accountId;
  String commandName;
  CommandUnitsProgress commandUnitsProgress;
  String elastigroupJson;
  String elastigroupNamePrefix;
  ElastiGroup elastigroupOriginalConfig;
  private Integer maxInstanceCount;
  private boolean useCurrentRunningInstanceCount;
  private Integer currentRunningInstanceCount;
  String startupScript;
  String image;
  String awsRegion;
  boolean blueGreen;
  ResizeStrategy resizeStrategy;
  List<LoadBalancerDetailsForBGDeployment> awsLoadBalancerConfigs;
  @NonFinal @Expression(ALLOW_SECRETS) Integer timeoutIntervalInMin;
  @NonFinal @Expression(ALLOW_SECRETS) SpotInstConfig spotInstConfig;
}
