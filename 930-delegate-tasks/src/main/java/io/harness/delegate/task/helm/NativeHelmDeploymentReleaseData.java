/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.helm;
import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.delegate.task.k8s.K8sInfraDelegateConfig;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Data;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_K8S})
@Data
@Builder
@OwnedBy(CDP)
public class NativeHelmDeploymentReleaseData {
  private K8sInfraDelegateConfig k8sInfraDelegateConfig;
  private LinkedHashSet<String> namespaces;
  private String releaseName;
  HelmChartInfo helmChartInfo;
  private Map<String, List<String>> workloadLabelSelectors;
}
