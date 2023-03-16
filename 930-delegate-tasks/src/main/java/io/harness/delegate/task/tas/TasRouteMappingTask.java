
/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.tas;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.DelegateTaskResponse;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.pcf.CfRouteMappingCommandTaskHandlerNG;
import io.harness.delegate.task.TaskParameters;
import io.harness.delegate.task.common.AbstractDelegateRunnableTask;
import io.harness.delegate.task.pcf.CfDelegateTaskHelper;
import io.harness.delegate.task.pcf.request.CfCommandRequestNG;
import io.harness.delegate.task.pcf.response.CfCommandResponseNG;
import io.harness.secret.SecretSanitizerThreadLocal;

import com.google.inject.Inject;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import org.apache.commons.lang3.NotImplementedException;

@OwnedBy(HarnessTeam.CDP)
public class TasRouteMappingTask extends AbstractDelegateRunnableTask {
  @Inject private CfDelegateTaskHelper cfDelegateTaskHelper;
  @Inject CfRouteMappingCommandTaskHandlerNG cfRouteMappingCommandTaskHandlerNG;

  public TasRouteMappingTask(DelegateTaskPackage delegateTaskPackage, ILogStreamingTaskClient logStreamingTaskClient,
      Consumer<DelegateTaskResponse> consumer, BooleanSupplier preExecute) {
    super(delegateTaskPackage, logStreamingTaskClient, consumer, preExecute);

    SecretSanitizerThreadLocal.addAll(delegateTaskPackage.getSecrets());
  }

  @Override
  public CfCommandResponseNG run(Object[] parameters) {
    throw new NotImplementedException("not implemented");
  }

  @Override
  public CfCommandResponseNG run(TaskParameters parameters) {
    CfCommandRequestNG cfCommandRequestNG = (CfCommandRequestNG) parameters;
    return cfDelegateTaskHelper.getCfCommandResponse(
        cfRouteMappingCommandTaskHandlerNG, cfCommandRequestNG, getLogStreamingTaskClient());
  }

  @Override
  public boolean isSupportingErrorFramework() {
    return true;
  }
}
