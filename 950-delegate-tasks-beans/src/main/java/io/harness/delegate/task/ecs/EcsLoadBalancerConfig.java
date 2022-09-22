package io.harness.delegate.task.ecs;

import static io.harness.expression.Expression.ALLOW_SECRETS;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.expression.Expression;

import lombok.Builder;
import lombok.Value;
import lombok.experimental.NonFinal;

@Value
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
