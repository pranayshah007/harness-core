/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.ecs;

import static io.harness.expression.Expression.ALLOW_SECRETS;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.expression.Expression;

import lombok.Builder;
import lombok.Data;
import lombok.Value;
import lombok.experimental.NonFinal;

@Value
@Data
@Builder
@OwnedBy(HarnessTeam.CDP)
public class EcsLoadBalancerConfig {
  @NonFinal @Expression(ALLOW_SECRETS) String loadBalancer;
  @NonFinal @Expression(ALLOW_SECRETS) String stageListenerArn;
  @NonFinal @Expression(ALLOW_SECRETS) String stageListenerRuleArn;
  @NonFinal @Expression(ALLOW_SECRETS) String prodListenerArn;
  @NonFinal @Expression(ALLOW_SECRETS) String prodListenerRuleArn;
  String prodTargetGroupArn;
  String stageTargetGroupArn;
}
