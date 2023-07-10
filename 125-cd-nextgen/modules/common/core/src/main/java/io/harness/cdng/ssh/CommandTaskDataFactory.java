/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.ssh;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.beans.storeconfig.StoreDelegateConfig;
import io.harness.delegate.beans.storeconfig.StoreDelegateConfigType;
import io.harness.delegate.task.shell.CommandTaskParameters;
import io.harness.delegate.task.ssh.artifact.AzureArtifactDelegateConfig;
import io.harness.delegate.task.ssh.artifact.SshWinRmArtifactDelegateConfig;
import io.harness.delegate.task.ssh.artifact.SshWinRmArtifactType;
import io.harness.delegate.task.ssh.config.FileDelegateConfig;
import io.harness.pms.yaml.ParameterField;
import io.harness.steps.StepUtils;

import software.wings.beans.TaskType;

import com.google.inject.Singleton;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
@OwnedBy(HarnessTeam.CDP)
public class CommandTaskDataFactory {
  private static final String AZURE_UNIVERSAL_PACKAGE = "upack";

  public TaskData create(CommandTaskParameters taskParameters, ParameterField<String> timeout) {
    TaskType taskType = getTaskType(taskParameters);
    return TaskData.builder()
        .async(true)
        .taskType(taskType.name())
        .parameters(new Object[] {taskParameters})
        .timeout(StepUtils.getTimeoutMillis(timeout, StepUtils.DEFAULT_STEP_TIMEOUT))
        .build();
  }

  private TaskType getTaskType(CommandTaskParameters taskParameters) {
    SshWinRmArtifactDelegateConfig artifactDelegateConfig = taskParameters.getArtifactDelegateConfig();
    if (isNotEmpty(taskParameters.getSecretOutputVariables())) {
      return TaskType.COMMAND_TASK_NG_WITH_OUTPUT_VARIABLE_SECRETS;
    } else if (gitConfigExists(taskParameters)) {
      return TaskType.COMMAND_TASK_NG_WITH_GIT_CONFIGS;
    } else if (artifactDelegateConfig != null
        && SshWinRmArtifactType.AZURE.equals(artifactDelegateConfig.getArtifactType())) {
      if (AZURE_UNIVERSAL_PACKAGE.equals(((AzureArtifactDelegateConfig) artifactDelegateConfig).getPackageType())) {
        return TaskType.COMMAND_TASK_NG_WITH_AZURE_UNIVERSAL_PACKAGE_ARTIFACT;
      }
      return TaskType.COMMAND_TASK_NG_WITH_AZURE_ARTIFACT;
    } else if (artifactDelegateConfig != null
        && SshWinRmArtifactType.GITHUB_PACKAGE.equals(artifactDelegateConfig.getArtifactType())) {
      return TaskType.COMMAND_TASK_NG_WITH_GITHUB_PACKAGE_ARTIFACT;
    }
    return TaskType.COMMAND_TASK_NG;
  }

  private boolean gitConfigExists(CommandTaskParameters taskParameters) {
    FileDelegateConfig fileDelegateConfig = taskParameters.getFileDelegateConfig();
    if (fileDelegateConfig == null) {
      return false;
    }

    List<StoreDelegateConfig> stores = fileDelegateConfig.getStores();
    if (isEmpty(stores)) {
      return false;
    }

    // use classical iteration in order to return early
    for (StoreDelegateConfig storeDelegateConfig : stores) {
      if (StoreDelegateConfigType.GIT_FETCHED.equals(storeDelegateConfig.getType())) {
        return true;
      }
    }

    return false;
  }
}
