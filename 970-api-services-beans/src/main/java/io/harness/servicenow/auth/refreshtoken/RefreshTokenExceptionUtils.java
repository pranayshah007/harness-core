/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.servicenow.auth.refreshtoken;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.eraro.ErrorCode.SERVICENOW_REFRESH_TOKEN_ERROR;
import static io.harness.exception.WingsException.USER;

import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.ServiceNowOIDCException;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(CDC)
@UtilityClass
@Slf4j
public class RefreshTokenExceptionUtils {
  public static final String REFRESH_TOKEN_ERROR_PREFIX = "Refresh Token grant error";
  public static void throwRefreshTokenException(String message) {
    throw new ServiceNowOIDCException(
        String.format("%s: %s", REFRESH_TOKEN_ERROR_PREFIX, message), SERVICENOW_REFRESH_TOKEN_ERROR, USER);
  }

  public static ServiceNowOIDCException prepareRefreshTokenException(String message, Throwable cause) {
    return new ServiceNowOIDCException(
        String.format("%s: %s", REFRESH_TOKEN_ERROR_PREFIX, message), SERVICENOW_REFRESH_TOKEN_ERROR, USER, cause);
  }

  /**
   * Logs and throws ServiceNowOIDCException exception related to error in cache operations
   *
   * Expects exception to be sanitized.
   *
   */
  public static ServiceNowOIDCException handleRefreshTokenCacheException(
      String cacheAction, String tokenUrl, Exception ex) {
    log.error("Error occurred while {} access token entry from refreshTokenCache for tokenUrl : {}", cacheAction,
        tokenUrl, ex);
    throw prepareRefreshTokenException(
        String.format("Error occurred while %s access token entry from cache for tokenUrl : %s", cacheAction, tokenUrl),
        ex);
  }
}
