/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package software.wings.verification.newrelic;

import static software.wings.service.impl.newrelic.NewRelicMetricDataRecord.DEFAULT_GROUP_NAME;

import software.wings.service.impl.analysis.DataCollectionInfoV2;
import software.wings.service.impl.newrelic.NewRelicDataCollectionInfoV2;
import software.wings.verification.CVConfiguration;
import software.wings.verification.MetricCVConfigurationYaml;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.github.reinert.jjschema.Attributes;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * @author Vaibhav Tulsyan
 * 05/Oct/2018
 */

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
public class NewRelicCVServiceConfiguration extends CVConfiguration {
  @Attributes(required = true, title = "Application Name") private String applicationId;
  @Attributes(required = true, title = "Metrics") private List<String> metrics;

  @Override
  public CVConfiguration deepCopy() {
    NewRelicCVServiceConfiguration clonedConfig = new NewRelicCVServiceConfiguration();
    super.copy(clonedConfig);
    clonedConfig.setApplicationId(this.getApplicationId());
    clonedConfig.setMetrics(this.getMetrics());
    return clonedConfig;
  }

  @Override
  public DataCollectionInfoV2 toDataCollectionInfo() {
    Map<String, String> hostsMap = new HashMap<>();
    hostsMap.put("DUMMY_24_7_HOST", DEFAULT_GROUP_NAME);
    NewRelicDataCollectionInfoV2 newRelicDataCollectionInfoV2 =
        NewRelicDataCollectionInfoV2.builder()
            .newRelicAppId(Long.parseLong(this.getApplicationId()))
            .hostsToGroupNameMap(hostsMap)
            .build();
    fillDataCollectionInfoWithCommonFields(newRelicDataCollectionInfoV2);
    return newRelicDataCollectionInfoV2;
  }

  /**
   * The type Yaml.
   */
  @Data
  @Builder
  @AllArgsConstructor
  @EqualsAndHashCode(callSuper = true)
  @JsonPropertyOrder({"type", "harnessApiVersion"})
  public static final class NewRelicCVConfigurationYaml extends MetricCVConfigurationYaml {
    private String newRelicApplicationName;
    private List<String> metrics;
  }
}
