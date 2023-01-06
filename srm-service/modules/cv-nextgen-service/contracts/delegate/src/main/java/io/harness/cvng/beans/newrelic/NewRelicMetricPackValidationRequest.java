/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cvng.beans.newrelic;

import static io.harness.annotations.dev.HarnessTeam.CV;

import io.harness.annotations.dev.OwnedBy;
import io.harness.cvng.beans.DataCollectionRequest;
import io.harness.cvng.beans.MetricPackDTO;
import io.harness.delegate.beans.connector.newrelic.NewRelicConnectorDTO;
import io.harness.delegate.beans.cvng.newrelic.NewRelicUtils;

import com.fasterxml.jackson.annotation.JsonTypeName;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@JsonTypeName("NEWRELIC_VALIDATION_REQUEST")
@Data
@SuperBuilder
@NoArgsConstructor
@OwnedBy(CV)
public class NewRelicMetricPackValidationRequest extends DataCollectionRequest<NewRelicConnectorDTO> {
  public static final String DSL = DataCollectionRequest.readDSL(
      "newrelic-metric-pack-validation.datacollection", NewRelicMetricPackValidationRequest.class);

  private String applicationName;
  private String applicationId;
  private Set<MetricPackDTO> metricPackDTOSet;

  @Override
  public String getDSL() {
    return DSL;
  }

  @Override
  public String getBaseUrl() {
    return NewRelicUtils.getBaseUrl(getConnectorConfigDTO());
  }

  @Override
  public Map<String, String> collectionHeaders() {
    return NewRelicUtils.collectionHeaders(getConnectorConfigDTO());
  }

  @Override
  public Map<String, Object> fetchDslEnvVariables() {
    Map<String, Object> envVariables = new HashMap<>();
    envVariables.put("appId", applicationId);
    envVariables.put("appName", applicationName);
    List<NewRelicValidationCollectionInfo> collectionInfoList = getNewRelicValidationCollectionInfo();
    List<String> queryList = new ArrayList<>();
    List<String> metricNameList = new ArrayList<>();
    List<String> jsonPathList = new ArrayList<>();

    for (NewRelicValidationCollectionInfo validationCollectionInfo : collectionInfoList) {
      queryList.add(validationCollectionInfo.getQuery());
      jsonPathList.add(validationCollectionInfo.getJsonPath());
      metricNameList.add(validationCollectionInfo.getMetricName());
    }
    envVariables.put("queries", queryList);
    envVariables.put("jsonPaths", jsonPathList);
    envVariables.put("metricNames", metricNameList);
    return envVariables;
  }

  private List<NewRelicValidationCollectionInfo> getNewRelicValidationCollectionInfo() {
    if (metricPackDTOSet != null) {
      List<NewRelicValidationCollectionInfo> returnval = new ArrayList<>();
      metricPackDTOSet.forEach(metricPackDTO -> {
        metricPackDTO.getMetrics().forEach(metricDefinitionDTO -> {
          returnval.add(NewRelicValidationCollectionInfo.builder()
                            .jsonPath(metricDefinitionDTO.getValidationResponseJsonPath())
                            .query(metricDefinitionDTO.getValidationPath())
                            .metricName(metricDefinitionDTO.getName())
                            .build());
        });
      });
      return returnval;
    }
    return null;
  }

  @Data
  @Builder
  static class NewRelicValidationCollectionInfo {
    private String query;
    private String jsonPath;
    private String metricName;
  }
}
