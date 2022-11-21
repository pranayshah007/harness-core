/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.service.step;

import io.harness.exception.InvalidRequestException;

import com.google.inject.Inject;

public class StepMapperFactory {
  @Inject ShellScriptStepMapperImpl shellScriptStepMapper;
  @Inject K8sRollingStepMapperImpl k8sRollingStepMapper;
  @Inject HttpStepMapperImpl httpStepMapper;
  @Inject ApprovalStepMapperImpl approvalStepMapper;
  @Inject BarrierStepMapperImpl barrierStepMapper;
  @Inject K8sApplyStepMapperImpl k8sApplyStepMapper;
  @Inject K8sDeleteStepMapperImpl k8sDeleteStepMapper;
  @Inject EmailStepMapperImpl emailStepMapper;
  @Inject K8sRollingRollbackStepMapperImpl k8sRollingRollbackStepMapper;
  @Inject K8sCanaryDeployStepMapperImpl k8sCanaryDeployStepMapper;

  public StepMapper getStepMapper(String stepType) {
    switch (stepType) {
      case "SHELL_SCRIPT":
        return shellScriptStepMapper;
      case "K8S_DEPLOYMENT_ROLLING":
        return k8sRollingStepMapper;
      case "HTTP":
        return httpStepMapper;
      case "APPROVAL":
        return approvalStepMapper;
      case "BARRIER":
        return barrierStepMapper;
      case "K8S_DELETE":
        return k8sDeleteStepMapper;
      case "K8S_APPLY":
        return k8sApplyStepMapper;
      case "EMAIL":
        return emailStepMapper;
      case "K8S_DEPLOYMENT_ROLLING_ROLLBACK":
        return k8sRollingRollbackStepMapper;
      case "K8S_CANARY_DEPLOY":
        return k8sCanaryDeployStepMapper;
      default:
        throw new InvalidRequestException("Unsupported step");
    }
  }
}
