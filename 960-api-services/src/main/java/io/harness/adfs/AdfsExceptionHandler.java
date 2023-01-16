/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.adfs;

import static io.harness.adfs.AdfsConstants.INVALID_ADFS_CREDENTIALS;
import static io.harness.adfs.AdfsConstants.NOT_FOUND;
import static io.harness.eraro.ErrorCode.ADFS_ERROR;
import static io.harness.exception.WingsException.USER;

import static java.util.Objects.isNull;

import io.harness.exception.AdfsAuthException;
import io.harness.serializer.JsonUtils;

import com.fasterxml.jackson.core.type.TypeReference;
import java.io.IOException;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import retrofit2.Response;

@UtilityClass
@Slf4j
public class AdfsExceptionHandler {
  public static void handleResponse(Response<AdfsAccessTokenResponse> response, String message) throws IOException {
    if (response.isSuccessful()) {
      return;
    }
    if (response.code() == 401) {
      throw new AdfsAuthException(INVALID_ADFS_CREDENTIALS, ADFS_ERROR, USER);
    }
    if (response.code() == 404) {
      throw new AdfsAuthException(NOT_FOUND, ADFS_ERROR, USER);
    }
    if (isNull(response.errorBody())) {
      throw new AdfsAuthException(message + " : " + response.message(), ADFS_ERROR, USER);
    }
    throw new AdfsAuthException(getFormattedError(response.errorBody().string()), ADFS_ERROR, USER);
  }

  private static String getFormattedError(String errorBody) {
    try {
      // processing the error
      AdfsAccessTokenErrorResponse adfsAccessTokenErrorResponse =
          JsonUtils.asObject(errorBody, new TypeReference<>() {});
      String formattedError;
      formattedError = adfsAccessTokenErrorResponse.getErrorCode();
      if (!StringUtils.isBlank(adfsAccessTokenErrorResponse.getErrorDetails())) {
        formattedError = String.format(
            "[%s] : %s", adfsAccessTokenErrorResponse.getErrorCode(), adfsAccessTokenErrorResponse.getErrorDetails());
      }
      return formattedError;
    } catch (Exception ex) {
      log.warn("Error occurred while trying to format Adfs error body", ex);
      return errorBody;
    }
  }
}