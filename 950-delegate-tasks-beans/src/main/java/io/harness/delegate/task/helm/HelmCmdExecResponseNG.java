/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.helm;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.delegate.beans.CDDelegateTaskNotifyResponseData;
import io.harness.delegate.beans.DelegateMetaInfo;
import io.harness.delegate.beans.logstreaming.UnitProgressData;
import io.harness.delegate.cdng.execution.K8sStepInstanceInfo;
import io.harness.delegate.cdng.execution.StepExecutionInstanceInfo;
import io.harness.delegate.cdng.execution.StepInstanceInfo;
import io.harness.k8s.model.K8sPod;
import io.harness.logging.CommandExecutionStatus;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.NonFinal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
public class HelmCmdExecResponseNG implements CDDelegateTaskNotifyResponseData {
  private HelmCommandResponseNG helmCommandResponse;
  private String errorMessage;
  private CommandExecutionStatus commandExecutionStatus;
  private UnitProgressData commandUnitsProgress;
  @NonFinal DelegateMetaInfo delegateMetaInfo;

  @Override
  public void setDelegateMetaInfo(DelegateMetaInfo metaInfo) {
    this.delegateMetaInfo = metaInfo;
  }

  @Override
  public StepExecutionInstanceInfo getStepExecutionInstanceInfo() {
    return StepExecutionInstanceInfo.builder()
        .serviceInstancesBefore(convertK8sPodsToK8sStepInstanceInfo(helmCommandResponse.getPreviousK8sPodList()))
        .deployedServiceInstances(
            convertK8sPodsToK8sStepInstanceInfo(filterNewK8sPods(helmCommandResponse.getTotalK8sPodList())))
        .serviceInstancesAfter(convertK8sPodsToK8sStepInstanceInfo(helmCommandResponse.getTotalK8sPodList()))
        .build();
  }

  private List<K8sPod> filterNewK8sPods(List<K8sPod> k8sPods) {
    if (isEmpty(k8sPods)) {
      return Collections.emptyList();
    }
    return k8sPods.stream().filter(K8sPod::isNewPod).collect(Collectors.toList());
  }

  private List<StepInstanceInfo> convertK8sPodsToK8sStepInstanceInfo(List<K8sPod> k8sPods) {
    if (isEmpty(k8sPods)) {
      return Collections.emptyList();
    }
    return k8sPods.stream()
        .map(k8sPod -> K8sStepInstanceInfo.builder().podName(k8sPod.getName()).build())
        .collect(Collectors.toList());
  }
}
