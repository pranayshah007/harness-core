/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.manifest.yaml.summary;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.manifest.ManifestType;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfig;
import io.harness.k8s.model.HelmVersion;

import com.fasterxml.jackson.annotation.JsonTypeName;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldNameConstants;
import org.springframework.data.annotation.TypeAlias;

@Data
@Builder
@OwnedBy(CDP)
@JsonTypeName(ManifestType.HelmChart)
@TypeAlias("HelmChartManifestSummary")
@FieldNameConstants(innerTypeName = "HelmChartManifestSummaryKeys")
public class HelmChartManifestSummary implements ManifestSummary {
  String identifier;
  String type;
  StoreConfig store;
  String chartVersion;
  HelmVersion helmVersion;
}
