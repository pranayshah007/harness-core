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
import io.harness.delegate.task.k8s.DeleteResourcesType;
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
@TypeAlias("k8sDeleteStepParameters")
@RecasterAlias("io.harness.cdng.k8s.K8sDeleteStepParameters")
public class K8sDeleteStepParameters extends K8sDeleteBaseStepInfo implements K8sSpecParameters {
  @Builder(builderMethodName = "infoBuilder")
  public K8sDeleteStepParameters(DeleteResourcesWrapper deleteResources, ParameterField<Boolean> skipDryRun,
      ParameterField<List<TaskSelectorYaml>> delegateSelectors) {
    super(deleteResources, skipDryRun, delegateSelectors);
  }

  @Nonnull
  @JsonIgnore
  @Override
  public List<String> getCommandUnits() {
    if (deleteResources != null && deleteResources.getType() == DeleteResourcesType.ManifestPath) {
      return Arrays.asList(
          K8sCommandUnitConstants.FetchFiles, K8sCommandUnitConstants.Init, K8sCommandUnitConstants.Delete);
    }
    return Arrays.asList(K8sCommandUnitConstants.Init, K8sCommandUnitConstants.Delete);
  }
}
