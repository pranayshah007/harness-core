/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.manifest.mappers;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.cdng.manifest.ManifestType.HelmChart;
import static io.harness.common.ParameterFieldHelper.getParameterFieldValue;

import static java.util.Objects.isNull;

import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.manifest.yaml.HelmChartManifestOutcome;
import io.harness.cdng.manifest.yaml.ManifestOutcome;
import io.harness.cdng.manifest.yaml.summary.HelmChartManifestSummary;
import io.harness.cdng.manifest.yaml.summary.ManifestSummary;

import lombok.experimental.UtilityClass;

@UtilityClass
@OwnedBy(CDP)
public class ManifestSummaryMapper {
  public ManifestSummary toManifestSummary(ManifestOutcome manifestOutcome) {
    if (isNull(manifestOutcome)) {
      return null;
    }
    if (HelmChart.equals(manifestOutcome.getType())) {
      return getHelmChartSummary(manifestOutcome);
    }
    return null;
  }

  private HelmChartManifestSummary getHelmChartSummary(ManifestOutcome manifestOutcome) {
    HelmChartManifestOutcome helmChartManifestOutcome = (HelmChartManifestOutcome) manifestOutcome;

    return HelmChartManifestSummary.builder()
        .identifier(helmChartManifestOutcome.getIdentifier())
        .type(HelmChart)
        .store(helmChartManifestOutcome.getStore())
        .chartVersion(getParameterFieldValue(helmChartManifestOutcome.getChartVersion()))
        .helmVersion(helmChartManifestOutcome.getHelmVersion())
        .build();
  }
}
