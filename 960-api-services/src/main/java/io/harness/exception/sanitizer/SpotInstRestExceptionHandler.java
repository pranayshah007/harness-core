/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.exception.sanitizer;

import static java.lang.String.format;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.WingsException;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.EnumSet;
import lombok.experimental.UtilityClass;

@UtilityClass
@OwnedBy(HarnessTeam.CDC)
public class SpotInstRestExceptionHandler {
  private static final int STATUS_CODE_ERR = 400;
  private static final ObjectMapper mapper = new ObjectMapper();
  private static final String INSTANCE_HEALTHINESS_ERR = "An error occurred when calling InstanceHealthiness API %s";
  private static final String MESSAGE = "/message";
  private static final String REQUEST_URL = "/request/url";
  private static final String RESPONSE_ERRORS = "/response/errors";

  public WingsException handleException(Exception exception) {
    String error = exception.getMessage();
    JsonNode jsonNode = parseToJson(error);
    if (jsonNode == null) {
      return getDefaultException(error);
    }
    if (jsonNode.isObject()) {
      if (isInstanceHealthinessError(jsonNode)) {
        String url = jsonNode.at(REQUEST_URL).asText();
        return new WingsException(
            format(INSTANCE_HEALTHINESS_ERR, url), EnumSet.of(WingsException.ReportTarget.UNIVERSAL));
      }

      // put here other Spot related API errors

      StringBuilder errorMessage = new StringBuilder();
      JsonNode responseErrorMessages = jsonNode.at(RESPONSE_ERRORS);
      if (responseErrorMessages != null && responseErrorMessages.isArray()) {
        for (JsonNode jsonNodeError : responseErrorMessages) {
          JsonNode messageNode = jsonNodeError.at(MESSAGE);
          if (messageNode != null) {
            errorMessage.append(messageNode.asText()).append('\n');
          }
        }
      }
      if (EmptyPredicate.isNotEmpty(errorMessage.toString())) {
        return getDefaultException(errorMessage.toString());
      }
    }

    return getDefaultException(error);
  }

  private JsonNode parseToJson(String json) {
    try {
      return mapper.readTree(json);
    } catch (JacksonException e) {
      return null;
    }
  }

  private WingsException getDefaultException(String error) {
    return new WingsException(error, WingsException.USER);
  }

  private boolean isInstanceHealthinessError(JsonNode jsonNode) {
    String url = jsonNode.at("/request/url").asText("");
    int statusCode = jsonNode.at("/response/status/code").asInt();
    return statusCode == STATUS_CODE_ERR && url.contains("instanceHealthiness");
  }
}
