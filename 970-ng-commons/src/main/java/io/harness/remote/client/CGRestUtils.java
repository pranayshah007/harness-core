/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.remote.client;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static java.lang.String.format;

import io.harness.annotations.dev.OwnedBy;
import io.harness.eraro.ErrorCode;
import io.harness.eraro.ResponseMessage;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.UnexpectedException;
import io.harness.rest.RestResponse;
import io.harness.serializer.JsonUtils;

import com.fasterxml.jackson.core.type.TypeReference;
import java.io.IOException;
import java.time.temporal.ChronoUnit;
import java.util.List;
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

@OwnedBy(PL)
@UtilityClass
@Slf4j
public class CGRestUtils {
  private static final int MAX_ATTEMPTS = 3;

  public static final String DEFAULT_ERROR_MESSAGE = "Error occurred while performing this operation.";

  public static <T> T getResponse(Call<RestResponse<T>> request) {
    RetryPolicy<Response<RestResponse<T>>> retryPolicy = getRetryPolicy("Request failed");
    try {
      Response<RestResponse<T>> response = Failsafe.with(retryPolicy).get(() -> executeRequest(request));
      return handleResponse(response, DEFAULT_ERROR_MESSAGE);
    } catch (FailsafeException ex) {
      throw new UnexpectedException(DEFAULT_ERROR_MESSAGE, ex.getCause());
    }
  }

  public static <T> T getResponse(Call<RestResponse<T>> request, String defaultErrorMessage) {
    RetryPolicy<Response<RestResponse<T>>> retryPolicy = getRetryPolicy(format(defaultErrorMessage));
    try {
      Response<RestResponse<T>> response = Failsafe.with(retryPolicy).get(() -> executeRequest(request));
      return handleResponse(response, defaultErrorMessage);
    } catch (FailsafeException ex) {
      throw new UnexpectedException(defaultErrorMessage, ex.getCause());
    }
  }

  private static <T> Response<RestResponse<T>> executeRequest(Call<RestResponse<T>> request) throws IOException {
    try {
      Call<RestResponse<T>> cloneRequest = request.clone();
      return cloneRequest == null ? request.execute() : cloneRequest.execute();
    } catch (IOException ioException) {
      String url = Optional.ofNullable(request.request()).map(x -> x.url().encodedPath()).orElse(null);
      log.error("IO error while connecting to the service: {}", url, ioException);
      throw ioException;
    }
  }

  private static <T> T handleResponse(Response<RestResponse<T>> response, String defaultErrorMessage) {
    if (response.isSuccessful()) {
      return response.body().getResource();
    } else {
      String errorMessage = "";
      try {
        RestResponse<T> restResponse =
            JsonUtils.asObject(response.errorBody().string(), new TypeReference<RestResponse<T>>() {});
        if (restResponse != null && isNotEmpty(restResponse.getResponseMessages())) {
          List<ResponseMessage> responseMessageList = restResponse.getResponseMessages();
          errorMessage = responseMessageList.get(0).getMessage();
          if (!StringUtils.isEmpty(errorMessage) && responseMessageList.get(0).getCode() == ErrorCode.INVALID_REQUEST) {
            errorMessage = errorMessage.substring(17);
          }
        }
      } catch (Exception e) {
        log.debug("Error while converting error received from upstream systems", e);
      }
      throw new InvalidRequestException(StringUtils.isEmpty(errorMessage) ? defaultErrorMessage : errorMessage);
    }
  }

  private <T> RetryPolicy<Response<RestResponse<T>>> getRetryPolicy(String failureMessage) {
    return new RetryPolicy<Response<RestResponse<T>>>()
        .withBackoff(1, 10, ChronoUnit.SECONDS)
        .handle(IOException.class)
        .handleResultIf(result -> !result.isSuccessful() && isRetryableHttpCode(result.code()))
        .withMaxAttempts(MAX_ATTEMPTS)
        .onFailedAttempt(event -> handleFailure(event, failureMessage));
  }

  private static <T> void handleFailure(
      ExecutionAttemptedEvent<Response<RestResponse<T>>> event, String failureMessage) {
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
