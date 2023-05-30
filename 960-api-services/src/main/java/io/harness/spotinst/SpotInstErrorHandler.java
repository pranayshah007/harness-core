/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */
package io.harness.spotinst;

import static java.lang.String.format;

import io.harness.exception.WingsException;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.EnumSet;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

@UtilityClass
@Slf4j
public class SpotInstErrorHandler {
  private static final int STATUS_CODE_ERR = 400;
  private static final ObjectMapper mapper = new ObjectMapper();

  private static final String INSTANCE_HEALTHINESS_ERR = "An error occurred when calling InstanceHealthiness API %s";

  private JsonNode parseToJson(String json) {
    try {
      return mapper.readTree(json);
    } catch (JacksonException e) {
      return null;
    }
  }

  public WingsException generateException(String error) {
    JsonNode jsonNode = parseToJson(error);
    if (jsonNode == null) {
      return getDefaultException(error);
    }

    if (jsonNode.isObject()) {
      if (isInstanceHealthinessError(jsonNode)) {
        String url = jsonNode.at("/request/url").asText();
        return new WingsException(
            format(INSTANCE_HEALTHINESS_ERR, url), EnumSet.of(WingsException.ReportTarget.UNIVERSAL));
      }

      // put here other Spot related API errors
    }

    return getDefaultException(error);
  }

  private WingsException getDefaultException(String error) {
    return new WingsException(error, EnumSet.of(WingsException.ReportTarget.UNIVERSAL));
  }

  private boolean isInstanceHealthinessError(JsonNode jsonNode) {
    String url = jsonNode.at("/request/url").asText("");
    int statusCode = jsonNode.at("/response/status/code").asInt();
    return statusCode == STATUS_CODE_ERR && url.contains("instanceHealthiness");
  }
}
