/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.remote.client;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.UnexpectedException;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.dto.GenericErrorMessage;
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

@UtilityClass
@Slf4j
@OwnedBy(HarnessTeam.PL)
public class NGRestUtils {
  private static final int MAX_ATTEMPTS = 3;

  public static final String DEFAULT_ERROR_MESSAGE = "Error occurred while performing this operation.";

  public static <T> T getResponse(Call<ResponseDTO<T>> request) {
    RetryPolicy<Response<ResponseDTO<T>>> retryPolicy = getRetryPolicy("Request failed");
    try {
      Response<ResponseDTO<T>> response = Failsafe.with(retryPolicy).get(() -> executeRequest(request));
      return handleResponse(response, "");
    } catch (FailsafeException ex) {
      throw new UnexpectedException(DEFAULT_ERROR_MESSAGE, ex.getCause());
    }
  }

  // This method handles all call requests not using ResponseDTO
  public static <T> T getGeneralResponse(Call<T> request) {
    RetryPolicy<Response<T>> retryPolicy = getRetryPolicyForResponse("Request failed");
    try {
      Response<T> response = Failsafe.with(retryPolicy).get(() -> executeGeneralRequest(request));
      return handleGeneralResponse(response, "");
    } catch (FailsafeException ex) {
      throw new UnexpectedException(DEFAULT_ERROR_MESSAGE, ex.getCause());
    }
  }

  public static <T> T getResponse(Call<ResponseDTO<T>> request, String defaultErrorMessage) {
    RetryPolicy<Response<ResponseDTO<T>>> retryPolicy = getRetryPolicy(defaultErrorMessage);
    try {
      Response<ResponseDTO<T>> response = Failsafe.with(retryPolicy).get(() -> executeRequest(request));
      return handleResponse(response, defaultErrorMessage);
    } catch (FailsafeException ex) {
      throw new UnexpectedException(defaultErrorMessage, ex.getCause());
    }
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

  // Execute general response (not using ResponseDTO)
  private static <T> Response<T> executeGeneralRequest(Call<T> request) throws IOException {
    try {
      Call<T> cloneRequest = request.clone();
      return cloneRequest == null ? request.execute() : cloneRequest.execute();
    } catch (IOException ioException) {
      String url = Optional.ofNullable(request.request()).map(x -> x.url().encodedPath()).orElse(null);
      log.error("IO error while connecting to the service: {}", url, ioException);
      throw ioException;
    }
  }

  // Handles for general response (not using ResponseDTO)
  // todo: Refactor API to send standard Error Response
  private static <T> T handleGeneralResponse(Response<T> response, String defaultErrorMessage) {
    if (response.isSuccessful()) {
      return response.body();
    }

    log.error("Error response received: {}", response);
    String errorMessage = "";
    try {
      GenericErrorMessage restResponse =
          JsonUtils.asObject(response.errorBody().string(), new TypeReference<GenericErrorMessage>() {});
      errorMessage = restResponse.getErrorMessage();
      throw new InvalidRequestException(StringUtils.isEmpty(errorMessage) ? defaultErrorMessage : errorMessage);
    } catch (InvalidRequestException e) {
      throw e;
    } catch (Exception e) {
      log.error("Error while converting rest response to GenericErrorMessage", e);
      throw new InvalidRequestException(StringUtils.isEmpty(errorMessage) ? defaultErrorMessage : errorMessage);
    } finally {
      if (!response.isSuccessful() && response.errorBody() != null) {
        response.errorBody().close();
      }
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
    } catch (InvalidRequestException e) {
      throw e;
    } catch (Exception e) {
      log.error("Error while converting rest response to ErrorDTO", e);
      throw new InvalidRequestException(StringUtils.isEmpty(errorMessage) ? defaultErrorMessage : errorMessage);
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

  // Returns retry policy for general response (not using ResponseDTO)
  private <T> RetryPolicy<Response<T>> getRetryPolicyForResponse(String failureMessage) {
    return new RetryPolicy<Response<T>>()
        .withBackoff(1, 10, ChronoUnit.SECONDS)
        .handle(IOException.class)
        .handleResultIf(result -> !result.isSuccessful() && isRetryableHttpCode(result.code()))
        .withMaxAttempts(MAX_ATTEMPTS)
        .onFailedAttempt(event -> handleFailureForResponse(event, failureMessage));
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

  private static <T> void handleFailureForResponse(ExecutionAttemptedEvent<Response<T>> event, String failureMessage) {
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
}
