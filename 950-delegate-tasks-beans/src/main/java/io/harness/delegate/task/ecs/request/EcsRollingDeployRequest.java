package io.harness.delegate.task.ecs.request;

import static io.harness.expression.Expression.ALLOW_SECRETS;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.task.ecs.EcsCommandTypeNG;
import io.harness.delegate.task.ecs.EcsInfraConfig;
import io.harness.expression.Expression;
import io.harness.expression.ExpressionReflectionUtils.NestedAnnotationResolver;

import java.util.List;
import lombok.Builder;
import lombok.Value;
import lombok.experimental.NonFinal;

@Value
@Builder
@OwnedBy(HarnessTeam.CDP)
public class EcsRollingDeployRequest implements EcsCommandRequest, NestedAnnotationResolver {
  String accountId;
  EcsCommandTypeNG ecsCommandType;
  String commandName;
  CommandUnitsProgress commandUnitsProgress;
  @NonFinal @Expression(ALLOW_SECRETS) EcsInfraConfig ecsInfraConfig;
  @NonFinal @Expression(ALLOW_SECRETS) Integer timeoutIntervalInMin;
  @NonFinal @Expression(ALLOW_SECRETS) String ecsTaskDefinitionManifestContent;
  @NonFinal @Expression(ALLOW_SECRETS) String ecsServiceDefinitionManifestContent;
  @NonFinal @Expression(ALLOW_SECRETS) List<String> ecsScalableTargetManifestContentList;
  @NonFinal @Expression(ALLOW_SECRETS) List<String> ecsScalingPolicyManifestContentList;
  @NonFinal @Expression(ALLOW_SECRETS) boolean sameAsAlreadyRunningInstances;
  @NonFinal @Expression(ALLOW_SECRETS) boolean forceNewDeployment;
}
