/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.datapoints.parser;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.common.CommonUtils;
import io.harness.idp.scorecard.datapoints.entity.DataPointEntity;

import com.google.gson.internal.LinkedTreeMap;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.IDP)
@Slf4j
public class PagerDutyAvgAcknowledgementTimeForLastTenIncidents implements DataPointParser {
  private static final String INCIDENTS_RESPONSE_KEY = "incidents";
  @Override
  public Object parseDataPoint(Map<String, Object> data, DataPointEntity dataPoint, Set<String> inputValues) {
    log.info(
        "Parser for AvgAcknowledgementTimeForLastTenIncidents is invoked data - {}, data point - {}, input values - {}",
        data, dataPoint, inputValues);

    List<LinkedTreeMap> incidents = new ArrayList<>();

    // we cannot creat error scenario here as even if service id is incorrect we are getting incident
    // with incidents as empty array

    if (CommonUtils.findObjectByName(data, INCIDENTS_RESPONSE_KEY) != null) {
      incidents = (ArrayList) CommonUtils.findObjectByName(data, INCIDENTS_RESPONSE_KEY);
    }

    int noOfIncidentsForCalculation = Math.min(incidents.size(), 10);

    long sumOfAckTime = 0;
    for (int i = 0; i < noOfIncidentsForCalculation; i++) {
      String status = incidents.get(i).get("status").toString();
      log.info("Status of incident - {}", status);

      if (status.equals("acknowledged")) {
        ArrayList<LinkedTreeMap> acknowledgements = (ArrayList) incidents.get(i).get("acknowledgements");
        sumOfAckTime += getDifferenceBetweenTimeInMinutes(
            incidents.get(i).get("created_at").toString(), acknowledgements.get(0).get("at").toString());
      }
    }
    if (noOfIncidentsForCalculation == 0) {
      return constructDataPointInfoWithoutInputValue(0, null);
    }

    return constructDataPointInfoWithoutInputValue(sumOfAckTime / noOfIncidentsForCalculation, null);
  }

  private long getDifferenceBetweenTimeInMinutes(String createdAtTime, String acknowledgementTime) {
    Instant createdAtTimeParsed = Instant.parse(createdAtTime);
    Instant acknowledgementTimeParsed = Instant.parse(acknowledgementTime);

    // Convert Instant objects to ZonedDateTime
    ZonedDateTime createdAtTimeZoned = createdAtTimeParsed.atZone(ZoneId.of("UTC"));
    ZonedDateTime acknowledgementTimeZoned = acknowledgementTimeParsed.atZone(ZoneId.of("UTC"));

    // Calculate the difference in hours
    Duration duration = Duration.between(createdAtTimeZoned, acknowledgementTimeZoned);
    return duration.toMinutes();
  }
}