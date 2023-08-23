/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.entities.instanceinfo;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.task.helm.HelmChartInfo;
import io.harness.k8s.model.K8sContainer;

import java.util.List;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

@OwnedBy(HarnessTeam.DX)
@Data
@Builder
@EqualsAndHashCode(callSuper = true)
public class K8sInstanceInfo extends InstanceInfo {
  @NotNull private String namespace;
  @NotNull private String releaseName;
  @NotNull private String podName;
  private String podIP;
  private String blueGreenColor;
  @NotNull private List<K8sContainer> containerList;
  private HelmChartInfo helmChartInfo;
}
