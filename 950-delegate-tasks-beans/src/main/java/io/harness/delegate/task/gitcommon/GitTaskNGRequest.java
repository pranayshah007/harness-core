package io.harness.delegate.task.gitcommon;

import static io.harness.expression.Expression.ALLOW_SECRETS;

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
import io.harness.expression.Expression;
import io.harness.expression.ExpressionEvaluator;
import io.harness.reflection.ExpressionReflectionUtils;

import java.util.ArrayList;
import java.util.List;
import lombok.Builder;
import lombok.Value;
import lombok.experimental.NonFinal;
import org.apache.commons.collections.CollectionUtils;

@Value
@Builder
@OwnedBy(HarnessTeam.CDP)
public class GitTaskNGRequest implements ActivityAccess, TaskParameters, ExecutionCapabilityDemander,
                                         ExpressionReflectionUtils.NestedAnnotationResolver {
  String accountId;
  String activityId;
  @NonFinal @Expression(ALLOW_SECRETS) List<GitRequestFileConfig> gitRequestFileConfigs;
  @Builder.Default boolean shouldOpenLogStream = true;
  boolean closeLogStream;
  String commandUnitName;

  @Override
  public List<ExecutionCapability> fetchRequiredExecutionCapabilities(ExpressionEvaluator maskingEvaluator) {
    List<ExecutionCapability> capabilities = new ArrayList<>();

    for (GitRequestFileConfig gitRequestFileConfig : gitRequestFileConfigs) {
      GitStoreDelegateConfig gitStoreDelegateConfig = gitRequestFileConfig.getGitStoreDelegateConfig();

      capabilities.addAll(GitCapabilityHelper.fetchRequiredExecutionCapabilities(
          ScmConnectorMapper.toGitConfigDTO(gitStoreDelegateConfig.getGitConfigDTO()),
          gitStoreDelegateConfig.getEncryptedDataDetails(), gitStoreDelegateConfig.getSshKeySpecDTO()));

      capabilities.addAll(EncryptedDataDetailsCapabilityHelper.fetchExecutionCapabilitiesForEncryptedDataDetails(
          gitStoreDelegateConfig.getEncryptedDataDetails(), maskingEvaluator));
    }

    return capabilities;
  }
}
