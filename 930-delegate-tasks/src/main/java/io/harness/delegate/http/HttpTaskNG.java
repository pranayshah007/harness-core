/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.http;

import io.harness.beans.HttpCertificate;
import io.harness.beans.HttpCertificate.HttpCertificateBuilder;
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
import org.jetbrains.annotations.NotNull;

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
                                   .certificate(getHttpCertificate(httpTaskParametersNg))
                                   .encryptedDataDetails(httpTaskParametersNg.getEncryptedDataDetails())
                                   .isCertValidationRequired(httpTaskParametersNg.isCertValidationRequired())
                                   .throwErrorIfNoProxySetWithDelegateProxy(false)
                                   .supportNonTextResponse(httpTaskParametersNg.isSupportNonTextResponse())
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
        .httpResponseBodyInBytes(httpInternalResponse.getHttpResponseBodyInBytes())
        .build();
  }

  @NotNull
  protected static HttpCertificate getHttpCertificate(HttpTaskParametersNg httpTaskParametersNg) {
    if (httpTaskParametersNg.getCertificateNG() != null) {
      HttpCertificateBuilder httpCertificate = HttpCertificate.builder();
      if (httpTaskParametersNg.getCertificateNG().getCertificate() != null) {
        httpCertificate.cert(httpTaskParametersNg.getCertificateNG().getCertificate().toCharArray());
      }
      if (httpTaskParametersNg.getCertificateNG().getCertificateKey() != null) {
        httpCertificate.certKey(httpTaskParametersNg.getCertificateNG().getCertificateKey().toCharArray());
      }
      httpCertificate.keyStoreType(httpTaskParametersNg.getCertificateNG().getKeyStoreType());
      return httpCertificate.build();
    }
    // To keep existing behavior, if certificate is not set then set certificate as null
    return null;
  }

  @Override
  public HttpStepResponse run(Object[] parameters) {
    throw new NotImplementedException("Not implemented");
  }
}
