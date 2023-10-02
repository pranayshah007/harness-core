/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.serverless;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.DelegateTaskResponse;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.serverless.ServerlessAwsLambdaRollbackV2CommandTaskHandler;
import io.harness.delegate.task.TaskParameters;
import io.harness.delegate.task.common.AbstractDelegateRunnableTask;
import io.harness.delegate.task.serverless.request.ServerlessRollbackV2Request;
import io.harness.delegate.task.serverless.response.ServerlessCommandResponse;
import io.harness.secret.SecretSanitizerThreadLocal;

import com.google.inject.Inject;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import org.apache.commons.lang3.NotImplementedException;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_ECS})
@OwnedBy(HarnessTeam.CDP)
public class ServerlessAwsLambdaRollbackV2CommandTask extends AbstractDelegateRunnableTask {
  @Inject private ServerlessDelegateTaskHelper serverlessDelegateTaskHelper;

  @Inject private ServerlessAwsLambdaRollbackV2CommandTaskHandler rollbackV2commandTaskHandler;

  public ServerlessAwsLambdaRollbackV2CommandTask(DelegateTaskPackage delegateTaskPackage,
      ILogStreamingTaskClient logStreamingTaskClient, Consumer<DelegateTaskResponse> consumer,
      BooleanSupplier preExecute) {
    super(delegateTaskPackage, logStreamingTaskClient, consumer, preExecute);

    SecretSanitizerThreadLocal.addAll(delegateTaskPackage.getSecrets());
  }

  @Override
  public ServerlessCommandResponse run(Object[] parameters) {
    throw new NotImplementedException("not implemented");
  }

  @Override
  public ServerlessCommandResponse run(TaskParameters parameters) {
    ServerlessRollbackV2Request serverlessCommandRequest = (ServerlessRollbackV2Request) parameters;
    return serverlessDelegateTaskHelper.getServerlessRollbackV2Response(
        rollbackV2commandTaskHandler, serverlessCommandRequest, getLogStreamingTaskClient());
  }

  @Override
  public boolean isSupportingErrorFramework() {
    return true;
  }
}
