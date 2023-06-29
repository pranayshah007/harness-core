/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.servicenow.auth.refreshtoken;

import static io.harness.servicenow.auth.refreshtoken.RefreshTokenConstants.INVALID_CREDENTIALS;
import static io.harness.servicenow.auth.refreshtoken.RefreshTokenConstants.NOT_FOUND;
import static io.harness.servicenow.auth.refreshtoken.RefreshTokenExceptionUtils.throwRefreshTokenException;

import static java.util.Objects.isNull;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import retrofit2.Response;

@UtilityClass
@Slf4j
public class RefreshTokenResponseHandler {
  public static void handleResponse(Response<AccessTokenResponse> response, String message) throws IOException {
    if (response.isSuccessful()) {
      return;
    }
    if (response.code() == 401) {
      throwRefreshTokenException(INVALID_CREDENTIALS);
    }
    if (response.code() == 404) {
      throwRefreshTokenException(NOT_FOUND);
    }
    if (isNull(response.errorBody())) {
      throwRefreshTokenException(message + " : " + response.message());
    }
    throwRefreshTokenException(getFormattedError(response.errorBody().string()));
  }

  private static String getFormattedError(String errorBody) {
    try {
      // processing the error
      ObjectMapper objectMapper = new ObjectMapper();
      JsonNode jsonNode = objectMapper.readTree(errorBody);
      AccessTokenErrorResponse accessTokenErrorResponse = new AccessTokenErrorResponse(jsonNode);
      return accessTokenErrorResponse.getFormattedError();
    } catch (Exception ex) {
      log.warn("Error occurred while trying to format Access token error body", ex);
      return errorBody;
    }
  }
}