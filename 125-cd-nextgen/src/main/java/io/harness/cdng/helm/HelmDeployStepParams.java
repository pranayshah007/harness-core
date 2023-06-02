/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.helm;

import io.harness.plancreator.steps.TaskSelectorYaml;
import io.harness.pms.yaml.ParameterField;

import software.wings.beans.command.HelmDummyCommandUnitConstants;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.Arrays;
import java.util.List;
import javax.annotation.Nonnull;
import lombok.Builder;

public class HelmDeployStepParams extends HelmDeployBaseStepInfo implements HelmSpecParameters {
  @Builder(builderMethodName = "infoBuilder")
  public HelmDeployStepParams(ParameterField<List<TaskSelectorYaml>> delegateSelectors, String helmDeployFqn,
      ParameterField<Boolean> ignoreReleaseHistFailStatus, ParameterField<Boolean> skipSteadyStateCheck) {
    super(delegateSelectors, helmDeployFqn, ignoreReleaseHistFailStatus, skipSteadyStateCheck);
  }

  @Nonnull
  @Override
  @JsonIgnore
  public List<String> getCommandUnits() {
    return Arrays.asList(HelmDummyCommandUnitConstants.FetchFiles, HelmDummyCommandUnitConstants.Init,
        HelmDummyCommandUnitConstants.Prepare, HelmDummyCommandUnitConstants.InstallUpgrade,
        HelmDummyCommandUnitConstants.WaitForSteadyState, HelmDummyCommandUnitConstants.WrapUp);
  }
}
