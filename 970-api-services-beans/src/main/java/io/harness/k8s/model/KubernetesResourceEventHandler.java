/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.k8s.model;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.k8s.model.Kind.HorizontalPodAutoscaler;
import static io.harness.k8s.model.Kind.NOOP;
import static io.harness.k8s.model.Kind.PodDisruptionBudget;

import io.harness.annotations.dev.OwnedBy;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(CDP)
@Slf4j
public class KubernetesResourceEventHandler {
  static Map<Kind, KubernetesResourceUpdateHandler> kubernetesResourceUpdateHandlers =
      Collections.unmodifiableMap(new HashMap() {
        {
          put(HorizontalPodAutoscaler, HorizontalPodAutoscalerResourceUpdateHandler.builder().build());
          put(PodDisruptionBudget, PodDisruptionBudgetResourceUpdateHandler.builder().build());
          put(NOOP, NoopResourceUpdateHandler.builder().build());
        }
      });

  public static void handleNameChange(KubernetesResourceUpdateContext kubernetesResourceUpdateContext) {
    handleChange(kubernetesResourceUpdateContext,
        resource
        -> getHandler(resource.getResourceId().getKind()).onNameChange(resource, kubernetesResourceUpdateContext));
  }

  public static void handleSelectorChange(KubernetesResourceUpdateContext kubernetesResourceUpdateContext) {
    handleChange(kubernetesResourceUpdateContext,
        resource
        -> getHandler(resource.getResourceId().getKind()).onSelectorsChange(resource, kubernetesResourceUpdateContext));
  }

  private static void handleChange(
      KubernetesResourceUpdateContext kubernetesResourceUpdateContext, Consumer<KubernetesResource> function) {
    if (kubernetesResourceUpdateContext.getK8sRequestHandlerContext().getResources() != null) {
      kubernetesResourceUpdateContext.getK8sRequestHandlerContext().getResources().stream().forEach(function);
    }
  }

  public static KubernetesResourceUpdateHandler getHandler(String kindType) {
    try {
      Kind kind = Kind.valueOf(kindType);
      KubernetesResourceUpdateHandler resourceUpdateHandler = kubernetesResourceUpdateHandlers.get(kind);

      if (resourceUpdateHandler != null) {
        return resourceUpdateHandler;
      }
    } catch (Exception e) {
      log.debug("Kind type {} is not part of recognize K8s Kinds.", kindType);
    }

    return kubernetesResourceUpdateHandlers.get(Kind.NOOP);
  }
}
