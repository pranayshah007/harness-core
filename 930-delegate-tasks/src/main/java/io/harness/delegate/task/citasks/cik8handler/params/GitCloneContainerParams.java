/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.citasks.cik8handler.params;

import io.harness.delegate.beans.ci.pod.ConnectorDetails;
import io.harness.delegate.beans.ci.pod.ContainerParams;
import io.harness.delegate.beans.ci.pod.ContainerResourceParams;
import io.harness.delegate.beans.ci.pod.ContainerSecrets;
import io.harness.delegate.beans.ci.pod.ContainerSecurityContext;
import io.harness.delegate.beans.ci.pod.ImageDetailsWithConnector;
import io.harness.delegate.beans.ci.pod.SecretVarParams;
import io.harness.delegate.beans.ci.pod.SecretVolumeParams;

import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;

@Getter
@EqualsAndHashCode(callSuper = true)
public class GitCloneContainerParams extends ContainerParams {
  private final String branchName;
  private final String commitId;
  private final String stepExecVolumeName;
  private final String stepExecWorkingDir;

  @Builder
  public GitCloneContainerParams(String branchName, String commitId,
      String stepExecVolumeName, String stepExecWorkingDir, String name,
      ImageDetailsWithConnector imageDetailsWithConnector, List<String> commands, List<String> args, String workingDir,
      List<Integer> ports, Map<String, String> envVars, Map<String, String> envVarsWithSecretRef,
      Map<String, SecretVarParams> secretEnvVars, Map<String, SecretVolumeParams> secretVolumes, String imageSecret,
      Map<String, String> volumeToMountPath, ContainerResourceParams containerResourceParams,
      ContainerSecrets containerSecrets, Integer runAsUser, boolean privileged, String imagePullPolicy,
      ContainerSecurityContext securityContext, ConnectorDetails gitConnector) {
    super(name, imageDetailsWithConnector, commands, args, workingDir, ports, envVars, envVarsWithSecretRef,
        secretEnvVars, secretVolumes, imageSecret, volumeToMountPath, containerResourceParams, containerSecrets,
        runAsUser, privileged, imagePullPolicy, securityContext, gitConnector);
    this.branchName = branchName;
    this.commitId = commitId;
    this.stepExecVolumeName = stepExecVolumeName;
    this.stepExecWorkingDir = stepExecWorkingDir;
  }

  @Override
  public ContainerParams.Type getType() {
    return ContainerParams.Type.K8_GIT_CLONE;
  }
}
