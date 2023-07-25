/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.citasks;
import static java.lang.String.format;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.delegate.beans.DelegateMetaInfo;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.DelegateTaskResponse;
import io.harness.delegate.beans.ci.CIInitializeTaskParams;
import io.harness.delegate.beans.ci.CITaskExecutionResponse;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.task.TaskParameters;
import io.harness.delegate.task.common.AbstractDelegateRunnableTask;
import io.harness.exception.ngexception.CIStageExecutionException;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.NotImplementedException;

/**
 * Delegate task to setup CI build environment. It calls CIK8BuildTaskHandler class to setup the build environment on
 * K8.
 */

@CodePulse(
    module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_COMMON_STEPS})
@Slf4j
public class CIInitializeTask extends AbstractDelegateRunnableTask {
  @Inject @Named(CITaskConstants.INIT_VM) private CIInitializeTaskHandler ciVmInitializeTaskHandler;
  @Inject @Named(CITaskConstants.INIT_K8) private CIInitializeTaskHandler ciK8InitializeTaskHandler;

  public CIInitializeTask(DelegateTaskPackage delegateTaskPackage, ILogStreamingTaskClient logStreamingTaskClient,
      Consumer<DelegateTaskResponse> consumer, BooleanSupplier preExecute) {
    super(delegateTaskPackage, logStreamingTaskClient, consumer, preExecute);
  }

  @Override
  public DelegateResponseData run(Object[] parameters) {
    throw new NotImplementedException("Not implemented");
  }

  @Override
  public DelegateResponseData run(TaskParameters parameters) {
    CIInitializeTaskParams ciInitializeTaskParams = (CIInitializeTaskParams) parameters;
    CIInitializeTaskParams.Type type = ciInitializeTaskParams.getType();
    if (type == CIInitializeTaskParams.Type.GCP_K8) {
      return ciK8InitializeTaskHandler.executeTaskInternal(
          ciInitializeTaskParams, getLogStreamingTaskClient(), getTaskId());
    } else if (type == CIInitializeTaskParams.Type.VM || type == CIInitializeTaskParams.Type.DOCKER) {
      CITaskExecutionResponse response = ciVmInitializeTaskHandler.executeTaskInternal(
          ciInitializeTaskParams, getLogStreamingTaskClient(), getTaskId());
      response.setDelegateMetaInfo(DelegateMetaInfo.builder().id(getDelegateId()).build());
      return response;
    } else {
      throw new CIStageExecutionException(
          format("Invalid infra type [%s] for initializing stage", ciInitializeTaskParams.getType()));
    }
  }
}
