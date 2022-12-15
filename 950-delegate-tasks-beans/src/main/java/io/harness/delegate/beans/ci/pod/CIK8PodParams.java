/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.beans.ci.pod;

import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;

@Getter
@EqualsAndHashCode(callSuper = true)
public class CIK8PodParams<T extends ContainerParams> extends PodParams<T> {
  private final ConnectorDetails gitConnector;
  private final String branchName;
  private final String commitId;
  private final String stepExecVolumeName;
  private final String stepExecWorkingDir;

  @Builder
  public CIK8PodParams(ConnectorDetails gitConnector, String branchName, String commitId, String stepExecVolumeName,
      String stepExecWorkingDir, String name, String namespace, Map<String, String> annotations,
      Map<String, String> labels, List<T> containerParamsList, List<T> initContainerParamsList,
      List<PVCParams> pvcParamList, List<HostAliasParams> hostAliasParamsList, Integer runAsUser,
      String serviceAccountName, Boolean automountServiceAccountToken, Map<String, String> nodeSelector,
      List<PodToleration> tolerations, List<PodVolume> volumes, String runtime, String priorityClassName,
      Long activeDeadLineSeconds) {
    super(name, namespace, annotations, labels, containerParamsList, initContainerParamsList, pvcParamList,
        hostAliasParamsList, runAsUser, serviceAccountName, automountServiceAccountToken, nodeSelector, tolerations,
        volumes, runtime, priorityClassName, activeDeadLineSeconds);
    this.gitConnector = gitConnector;
    this.branchName = branchName;
    this.commitId = commitId;
    this.stepExecVolumeName = stepExecVolumeName;
    this.stepExecWorkingDir = stepExecWorkingDir;
  }

  @Override
  public Type getType() {
    return Type.K8;
  }
}