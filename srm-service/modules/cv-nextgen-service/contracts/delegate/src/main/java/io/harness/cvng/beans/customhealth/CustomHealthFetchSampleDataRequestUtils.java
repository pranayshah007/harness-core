/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cvng.beans.customhealth;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.delegate.beans.connector.customhealthconnector.CustomHealthMethod;
import io.harness.serializer.JsonUtils;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
public class CustomHealthFetchSampleDataRequestUtils {
  public static Map<String, Object> getDSLEnvironmentVariables(
      String urlPath, CustomHealthMethod method, String body, TimestampInfo startTime, TimestampInfo endTime) {
    Map<String, Object> commonEnvVariables = new HashMap<>();
    commonEnvVariables.put("method", method);
    commonEnvVariables.put("urlPath", urlPath);
    commonEnvVariables.put("startTimePlaceholder", startTime.getPlaceholder());
    commonEnvVariables.put("body", null);

    Instant instant = Instant.now();

    if (startTime.getTimestampFormat().equals(TimestampInfo.TimestampFormat.CUSTOM)) {
      commonEnvVariables.put("startTimeValue", startTime.getCustomTimestampFormat());
    } else if (startTime.getTimestampFormat().equals(TimestampInfo.TimestampFormat.MILLISECONDS)) {
      commonEnvVariables.put("startTimeValue", String.valueOf(instant.minusSeconds(30 * 60).toEpochMilli()));
    } else if (startTime.getTimestampFormat().equals(TimestampInfo.TimestampFormat.SECONDS)) {
      commonEnvVariables.put("startTimeValue", String.valueOf(instant.minusSeconds(30 * 60).getEpochSecond()));
    }

    commonEnvVariables.put("endTimePlaceholder", endTime.getPlaceholder());
    if (endTime.getTimestampFormat().equals(TimestampInfo.TimestampFormat.CUSTOM)) {
      commonEnvVariables.put("endTimeValue", endTime.getCustomTimestampFormat());
    } else if (endTime.getTimestampFormat().equals(TimestampInfo.TimestampFormat.MILLISECONDS)) {
      commonEnvVariables.put("endTimeValue", String.valueOf(instant.toEpochMilli()));
    } else if (endTime.getTimestampFormat().equals(TimestampInfo.TimestampFormat.SECONDS)) {
      commonEnvVariables.put("endTimeValue", String.valueOf(instant.getEpochSecond()));
    }

    if (isNotEmpty(body)) {
      body = body.replaceAll(startTime.getPlaceholder(), (String) commonEnvVariables.get("startTimeValue"));
      body = body.replaceAll(endTime.getPlaceholder(), (String) commonEnvVariables.get("endTimeValue"));
      commonEnvVariables.put("body", JsonUtils.asMap(body));
    }

    return commonEnvVariables;
  }
}
