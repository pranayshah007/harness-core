/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.polling.bean.manifest;

import static com.fasterxml.jackson.annotation.JsonTypeInfo.As.EXTERNAL_PROPERTY;
import static com.fasterxml.jackson.annotation.JsonTypeInfo.Id.NAME;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.manifest.ManifestType;
import io.harness.cdng.manifest.yaml.HelmChartManifestOutcome;
import io.harness.cdng.manifest.yaml.ManifestOutcome;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfig;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfigType;
import io.harness.k8s.model.HelmVersion;
import io.harness.pms.yaml.ParameterField;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeName;
import lombok.Builder;
import lombok.Data;

@OwnedBy(HarnessTeam.CDC)
@Data
@Builder
@JsonTypeName(ManifestType.HelmChart)
public class HelmChartManifestInfo implements ManifestInfo {
  @JsonProperty("storeType") StoreConfigType storeType;
  @JsonTypeInfo(use = NAME, property = "storeType", include = EXTERNAL_PROPERTY, visible = true) StoreConfig store;
  String chartName;
  HelmVersion helmVersion;

  @Override
  public ManifestOutcome toManifestOutcome() {
    return HelmChartManifestOutcome.builder()
        .store(store)
        .chartName(ParameterField.<String>builder().value(chartName).build())
        .helmVersion(helmVersion)
        .build();
  }

  @Override
  public String getType() {
    return ManifestType.HelmChart;
  }
}
