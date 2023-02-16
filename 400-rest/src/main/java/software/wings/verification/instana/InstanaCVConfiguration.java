/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package software.wings.verification.instana;

import static software.wings.beans.dto.NewRelicMetricDataRecord.DEFAULT_GROUP_NAME;

import software.wings.service.impl.analysis.DataCollectionInfoV2;
import software.wings.service.impl.instana.InstanaDataCollectionInfo;
import software.wings.service.impl.instana.InstanaTagFilter;
import software.wings.verification.CVConfiguration;
import software.wings.verification.CVConfigurationYaml;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;

@FieldNameConstants(innerTypeName = "InstanaCVConfigurationKeys")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
public class InstanaCVConfiguration extends CVConfiguration {
  private List<InstanaTagFilter> tagFilters;

  public List<InstanaTagFilter> getTagFilters() {
    if (tagFilters == null) {
      return Collections.emptyList();
    }
    return new ArrayList<>(tagFilters);
  }
  @Override
  public CVConfiguration deepCopy() {
    InstanaCVConfiguration clonedConfig = new InstanaCVConfiguration();
    super.copy(clonedConfig);
    clonedConfig.setTagFilters(getTagFilters());
    return clonedConfig;
  }

  @Override
  public DataCollectionInfoV2 toDataCollectionInfo() {
    Map<String, String> hostsMap = new HashMap<>();
    hostsMap.put("DUMMY_24_7_HOST", DEFAULT_GROUP_NAME);
    InstanaDataCollectionInfo instanaDataCollectionInfo =
        InstanaDataCollectionInfo.builder().tagFilters(this.getTagFilters()).hostsToGroupNameMap(hostsMap).build();
    fillDataCollectionInfoWithCommonFields(instanaDataCollectionInfo);
    return instanaDataCollectionInfo;
  }

  @Override
  public boolean isCVTaskBasedCollectionEnabled() {
    return true;
  }
  /**
   * The type Yaml.
   */
  @Data
  @JsonPropertyOrder({"type", "harnessApiVersion"})
  @Builder
  @AllArgsConstructor
  @EqualsAndHashCode(callSuper = true)
  public static final class InstanaCVConfigurationYaml extends CVConfigurationYaml {
    private List<InstanaTagFilter> tagFilters;
  }
}
