/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.citasks;

import static java.lang.String.format;

import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.DelegateTaskResponse;
import io.harness.delegate.beans.ci.CIExecuteStepTaskParams;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.task.common.AbstractDelegateRunnableTask;
import io.harness.delegate.task.TaskParameters;
import io.harness.exception.ngexception.CIStageExecutionException;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import org.apache.commons.lang3.NotImplementedException;

public class CIExecuteStepTask extends AbstractDelegateRunnableTask {
  @Inject @Named(CITaskConstants.EXECUTE_STEP_VM) private CIExecuteStepTaskHandler ciVmExecuteStepTaskHandler;
  @Inject @Named(CITaskConstants.EXECUTE_STEP_K8) private CIExecuteStepTaskHandler ciK8ExecuteStepTaskHandler;

  public CIExecuteStepTask(DelegateTaskPackage delegateTaskPackage, ILogStreamingTaskClient logStreamingTaskClient,
      Consumer<DelegateTaskResponse> consumer, BooleanSupplier preExecute) {
    super(delegateTaskPackage, logStreamingTaskClient, consumer, preExecute);
  }

  @Override
  public DelegateResponseData run(Object[] parameters) {
    throw new NotImplementedException("Not implemented");
  }

  @Override
  public DelegateResponseData run(TaskParameters parameters) {
    CIExecuteStepTaskParams ciExecuteStepTaskParams = (CIExecuteStepTaskParams) parameters;
    if (ciExecuteStepTaskParams.getType() == CIExecuteStepTaskParams.Type.K8) {
      return ciK8ExecuteStepTaskHandler.executeTaskInternal(ciExecuteStepTaskParams, getTaskId());
    } else if (ciExecuteStepTaskParams.getType() == CIExecuteStepTaskParams.Type.VM) {
      return ciVmExecuteStepTaskHandler.executeTaskInternal(ciExecuteStepTaskParams, getTaskId());
    } else {
      throw new CIStageExecutionException(
          format("Invalid infra type for executing step", ciExecuteStepTaskParams.getType()));
    }
  }
}
