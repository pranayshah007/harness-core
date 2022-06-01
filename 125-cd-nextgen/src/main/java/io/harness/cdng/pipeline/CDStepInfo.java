/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.pipeline;

import io.harness.advisers.rollback.OnFailRollbackParameters;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.helm.HelmDeployStepInfo;
import io.harness.cdng.helm.rollback.HelmRollbackStepInfo;
import io.harness.cdng.k8s.K8sApplyStepInfo;
import io.harness.cdng.k8s.K8sBGSwapServicesStepInfo;
import io.harness.cdng.k8s.K8sBlueGreenStepInfo;
import io.harness.cdng.k8s.K8sCanaryDeleteStepInfo;
import io.harness.cdng.k8s.K8sCanaryStepInfo;
import io.harness.cdng.k8s.K8sDeleteStepInfo;
import io.harness.cdng.k8s.K8sRollingRollbackStepInfo;
import io.harness.cdng.k8s.K8sRollingStepInfo;
import io.harness.cdng.k8s.K8sScaleStepInfo;
import io.harness.cdng.pipeline.steps.CdStepParametersUtils;
import io.harness.cdng.provision.cloudformation.CloudformationCreateStackStepInfo;
import io.harness.cdng.provision.cloudformation.CloudformationDeleteStackStepInfo;
import io.harness.cdng.provision.cloudformation.CloudformationRollbackStepInfo;
import io.harness.cdng.provision.terraform.TerraformApplyStepInfo;
import io.harness.cdng.provision.terraform.TerraformDestroyStepInfo;
import io.harness.cdng.provision.terraform.TerraformPlanStepInfo;
import io.harness.cdng.provision.terraform.steps.rolllback.TerraformRollbackStepInfo;
import io.harness.cdng.serverless.ServerlessAwsLambdaDeployStepInfo;
import io.harness.cdng.serverless.ServerlessAwsLambdaRollbackStepInfo;
import io.harness.plancreator.steps.common.StepElementParameters.StepElementParametersBuilder;
import io.harness.plancreator.steps.common.WithDelegateSelector;
import io.harness.plancreator.steps.common.WithStepElementParameters;
import io.harness.pms.sdk.core.plan.creation.beans.PlanCreationContext;
import io.harness.pms.sdk.core.steps.io.StepParameters;
import io.harness.steps.StepUtils;
import io.harness.yaml.core.StepSpecType;

import io.swagger.annotations.ApiModel;

@ApiModel(
    subTypes = {K8sApplyStepInfo.class, K8sBlueGreenStepInfo.class, K8sCanaryStepInfo.class, K8sRollingStepInfo.class,
        K8sRollingRollbackStepInfo.class, K8sScaleStepInfo.class, K8sDeleteStepInfo.class,
        K8sBGSwapServicesStepInfo.class, K8sCanaryDeleteStepInfo.class, TerraformApplyStepInfo.class,
        TerraformPlanStepInfo.class, TerraformDestroyStepInfo.class, TerraformRollbackStepInfo.class,
        HelmDeployStepInfo.class, HelmRollbackStepInfo.class, CloudformationCreateStackStepInfo.class,
        CloudformationDeleteStackStepInfo.class, ServerlessAwsLambdaDeployStepInfo.class,
        ServerlessAwsLambdaRollbackStepInfo.class, CloudformationRollbackStepInfo.class})

@OwnedBy(HarnessTeam.CDC)
public interface CDStepInfo extends StepSpecType, WithStepElementParameters, WithDelegateSelector {
  default StepParameters getStepParameters(
      CdAbstractStepNode stepElementConfig, OnFailRollbackParameters failRollbackParameters, PlanCreationContext ctx) {
    StepElementParametersBuilder stepParametersBuilder =
        CdStepParametersUtils.getStepParameters(stepElementConfig, failRollbackParameters);
    StepUtils.appendDelegateSelectorsToSpecParameters(stepElementConfig.getStepSpecType(), ctx);
    stepParametersBuilder.spec(getSpecParameters());
    return stepParametersBuilder.build();
  }
}
