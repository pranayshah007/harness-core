/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.beans.stackdriver;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.cvng.beans.stackdriver.StackDriverMetricDefinition;
import io.harness.serializer.JsonUtils;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class StackdriverDashboardDetail {
  private static final String dataSetsJsonAppender = "{\"dataSets\": [";
  private static final String metricTypeField = "metric.type";

  String widgetName;
  Object dataSets;
  List<DataSet> dataSetList;

  @JsonIgnore
  public Object getDataSets() {
    return dataSets;
  }

  @Data
  @Builder
  public static class DataSet {
    Object timeSeriesQuery;
    String metricName;
  }

  public void transformDataSets() {
    final Gson gson = new Gson();
    Type type = new TypeToken<List<DataSet>>() {}.getType();
    this.dataSetList = gson.fromJson(JsonUtils.asJson(dataSets), type);
    List<DataSet> modifiedDataset = new ArrayList<>();
    if (isNotEmpty(dataSetList)) {
      dataSetList.forEach(dataSet -> {
        String appendedDataSet = dataSetsJsonAppender + JsonUtils.asJson(dataSet) + "]}";
        StackDriverMetricDefinition metricDefinition = StackDriverMetricDefinition.extractFromJson(appendedDataSet);
        dataSet.setMetricName(extractMetric(metricDefinition));
        modifiedDataset.add(DataSet.builder()
                                .metricName(extractMetric(metricDefinition))
                                .timeSeriesQuery(JsonUtils.asObject(appendedDataSet, Object.class))
                                .build());
      });
      this.dataSetList = modifiedDataset;
    }
  }

  public String extractMetric(StackDriverMetricDefinition metricDefinition) {
    String[] filters = metricDefinition.getFilter().split(" ");
    for (String innerFilter : filters) {
      if (innerFilter.contains(metricTypeField)) {
        return innerFilter.substring(innerFilter.indexOf('=') + 1).replaceAll("\"", "");
      }
    }
    return null;
  }
}
