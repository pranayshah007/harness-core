/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.datapoints.parser;

import static io.harness.idp.common.Constants.DATA_POINT_VALUE_KEY;
import static io.harness.idp.common.Constants.ERROR_MESSAGE_KEY;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.common.CommonUtils;
import io.harness.idp.common.DateUtils;
import io.harness.idp.scorecard.datapoints.entity.DataPointEntity;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@OwnedBy(HarnessTeam.IDP)
public class GithubMeanTimeToMergeParser implements DataPointParser {
  @Override
  public Object parseDataPoint(Map<String, Object> data, DataPointEntity dataPoint, Set<String> inputValues) {
    List<Map<String, Object>> edges = (List<Map<String, Object>>) CommonUtils.findObjectByName(data, "edges");

    int numberOfPullRequests = 0;
    long totalTimeToMerge = 0;
    for (Map<String, Object> edge : edges) {
      Map<String, Object> node = (Map<String, Object>) edge.get("node");
      long createdAtMillis = DateUtils.parseTimestamp((String) node.get("createdAt"));
      long mergedAtMillis = DateUtils.parseTimestamp((String) node.get("mergedAt"));
      long timeToMergeMillis = mergedAtMillis - createdAtMillis;
      totalTimeToMerge += timeToMergeMillis;
      numberOfPullRequests++;
    }

    long value = 0;
    if (numberOfPullRequests != 0) {
      double meanTimeToMergeMillis = (double) totalTimeToMerge / numberOfPullRequests;
      value = (long) (meanTimeToMergeMillis / (60 * 60 * 1000));
    }
    return constructDataPointInfo(inputValues, value);
  }

  private Map<String, Object> constructDataPointInfo(Set<String> inputValues, long value) {
    Map<String, Object> data = new HashMap<>();
    data.put(DATA_POINT_VALUE_KEY, value);
    data.put(ERROR_MESSAGE_KEY, null);
    if (inputValues.isEmpty()) {
      return data;
    } else {
      String inputValue = inputValues.iterator().next();
      return Map.of(inputValue, data);
    }
  }
}
