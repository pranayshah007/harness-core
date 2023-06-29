/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.servicenow.auth.refreshtoken;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.servicenow.auth.refreshtoken.RefreshTokenExceptionUtils.throwRefreshTokenException;

import io.harness.annotations.dev.OwnedBy;
import io.harness.jackson.JsonNodeUtils;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.AccessLevel;
import lombok.Data;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@OwnedBy(CDC)
@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonIgnoreProperties(ignoreUnknown = true)
@Slf4j
public class AccessTokenErrorResponse {
  String errorCode;
  String errorDetails;

  /**
   * We expect the error response to have atleast error or errorCode field
   *
   * In most case error and error_description field are obtained
   * but in some cases, we get errorCode and errorSummary like invalid client_id in okta.
   **/
  public AccessTokenErrorResponse(JsonNode node) {
    String error = JsonNodeUtils.getString(node, "error");
    String errorCode = JsonNodeUtils.getString(node, "errorCode");
    String parsedErrorCode = StringUtils.isBlank(error) ? errorCode : error;

    String errorDescription = JsonNodeUtils.getString(node, "error_description");
    String errorSummary = JsonNodeUtils.getString(node, "errorSummary");
    String parsedErrorDetails = StringUtils.isBlank(errorDescription) ? errorSummary : errorDescription;

    if (!StringUtils.isBlank(parsedErrorCode)) {
      this.errorCode = parsedErrorCode;
      this.errorDetails = parsedErrorDetails;
      return;
    }

    throwRefreshTokenException("error response doesn't have \"error\" or \"errorCode\" field");
  }

  public String getFormattedError() {
    String formattedError = this.getErrorCode();
    if (!StringUtils.isBlank(this.getErrorDetails())) {
      formattedError = String.format("[%s] : %s", this.getErrorCode(), this.getErrorDetails());
    }
    return formattedError;
  }
}