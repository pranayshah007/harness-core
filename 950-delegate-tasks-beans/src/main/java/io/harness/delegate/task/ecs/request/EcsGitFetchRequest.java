package io.harness.delegate.task.ecs.request;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.connector.scm.GitCapabilityHelper;
import io.harness.delegate.beans.connector.scm.adapter.ScmConnectorMapper;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.beans.executioncapability.ExecutionCapabilityDemander;
import io.harness.delegate.beans.storeconfig.GitStoreDelegateConfig;
import io.harness.delegate.capability.EncryptedDataDetailsCapabilityHelper;
import io.harness.delegate.task.ActivityAccess;
import io.harness.delegate.task.TaskParameters;
import io.harness.delegate.task.ecs.EcsGitFetchFileConfig;
import io.harness.delegate.task.serverless.ServerlessGitFetchFileConfig;
import io.harness.expression.Expression;
import io.harness.expression.ExpressionEvaluator;
import io.harness.expression.ExpressionReflectionUtils;
import lombok.Builder;
import lombok.Value;
import lombok.experimental.NonFinal;

import java.util.ArrayList;
import java.util.List;

import static io.harness.expression.Expression.ALLOW_SECRETS;

@Value
@Builder
@OwnedBy(HarnessTeam.CDP)
public class EcsGitFetchRequest implements ActivityAccess, TaskParameters, ExecutionCapabilityDemander, ExpressionReflectionUtils.NestedAnnotationResolver {

  String executionLogName;
  String activityId;
  String accountId;

  @NonFinal
  @Expression(ALLOW_SECRETS)
  EcsGitFetchFileConfig ecsTaskDefinitionGitFetchFileConfig;

  @NonFinal
  @Expression(ALLOW_SECRETS)
  EcsGitFetchFileConfig ecsServiceDefinitionGitFetchFileConfig;

  @NonFinal
  @Expression(ALLOW_SECRETS)
  List<EcsGitFetchFileConfig> ecsScalableTargetGitFetchFileConfigs;

  @NonFinal
  @Expression(ALLOW_SECRETS)
  List<EcsGitFetchFileConfig> ecsScalingPolicyGitFetchFileConfigs;



  @Builder.Default boolean shouldOpenLogStream = true;
  boolean closeLogStream;

  @Override
  public List<ExecutionCapability> fetchRequiredExecutionCapabilities(ExpressionEvaluator maskingEvaluator) {
    List<ExecutionCapability> capabilities = new ArrayList<>();

    List<EcsGitFetchFileConfig> allEcsGitFetchFileConfigs = new ArrayList<>();

    allEcsGitFetchFileConfigs.add(ecsTaskDefinitionGitFetchFileConfig);
    allEcsGitFetchFileConfigs.add(ecsServiceDefinitionGitFetchFileConfig);
    allEcsGitFetchFileConfigs.addAll(ecsScalableTargetGitFetchFileConfigs);
    allEcsGitFetchFileConfigs.addAll(ecsScalingPolicyGitFetchFileConfigs);

    for (EcsGitFetchFileConfig ecsGitFetchFileConfig : allEcsGitFetchFileConfigs) {

      GitStoreDelegateConfig gitStoreDelegateConfig = ecsGitFetchFileConfig.getGitStoreDelegateConfig();

      capabilities.addAll(GitCapabilityHelper.fetchRequiredExecutionCapabilities(
              ScmConnectorMapper.toGitConfigDTO(gitStoreDelegateConfig.getGitConfigDTO()),
              gitStoreDelegateConfig.getEncryptedDataDetails(), gitStoreDelegateConfig.getSshKeySpecDTO()));

      capabilities.addAll(EncryptedDataDetailsCapabilityHelper.fetchExecutionCapabilitiesForEncryptedDataDetails(
              gitStoreDelegateConfig.getEncryptedDataDetails(), maskingEvaluator));

    }

    return capabilities;
  }
}
