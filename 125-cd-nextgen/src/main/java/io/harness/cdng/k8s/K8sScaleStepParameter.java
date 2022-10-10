/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.k8s;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.CDStepHelper;
import io.harness.executions.steps.ExecutionNodeType;
import io.harness.k8s.K8sCommandUnitConstants;
import io.harness.plancreator.steps.TaskSelectorYaml;
import io.harness.pms.yaml.ParameterField;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.Arrays;
import java.util.List;
import javax.annotation.Nonnull;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.TypeAlias;

@OwnedBy(CDP)
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TypeAlias("K8sScaleStepParameter")
@RecasterAlias("io.harness.cdng.k8s.K8sScaleStepParameter")
public class K8sScaleStepParameter extends K8sScaleBaseStepInfo implements K8sSpecParameters {
  @Builder(builderMethodName = "infoBuilder")
  public K8sScaleStepParameter(ParameterField<Boolean> skipDryRun, ParameterField<Boolean> skipSteadyStateCheck,
      InstanceSelectionWrapper instanceSelection, ParameterField<String> workload,
      ParameterField<List<TaskSelectorYaml>> delegateSelectors) {
    super(instanceSelection, workload, skipDryRun, skipSteadyStateCheck, delegateSelectors);
  }

  @Nonnull
  @Override
  @JsonIgnore
  public List<String> getCommandUnits() {
    if (!ParameterField.isNull(skipSteadyStateCheck)
        && CDStepHelper.getParameterFieldBooleanValue(skipSteadyStateCheck,
            K8sScaleBaseStepInfoKeys.skipSteadyStateCheck,
            String.format("%s step", ExecutionNodeType.K8S_SCALE.getYamlType()))) {
      return Arrays.asList(K8sCommandUnitConstants.Init, K8sCommandUnitConstants.Scale, K8sCommandUnitConstants.WrapUp);
    } else {
      return Arrays.asList(K8sCommandUnitConstants.Init, K8sCommandUnitConstants.Scale,
          K8sCommandUnitConstants.WaitForSteadyState, K8sCommandUnitConstants.WrapUp);
    }
  }
}
