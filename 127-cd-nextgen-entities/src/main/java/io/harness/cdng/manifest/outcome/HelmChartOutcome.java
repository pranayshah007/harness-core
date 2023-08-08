/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.manifest.outcome;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.delegate.task.helm.response.HelmChartManifest;

import java.util.Map;
import lombok.Builder;
import lombok.Value;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_K8S})
@Value
@Builder
@OwnedBy(HarnessTeam.CDP)
public class HelmChartOutcome {
  String name;
  String description;
  String version;
  String apiVersion;
  String appVersion;
  String kubeVersion;
  Map<String, String> metadata;

  public static HelmChartOutcome from(HelmChartManifest helmChartManifest) {
    return HelmChartOutcome.builder()
        .name(helmChartManifest.getName())
        .description(helmChartManifest.getDescription())
        .apiVersion(helmChartManifest.getApiVersion())
        .appVersion(helmChartManifest.getAppVersion())
        .version(helmChartManifest.getVersion())
        .kubeVersion(helmChartManifest.getKubeVersion())
        .metadata(helmChartManifest.getMetadata())
        .build();
  }
}
