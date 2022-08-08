/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package software.wings.verification.stackdriver;

import io.harness.eraro.ErrorCode;
import io.harness.exception.VerificationOperationException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.json.JSONArray;
import org.json.JSONObject;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StackDriverMetricDefinition {
  private String metricType;
  private String metricName;
  private String txnName;
  private String filterJson;
  private String filter;
  private Aggregation aggregation;

  private static final String dataSetsKey = "dataSets";
  private static final String timeSeriesFilterKey = "timeSeriesFilter";
  private static final String aggregationKey = "aggregations";
  private static final String perSeriesAlignerKey = "perSeriesAligner";
  private static final String crossSeriesReducerKey = "crossSeriesReducer";
  private static final String groupByKey = "groupByFields";

  @Getter
  @Setter
  @NoArgsConstructor
  public static class Aggregation {
    String perSeriesAligner;
    String crossSeriesReducer;
    List<String> groupByFields;
  }

  public void extractJson() {
    try {
      setFilter();
      setAggregation();
    } catch (Exception ex) {
      throw new VerificationOperationException(
          ErrorCode.APM_CONFIGURATION_ERROR, "Exception while parsing JSON Query", ex);
    }
  }

  private void setAggregation() {
    JSONObject metric = new JSONObject(filterJson);
    if (metric.get(dataSetsKey) == null) {
      throw new VerificationOperationException(
          ErrorCode.APM_CONFIGURATION_ERROR, "Stackdriver JSON does not contain dataSets field");
    }
    JSONArray dataSets = (JSONArray) metric.get(dataSetsKey);
    if (dataSets.length() > 1) {
      throw new VerificationOperationException(
          ErrorCode.APM_CONFIGURATION_ERROR, "Stackdriver JSON contains more than one dataset");
    }
    JSONObject timeSeriesFilterObj = (JSONObject) dataSets.get(0);
    JSONArray aggregationArray =
        ((JSONObject) timeSeriesFilterObj.get(timeSeriesFilterKey)).getJSONArray(aggregationKey);
    if (aggregationArray.length() < 1) {
      throw new VerificationOperationException(
          ErrorCode.APM_CONFIGURATION_ERROR, "Stackdriver JSON contains empty aggregations");
    }
    JSONObject aggregationJson = (JSONObject) aggregationArray.get(0);
    Aggregation aggregationObj = new Aggregation();
    aggregationObj.setPerSeriesAligner(aggregationJson.getString(perSeriesAlignerKey));
    aggregationObj.setCrossSeriesReducer(
        aggregationJson.has(crossSeriesReducerKey) ? aggregationJson.getString(crossSeriesReducerKey) : null);
    aggregationObj.setGroupByFields(new ArrayList<>());
    JSONArray groupArray = aggregationJson.getJSONArray(groupByKey);
    int counter = 0;
    while (counter < groupArray.length()) {
      aggregationObj.getGroupByFields().add(groupArray.getString(counter++));
    }
    this.aggregation = aggregationObj;
  }

  private void setFilter() {
    JSONObject metric = new JSONObject(filterJson);
    if (metric.get(dataSetsKey) == null) {
      throw new VerificationOperationException(
          ErrorCode.APM_CONFIGURATION_ERROR, "Stackdriver JSON does not contain dataSets field");
    }
    JSONArray dataSets = (JSONArray) metric.get(dataSetsKey);
    if (dataSets.length() > 1) {
      throw new VerificationOperationException(
          ErrorCode.APM_CONFIGURATION_ERROR, "Stackdriver JSON contains more than one dataset");
    }
    JSONObject timeSeriesFilterObj = (JSONObject) dataSets.get(0);
    this.filter = ((JSONObject) timeSeriesFilterObj.get(timeSeriesFilterKey)).getString("filter");
  }

  public static String getUpdatedFilterJson(String filterJson) {
    JSONObject metric = new JSONObject(filterJson);
    if (metric.get(dataSetsKey) == null) {
      throw new VerificationOperationException(
          ErrorCode.APM_CONFIGURATION_ERROR, "Stackdriver JSON does not contain dataSets field");
    }
    JSONArray dataSets = (JSONArray) metric.get(dataSetsKey);
    if (dataSets.length() > 1) {
      throw new VerificationOperationException(
          ErrorCode.APM_CONFIGURATION_ERROR, "Stackdriver JSON contains more than one dataset");
    }
    JSONObject timeSeriesFilterObj = (JSONObject) dataSets.get(0);
    JSONObject timeSeriesFilter = (JSONObject) timeSeriesFilterObj.get(timeSeriesFilterKey);

    String perSeriesAligner = timeSeriesFilter.getString(perSeriesAlignerKey);
    String crossSeriesReducer =
        timeSeriesFilter.has(crossSeriesReducerKey) ? timeSeriesFilter.getString(crossSeriesReducerKey) : null;
    List<String> groupByStrings = new ArrayList<>();
    JSONArray groupArray = timeSeriesFilter.getJSONArray(groupByKey);
    int counter = 0;
    while (counter < groupArray.length()) {
      groupByStrings.add(groupArray.getString(counter++));
    }

    Aggregation aggregation = new Aggregation();
    aggregation.setGroupByFields(groupByStrings);
    aggregation.setPerSeriesAligner(perSeriesAligner);
    aggregation.setCrossSeriesReducer(crossSeriesReducer);

    timeSeriesFilter.put(aggregationKey, Collections.singletonList(aggregation));
    return metric.toString();
  }

  public boolean checkIfOldFilter() {
    try {
      extractJson();
      return false;
    } catch (VerificationOperationException e) {
      return true;
    }
  }
}
