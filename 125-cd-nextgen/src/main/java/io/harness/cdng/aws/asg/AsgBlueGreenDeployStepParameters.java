/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.aws.asg;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.aws.asg.AsgCommandUnitConstants;
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
@TypeAlias("asgBlueGreenDeployStepParameters")
@RecasterAlias("io.harness.cdng.aws.asg.AsgBlueGreenDeployStepParameters")
public class AsgBlueGreenDeployStepParameters extends AsgBlueGreenDeployBaseStepInfo implements AsgSpecParameters {
  @Builder(builderMethodName = "infoBuilder")
  public AsgBlueGreenDeployStepParameters(ParameterField<String> loadBalancer, ParameterField<String> prodListener,
      ParameterField<String> prodListenerRuleArn, ParameterField<String> stageListener,
      ParameterField<String> stageListenerRuleArn, ParameterField<List<TaskSelectorYaml>> delegateSelectors,
      ParameterField<Boolean> useAlreadyRunningInstances) {
    super(loadBalancer, prodListener, prodListenerRuleArn, stageListener, stageListenerRuleArn, delegateSelectors,
        useAlreadyRunningInstances);
  }

  public List<String> getCommandUnits() {
    return Arrays.asList(AsgCommandUnitConstants.fetchManifests.toString(),
        AsgCommandUnitConstants.prepareRollbackData.toString(), AsgCommandUnitConstants.deploy.toString());
  }
}
