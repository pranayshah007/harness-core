/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.remote.client;

import static java.lang.String.format;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.InvalidRequestException;
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
import net.jodah.failsafe.RetryPolicy;
import net.jodah.failsafe.event.ExecutionCompletedEvent;
import org.apache.commons.lang3.StringUtils;
import retrofit2.Call;
import retrofit2.Response;

@UtilityClass
@Slf4j
@OwnedBy(HarnessTeam.PL)
public class NGRestUtils {
  private static final int MAX_ATTEMPTS = 3;

  public static <T> T getResponse(Call<ResponseDTO<T>> request) {
    RetryPolicy<Response<ResponseDTO<T>>> retryPolicy = getRetryPolicy("Request failed");
    Response<ResponseDTO<T>> response = Failsafe.with(retryPolicy).get(() -> executeRequest(request));
    return handleResponse(response, "");
  }

  public static <T> T getResponse(Call<ResponseDTO<T>> request, String defaultErrorMessage) {
    RetryPolicy<Response<ResponseDTO<T>>> retryPolicy = getRetryPolicy(format(defaultErrorMessage));
    Response<ResponseDTO<T>> response = Failsafe.with(retryPolicy).get(() -> executeRequest(request));
    return handleResponse(response, defaultErrorMessage);
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

  private static <T> T handleResponse(Response<ResponseDTO<T>> response, String defaultErrorMessage) {
    if (response.isSuccessful()) {
      return response.body().getData();
    }

    log.error("Error response received: {}", response);
    String errorMessage = "";
    try {
      ErrorDTO restResponse = JsonUtils.asObject(response.errorBody().string(), new TypeReference<ErrorDTO>() {});
      errorMessage = restResponse.getMessage();
      throw new InvalidRequestException(
          StringUtils.isEmpty(errorMessage) ? defaultErrorMessage : errorMessage, restResponse.getMetadata());
    } catch (Exception e) {
      log.error("Error while converting rest response to ErrorDTO", e);
      throw new InvalidRequestException(StringUtils.isEmpty(errorMessage) ? defaultErrorMessage : errorMessage);
    }
  }

  private <T> RetryPolicy<Response<ResponseDTO<T>>> getRetryPolicy(String failureMessage) {
    return new RetryPolicy<Response<ResponseDTO<T>>>()
        .withBackoff(1, 5, ChronoUnit.SECONDS)
        .handle(IOException.class)
        .handleResultIf(result -> !result.isSuccessful() && isRetryableHttpCode(result.code()))
        .withMaxAttempts(MAX_ATTEMPTS)
        .onFailure(event -> handleFailure(event, failureMessage))
        .onRetriesExceeded(event -> handleFailure(event, failureMessage));
  }

  private static <T> void handleFailure(
      ExecutionCompletedEvent<Response<ResponseDTO<T>>> event, String failureMessage) {
    log.warn(failureMessage + ". "
            + "Attempts : {}",
        event.getAttemptCount(), event.getFailure());
  }

  private static boolean isRetryableHttpCode(int httpCode) {
    // https://stackoverflow.com/questions/51770071/what-are-the-http-codes-to-automatically-retry-the-request
    return httpCode == 408 || httpCode == 502 || httpCode == 503 || httpCode == 504;
  }
}
