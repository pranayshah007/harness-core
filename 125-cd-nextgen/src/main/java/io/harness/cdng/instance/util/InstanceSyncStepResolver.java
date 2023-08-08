/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.instance.util;
import static io.harness.annotations.dev.HarnessTeam.CDP;

import static java.util.Objects.nonNull;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.cdng.aws.asg.AsgBlueGreenDeployStep;
import io.harness.cdng.aws.asg.AsgBlueGreenRollbackStep;
import io.harness.cdng.aws.asg.AsgBlueGreenSwapServiceStep;
import io.harness.cdng.aws.asg.AsgCanaryDeleteStep;
import io.harness.cdng.aws.asg.AsgCanaryDeployStep;
import io.harness.cdng.aws.asg.AsgRollingDeployStep;
import io.harness.cdng.aws.asg.AsgRollingRollbackStep;
import io.harness.cdng.aws.lambda.deploy.AwsLambdaDeployStep;
import io.harness.cdng.aws.lambda.rollback.AwsLambdaRollbackStep;
import io.harness.cdng.aws.sam.AwsSamDeployStep;
import io.harness.cdng.azure.webapp.AzureWebAppRollbackStep;
import io.harness.cdng.azure.webapp.AzureWebAppSlotDeploymentStep;
import io.harness.cdng.customDeployment.FetchInstanceScriptStep;
import io.harness.cdng.ecs.EcsBlueGreenRollbackStep;
import io.harness.cdng.ecs.EcsBlueGreenSwapTargetGroupsStep;
import io.harness.cdng.ecs.EcsCanaryDeployStep;
import io.harness.cdng.ecs.EcsRollingDeployStep;
import io.harness.cdng.ecs.EcsRollingRollbackStep;
import io.harness.cdng.elastigroup.ElastigroupBGStageSetupStep;
import io.harness.cdng.elastigroup.ElastigroupSwapRouteStep;
import io.harness.cdng.elastigroup.deploy.ElastigroupDeployStep;
import io.harness.cdng.elastigroup.rollback.ElastigroupRollbackStep;
import io.harness.cdng.googlefunctions.deploy.GoogleFunctionsDeployStep;
import io.harness.cdng.googlefunctions.deployWithoutTraffic.GoogleFunctionsDeployWithoutTrafficStep;
import io.harness.cdng.googlefunctions.deploygenone.GoogleFunctionsGenOneDeployStep;
import io.harness.cdng.googlefunctions.rollback.GoogleFunctionsRollbackStep;
import io.harness.cdng.googlefunctions.rollbackgenone.GoogleFunctionsGenOneRollbackStep;
import io.harness.cdng.googlefunctions.trafficShift.GoogleFunctionsTrafficShiftStep;
import io.harness.cdng.helm.HelmDeployStep;
import io.harness.cdng.helm.HelmRollbackStep;
import io.harness.cdng.k8s.K8sBlueGreenStep;
import io.harness.cdng.k8s.K8sCanaryStep;
import io.harness.cdng.k8s.K8sRollingRollbackStep;
import io.harness.cdng.k8s.K8sRollingStep;
import io.harness.cdng.serverless.ServerlessAwsLambdaDeployStep;
import io.harness.cdng.serverless.container.steps.ServerlessAwsLambdaDeployV2Step;
import io.harness.cdng.ssh.CommandStep;
import io.harness.cdng.tas.TasAppResizeStep;
import io.harness.cdng.tas.TasRollbackStep;
import io.harness.cdng.tas.TasRollingDeployStep;
import io.harness.cdng.tas.TasRollingRollbackStep;
import io.harness.cdng.tas.TasSwapRollbackStep;
import io.harness.cdng.tas.TasSwapRoutesStep;
import io.harness.pms.contracts.steps.StepType;

import com.google.common.collect.Sets;
import java.util.Collections;
import java.util.Set;
import lombok.experimental.UtilityClass;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_ECS})
@OwnedBy(CDP)
@UtilityClass
public class InstanceSyncStepResolver {
  public final Set<String> INSTANCE_SYN_STEP_TYPES = Collections.unmodifiableSet(Sets.newHashSet(
      K8sRollingStep.STEP_TYPE.getType(), K8sCanaryStep.STEP_TYPE.getType(), K8sBlueGreenStep.STEP_TYPE.getType(),
      K8sRollingRollbackStep.STEP_TYPE.getType(), HelmDeployStep.STEP_TYPE.getType(),
      HelmRollbackStep.STEP_TYPE.getType(), ServerlessAwsLambdaDeployStep.STEP_TYPE.getType(),
      AzureWebAppSlotDeploymentStep.STEP_TYPE.getType(), AzureWebAppRollbackStep.STEP_TYPE.getType(),
      CommandStep.STEP_TYPE.getType(), EcsRollingDeployStep.STEP_TYPE.getType(),
      EcsRollingRollbackStep.STEP_TYPE.getType(), EcsCanaryDeployStep.STEP_TYPE.getType(),
      EcsBlueGreenSwapTargetGroupsStep.STEP_TYPE.getType(), EcsBlueGreenRollbackStep.STEP_TYPE.getType(),
      FetchInstanceScriptStep.STEP_TYPE.getType(), ElastigroupDeployStep.STEP_TYPE.getType(),
      TasAppResizeStep.STEP_TYPE.getType(), TasSwapRoutesStep.STEP_TYPE.getType(), TasRollbackStep.STEP_TYPE.getType(),
      TasSwapRollbackStep.STEP_TYPE.getType(), ElastigroupBGStageSetupStep.STEP_TYPE.getType(),
      ElastigroupSwapRouteStep.STEP_TYPE.getType(), ElastigroupRollbackStep.STEP_TYPE.getType(),
      TasRollingDeployStep.STEP_TYPE.getType(), TasRollingRollbackStep.STEP_TYPE.getType(),
      AsgRollingDeployStep.STEP_TYPE.getType(), AsgCanaryDeployStep.STEP_TYPE.getType(),
      AsgCanaryDeleteStep.STEP_TYPE.getType(), AsgRollingRollbackStep.STEP_TYPE.getType(),
      AsgBlueGreenDeployStep.STEP_TYPE.getType(), AsgBlueGreenSwapServiceStep.STEP_TYPE.getType(),
      AsgBlueGreenRollbackStep.STEP_TYPE.getType(), GoogleFunctionsDeployStep.STEP_TYPE.getType(),
      GoogleFunctionsRollbackStep.STEP_TYPE.getType(), GoogleFunctionsDeployWithoutTrafficStep.STEP_TYPE.getType(),
      GoogleFunctionsTrafficShiftStep.STEP_TYPE.getType(), AwsLambdaDeployStep.STEP_TYPE.getType(),
      AwsLambdaRollbackStep.STEP_TYPE.getType(), GoogleFunctionsGenOneDeployStep.STEP_TYPE.getType(),
      GoogleFunctionsGenOneRollbackStep.STEP_TYPE.getType(), AwsSamDeployStep.STEP_TYPE.getType(),
      ServerlessAwsLambdaDeployV2Step.STEP_TYPE.getType()));

  public boolean shouldRunInstanceSync(StepType stepType) {
    return nonNull(stepType) && INSTANCE_SYN_STEP_TYPES.contains(stepType.getType());
  }
}
