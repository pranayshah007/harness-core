/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.remote.client;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.exception.HarnessRemoteServiceException;
import io.harness.exception.UnexpectedException;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.serializer.JsonUtils;

import com.fasterxml.jackson.core.type.TypeReference;
import java.io.IOException;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.FailsafeException;
import net.jodah.failsafe.RetryPolicy;
import net.jodah.failsafe.event.ExecutionAttemptedEvent;
import org.apache.commons.lang3.StringUtils;
import retrofit2.Call;
import retrofit2.Response;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true,
    components = {HarnessModuleComponent.CDS_PIPELINE, HarnessModuleComponent.CDS_FIRST_GEN})
@UtilityClass
@Slf4j
public class PipelineRestUtils {
  private static final int MAX_ATTEMPTS = 3;

  /**
   * This functions calls the given service and returns the response if api call was successful.
   * If api call fails, this returns an HarnessServiceException with  full hints and explanations from api response.
   */
  public <T> T getResponse(Call<ResponseDTO<T>> request) {
    RetryPolicy<Response<ResponseDTO<T>>> retryPolicy = getRetryPolicy("Request failed");
    try {
      Response<ResponseDTO<T>> response = Failsafe.with(retryPolicy).get(() -> executeRequest(request));
      return handleResponse(response, "");
    } catch (FailsafeException ex) {
      throw new UnexpectedException(NGRestUtils.DEFAULT_ERROR_MESSAGE, ex.getCause());
    }
  }

  /**
   * Implementation for handleResponse was not proper as for any error it was just using message and not utilising stack
   * trace. This will now throw an exception which we know for sure how to resolve.
   */
  private static <T> T handleResponse(Response<ResponseDTO<T>> response, String defaultErrorMessage) {
    if (response.isSuccessful()) {
      return response.body().getData();
    }

    log.error("Error response received: {}", response);
    String errorMessage = "";
    try {
      ErrorDTO restResponse = JsonUtils.asObject(response.errorBody().string(), new TypeReference<ErrorDTO>() {});
      errorMessage = restResponse.getMessage();
      throw new HarnessRemoteServiceException(StringUtils.isEmpty(errorMessage) ? defaultErrorMessage : errorMessage,
          restResponse.getMetadata(), restResponse.getResponseMessages());
    } catch (HarnessRemoteServiceException e) {
      throw e;
    } catch (IOException e) {
      throw new RuntimeException(e);
    } finally {
      if (!response.isSuccessful() && response.errorBody() != null) {
        response.errorBody().close();
      }
    }
  }

  private <T> RetryPolicy<Response<ResponseDTO<T>>> getRetryPolicy(String failureMessage) {
    return new RetryPolicy<Response<ResponseDTO<T>>>()
        .withBackoff(1, 10, ChronoUnit.SECONDS)
        .handle(IOException.class)
        .handleResultIf(result -> !result.isSuccessful() && isRetryableHttpCode(result.code()))
        .withMaxAttempts(MAX_ATTEMPTS)
        .onFailedAttempt(event -> handleFailure(event, failureMessage));
  }

  private static <T> void handleFailure(
      ExecutionAttemptedEvent<Response<ResponseDTO<T>>> event, String failureMessage) {
    if (event.getLastResult() == null) {
      log.warn(String.format("%s. Attempt : %d.", failureMessage, event.getAttemptCount()), event.getLastFailure());
    } else {
      log.warn(String.format(
                   "%s. Attempt : %d. Response : %s", failureMessage, event.getAttemptCount(), event.getLastResult()),
          event.getLastFailure());
    }
  }

  private static boolean isRetryableHttpCode(int httpCode) {
    // https://stackoverflow.com/questions/51770071/what-are-the-http-codes-to-automatically-retry-the-request
    return httpCode == 408 || httpCode == 502 || httpCode == 503 || httpCode == 504;
  }

  private static <T> Response<ResponseDTO<T>> executeRequest(Call<ResponseDTO<T>> request) throws IOException {
    try {
      Call<ResponseDTO<T>> cloneRequest = request.clone();
      return cloneRequest == null ? request.execute() : cloneRequest.execute();
    } catch (IOException ioException) {
      String url = Optional.ofNullable(request.request()).map(x -> x.url().encodedPath()).orElse(null);
      log.error("IO error while connecting to the service: {}", url, ioException);
      throw ioException;
    }
  }
}
