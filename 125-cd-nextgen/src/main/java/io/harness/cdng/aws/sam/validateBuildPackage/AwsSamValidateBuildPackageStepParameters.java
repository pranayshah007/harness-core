/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.aws.sam.validateBuildPackage;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.aws.sam.command.AwsSamCommandUnitConstants;
import io.harness.cdng.aws.sam.AwsSamSpecParameters;
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
@TypeAlias("awsSamValidateBuildPackageStepParameters")
@RecasterAlias("io.harness.cdng.aws.sam.validateBuildPackage.AwsSamValidateBuildPackageStepParameters")
public class AwsSamValidateBuildPackageStepParameters
    extends AwsSamValidateBuildPackageBaseStepInfo implements AwsSamSpecParameters {
  @Builder(builderMethodName = "infoBuilder")
  public AwsSamValidateBuildPackageStepParameters(ParameterField<List<TaskSelectorYaml>> delegateSelectors,
      ParameterField<String> validateCommandOptions, ParameterField<String> buildCommandOptions,
      ParameterField<String> packageCommandOptions) {
    super(delegateSelectors, validateCommandOptions, buildCommandOptions, packageCommandOptions);
  }

  public List<String> getCommandUnits() {
    return Arrays.asList(AwsSamCommandUnitConstants.validate.toString(), AwsSamCommandUnitConstants.build.toString(),
        AwsSamCommandUnitConstants.Package.toString());
  }
}
