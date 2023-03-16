/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.http;

import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.DelegateTaskResponse;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.beans.logstreaming.NGDelegateLogCallback;
import io.harness.delegate.task.TaskParameters;
import io.harness.delegate.task.common.AbstractDelegateRunnableTask;
import io.harness.delegate.task.http.HttpStepResponse;
import io.harness.delegate.task.http.HttpTaskParametersNg;
import io.harness.http.HttpHeaderConfig;
import io.harness.http.HttpService;
import io.harness.http.beans.HttpInternalConfig;
import io.harness.http.beans.HttpInternalResponse;
import io.harness.logging.LogCallback;

import com.google.inject.Inject;
import java.io.IOException;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.NotImplementedException;

@Slf4j
public class HttpTaskNG extends AbstractDelegateRunnableTask {
  public static final String COMMAND_UNIT = "Execute";
  @Inject private HttpService httpService;

  public HttpTaskNG(DelegateTaskPackage delegateTaskPackage, ILogStreamingTaskClient logStreamingTaskClient,
      Consumer<DelegateTaskResponse> postExecute, BooleanSupplier preExecute) {
    super(delegateTaskPackage, logStreamingTaskClient, postExecute, preExecute);
  }

  @Override
  public boolean isSupportingErrorFramework() {
    return true;
  }

  @Override
  public HttpStepResponse run(TaskParameters parameters) throws IOException {
    HttpTaskParametersNg httpTaskParametersNg = (HttpTaskParametersNg) parameters;
    // Todo: Need to look into useProxy and isCertValidationRequired Field.

    final CommandUnitsProgress commandUnitsProgress = CommandUnitsProgress.builder().build();
    // log stream opened already
    final LogCallback executionLogCallback =
        new NGDelegateLogCallback(getLogStreamingTaskClient(), COMMAND_UNIT, false, commandUnitsProgress);

    HttpInternalResponse httpInternalResponse =
        httpService.executeUrl(HttpInternalConfig.builder()
                                   .method(httpTaskParametersNg.getMethod())
                                   .body(httpTaskParametersNg.getBody())
                                   .header(null)
                                   .requestHeaders(httpTaskParametersNg.getRequestHeader() == null
                                           ? null
                                           : httpTaskParametersNg.getRequestHeader().stream().collect(
                                               Collectors.toMap(HttpHeaderConfig::getKey, HttpHeaderConfig::getValue)))
                                   .socketTimeoutMillis(httpTaskParametersNg.getSocketTimeoutMillis())
                                   .url(httpTaskParametersNg.getUrl())
                                   .useProxy(true)
                                   .isCertValidationRequired(false)
                                   .throwErrorIfNoProxySetWithDelegateProxy(false)
                                   .build(),
            executionLogCallback);
    return HttpStepResponse.builder()
        .commandExecutionStatus(httpInternalResponse.getCommandExecutionStatus())
        .errorMessage(httpInternalResponse.getErrorMessage())
        .header(httpInternalResponse.getHeader())
        .httpMethod(httpInternalResponse.getHttpMethod())
        .httpUrl(httpInternalResponse.getHttpUrl())
        .httpResponseCode(httpInternalResponse.getHttpResponseCode())
        .httpResponseBody(httpInternalResponse.getHttpResponseBody())
        .build();
  }

  @Override
  public HttpStepResponse run(Object[] parameters) {
    throw new NotImplementedException("Not implemented");
  }
}
