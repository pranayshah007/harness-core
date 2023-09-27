/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.ecs;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ecs.EcsCommandUnitConstants;
import io.harness.plancreator.steps.TaskSelectorYaml;
import io.harness.pms.yaml.ParameterField;

import java.util.Arrays;
import java.util.List;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.TypeAlias;

@OwnedBy(HarnessTeam.CDP)
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TypeAlias("ecsBlueGreenCreateServiceStepParameters")
@RecasterAlias("io.harness.cdng.ecs.EcsBlueGreenCreateServiceStepParameters")
public class EcsBlueGreenCreateServiceStepParameters
    extends EcsBlueGreenCreateServiceBaseStepInfo implements EcsSpecParameters {
  @Builder(builderMethodName = "infoBuilder")
  public EcsBlueGreenCreateServiceStepParameters(ParameterField<List<TaskSelectorYaml>> delegateSelectors,
      ParameterField<String> loadBalancer, ParameterField<String> prodListener,
      ParameterField<String> prodListenerRuleArn, ParameterField<String> stageListener,
      ParameterField<String> stageListenerRuleArn, ParameterField<Boolean> sameAsAlreadyRunningInstances,
      ParameterField<Boolean> enableAutoScalingInSwapStep) {
    super(delegateSelectors, loadBalancer, prodListener, prodListenerRuleArn, stageListener, stageListenerRuleArn,
        sameAsAlreadyRunningInstances, enableAutoScalingInSwapStep);
  }

  public List<String> getCommandUnits() {
    return Arrays.asList(EcsCommandUnitConstants.fetchManifests.toString(),
        EcsCommandUnitConstants.prepareRollbackData.toString(), EcsCommandUnitConstants.deploy.toString());
  }
}
