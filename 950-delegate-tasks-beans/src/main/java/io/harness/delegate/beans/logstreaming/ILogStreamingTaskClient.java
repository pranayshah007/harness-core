/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.beans.logstreaming;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.delegate.beans.taskprogress.ITaskProgressClient;
import io.harness.logging.LogCallback;
import io.harness.logstreaming.LogLine;

import java.util.Set;
import java.util.concurrent.ExecutorService;

@CodePulse(
    module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_COMMON_STEPS})
@OwnedBy(HarnessTeam.CDP)
public interface ILogStreamingTaskClient {
  /**
   * Open new log stream on log streaming service.
   */
  void openStream(String baseLogKeySuffix);

  /**
   * Close existing log stream on log streaming service
   */
  void closeStream(String baseLogKeySuffix);

  /**
   * Push log message to the existing log stream
   */
  void writeLogLine(LogLine logLine, String baseLogKeySuffix);

  /**
   * Provides an instance of LogCallback interface implementation for backward compatibility reasons, to be used in
   * delegate task implementations, instead of direct constructor invocation. LogService and accountId are provided by
   * the delegate, while appId and activityId should be available as part of task parameters. Please make sure that your
   * task parameters class implements {@link io.harness.delegate.task.Cd1ApplicationAccess} and {@link
   * io.harness.delegate.task.ActivityAccess} interfaces.
   */
  LogCallback obtainLogCallback(String commandName);

  /**
   *
   * Provides an instance of ITaskProgressClient interface implementation that should be used to send task progress
   * updates.
   *
   * @return instance of taskProgressClient or null if the task was submitted to the delegate service without the
   *     delegateCallbackToken
   */
  ITaskProgressClient obtainTaskProgressClient();

  ExecutorService obtainTaskProgressExecutor();

  default void dispatchLogs() {}

  Set<String> getMarkers();
}
