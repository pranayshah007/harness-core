/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cvng.beans;

import static io.harness.cvng.utils.StackdriverUtils.Scope.METRIC_SCOPE;
import static io.harness.cvng.utils.StackdriverUtils.checkForNullAndReturnValue;

import io.harness.cvng.beans.stackdriver.StackDriverMetricDefinition;
import io.harness.cvng.utils.StackdriverUtils;
import io.harness.delegate.beans.connector.gcpconnector.GcpConnectorDTO;

import com.google.common.base.Preconditions;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class StackdriverDataCollectionInfo extends TimeSeriesDataCollectionInfo<GcpConnectorDTO> {
  List<StackDriverMetricDefinition> metricDefinitions;

  public List<StackDriverMetricDefinition> getMetricDefinitions() {
    if (metricDefinitions == null) {
      return Collections.emptyList();
    }
    return metricDefinitions;
  }
  @Override
  public Map<String, Object> getDslEnvVariables(GcpConnectorDTO connectorConfigDTO) {
    Map<String, Object> dslEnvVariables = StackdriverUtils.getCommonEnvVariables(connectorConfigDTO, METRIC_SCOPE);
    List<String> crossSeriesReducerList = new ArrayList<>();
    List<String> perSeriesAlignerList = new ArrayList<>();
    List<String> metricIdentifiers = new ArrayList<>();
    List<String> filterList = new ArrayList<>();
    List<List<String>> groupByFieldsList = new ArrayList<>();
    Map<String, List<String>> groupByResponseList = new HashMap<>();
    List<String> serviceInstanceFieldList = new ArrayList<>();
    List<String> serviceInstanceResponseFields = new ArrayList<>();
    getMetricDefinitions().forEach(metricDefinition -> {
      if (this.isCollectHostData() && metricDefinition.getServiceInstanceField() != null) {
        serviceInstanceFieldList.add(metricDefinition.getServiceInstanceField());
        serviceInstanceResponseFields.add(
            metricDefinition.getServiceInstanceField().replace("\"", "").replace("label", "labels"));
      }
      metricIdentifiers.add(metricDefinition.getMetricIdentifier());
      crossSeriesReducerList.add(
          checkForNullAndReturnValue(metricDefinition.getAggregation().getCrossSeriesReducer(), ""));
      perSeriesAlignerList.add(checkForNullAndReturnValue(metricDefinition.getAggregation().getPerSeriesAligner(), ""));
      groupByFieldsList.add(
          checkForNullAndReturnValue(metricDefinition.getAggregation().getGroupByFields(), new ArrayList<>()));

      List<String> groupByResponse = new ArrayList<>();

      if (metricDefinition.getAggregation().getGroupByFields() != null) {
        for (String field : metricDefinition.getAggregation().getGroupByFields()) {
          groupByResponse.add(field.replace("\"", "").replace("label", "labels"));
        }
      }

      groupByResponseList.put(metricDefinition.getMetricName(), groupByResponse);

      filterList.add(checkForNullAndReturnValue(metricDefinition.getFilter(), ""));
    });

    dslEnvVariables.put("crossSeriesReducerList", crossSeriesReducerList);
    dslEnvVariables.put("perSeriesAlignerList", perSeriesAlignerList);
    dslEnvVariables.put("groupByFieldsList", groupByFieldsList);
    dslEnvVariables.put("groupByResponseList", groupByResponseList);
    dslEnvVariables.put("metricIdentifiers", metricIdentifiers);
    dslEnvVariables.put("filterList", filterList);

    if (this.isCollectHostData()) {
      Preconditions.checkState(serviceInstanceFieldList.size() == filterList.size(),
          "Not all metrics have the service instance field defined. We will not be able to collect host level metrics");
      dslEnvVariables.put("serviceInstanceFields", serviceInstanceFieldList);
      dslEnvVariables.put("serviceInstanceResponseFields", serviceInstanceResponseFields);
    }
    dslEnvVariables.put("collectHostData", Boolean.toString(this.isCollectHostData()));
    Preconditions.checkState(crossSeriesReducerList.size() == perSeriesAlignerList.size()
            && crossSeriesReducerList.size() == filterList.size(),
        "CrossSeriesReducer, PerSeriesAligner, Filter should all have same length");

    dslEnvVariables.put("timestampFormat", StackdriverUtils.TIMESTAMP_FORMAT);
    return dslEnvVariables;
  }

  @Override
  public String getBaseUrl(GcpConnectorDTO connectorConfigDTO) {
    return "https://monitoring.googleapis.com/v3/projects/";
  }

  @Override
  public Map<String, String> collectionHeaders(GcpConnectorDTO connectorConfigDTO) {
    return Collections.emptyMap();
  }

  @Override
  public Map<String, String> collectionParams(GcpConnectorDTO connectorConfigDTO) {
    return Collections.emptyMap();
  }
}
