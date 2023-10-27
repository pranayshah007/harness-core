/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.ecs;
import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.delegate.task.ecs.EcsResizeStrategy;
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

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_ECS})
@OwnedBy(HarnessTeam.CDP)
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TypeAlias("ecsServiceSetupStepParameters")
@RecasterAlias("io.harness.cdng.ecs.EcsServiceSetupStepParameters")
public class EcsServiceSetupStepParameters extends EcsServiceSetupBaseStepInfo implements EcsSpecParameters {
  @Builder(builderMethodName = "infoBuilder")
  public EcsServiceSetupStepParameters(ParameterField<List<TaskSelectorYaml>> delegateSelectors,
      ParameterField<Boolean> sameAsAlreadyRunningInstances, EcsResizeStrategy resizeStrategy) {
    super(delegateSelectors, sameAsAlreadyRunningInstances, resizeStrategy);
  }
  public List<String> getCommandUnits() {
    return Arrays.asList(EcsCommandUnitConstants.fetchManifests.toString(),
        EcsCommandUnitConstants.prepareRollbackData.toString(), EcsCommandUnitConstants.serviceSetup.toString());
  }
}
