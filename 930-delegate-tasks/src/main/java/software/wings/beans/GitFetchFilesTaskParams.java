/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.expression.Expression.ALLOW_SECRETS;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.beans.executioncapability.ExecutionCapabilityDemander;
import io.harness.delegate.beans.executioncapability.SelectorCapability;
import io.harness.delegate.capability.EncryptedDataDetailsCapabilityHelper;
import io.harness.delegate.task.ActivityAccess;
import io.harness.delegate.task.TaskParameters;
import io.harness.expression.Expression;
import io.harness.expression.ExpressionEvaluator;
import io.harness.reflection.ExpressionReflectionUtils.NestedAnnotationResolver;

import software.wings.beans.appmanifest.AppManifestKind;
import software.wings.delegatetasks.delegatecapability.CapabilityHelper;
import software.wings.delegatetasks.validation.capabilities.GitConnectionCapability;
import software.wings.service.impl.ContainerServiceParams;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@TargetModule(HarnessModule._870_CG_ORCHESTRATION)
@OwnedBy(CDP)
public class GitFetchFilesTaskParams
    implements ActivityAccess, TaskParameters, ExecutionCapabilityDemander, NestedAnnotationResolver {
  private String accountId;
  private String appId;
  private String activityId;
  private boolean isFinalState;
  private AppManifestKind appManifestKind;
  @Expression(ALLOW_SECRETS) private Map<String, GitFetchFilesConfig> gitFetchFilesConfigMap;
  private final ContainerServiceParams containerServiceParams;
  private boolean isBindTaskFeatureSet; // BIND_FETCH_FILES_TASK_TO_DELEGATE
  private String executionLogName;
  private Set<String> delegateSelectors;
  private boolean isGitHostConnectivityCheck;
  private boolean optimizedFilesFetch;
  private boolean shouldInheritGitFetchFilesConfigMap;
  private boolean isCloseLogStream;

  @Override
  public List<ExecutionCapability> fetchRequiredExecutionCapabilities(ExpressionEvaluator maskingEvaluator) {
    List<ExecutionCapability> executionCapabilities = new ArrayList<>();

    if (isBindTaskFeatureSet && containerServiceParams != null) {
      executionCapabilities.addAll(containerServiceParams.fetchRequiredExecutionCapabilities(maskingEvaluator));
    }

    if (isNotEmpty(gitFetchFilesConfigMap)) {
      if (isGitHostConnectivityCheck) {
        for (Map.Entry<String, GitFetchFilesConfig> entry : gitFetchFilesConfigMap.entrySet()) {
          GitFetchFilesConfig gitFetchFileConfig = entry.getValue();
          executionCapabilities.addAll(
              CapabilityHelper.generateExecutionCapabilitiesForGit(gitFetchFileConfig.getGitConfig()));
          executionCapabilities.addAll(
              EncryptedDataDetailsCapabilityHelper.fetchExecutionCapabilitiesForEncryptedDataDetails(
                  gitFetchFileConfig.getEncryptedDataDetails(), maskingEvaluator));
        }
      } else {
        for (Map.Entry<String, GitFetchFilesConfig> entry : gitFetchFilesConfigMap.entrySet()) {
          GitFetchFilesConfig gitFetchFileConfig = entry.getValue();
          final GitConfig gitConfig = gitFetchFileConfig.getGitConfig();
          executionCapabilities.add(GitConnectionCapability.builder()
                                        .gitConfig(gitConfig)
                                        .settingAttribute(gitConfig.getSshSettingAttribute())
                                        .encryptedDataDetails(gitFetchFileConfig.getEncryptedDataDetails())
                                        .build());
          if (isNotEmpty(gitConfig.getDelegateSelectors())) {
            executionCapabilities.add(
                SelectorCapability.builder().selectors(new HashSet<>(gitConfig.getDelegateSelectors())).build());
          }
          executionCapabilities.addAll(
              EncryptedDataDetailsCapabilityHelper.fetchExecutionCapabilitiesForEncryptedDataDetails(
                  gitFetchFileConfig.getEncryptedDataDetails(), maskingEvaluator));
        }
      }
    }

    if (isNotEmpty(delegateSelectors)) {
      executionCapabilities.add(SelectorCapability.builder().selectors(delegateSelectors).build());
    }

    return executionCapabilities;
  }
}
