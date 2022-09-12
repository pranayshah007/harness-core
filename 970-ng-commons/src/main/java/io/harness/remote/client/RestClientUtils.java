/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.remote.client;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.annotations.dev.OwnedBy;
import io.harness.eraro.ErrorCode;
import io.harness.eraro.ResponseMessage;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.UnexpectedException;
import io.harness.rest.RestResponse;
import io.harness.serializer.JsonUtils;

import com.fasterxml.jackson.core.type.TypeReference;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import retrofit2.Call;
import retrofit2.Response;

@OwnedBy(PL)
@UtilityClass
@Slf4j
public class RestClientUtils {
  public static final String DEFAULT_CONNECTION_ERROR_MESSAGE =
      "Unable to connect to upstream systems, please try again.";
  public static final String DEFAULT_ERROR_MESSAGE = "Error occurred while performing this operation.";

  public static <T> T getResponse(Call<RestResponse<T>> request) {
    return getResponse(request, DEFAULT_ERROR_MESSAGE, DEFAULT_CONNECTION_ERROR_MESSAGE);
  }

  private static <T> T getResponse(
      Call<RestResponse<T>> request, String defaultErrorMessage, String connectionErrorMessage) {
    try {
      Response<RestResponse<T>> response = request.execute();
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
            if (!StringUtils.isEmpty(errorMessage)
                && responseMessageList.get(0).getCode() == ErrorCode.INVALID_REQUEST) {
              errorMessage = errorMessage.substring(17);
            }
          }
        } catch (Exception e) {
          log.debug("Error while converting error received from upstream systems", e);
        }
        throw new InvalidRequestException(StringUtils.isEmpty(errorMessage) ? defaultErrorMessage : errorMessage);
      }
    } catch (IOException ex) {
      String url = Optional.ofNullable(request.request()).map(x -> x.url().encodedPath()).orElse(null);
      log.error("IO error while connecting to the service: {}", url, ex);
      throw new UnexpectedException(connectionErrorMessage);
    }
  }
}
