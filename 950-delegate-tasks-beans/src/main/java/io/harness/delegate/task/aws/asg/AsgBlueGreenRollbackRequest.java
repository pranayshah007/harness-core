/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.aws.asg;

import static io.harness.expression.Expression.ALLOW_SECRETS;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.aws.beans.AsgLoadBalancerConfig;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.expression.Expression;
import io.harness.reflection.ExpressionReflectionUtils.NestedAnnotationResolver;

import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Value;
import lombok.experimental.NonFinal;

@Value
@Builder
@OwnedBy(HarnessTeam.CDP)
public class AsgBlueGreenRollbackRequest implements AsgCommandRequest, NestedAnnotationResolver {
  String accountId;
  String commandName;
  CommandUnitsProgress commandUnitsProgress;
  Integer timeoutIntervalInMin;
  @NonFinal @Expression(ALLOW_SECRETS) AsgInfraConfig asgInfraConfig;
  @Deprecated AsgLoadBalancerConfig asgLoadBalancerConfig;
  String prodAsgName;
  String stageAsgName;
  Map<String, List<String>> stageAsgManifestsDataForRollback;
  Map<String, List<String>> prodAsgManifestsDataForRollback;
  boolean servicesSwapped;
  List<AsgLoadBalancerConfig> loadBalancers;

  @Override
  public String getAsgName() {
    return null;
  }
}
