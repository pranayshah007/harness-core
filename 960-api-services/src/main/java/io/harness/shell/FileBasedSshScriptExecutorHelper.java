/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.shell;
import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.logging.NoopExecutionCallback;
import io.harness.shell.ssh.SshClientManager;
import io.harness.shell.ssh.xfer.ScpRequest;
import io.harness.shell.ssh.xfer.ScpResponse;

import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_AMI_ASG})
@Slf4j
@OwnedBy(CDP)
public class FileBasedSshScriptExecutorHelper {
  public static CommandExecutionStatus scpOneFile(String remoteFilePath,
      AbstractScriptExecutor.FileProvider fileProvider, SshSessionConfig config, LogCallback logCallback,
      boolean shouldSaveExecutionLogs) {
    if (!shouldSaveExecutionLogs) {
      logCallback = new NoopExecutionCallback();
    }
    ScpResponse scpResponse = SshClientManager.scpUpload(
        ScpRequest.builder().fileProvider(fileProvider).remoteFilePath(remoteFilePath).build(), config, logCallback);
    return scpResponse.getStatus();
  }
}
