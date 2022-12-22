/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.beans;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.cvng.core.services.CVNextGenConstants;
import io.harness.delegate.beans.connector.dynatrace.DynatraceConnectorDTO;
import io.harness.delegate.beans.cvng.dynatrace.DynatraceUtils;

import com.mongodb.lang.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.apache.commons.collections4.CollectionUtils;

@Data
@Builder
@EqualsAndHashCode(callSuper = true)
public class DynatraceDataCollectionInfo extends TimeSeriesDataCollectionInfo<DynatraceConnectorDTO> {
  public static final String DYNATRACE_SERVICE_INSTANCE_DEFAULT_PLACEHOLDER = "dynatrace-placeholder-host";
  public static final String METRIC_NAME_PARAM = "metricName";
  public static final String METRIC_IDENTIFIER_PARAM = "metricIdentifier";
  public static final String QUERY_SELECTOR_PARAM = "querySelector";
  public static final String GROUP_NAME_PARAM = "groupName";
  public static final String ENTITY_ID_PARAM = "entitySelector";
  public static final String ENTITY_SELECTOR_PARAM = "entitySelector";
  public static final String METRICS_TO_VALIDATE_PARAM = "metricsToValidate";

  private String groupName;
  private String serviceId;
  private List<String> serviceMethodIds;
  @Nullable private List<MetricCollectionInfo> customMetrics;
  private MetricPackDTO metricPack;

  @Override
  public Map<String, Object> getDslEnvVariables(DynatraceConnectorDTO connectorConfigDTO) {
    Map<String, Object> dslEnvVariables = new HashMap<>();
    dslEnvVariables.put("resolution", "1m");
    dslEnvVariables.put(GROUP_NAME_PARAM, groupName != null ? groupName : metricPack.getIdentifier());
    dslEnvVariables.put("host", isCollectHostData() ? DYNATRACE_SERVICE_INSTANCE_DEFAULT_PLACEHOLDER : null);

    List<Map<String, String>> metricsToValidate = new ArrayList<>();
    if (!CVNextGenConstants.CUSTOM_PACK_IDENTIFIER.equals(metricPack.getIdentifier())) {
      // if collection is not for custom metric, we should filter by service methods
      String serviceMethodsIdsParam;
      if (isNotEmpty(serviceMethodIds)) {
        serviceMethodsIdsParam = serviceMethodIds.stream()
                                     .map(serviceMethodId -> "\"".concat(serviceMethodId).concat("\""))
                                     .reduce((prev, next) -> prev.concat(",").concat(next))
                                     .orElse(null);
        dslEnvVariables.put(
            ENTITY_ID_PARAM, "type(\"dt.entity.service_method\"),entityId(".concat(serviceMethodsIdsParam).concat(")"));
      } else {
        throw new IllegalArgumentException("Service methods IDs must be provided for Dynatrace data collection.");
      }
      metricsToValidate = CollectionUtils.emptyIfNull(metricPack.getMetrics())
                              .stream()
                              .map(metricDefinitionDTO -> {
                                Map<String, String> metricMap = new HashMap<>();
                                metricMap.put(METRIC_NAME_PARAM, metricDefinitionDTO.getName());
                                metricMap.put(METRIC_IDENTIFIER_PARAM, metricDefinitionDTO.getMetricIdentifier());
                                metricMap.put(QUERY_SELECTOR_PARAM, metricDefinitionDTO.getPath());
                                return metricMap;
                              })
                              .collect(Collectors.toList());
    } else if (customMetrics != null) {
      dslEnvVariables.put(ENTITY_ID_PARAM, "type(\"dt.entity.service\"),entityId(\"".concat(serviceId).concat("\")"));
      metricsToValidate = customMetrics.stream()
                              .map(metricDefinitionDTO -> {
                                Map<String, String> metricMap = new HashMap<>();
                                metricMap.put(METRIC_NAME_PARAM, metricDefinitionDTO.getMetricName());
                                metricMap.put(METRIC_IDENTIFIER_PARAM, metricDefinitionDTO.getIdentifier());
                                metricMap.put(QUERY_SELECTOR_PARAM, metricDefinitionDTO.getMetricSelector());
                                return metricMap;
                              })
                              .collect(Collectors.toList());
    }
    dslEnvVariables.put(METRICS_TO_VALIDATE_PARAM, metricsToValidate);
    return dslEnvVariables;
  }

  @Override
  public String getBaseUrl(DynatraceConnectorDTO dynatraceConnectorDTO) {
    return dynatraceConnectorDTO.getUrl();
  }

  @Override
  public Map<String, String> collectionHeaders(DynatraceConnectorDTO dynatraceConnectorDTO) {
    return DynatraceUtils.collectionHeaders(dynatraceConnectorDTO);
  }

  @Override
  public Map<String, String> collectionParams(DynatraceConnectorDTO dynatraceConnectorDTO) {
    return Collections.emptyMap();
  }

  @Data
  @Builder
  public static class MetricCollectionInfo {
    String identifier;
    String metricName;
    String metricSelector;
  }
}
