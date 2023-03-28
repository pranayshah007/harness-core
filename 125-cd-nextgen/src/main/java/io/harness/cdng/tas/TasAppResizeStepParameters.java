/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.tas;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.plancreator.steps.TaskSelectorYaml;
import io.harness.plancreator.steps.common.SpecParameters;
import io.harness.pms.yaml.ParameterField;

import java.util.List;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.TypeAlias;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@OwnedBy(HarnessTeam.CDP)
@TypeAlias("TasAppResizeStepParameters")
@RecasterAlias("io.harness.cdng.tas.TasAppResizeStepParameters")
public class TasAppResizeStepParameters extends TasAppResizeBaseStepInfo implements SpecParameters {
  @Builder(builderMethodName = "infoBuilder")
  public TasAppResizeStepParameters(ParameterField<List<TaskSelectorYaml>> delegateSelectors,
      TasInstanceSelectionWrapper newAppInstances, TasInstanceSelectionWrapper oldAppInstances,
      ParameterField<Boolean> ignoreInstanceCountManifest, String tasBGAppSetupFqn, String tasBasicAppSetupFqn,
      String tasCanaryAppSetupFqn) {
    super(newAppInstances, oldAppInstances, ignoreInstanceCountManifest, delegateSelectors, tasBGAppSetupFqn,
        tasBasicAppSetupFqn, tasCanaryAppSetupFqn);
  }
}