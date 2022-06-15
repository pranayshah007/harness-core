/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cvng.beans.stackdriver;

import static io.harness.annotations.dev.HarnessTeam.CV;
import static io.harness.cvng.utils.StackdriverUtils.Scope.METRIC_SCOPE;
import static io.harness.cvng.utils.StackdriverUtils.checkForNullAndReturnValue;

import io.harness.annotations.dev.OwnedBy;
import io.harness.cvng.beans.stackdriver.StackDriverMetricDefinition.Aggregation.AggregationKeys;
import io.harness.cvng.beans.stackdriver.StackDriverMetricDefinition.StackDriverMetricDefinitionKeys;
import io.harness.cvng.utils.StackdriverUtils;

import com.fasterxml.jackson.annotation.JsonTypeName;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.SuperBuilder;

@JsonTypeName("STACKDRIVER_SAMPLE_DATA")
@SuperBuilder
@Data
@NoArgsConstructor
@FieldNameConstants(innerTypeName = "StackdriverSampleDataRequestKeys")
@OwnedBy(CV)
public class StackdriverSampleDataRequest extends StackdriverRequest {
  private static final String timestampFormat = "yyyy-MM-dd'T'HH:mm:ss'Z'";
  private static final String timestampFormatKey = "timestampFormat";
  Instant startTime;
  Instant endTime;
  StackDriverMetricDefinition metricDefinition;
  public static final String DSL =
      StackdriverDashboardRequest.readDSL("stackdriver-sample-data.datacollection", StackdriverDashboardRequest.class);
  @Override
  public String getDSL() {
    return DSL;
  }

  @Override
  public String getBaseUrl() {
    return "https://monitoring.googleapis.com/v3/projects/";
  }

  @Override
  public Map<String, Object> fetchDslEnvVariables() {
    StackDriverMetricDefinition.Aggregation aggregation = metricDefinition.getAggregation();
    Map<String, Object> dslEnvVariables = StackdriverUtils.getCommonEnvVariables(getConnectorConfigDTO(), METRIC_SCOPE);
    dslEnvVariables.put(
        AggregationKeys.alignmentPeriod, checkForNullAndReturnValue(aggregation.getAlignmentPeriod(), "60s"));
    dslEnvVariables.put(
        AggregationKeys.crossSeriesReducer, checkForNullAndReturnValue(aggregation.getCrossSeriesReducer(), ""));
    dslEnvVariables.put(
        AggregationKeys.perSeriesAligner, checkForNullAndReturnValue(aggregation.getPerSeriesAligner(), ""));

    dslEnvVariables.put(
        AggregationKeys.groupByFields, checkForNullAndReturnValue(aggregation.getGroupByFields(), new ArrayList<>()));
    List<String> groupByResponse = new ArrayList<>();
    for (String field : aggregation.getGroupByFields()) {
      groupByResponse.add(field.replace("\"", "").replace("label", "labels"));
    }
    dslEnvVariables.put("groupByResponses", groupByResponse);
    dslEnvVariables.put(StackDriverMetricDefinitionKeys.filter, metricDefinition.getFilter());

    dslEnvVariables.put(StackdriverSampleDataRequestKeys.startTime, startTime.toString());
    dslEnvVariables.put(StackdriverSampleDataRequestKeys.endTime, endTime.toString());
    dslEnvVariables.put(timestampFormatKey, timestampFormat);

    return dslEnvVariables;
  }
}
