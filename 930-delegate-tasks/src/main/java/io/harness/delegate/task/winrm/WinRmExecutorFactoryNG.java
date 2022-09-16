/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.winrm;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.beans.logstreaming.NGDelegateLogCallback;
import io.harness.delegate.task.artifactory.ArtifactoryRequestMapper;
import io.harness.logging.LogCallback;
import io.harness.security.encryption.SecretDecryptionService;

import software.wings.core.winrm.executors.WinRmExecutor;

import com.google.inject.Inject;
import com.google.inject.Singleton;

@OwnedBy(HarnessTeam.CDP)
@Singleton
public class WinRmExecutorFactoryNG {
  @Inject private SecretDecryptionService secretDecryptionService;
  @Inject private ArtifactoryRequestMapper artifactoryRequestMapper;
  @Inject private WinRmExecutorHelper winRmExecutorHelper;

  public WinRmExecutor getExecutor(WinRmSessionConfig config, boolean disableCommandEncoding,
      ILogStreamingTaskClient logStreamingTaskClient, CommandUnitsProgress commandUnitsProgress) {
    return new DefaultWinRmExecutor(getExecutionLogCallback(config, logStreamingTaskClient, commandUnitsProgress), true,
        config, disableCommandEncoding);
  }

  public FileBasedWinRmExecutorNG getFiledBasedWinRmExecutor(WinRmSessionConfig config, boolean disableCommandEncoding,
      ILogStreamingTaskClient logStreamingTaskClient, CommandUnitsProgress commandUnitsProgress) {
    return new FileBasedWinRmExecutorNG(getExecutionLogCallback(config, logStreamingTaskClient, commandUnitsProgress),
        true, config, disableCommandEncoding, secretDecryptionService, artifactoryRequestMapper, winRmExecutorHelper);
  }

  private static LogCallback getExecutionLogCallback(WinRmSessionConfig sessionConfig,
      ILogStreamingTaskClient logStreamingTaskClient, CommandUnitsProgress commandUnitsProgress) {
    return new NGDelegateLogCallback(
        logStreamingTaskClient, sessionConfig.getCommandUnitName(), true, commandUnitsProgress);
  }
}
