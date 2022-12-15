/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.service.step;

import software.wings.yaml.workflow.StepYaml;

import com.google.inject.Inject;
import lombok.extern.slf4j.Slf4j;

@Slf4j
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
  @Inject EmptyStepMapperImpl emptyStepMapper;
  @Inject K8sScaleStepMapperImpl k8sScaleStepMapper;
  @Inject JenkinsStepMapperImpl jenkinsStepMapper;
  @Inject K8sSwapServiceSelectorsStepMapperImpl k8sSwapServiceSelectorsStepMapper;
  @Inject K8sBlueGreenDeployStepMapperImpl k8sBlueGreenDeployStepMapper;
  @Inject JiraCreateUpdateStepMapperImpl jiraCreateUpdateStepMapper;
  @Inject UnsupportedStepMapperImpl unsupportedStepMapper;

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
      case "K8S_SCALE":
        return k8sScaleStepMapper;
      case "EMAIL":
        return emailStepMapper;
      case "K8S_DEPLOYMENT_ROLLING_ROLLBACK":
        return k8sRollingRollbackStepMapper;
      case "K8S_CANARY_DEPLOY":
        return k8sCanaryDeployStepMapper;
      case "JENKINS":
        return jenkinsStepMapper;
      case "KUBERNETES_SWAP_SERVICE_SELECTORS":
        return k8sSwapServiceSelectorsStepMapper;
      case "K8S_BLUE_GREEN_DEPLOY":
        return k8sBlueGreenDeployStepMapper;
      case "JIRA_CREATE_UPDATE":
        return jiraCreateUpdateStepMapper;
      case "ARTIFACT_COLLECTION":
      case "ARTIFACT_CHECK":
        return emptyStepMapper;
      default:
        return unsupportedStepMapper;
    }
  }

  public boolean areSimilar(StepYaml stepYaml1, StepYaml stepYaml2) {
    if (!stepYaml1.getType().equals(stepYaml2.getType())) {
      return false;
    }
    try {
      return getStepMapper(stepYaml1.getType()).areSimilar(stepYaml1, stepYaml2);
    } catch (Exception e) {
      log.error("There was an error with finding similar steps", e);
      return false;
    }
  }
}
