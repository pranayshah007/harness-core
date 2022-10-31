/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks.aws;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.beans.ExecutionStatus.FAILED;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.ExecutionStatus;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.DelegateTaskResponse;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.task.AbstractDelegateRunnableTask;
import io.harness.delegate.task.TaskParameters;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.exception.sanitizer.ExceptionMessageSanitizer;

import software.wings.beans.command.ExecutionLogCallback;
import software.wings.delegatetasks.DelegateLogService;
import software.wings.service.impl.aws.model.AwsLambdaExecuteFunctionRequest;
import software.wings.service.impl.aws.model.AwsLambdaExecuteFunctionResponse;
import software.wings.service.impl.aws.model.AwsLambdaExecuteWfRequest;
import software.wings.service.impl.aws.model.AwsLambdaExecuteWfResponse;
import software.wings.service.impl.aws.model.AwsLambdaFunctionRequest;
import software.wings.service.impl.aws.model.AwsLambdaFunctionResponse;
import software.wings.service.impl.aws.model.AwsLambdaRequest;
import software.wings.service.impl.aws.model.AwsLambdaRequest.AwsLambdaRequestType;
import software.wings.service.impl.aws.model.AwsResponse;
import software.wings.service.impl.aws.model.request.AwsLambdaDetailsRequest;
import software.wings.service.intfc.aws.delegate.AwsLambdaHelperServiceDelegate;

import com.google.inject.Inject;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.NotImplementedException;

@Slf4j
@TargetModule(HarnessModule._930_DELEGATE_TASKS)
@OwnedBy(CDP)
public class AwsLambdaTask extends AbstractDelegateRunnableTask {
  @Inject private DelegateLogService delegateLogService;
  @Inject private AwsLambdaHelperServiceDelegate awsLambdaHelperServiceDelegate;

  public AwsLambdaTask(DelegateTaskPackage delegateTaskPackage, ILogStreamingTaskClient logStreamingTaskClient,
      Consumer<DelegateTaskResponse> consumer, BooleanSupplier preExecute) {
    super(delegateTaskPackage, logStreamingTaskClient, consumer, preExecute);
  }

  @Override
  public AwsResponse run(TaskParameters parameters) {
    throw new NotImplementedException("not implemented");
  }

  @Override
  public AwsResponse run(Object[] parameters) {
    AwsLambdaRequest request = (AwsLambdaRequest) parameters[0];
    AwsLambdaRequestType requestType = request.getRequestType();
    switch (requestType) {
      case EXECUTE_LAMBDA_FUNCTION: {
        try {
          return awsLambdaHelperServiceDelegate.executeFunction((AwsLambdaExecuteFunctionRequest) request);
        } catch (Exception ex) {
          return AwsLambdaExecuteFunctionResponse.builder()
              .executionStatus(ExecutionStatus.FAILED)
              .errorMessage(ExceptionUtils.getMessage(ExceptionMessageSanitizer.sanitizeException(ex)))
              .build();
        }
      }
      case EXECUTE_LAMBDA_WF: {
        try {
          AwsLambdaExecuteWfRequest awsLambdaExecuteWfRequest = (AwsLambdaExecuteWfRequest) request;
          ExecutionLogCallback logCallback = new ExecutionLogCallback(delegateLogService,
              awsLambdaExecuteWfRequest.getAccountId(), awsLambdaExecuteWfRequest.getAppId(),
              awsLambdaExecuteWfRequest.getActivityId(), awsLambdaExecuteWfRequest.getCommandName());
          return awsLambdaHelperServiceDelegate.executeWf(awsLambdaExecuteWfRequest, logCallback);
        } catch (Exception ex) {
          return AwsLambdaExecuteWfResponse.builder()
              .executionStatus(FAILED)
              .errorMessage(ExceptionUtils.getMessage(ExceptionMessageSanitizer.sanitizeException(ex)))
              .build();
        }
      }
      case LIST_LAMBDA_FUNCTION: {
        try {
          return awsLambdaHelperServiceDelegate.getLambdaFunctions((AwsLambdaFunctionRequest) request);
        } catch (Exception ex) {
          return AwsLambdaFunctionResponse.builder()
              .executionStatus(FAILED)
              .errorMessage(ExceptionUtils.getMessage(ExceptionMessageSanitizer.sanitizeException(ex)))
              .build();
        }
      }
      case LAMBDA_FUNCTION_DETAILS: {
        try {
          return awsLambdaHelperServiceDelegate.getFunctionDetails((AwsLambdaDetailsRequest) request, false);
        } catch (Exception ex) {
          return AwsLambdaFunctionResponse.builder()
              .executionStatus(FAILED)
              .errorMessage(ExceptionUtils.getMessage(ExceptionMessageSanitizer.sanitizeException(ex)))
              .build();
        }
      }

      default: {
        throw new InvalidRequestException("Invalid request type [" + requestType + "]", WingsException.USER);
      }
    }
  }
}
