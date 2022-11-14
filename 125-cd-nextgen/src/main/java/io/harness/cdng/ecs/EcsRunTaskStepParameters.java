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
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfigWrapper;
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
@TypeAlias("ecsRunTaskStepParameters")
@RecasterAlias("io.harness.cdng.ecs.EcsRunTaskStepParameters")
public class EcsRunTaskStepParameters extends EcsRunTaskBaseStepInfo implements EcsSpecParameters {
  @Builder(builderMethodName = "infoBuilder")
  public EcsRunTaskStepParameters(ParameterField<List<TaskSelectorYaml>> delegateSelectors,
      ParameterField<StoreConfigWrapper> taskDefinition, ParameterField<StoreConfigWrapper> runTaskRequestDefinition,
      ParameterField<Boolean> skipSteadyStateCheck) {
    super(delegateSelectors, taskDefinition, runTaskRequestDefinition, skipSteadyStateCheck);
  }

  public List<String> getCommandUnits() {
    return Arrays.asList(EcsCommandUnitConstants.fetchManifests.toString(), EcsCommandUnitConstants.runTask.toString());
  }
}
