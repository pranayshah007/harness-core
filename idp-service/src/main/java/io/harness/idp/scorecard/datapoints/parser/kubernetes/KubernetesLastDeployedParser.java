/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.datapoints.parser.kubernetes;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.UnexpectedException;
import io.harness.idp.scorecard.datapoints.parser.DataPointParser;

import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.IDP)
@Slf4j
public class KubernetesLastDeployedParser extends KubernetesExpressionParser implements DataPointParser {
  @Override
  Object parseValue(Object value) {
    if (value == null) {
      return null;
    }
    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssX");
    List<Map<String, String>> conditions = (List<Map<String, String>>) value;
    long maxDaysSince = Long.MIN_VALUE;
    for (Map<String, String> condition : conditions) {
      if (condition.get("type").equals("Available")) {
        try {
          String lastUpdateTime = condition.get("lastUpdateTime");
          Instant instant = Instant.from(formatter.parse(lastUpdateTime));
          Instant currentTime = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.DAYS);
          Duration duration = Duration.between(instant, currentTime);
          long daysSince = duration.toDays();
          maxDaysSince = Math.max(maxDaysSince, daysSince); // break here
        } catch (Exception e) {
          throw new UnexpectedException(
              String.format("Could not convert timestamp %s to epoch", condition.get("lastUpdateTime")));
        }
      }
    }
    return maxDaysSince == Long.MIN_VALUE ? null : maxDaysSince;
  }

  @Override
  boolean compare(Object value, Object compareValue) {
    return compareValue == null || (long) compareValue <= (long) value;
  }
}
