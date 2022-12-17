/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.elastigroup;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.DelegateTaskResponse;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.elastigroup.ElastigroupBGStageSetupCommandTaskHandler;
import io.harness.delegate.task.TaskParameters;
import io.harness.delegate.task.common.AbstractDelegateRunnableTask;
import io.harness.delegate.task.elastigroup.request.ElastigroupCommandRequest;
import io.harness.delegate.task.elastigroup.response.ElastigroupCommandResponse;
import io.harness.secret.SecretSanitizerThreadLocal;

import com.google.inject.Inject;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import org.apache.commons.lang3.NotImplementedException;

@OwnedBy(HarnessTeam.CDP)
public class ElastigroupBGStageSetupCommandTaskNG extends AbstractDelegateRunnableTask {
  @Inject private ElastigroupDelegateTaskHelper elastigroupDelegateTaskHelper;
  @Inject private ElastigroupBGStageSetupCommandTaskHandler elastigroupBGStageSetupCommandTaskHandler;

  public ElastigroupBGStageSetupCommandTaskNG(DelegateTaskPackage delegateTaskPackage,
      ILogStreamingTaskClient logStreamingTaskClient, Consumer<DelegateTaskResponse> consumer,
      BooleanSupplier preExecute) {
    super(delegateTaskPackage, logStreamingTaskClient, consumer, preExecute);

    SecretSanitizerThreadLocal.addAll(delegateTaskPackage.getSecrets());
  }

  @Override
  public ElastigroupCommandResponse run(Object[] parameters) {
    throw new NotImplementedException("not implemented");
  }

  @Override
  public ElastigroupCommandResponse run(TaskParameters parameters) {
    ElastigroupCommandRequest elastigroupCommandRequest = (ElastigroupCommandRequest) parameters;
    return elastigroupDelegateTaskHelper.getElastigroupCommandResponse(
        elastigroupBGStageSetupCommandTaskHandler, elastigroupCommandRequest, getLogStreamingTaskClient());
  }

  @Override
  public boolean isSupportingErrorFramework() {
    return true;
  }
}
