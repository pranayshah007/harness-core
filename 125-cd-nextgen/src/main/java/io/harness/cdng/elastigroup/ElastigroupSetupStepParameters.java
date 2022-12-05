/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.elastigroup;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.elastigroup.ElastigroupCommandUnitConstants;
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
@TypeAlias("elastigroupSetupStepParameters")
@RecasterAlias("io.harness.cdng.elastigroup.ElastigroupSetupStepParameters")
public class ElastigroupSetupStepParameters extends ElastigroupSetupBaseStepInfo implements ElastigroupSpecParameters {
  @Builder(builderMethodName = "infoBuilder")
  public ElastigroupSetupStepParameters(ParameterField<List<TaskSelectorYaml>> delegateSelectors,
      ParameterField<String> name, ElastigroupInstances instances) {
    super(delegateSelectors, name, instances);
  }

  public List<String> getCommandUnits() {
    return Arrays.asList(ElastigroupCommandUnitConstants.fetchStartupScript.toString());
  }
}
