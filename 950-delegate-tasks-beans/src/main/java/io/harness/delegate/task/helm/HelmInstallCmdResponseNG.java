/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.helm;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.container.ContainerInfo;
import io.harness.k8s.model.HelmVersion;
import io.harness.k8s.model.K8sPod;
import io.harness.logging.CommandExecutionStatus;

import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Data;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_K8S})
@Data
public class HelmInstallCmdResponseNG extends HelmCommandResponseNG {
  private List<ContainerInfo> containerInfoList;
  List<K8sPod> k8sPodList;
  List<K8sPod> previousK8sPodList;
  private HelmChartInfo helmChartInfo;
  private int prevReleaseVersion;
  private String releaseName;
  private HelmVersion helmVersion;
  private Map<String, List<String>> workloadLabelSelectors;

  @Builder
  public HelmInstallCmdResponseNG(CommandExecutionStatus commandExecutionStatus, String output,
      List<ContainerInfo> containerInfoList, HelmChartInfo helmChartInfo, int prevReleaseVersion, String releaseName,
      HelmVersion helmVersion, List<K8sPod> k8sPodList, List<K8sPod> previousK8sPodList,
      Map<String, List<String>> workloadLabelSelectors) {
    super(commandExecutionStatus, output);
    this.containerInfoList = containerInfoList;
    this.helmChartInfo = helmChartInfo;
    this.prevReleaseVersion = prevReleaseVersion;
    this.releaseName = releaseName;
    this.helmVersion = helmVersion;
    this.k8sPodList = k8sPodList;
    this.previousK8sPodList = previousK8sPodList;
    this.workloadLabelSelectors = workloadLabelSelectors;
  }

  public List<K8sPod> getPreviousK8sPodList() {
    return previousK8sPodList;
  }

  public List<K8sPod> getTotalK8sPodList() {
    return k8sPodList;
  }
}
