/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cvng.beans;

import io.harness.cvng.core.services.CVNextGenConstants;
import io.harness.delegate.beans.connector.appdynamicsconnector.AppDynamicsConnectorDTO;
import io.harness.delegate.beans.cvng.appd.AppDynamicsUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.FieldDefaults;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ArrayUtils;

@Data
@Builder
@EqualsAndHashCode(callSuper = true)
public class AppDynamicsDataCollectionInfo extends TimeSeriesDataCollectionInfo<AppDynamicsConnectorDTO> {
  private String applicationName;
  private String tierName;
  private MetricPackDTO metricPack;
  private List<AppMetricInfoDTO> customMetrics;
  private String groupName;

  public List<AppMetricInfoDTO> getCustomMetrics() {
    if (customMetrics == null) {
      return new ArrayList<>();
    }
    return customMetrics;
  }

  @Override
  public Map<String, Object> getDslEnvVariables(AppDynamicsConnectorDTO appDynamicsConnectorDTO) {
    Map<String, Object> dslEnvVariables = AppDynamicsUtils.getCommonEnvVariables(appDynamicsConnectorDTO);
    dslEnvVariables.put("applicationName", getApplicationName());
    dslEnvVariables.put("tierName", getTierName());
    final List<String> metricPaths = CollectionUtils.emptyIfNull(getMetricPack().getMetrics())
                                         .stream()
                                         .filter(metricDefinition -> metricDefinition.isIncluded())
                                         .map(metricDefinition -> metricDefinition.getPath())
                                         .collect(Collectors.toList());
    dslEnvVariables.put("metricsToCollect", metricPaths);
    dslEnvVariables.put("collectHostData", Boolean.toString(this.isCollectHostData()));
    dslEnvVariables.put("metricIdentifiers",
        CollectionUtils.emptyIfNull(getMetricPack().getMetrics())
            .stream()
            .filter(metricDefinition -> metricDefinition.isIncluded())
            .map(metricDefinition -> metricDefinition.getMetricIdentifier())
            .collect(Collectors.toList()));

    if (metricPack.getIdentifier().equals(CVNextGenConstants.CUSTOM_PACK_IDENTIFIER)) {
      dslEnvVariables.put(
          "metricNames", getCustomMetrics().stream().map(AppMetricInfoDTO::getMetricName).collect(Collectors.toList()));
      dslEnvVariables.put("metricIdentifiers",
          getCustomMetrics().stream().map(AppMetricInfoDTO::getMetricIdentifier).collect(Collectors.toList()));
      dslEnvVariables.put("groupName", groupName);

      if (this.isCollectHostData()) {
        List<String> customMetricPaths = getCustomMetrics()
                                             .stream()
                                             .map(mi -> mi.getCompleteServiceInstanceMetricPath())
                                             .collect(Collectors.toList());
        List<Integer> getServiceInstanceIndexs =
            customMetricPaths.stream().map(this::getServiceInstanceIndex).collect(Collectors.toList());
        dslEnvVariables.put("metricPaths", customMetricPaths);
        dslEnvVariables.put("serviceInstanceIndexes", getServiceInstanceIndexs);
      } else {
        List<String> customMetricPaths =
            getCustomMetrics().stream().map(mi -> mi.getCompleteMetricPath()).collect(Collectors.toList());
        dslEnvVariables.put("metricPaths", customMetricPaths);
      }
    }

    return dslEnvVariables;
  }

  @Override
  public String getBaseUrl(AppDynamicsConnectorDTO appDynamicsConnectorDTO) {
    return appDynamicsConnectorDTO.getControllerUrl();
  }

  @Override
  public Map<String, String> collectionHeaders(AppDynamicsConnectorDTO appDynamicsConnectorDTO) {
    return AppDynamicsUtils.collectionHeaders(appDynamicsConnectorDTO);
  }

  @Override
  public Map<String, String> collectionParams(AppDynamicsConnectorDTO appDynamicsConnectorDTO) {
    return Collections.emptyMap();
  }

  private Integer getServiceInstanceIndex(String fullMetricPath) {
    return ArrayUtils.indexOf(fullMetricPath.split("\\|"), "Individual Nodes") + 1;
  }

  @Data
  @Builder
  @FieldDefaults(level = AccessLevel.PRIVATE)
  public static class AppMetricInfoDTO {
    String metricName;
    String metricIdentifier;
    String completeMetricPath;
    String completeServiceInstanceMetricPath;
  }
}
