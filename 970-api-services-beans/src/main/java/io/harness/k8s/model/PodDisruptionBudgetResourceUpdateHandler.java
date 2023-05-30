/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.k8s.model;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.validation.Validator.notNullCheck;

import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.EmptyPredicate;

import io.kubernetes.client.openapi.models.V1PodDisruptionBudget;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;

@Builder
@Slf4j
@OwnedBy(CDP)
public class PodDisruptionBudgetResourceUpdateHandler implements KubernetesResourceUpdateHandler {
  private static final String MISSING_PDB_SPEC_MSG = "Pod Disruption Budget resource does not have spec";
  private static final String MISSING_PDB_SELECTORS__MSG = "Pod Disruption Budget resource does not have selectors";

  @Override
  public void onNameChange(
      KubernetesResource kubernetesResource, KubernetesResourceUpdateContext kubernetesResourceUpdateContext) {
    // currently not implemented as it is not needed
  }

  @Override
  public void onSelectorsChange(
      KubernetesResource kubernetesResource, KubernetesResourceUpdateContext kubernetesResourceUpdateContext) {
    if (!kubernetesResource.isDirectApply()) {
      V1PodDisruptionBudget v1PodDisruptionBudget = (V1PodDisruptionBudget) kubernetesResource.getK8sResource();
      notNullCheck(MISSING_PDB_SPEC_MSG, v1PodDisruptionBudget.getSpec());
      notNullCheck(MISSING_PDB_SELECTORS__MSG, v1PodDisruptionBudget.getSpec().getSelector());

      if (EmptyPredicate.isNotEmpty(v1PodDisruptionBudget.getSpec().getSelector().getMatchLabels())
          && v1PodDisruptionBudget.getSpec().getSelector().getMatchLabels().equals(
              kubernetesResourceUpdateContext.getOldSelectors())) {
        kubernetesResource.addLabelsInResourceSelector(kubernetesResourceUpdateContext.newSelectors,
            kubernetesResourceUpdateContext.getK8sRequestHandlerContext());
        kubernetesResourceUpdateContext.getK8sRequestHandlerContext().getResourcesForNameUpdate().add(
            kubernetesResource);
      }
    }
  }
}
