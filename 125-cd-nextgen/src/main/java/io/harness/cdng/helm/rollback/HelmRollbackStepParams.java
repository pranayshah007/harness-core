/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.helm.rollback;

import io.harness.cdng.helm.HelmSpecParameters;
import io.harness.plancreator.steps.TaskSelectorYaml;
import io.harness.pms.yaml.ParameterField;

import software.wings.beans.command.HelmDummyCommandUnitConstants;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.Arrays;
import java.util.List;
import javax.annotation.Nonnull;
import lombok.Builder;

public class HelmRollbackStepParams extends HelmRollbackBaseStepInfo implements HelmSpecParameters {
  @Builder(builderMethodName = "infoBuilder")
  public HelmRollbackStepParams(ParameterField<List<TaskSelectorYaml>> delegateSelectors, String helmRollbackFqn,
      ParameterField<Boolean> skipSteadyStateCheck) {
    super(delegateSelectors, helmRollbackFqn, skipSteadyStateCheck);
  }

  @Nonnull
  @JsonIgnore
  public List<String> getCommandUnits() {
    return Arrays.asList(HelmDummyCommandUnitConstants.Init, HelmDummyCommandUnitConstants.Rollback,
        HelmDummyCommandUnitConstants.WaitForSteadyState);
  }
}
