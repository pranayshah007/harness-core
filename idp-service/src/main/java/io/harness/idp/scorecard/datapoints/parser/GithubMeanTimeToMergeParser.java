/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.datapoints.parser;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.idp.scorecard.datapoints.constants.DataPoints.INVALID_BRANCH_NAME_ERROR;
import static io.harness.idp.scorecard.datapoints.constants.DataPoints.NO_PULL_REQUESTS_FOUND;

import static java.lang.String.format;

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
  private static final String DATE_FORMAT = "yyyy-MM-dd'T'HH:mm:ss'Z'";
  @Override
  public Object parseDataPoint(Map<String, Object> data, DataPointEntity dataPoint, Set<String> inputValues) {
    Map<String, Object> dataPointData = new HashMap<>();
    for (String inputValue : inputValues) {
      if (!data.containsKey(inputValue)) {
        dataPointData.putAll(constructDataPointInfo(inputValue, Long.MAX_VALUE, INVALID_BRANCH_NAME_ERROR));
        continue;
      }

      Map<String, Object> inputValueData = (Map<String, Object>) data.get(inputValue);
      List<Map<String, Object>> edges =
          (List<Map<String, Object>>) CommonUtils.findObjectByName(inputValueData, "edges");
      if (isEmpty(edges)) {
        dataPointData.putAll(constructDataPointInfo(
            inputValue, Long.MAX_VALUE, format(NO_PULL_REQUESTS_FOUND, inputValue.replace("\"", ""))));
        continue;
      }
      int numberOfPullRequests = edges.size();
      long totalTimeToMerge = 0;
      for (Map<String, Object> edge : edges) {
        Map<String, Object> node = (Map<String, Object>) edge.get("node");
        long createdAtMillis = DateUtils.parseTimestamp((String) node.get("createdAt"), DATE_FORMAT);
        long mergedAtMillis = DateUtils.parseTimestamp((String) node.get("mergedAt"), DATE_FORMAT);
        long timeToMergeMillis = mergedAtMillis - createdAtMillis;
        totalTimeToMerge += timeToMergeMillis;
      }

      double meanTimeToMergeMillis = (double) totalTimeToMerge / numberOfPullRequests;
      long value = (long) (meanTimeToMergeMillis / (60 * 60 * 1000));
      dataPointData.putAll(constructDataPointInfo(inputValue, value, null));
    }
    return dataPointData;
  }
}
