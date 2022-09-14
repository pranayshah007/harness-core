package io.harness.delegate.task.ecs.request;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.task.ecs.EcsCommandTypeNG;
import io.harness.delegate.task.ecs.EcsInfraConfig;
import io.harness.expression.Expression;
import io.harness.expression.ExpressionReflectionUtils;
import lombok.Builder;
import lombok.Value;
import lombok.experimental.NonFinal;

import static io.harness.expression.Expression.ALLOW_SECRETS;

@Value
@Builder
@OwnedBy(HarnessTeam.CDP)
public class EcsBlueGreenSwapTargetGroupsRequest implements EcsCommandRequest, ExpressionReflectionUtils.NestedAnnotationResolver {
    String accountId;
    EcsCommandTypeNG ecsCommandType;
    String commandName;
    CommandUnitsProgress commandUnitsProgress;
    @NonFinal @Expression(ALLOW_SECRETS) EcsInfraConfig ecsInfraConfig;
    @NonFinal @Expression(ALLOW_SECRETS) Integer timeoutIntervalInMin;
    @NonFinal @Expression(ALLOW_SECRETS) String loadBalancer;
    @NonFinal @Expression(ALLOW_SECRETS) String stageListenerArn;
    @NonFinal @Expression(ALLOW_SECRETS) String stageListenerRuleArn;
    @NonFinal @Expression(ALLOW_SECRETS) String prodListenerArn;
    @NonFinal @Expression(ALLOW_SECRETS) String prodListenerRuleArn;
    String prodTargetGroupArn;
    String stageTargetGroupArn;
    String oldServiceName;
    String newServiceName;

}
