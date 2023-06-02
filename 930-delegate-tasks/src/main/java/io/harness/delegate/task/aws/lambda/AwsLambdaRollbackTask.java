/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.aws.lambda;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.aws.lambda.AwsLambdaRollbackTaskCommandHandler;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.DelegateTaskResponse;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.beans.logstreaming.UnitProgressDataMapper;
import io.harness.delegate.exception.TaskNGDataException;
import io.harness.delegate.task.TaskParameters;
import io.harness.delegate.task.aws.lambda.request.AwsLambdaCommandRequest;
import io.harness.delegate.task.aws.lambda.response.AwsLambdaRollbackResponse;
import io.harness.delegate.task.common.AbstractDelegateRunnableTask;
import io.harness.exception.sanitizer.ExceptionMessageSanitizer;
import io.harness.secret.SecretSanitizerThreadLocal;

import com.google.inject.Inject;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.NotImplementedException;

@Slf4j
@OwnedBy(HarnessTeam.CDP)
public class AwsLambdaRollbackTask extends AbstractDelegateRunnableTask {
  @Inject private AwsLambdaRollbackTaskCommandHandler awsLambdaRollbackTaskCommandHandler;
  @Inject private AwsLambdaInfraConfigHelper awsLambdaInfraConfigHelper;

  public AwsLambdaRollbackTask(DelegateTaskPackage delegateTaskPackage, ILogStreamingTaskClient logStreamingTaskClient,
      Consumer<DelegateTaskResponse> consumer, BooleanSupplier preExecute) {
    super(delegateTaskPackage, logStreamingTaskClient, consumer, preExecute);

    SecretSanitizerThreadLocal.addAll(delegateTaskPackage.getSecrets());
  }

  @Override
  public AwsLambdaRollbackResponse run(Object[] parameters) {
    throw new NotImplementedException("not implemented");
  }

  public AwsLambdaRollbackResponse run(TaskParameters parameters) {
    AwsLambdaCommandRequest awsLambdaCommandRequest = (AwsLambdaCommandRequest) parameters;
    CommandUnitsProgress commandUnitsProgress = awsLambdaCommandRequest.getCommandUnitsProgress() != null
        ? awsLambdaCommandRequest.getCommandUnitsProgress()
        : CommandUnitsProgress.builder().build();
    log.info("Starting task execution for command: {}", awsLambdaCommandRequest.getAwsLambdaCommandType().name());
    awsLambdaInfraConfigHelper.decryptInfraConfig(awsLambdaCommandRequest.getAwsLambdaInfraConfig());

    try {
      AwsLambdaRollbackResponse awsLambdaRollbackResponse = awsLambdaRollbackTaskCommandHandler.executeTaskInternal(
          awsLambdaCommandRequest, getLogStreamingTaskClient(), commandUnitsProgress);
      awsLambdaRollbackResponse.setCommandUnitsProgress(
          UnitProgressDataMapper.toUnitProgressData(commandUnitsProgress));
      return awsLambdaRollbackResponse;
    } catch (Exception e) {
      Exception sanitizedException = ExceptionMessageSanitizer.sanitizeException(e);
      log.error("Exception in processing Aws Lambda function task [{}]",
          awsLambdaCommandRequest.getCommandName() + ":" + awsLambdaCommandRequest.getAwsLambdaCommandType(),
          sanitizedException);
      throw new TaskNGDataException(
          UnitProgressDataMapper.toUnitProgressData(commandUnitsProgress), sanitizedException);
    }
  }
}
