/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.citasks;

/**
 * Delegate task to setup CI setup build environment.
 */

import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.DelegateTaskResponse;
import io.harness.delegate.beans.ci.CICleanupTaskParams;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.task.common.AbstractDelegateRunnableTask;
import io.harness.delegate.task.TaskParameters;
import io.harness.exception.ngexception.CIStageExecutionException;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.NotImplementedException;

@Slf4j
public class CICleanupTask extends AbstractDelegateRunnableTask {
  @Inject @Named(CITaskConstants.CLEANUP_VM) private CICleanupTaskHandler ciVmCleanupTaskHandler;
  @Inject @Named(CITaskConstants.CLEANUP_K8) private CICleanupTaskHandler ciK8CleanupTaskHandler;

  public CICleanupTask(DelegateTaskPackage delegateTaskPackage, ILogStreamingTaskClient logStreamingTaskClient,
      Consumer<DelegateTaskResponse> consumer, BooleanSupplier preExecute) {
    super(delegateTaskPackage, logStreamingTaskClient, consumer, preExecute);
  }

  @Override
  public DelegateResponseData run(Object[] parameters) {
    throw new NotImplementedException("Not implemented");
  }

  @Override
  public DelegateResponseData run(TaskParameters parameters) {
    CICleanupTaskParams ciCleanupTaskParams = (CICleanupTaskParams) parameters;
    if (ciCleanupTaskParams.getType() == CICleanupTaskParams.Type.GCP_K8) {
      return ciK8CleanupTaskHandler.executeTaskInternal(ciCleanupTaskParams, getTaskId());
    } else if (ciCleanupTaskParams.getType() == CICleanupTaskParams.Type.VM) {
      return ciVmCleanupTaskHandler.executeTaskInternal(ciCleanupTaskParams, getTaskId());
    } else {
      throw new CIStageExecutionException(
          String.format("Invalid infra type for cleanup step", ciCleanupTaskParams.getType()));
    }
  }
}
