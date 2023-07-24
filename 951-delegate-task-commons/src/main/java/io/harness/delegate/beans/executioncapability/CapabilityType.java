/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.beans.executioncapability;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.annotations.dev.TargetModule;

// ALWAYS_TRUE should not be a capability type. In this case, task validation should not even happen.
// But Validation needs to happen at delegate as its part of Handshake between Delegate and manager,
// in order for delegate to acquire a task.
// May be changed later

@CodePulse(
    module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_COMMON_STEPS})
@TargetModule(HarnessModule._955_DELEGATE_BEANS)
@OwnedBy(HarnessTeam.DEL)
public enum CapabilityType {
  SOCKET,
  SOCKET_BULK_OR,
  ALWAYS_TRUE,
  PROCESS_EXECUTOR,
  AWS_REGION,
  SYSTEM_ENV,
  HTTP,
  HELM_INSTALL,
  CHART_MUSEUM,
  ALWAYS_FALSE,
  SMTP,
  WINRM_HOST_CONNECTION,
  SSH_HOST_CONNECTION,
  SFTP,
  PCF_AUTO_SCALAR,
  PCF_CONNECTIVITY,
  PCF_INSTALL,
  POWERSHELL,
  HELM_COMMAND,
  CLUSTER_MASTER_URL,
  SHELL_CONNECTION,
  GIT_CONNECTION,
  KUSTOMIZE,
  SMB,
  SELECTORS,
  GIT_CONNECTION_NG,
  GIT_INSTALLATION,
  LITE_ENGINE,
  CI_VM,
  ARTIFACTORY,
  SERVERLESS_INSTALL,
  OCI_HELM_REPO,
  AWS_CLI_INSTALL,
  NG_WINRM_HOST_CONNECTION,
  NG_SSH_HOST_CONNECTION,
  AWS_SAM_INSTALL
}
