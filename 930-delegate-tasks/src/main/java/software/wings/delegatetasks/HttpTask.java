/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.ExecutionStatus;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.DelegateTaskResponse;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.task.AbstractDelegateRunnableTask;
import io.harness.delegate.task.TaskParameters;
import io.harness.delegate.task.http.HttpTaskParameters;
import io.harness.http.HttpService;
import io.harness.http.beans.HttpInternalConfig;
import io.harness.http.beans.HttpInternalResponse;

import software.wings.beans.HttpStateExecutionResponse;

import com.google.inject.Inject;
import java.io.IOException;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.NotImplementedException;

@OwnedBy(CDC)
@Slf4j
@TargetModule(HarnessModule._930_DELEGATE_TASKS)
public class HttpTask extends AbstractDelegateRunnableTask {
  @Inject private HttpService httpService;

  public HttpTask(DelegateTaskPackage delegateTaskPackage, ILogStreamingTaskClient logStreamingTaskClient,
      Consumer<DelegateTaskResponse> postExecute, BooleanSupplier preExecute) {
    super(delegateTaskPackage, logStreamingTaskClient, postExecute, preExecute);
  }

  @Override
  public HttpStateExecutionResponse run(TaskParameters parameters) throws IOException {
    HttpTaskParameters httpTaskParameters = (HttpTaskParameters) parameters;
    HttpInternalResponse httpInternalResponse =
        httpService.executeUrl(HttpInternalConfig.builder()
                                   .method(httpTaskParameters.getMethod())
                                   .body(httpTaskParameters.getBody())
                                   .headers(httpTaskParameters.getHeaders())
                                   .socketTimeoutMillis(httpTaskParameters.getSocketTimeoutMillis())
                                   .url(httpTaskParameters.getUrl())
                                   .useProxy(httpTaskParameters.isUseProxy())
                                   .isCertValidationRequired(httpTaskParameters.isCertValidationRequired())
                                   .throwErrorIfNoProxySetWithDelegateProxy(true)
                                   .build());
    return HttpStateExecutionResponse.builder()
        .executionStatus(
            ExecutionStatus.translateCommandExecutionStatus(httpInternalResponse.getCommandExecutionStatus()))
        .errorMessage(httpInternalResponse.getErrorMessage())
        .header(httpInternalResponse.getHeader())
        .httpMethod(httpInternalResponse.getHttpMethod())
        .httpUrl(httpInternalResponse.getHttpUrl())
        .httpResponseCode(httpInternalResponse.getHttpResponseCode())
        .httpResponseBody(httpInternalResponse.getHttpResponseBody())
        .timedOut(httpInternalResponse.isTimedOut())
        .build();
  }

  @Override
  public HttpStateExecutionResponse run(Object[] parameters) {
    throw new NotImplementedException("Not implemented");
  }
}
