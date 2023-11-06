/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.k8s;
import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
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
import lombok.Builder;
import lombok.Value;
import lombok.experimental.NonFinal;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_K8S})
@Value
@Builder
public class K8sDeployResponse implements CDDelegateTaskNotifyResponseData {
  CommandExecutionStatus commandExecutionStatus;
  String errorMessage;
  K8sNGTaskResponse k8sNGTaskResponse;
  @NonFinal UnitProgressData commandUnitsProgress;
  @NonFinal DelegateMetaInfo delegateMetaInfo;

  @Override
  public void setDelegateMetaInfo(DelegateMetaInfo metaInfo) {
    this.delegateMetaInfo = metaInfo;
  }

  public void setCommandUnitsProgress(UnitProgressData commandUnitsProgress) {
    this.commandUnitsProgress = commandUnitsProgress;
  }

  @Override
  public StepExecutionInstanceInfo getStepExecutionInstanceInfo() {
    return StepExecutionInstanceInfo.builder()
        .serviceInstancesBefore(convertK8sPodsToK8sStepInstanceInfo(
            k8sNGTaskResponse == null ? Collections.emptyList() : k8sNGTaskResponse.getPreviousK8sPodList()))
        .deployedServiceInstances(convertK8sPodsToK8sStepInstanceInfo(filterNewK8sPods(
            k8sNGTaskResponse == null ? Collections.emptyList() : k8sNGTaskResponse.getTotalK8sPodList())))
        .serviceInstancesAfter(convertK8sPodsToK8sStepInstanceInfo(
            k8sNGTaskResponse == null ? Collections.emptyList() : k8sNGTaskResponse.getTotalK8sPodList()))
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
