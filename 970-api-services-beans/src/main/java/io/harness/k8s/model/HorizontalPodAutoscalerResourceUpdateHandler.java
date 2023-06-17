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

import io.kubernetes.client.openapi.models.V1HorizontalPodAutoscaler;
import io.kubernetes.client.openapi.models.V2HorizontalPodAutoscaler;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;

@Builder
@Slf4j
@OwnedBy(CDP)
public class HorizontalPodAutoscalerResourceUpdateHandler implements KubernetesResourceUpdateHandler {
  private static final String MISSING_HPA_SPEC_MSG = "Horizontal Pod Autoscaler resource does not have spec";
  private static final String MISSING_HPA_SCALE_TARGET_REF_SPEC_MSG =
      "Horizontal Pod Autoscaler ScaleTargetRef does not have spec";

  @Override
  public void onNameChange(
      KubernetesResource kubernetesResource, KubernetesResourceUpdateContext kubernetesResourceUpdateContext) {
    if (!kubernetesResource.isDirectApply()) {
      if (kubernetesResource.getK8sResource() instanceof V1HorizontalPodAutoscaler) {
        V1HorizontalPodAutoscaler v1HorizontalPodAutoscaler =
            (V1HorizontalPodAutoscaler) kubernetesResource.getK8sResource();
        notNullCheck(MISSING_HPA_SPEC_MSG, v1HorizontalPodAutoscaler.getSpec());
        notNullCheck(MISSING_HPA_SCALE_TARGET_REF_SPEC_MSG, v1HorizontalPodAutoscaler.getSpec().getScaleTargetRef());
        if (v1HorizontalPodAutoscaler.getSpec().getScaleTargetRef().getKind().equals(
                kubernetesResourceUpdateContext.getKind().name())
            && v1HorizontalPodAutoscaler.getSpec().getScaleTargetRef().getName().equals(
                kubernetesResourceUpdateContext.getOldName())) {
          v1HorizontalPodAutoscaler.getSpec().getScaleTargetRef().setName(kubernetesResourceUpdateContext.getNewName());
          kubernetesResource.saveResourceSpec(v1HorizontalPodAutoscaler);
          kubernetesResourceUpdateContext.getK8sRequestHandlerContext().getResourcesForNameUpdate().add(
              kubernetesResource);
        }
      } else {
        V2HorizontalPodAutoscaler v2HorizontalPodAutoscaler =
            (V2HorizontalPodAutoscaler) kubernetesResource.getK8sResource();
        notNullCheck(MISSING_HPA_SPEC_MSG, v2HorizontalPodAutoscaler.getSpec());
        notNullCheck(MISSING_HPA_SCALE_TARGET_REF_SPEC_MSG, v2HorizontalPodAutoscaler.getSpec().getScaleTargetRef());
        if (v2HorizontalPodAutoscaler.getSpec().getScaleTargetRef().getKind().equals(
                kubernetesResourceUpdateContext.getKind().name())
            && v2HorizontalPodAutoscaler.getSpec().getScaleTargetRef().getName().equals(
                kubernetesResourceUpdateContext.getOldName())) {
          v2HorizontalPodAutoscaler.getSpec().getScaleTargetRef().setName(kubernetesResourceUpdateContext.getNewName());
          kubernetesResource.saveResourceSpec(v2HorizontalPodAutoscaler);
          kubernetesResourceUpdateContext.getK8sRequestHandlerContext().getResourcesForNameUpdate().add(
              kubernetesResource);
        }
      }
    }
  }

  @Override
  public void onSelectorsChange(
      KubernetesResource kubernetesResource, KubernetesResourceUpdateContext kubernetesResourceUpdateContext) {
    // currently not implemented as it is not needed
  }
}
