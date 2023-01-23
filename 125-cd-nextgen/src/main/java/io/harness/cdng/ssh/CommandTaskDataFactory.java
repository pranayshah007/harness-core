/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.ssh;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.task.shell.CommandTaskParameters;
import io.harness.delegate.task.ssh.artifact.SshWinRmArtifactDelegateConfig;
import io.harness.delegate.task.ssh.artifact.SshWinRmArtifactType;
import io.harness.pms.yaml.ParameterField;
import io.harness.steps.StepUtils;

import software.wings.beans.TaskType;

import com.google.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
@OwnedBy(HarnessTeam.CDP)
public class CommandTaskDataFactory {
  public TaskData create(CommandTaskParameters taskParameters, ParameterField<String> timeout) {
    SshWinRmArtifactDelegateConfig artifactDelegateConfig = taskParameters.getArtifactDelegateConfig();
    TaskType taskType;
    if (artifactDelegateConfig != null && SshWinRmArtifactType.AZURE.equals(artifactDelegateConfig.getArtifactType())) {
      taskType = TaskType.COMMAND_TASK_NG_WITH_AZURE_ARTIFACT;
    } else {
      taskType = TaskType.COMMAND_TASK_NG;
    }

    return TaskData.builder()
        .async(true)
        .taskType(taskType.name())
        .parameters(new Object[] {taskParameters})
        .timeout(StepUtils.getTimeoutMillis(timeout, StepUtils.DEFAULT_STEP_TIMEOUT))
        .build();
  }
}
