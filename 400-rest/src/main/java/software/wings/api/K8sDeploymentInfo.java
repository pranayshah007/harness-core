/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.api;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.delegate.task.helm.HelmChartInfo;

import software.wings.beans.infrastructure.instance.info.K8sPodInfo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

@JsonIgnoreProperties(ignoreUnknown = true)
@Data
@EqualsAndHashCode(callSuper = true)
@OwnedBy(CDP)
@TargetModule(HarnessModule._957_CG_BEANS)
public class K8sDeploymentInfo extends DeploymentInfo {
  private String namespace;
  private String releaseName;
  private Integer releaseNumber;
  private Set<String> namespaces = new HashSet<>();
  private HelmChartInfo helmChartInfo;
  private String blueGreenStageColor;
  private String clusterName;
  private List<K8sPodInfo> k8sPods = new ArrayList<>();

  @Builder
  public K8sDeploymentInfo(String namespace, String releaseName, Integer releaseNumber, Set<String> namespaces,
      HelmChartInfo helmChartInfo, String blueGreenStageColor, String clusterName, List<K8sPodInfo> k8sPods) {
    this.namespace = namespace;
    this.releaseName = releaseName;
    this.releaseNumber = releaseNumber;
    this.namespaces = namespaces;
    this.helmChartInfo = helmChartInfo;
    this.blueGreenStageColor = blueGreenStageColor;
    this.clusterName = clusterName;
    this.k8sPods = k8sPods;
  }
}
