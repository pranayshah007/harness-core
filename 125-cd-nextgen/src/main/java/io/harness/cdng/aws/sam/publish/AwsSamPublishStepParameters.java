/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.aws.sam.publish;

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
@TypeAlias("awsSamPublishStepParameters")
@RecasterAlias("io.harness.cdng.aws.sam.publish.AwsSamPublishStepParameters")
public class AwsSamPublishStepParameters extends AwsSamPublishBaseStepInfo implements AwsSamSpecParameters {
  @Builder(builderMethodName = "infoBuilder")
  public AwsSamPublishStepParameters(ParameterField<List<TaskSelectorYaml>> delegateSelectors,
      String awsSamValidateBuildPackageFnq, ParameterField<String> publishCommandOptions) {
    super(delegateSelectors, awsSamValidateBuildPackageFnq, publishCommandOptions);
  }

  public List<String> getCommandUnits() {
    return Arrays.asList(AwsSamCommandUnitConstants.setupDirectory.toString(),
        AwsSamCommandUnitConstants.configureCred.toString(), AwsSamCommandUnitConstants.publish.toString());
  }
}
