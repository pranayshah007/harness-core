/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.api.k8s;

import static io.harness.annotations.dev.HarnessModule._957_CG_BEANS;
import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.delegate.task.helm.HelmChartInfo;
import io.harness.k8s.model.KubernetesResourceId;

import software.wings.beans.infrastructure.instance.info.K8sPodInfo;
import software.wings.sm.StepExecutionSummary;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import java.util.Set;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

@JsonIgnoreProperties(ignoreUnknown = true)
@Data
@Builder
@TargetModule(_957_CG_BEANS)
@OwnedBy(CDP)
@EqualsAndHashCode(callSuper = true)
public class K8sExecutionSummary extends StepExecutionSummary {
  private String namespace;
  private String releaseName;
  private Integer releaseNumber;
  private Integer targetInstances;
  private Set<String> namespaces;
  private HelmChartInfo helmChartInfo;
  private String blueGreenStageColor;
  private Set<String> delegateSelectors;
  private List<KubernetesResourceId> prunedResourcesIds;
  private boolean exportManifests;
  private String clusterName;
  private List<K8sPodInfo> pods;
}
