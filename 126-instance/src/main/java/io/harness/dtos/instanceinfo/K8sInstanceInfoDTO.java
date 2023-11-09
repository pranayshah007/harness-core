/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.dtos.instanceinfo;

import static io.harness.data.structure.CollectionUtils.emptyIfNull;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.task.helm.HelmChartInfo;
import io.harness.k8s.model.HarnessLabelValues;
import io.harness.k8s.model.K8sContainer;
import io.harness.util.InstanceSyncKey;
import io.harness.util.InstanceSyncKeyConstants;

import java.util.List;
import java.util.stream.Collectors;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

@OwnedBy(HarnessTeam.CDP)
@Data
@Builder
@EqualsAndHashCode(callSuper = true)
public class K8sInstanceInfoDTO extends InstanceInfoDTO {
  @NotNull private String namespace;
  @NotNull private String releaseName;
  @NotNull private String podName;
  private String podIP;
  private String blueGreenColor;
  @NotNull private List<K8sContainer> containerList;
  private HelmChartInfo helmChartInfo;
  private boolean canary;

  @Override
  public String prepareInstanceKey() {
    return InstanceSyncKey.builder()
        .clazz(K8sInstanceInfoDTO.class)
        .part(podName)
        .part(namespace)
        .part(getImageInStringFormat())
        .build()
        .toString();
  }

  @Override
  public String prepareInstanceSyncHandlerKey() {
    if (isNotEmpty(blueGreenColor)) {
      return InstanceSyncKey.builder().part(releaseName).part(blueGreenColor).build().toString();
    }

    if (canary) {
      return InstanceSyncKey.builder()
          .part(releaseName)
          .part(InstanceSyncKeyConstants.CanaryDeployment)
          .build()
          .toString();
    }

    return InstanceSyncKey.builder().part(releaseName).build().toString();
  }

  private String getImageInStringFormat() {
    return emptyIfNull(containerList).stream().map(K8sContainer::getImage).collect(Collectors.joining());
  }

  @Override
  public String getType() {
    return "K8s";
  }

  public void swapBlueGreenColor() {
    if (HarnessLabelValues.colorBlue.equals(blueGreenColor)) {
      this.blueGreenColor = HarnessLabelValues.colorGreen;
    } else if (HarnessLabelValues.colorGreen.equals(blueGreenColor)) {
      this.blueGreenColor = HarnessLabelValues.colorBlue;
    }
  }
}
