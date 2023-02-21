/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.sam;

import com.google.inject.Inject;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.DelegateTaskResponse;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.task.TaskParameters;
import io.harness.delegate.task.common.AbstractDelegateRunnableTask;
import io.harness.delegate.task.aws.sam.request.AwsSamCommandRequest;
import io.harness.delegate.task.aws.sam.request.AwsSamDeployRequest;
import io.harness.delegate.task.aws.sam.response.AwsSamDeployResponse;
import io.harness.exception.InvalidArgumentsException;
import io.harness.secret.SecretSanitizerThreadLocal;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.NotImplementedException;
import org.apache.commons.lang3.tuple.Pair;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

@Slf4j
@OwnedBy(HarnessTeam.CDP)
public class AwsSamDeployTask extends AbstractDelegateRunnableTask {

  @Inject private AwsSamDelegateTaskHelper awsSamDelegateTaskHelper;

  public AwsSamDeployTask(DelegateTaskPackage delegateTaskPackage,
                          ILogStreamingTaskClient logStreamingTaskClient, Consumer<DelegateTaskResponse> consumer,
                          BooleanSupplier preExecute) {
    super(delegateTaskPackage, logStreamingTaskClient, consumer, preExecute);
    SecretSanitizerThreadLocal.addAll(delegateTaskPackage.getSecrets());
  }

  @Override
  public AwsSamDeployResponse run(Object[] parameters) {
    throw new NotImplementedException("not implemented");
  }

  @Override
  public AwsSamDeployResponse run(TaskParameters parameters) {
    AwsSamCommandRequest awsSamCommandRequest = (AwsSamCommandRequest) parameters;

    if (!(awsSamCommandRequest instanceof AwsSamDeployRequest)) {
      throw new InvalidArgumentsException(
              Pair.of("awsCommandRequest", "Must be instance of AwsSamDeployRequest"));
    }

    AwsSamDeployRequest awsSamDeployRequest = (AwsSamDeployRequest) awsSamCommandRequest;

    AwsSamDeployResponse awsSamDeployResponse = null;

    try {
      awsSamDeployResponse = awsSamDelegateTaskHelper.handleAwsSamDeployRequest(
              awsSamDeployRequest, getLogStreamingTaskClient());
    } catch (Exception e) {
      log.error("Exception occured while executing sam deploy task", e);
      throw new RuntimeException(e);
    }

    return awsSamDeployResponse;
  }

  @Override
  public boolean isSupportingErrorFramework() {
    return true;
  }
}
