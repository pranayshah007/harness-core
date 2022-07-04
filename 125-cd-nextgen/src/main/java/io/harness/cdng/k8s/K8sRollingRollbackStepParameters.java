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
import io.harness.k8s.K8sCommandUnitConstants;
import io.harness.plancreator.steps.TaskSelectorYaml;
import io.harness.pms.yaml.ParameterField;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.Arrays;
import java.util.List;
import javax.annotation.Nonnull;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.TypeAlias;

@OwnedBy(CDP)
@Data
@NoArgsConstructor
@TypeAlias("k8sRollingRollbackStepParameters")
@RecasterAlias("io.harness.cdng.k8s.K8sRollingRollbackStepParameters")
public class K8sRollingRollbackStepParameters extends K8sRollingRollbackBaseStepInfo implements K8sSpecParameters {
  @Builder(builderMethodName = "infoBuilder")
  public K8sRollingRollbackStepParameters(ParameterField<Boolean> skipDryRun,
      ParameterField<List<TaskSelectorYaml>> delegateSelectors, String rollingStepFqn) {
    super(skipDryRun, delegateSelectors, rollingStepFqn);
  }

  @Nonnull
  @Override
  @JsonIgnore
  public List<String> getCommandUnits() {
    return Arrays.asList(
        K8sCommandUnitConstants.Init, K8sCommandUnitConstants.Rollback, K8sCommandUnitConstants.WaitForSteadyState);
  }

  @Nonnull
  @Override
  @JsonIgnore
  public List<String> getCommandUnits(boolean isPruningEnabled) {
    if (isPruningEnabled) {
      return Arrays.asList(K8sCommandUnitConstants.Init, K8sCommandUnitConstants.RecreatePrunedResource,
          K8sCommandUnitConstants.DeleteFailedReleaseResources, K8sCommandUnitConstants.Rollback,
          K8sCommandUnitConstants.WaitForSteadyState);
    }
    return getCommandUnits();
  }
}
