/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.shell.ssh;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.task.shell.CommandTaskParameters;
import io.harness.delegate.task.ssh.NgCommandUnit;
import io.harness.shell.ExecuteCommandResponse;

import java.util.Map;

@OwnedBy(HarnessTeam.CDP)
public interface CommandHandler {
  String RESOLVED_ENV_VARIABLES_KEY = "RESOLVED_ENV_VARIABLES";
  default String getExecutionStagingDir(CommandTaskParameters taskParameters) {
    return String.format("/tmp/%s", taskParameters.getExecutionId());
  }

  ExecuteCommandResponse handle(CommandTaskParameters parameters, NgCommandUnit commandUnit,
      ILogStreamingTaskClient logStreamingTaskClient, CommandUnitsProgress commandUnitsProgress,
      Map<String, Object> taskContext);
}
