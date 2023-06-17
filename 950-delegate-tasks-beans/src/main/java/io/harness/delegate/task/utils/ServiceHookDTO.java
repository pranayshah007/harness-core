/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.utils;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.task.helm.HelmInstallCommandRequestNG;
import io.harness.k8s.model.K8sDelegateTaskParams;

import lombok.Value;

@Value
@OwnedBy(HarnessTeam.CDP)
public class ServiceHookDTO {
  private String kubectlPath;
  private String kubeconfigPath;
  private String workingDirectory;
  private String kustomizeBinaryPath;
  private String gcpKeyFilePath;
  private String helmPath;
  private String ocPath;

  public ServiceHookDTO(K8sDelegateTaskParams k8sDelegateTaskParams) {
    kubectlPath = k8sDelegateTaskParams.getKubectlPath();
    kubeconfigPath = k8sDelegateTaskParams.getKubeconfigPath();
    workingDirectory = k8sDelegateTaskParams.getWorkingDirectory();
    kustomizeBinaryPath = k8sDelegateTaskParams.getKustomizeBinaryPath();
    gcpKeyFilePath = k8sDelegateTaskParams.getGcpKeyFilePath();
    helmPath = k8sDelegateTaskParams.getHelmPath();
    ocPath = k8sDelegateTaskParams.getOcPath();
  }

  public ServiceHookDTO(HelmInstallCommandRequestNG commandRequestNG) {
    kubectlPath = null;
    kubeconfigPath = commandRequestNG.getKubeConfigLocation();
    workingDirectory = commandRequestNG.getWorkingDir();
    kustomizeBinaryPath = null;
    gcpKeyFilePath = commandRequestNG.getGcpKeyPath();
    helmPath = null;
    ocPath = commandRequestNG.getOcPath();
  }
}
