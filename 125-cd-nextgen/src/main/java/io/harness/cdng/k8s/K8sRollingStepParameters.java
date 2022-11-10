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

import java.util.List;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.data.annotation.TypeAlias;

@OwnedBy(CDP)
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TypeAlias("k8sRollingStepParameters")
@RecasterAlias("io.harness.cdng.k8s.K8sRollingStepParameters")
public class K8sRollingStepParameters extends K8sRollingBaseStepInfo implements K8sSpecParameters {
  @Builder(builderMethodName = "infoBuilder")
  public K8sRollingStepParameters(ParameterField<Boolean> skipDryRun, ParameterField<Boolean> pruningEnabled,
      ParameterField<List<TaskSelectorYaml>> delegateSelectors, String canaryStepFqn) {
    super(skipDryRun, pruningEnabled, delegateSelectors, canaryStepFqn);
  }

  @NotNull
  @Override
  public List<String> getCommandUnits() {
    List<String> commandUnits = K8sSpecParameters.super.getCommandUnits();
    boolean isPruningEnabled =
        CDStepHelper.getParameterFieldBooleanValue(pruningEnabled, K8sRollingBaseStepInfoKeys.pruningEnabled,
            String.format("%s step", ExecutionNodeType.K8S_ROLLING.getYamlType()));
    if (isPruningEnabled) {
      commandUnits.add(K8sCommandUnitConstants.Prune);
    }
    return commandUnits;
  }
}
