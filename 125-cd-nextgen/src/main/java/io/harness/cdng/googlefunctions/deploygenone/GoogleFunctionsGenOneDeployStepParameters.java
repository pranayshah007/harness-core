/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.googlefunctions.deploygenone;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.googlefunctions.GoogleFunctionsSpecParameters;
import io.harness.plancreator.steps.TaskSelectorYaml;
import io.harness.pms.yaml.ParameterField;

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
@TypeAlias("googleFunctionsGenOneDeployStepParameters")
@RecasterAlias("io.harness.cdng.googlefunctions.deploygenone.GoogleFunctionsGenOneDeployStepParameters")
public class GoogleFunctionsGenOneDeployStepParameters
    extends GoogleFunctionsGenOneDeployBaseStepInfo implements GoogleFunctionsSpecParameters {
  @Builder(builderMethodName = "infoBuilder")
  public GoogleFunctionsGenOneDeployStepParameters(
      ParameterField<List<TaskSelectorYaml>> delegateSelectors, ParameterField<String> updateFieldMask) {
    super(delegateSelectors, updateFieldMask);
  }
}
