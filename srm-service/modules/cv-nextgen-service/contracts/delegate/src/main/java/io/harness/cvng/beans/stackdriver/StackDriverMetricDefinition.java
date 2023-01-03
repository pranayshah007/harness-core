/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cvng.beans.stackdriver;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;
import lombok.experimental.FieldNameConstants;
import org.json.JSONArray;
import org.json.JSONObject;

@Data
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@FieldNameConstants(innerTypeName = "StackDriverMetricDefinitionKeys")
public class StackDriverMetricDefinition {
  public static final String dataSetsKey = "dataSets";
  public static final String timeSeriesFilterKey = "timeSeriesFilter";
  public static final String timeSeriesQueryKey = "timeSeriesQuery";
  public static final String aggregationKey = "aggregations";
  public static final String perSeriesAlignerKey = "perSeriesAligner";
  public static final String crossSeriesReducerKey = "crossSeriesReducer";
  public static final String groupByKey = "groupByFields";

  private static final String metricTypeField = "metric.type";

  String metricName;
  String metricIdentifier;
  String filter;
  Aggregation aggregation;
  String serviceInstanceField;

  @Data
  @Builder
  @FieldNameConstants(innerTypeName = "AggregationKeys")
  public static class Aggregation {
    @Builder.Default String alignmentPeriod = "60s";
    String perSeriesAligner;
    String crossSeriesReducer;

    @Builder.Default List<String> groupByFields = new ArrayList<>();
  }

  public static StackDriverMetricDefinition extractFromJson(String jsonDefinition) {
    JSONObject metric = new JSONObject(jsonDefinition);
    Object datasetsObject = getIgnoreCase(metric, dataSetsKey);
    if (datasetsObject == null) {
      throw new StackdriverSetupException("Stackdriver JSON does not contain dataSets field");
    }
    JSONArray dataSets = (JSONArray) datasetsObject;
    if (dataSets.length() > 1) {
      throw new StackdriverSetupException("Stackdriver JSON contains more than one dataset");
    }
    JSONObject datasetItem = (JSONObject) dataSets.get(0);
    JSONObject timeSeriesFilterObj = null;
    if (datasetItem.keySet().contains(timeSeriesFilterKey)) {
      timeSeriesFilterObj = datasetItem;
    } else if (datasetItem.keySet().contains(timeSeriesQueryKey)) {
      timeSeriesFilterObj = datasetItem.getJSONObject(timeSeriesQueryKey);
    } else {
      throw new StackdriverSetupException(
          "Unknown JSON format. Both timeSeriesQuery and timeSeriesFilter are not present under datasets");
    }
    return getDefinitionFromTimeSeriesObject(timeSeriesFilterObj);
  }

  public static Object getIgnoreCase(JSONObject jobj, String key) {
    Iterator<String> iter = jobj.keySet().iterator();
    while (iter.hasNext()) {
      String key1 = iter.next();
      if (key1.equalsIgnoreCase(key)) {
        return jobj.get(key1);
      }
    }
    return null;
  }

  private static String extractMetric(String filter) {
    String[] filters = filter.split(" ");
    for (String innerFilter : filters) {
      if (innerFilter.contains(metricTypeField)) {
        return innerFilter.substring(innerFilter.indexOf('=') + 1).replaceAll("\"", "");
      }
    }
    return null;
  }

  private static StackDriverMetricDefinition getDefinitionFromTimeSeriesObject(JSONObject timeSeriesFilterObj) {
    JSONObject timesersFilter = (JSONObject) timeSeriesFilterObj.get(timeSeriesFilterKey);
    String filter = timesersFilter.getString("filter");
    JSONObject aggregationJson =
        timesersFilter.keySet().contains("aggregation") ? timesersFilter.getJSONObject("aggregation") : null;
    if (aggregationJson == null) {
      JSONArray aggregationArray =
          ((JSONObject) timeSeriesFilterObj.get(timeSeriesFilterKey)).getJSONArray(aggregationKey);
      if (aggregationArray.length() < 1) {
        throw new StackdriverSetupException("Stackdriver JSON contains empty aggregations");
      }
      aggregationJson = (JSONObject) aggregationArray.get(0);
    }
    Aggregation aggregationObj = Aggregation.builder().build();
    aggregationObj.setPerSeriesAligner(aggregationJson.getString(perSeriesAlignerKey));
    aggregationObj.setCrossSeriesReducer(
        aggregationJson.has(crossSeriesReducerKey) ? aggregationJson.getString(crossSeriesReducerKey) : null);
    aggregationObj.setGroupByFields(new ArrayList<>());
    if (aggregationJson.has(groupByKey)) {
      JSONArray groupArray = aggregationJson.getJSONArray(groupByKey);
      int counter = 0;
      while (counter < groupArray.length()) {
        aggregationObj.getGroupByFields().add(groupArray.getString(counter++));
      }
    }

    return StackDriverMetricDefinition.builder()
        .filter(filter)
        .metricName(extractMetric(filter))
        .aggregation(aggregationObj)
        .build();
  }
}
