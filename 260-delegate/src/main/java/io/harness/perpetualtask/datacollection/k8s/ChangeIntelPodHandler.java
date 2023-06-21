/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.perpetualtask.datacollection.k8s;

import io.harness.cvng.beans.change.ChangeEventDTO;

import io.kubernetes.client.openapi.models.V1OwnerReference;
import io.kubernetes.client.openapi.models.V1Pod;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import lombok.experimental.SuperBuilder;
import lombok.extern.slf4j.Slf4j;

@SuperBuilder
@Slf4j
public class ChangeIntelPodHandler extends BaseChangeHandler<V1Pod> {
  public static final String RUNNING_STATUS = "Running";

  @Override
  String getKind() {
    return "Pod";
  }

  @Override
  String getApiVersion() {
    return "v1";
  }

  @Override
  boolean hasOwnerReference(V1Pod resource) {
    if (resource.getMetadata().getOwnerReferences() != null) {
      for (V1OwnerReference ownerReference : resource.getMetadata().getOwnerReferences()) {
        if (Boolean.TRUE.equals(ownerReference.getController())) {
          return true;
        }
      }
    }
    return false;
  }

  @Override
  void processAndSendAddEvent(V1Pod newResource) {
    String yaml = k8sHandlerUtils.yamlDump(newResource);
    if (newResource != null && newResource.getMetadata() != null
        && (newResource.getMetadata().getCreationTimestamp() == null
            || newResource.getMetadata().getCreationTimestamp().isAfter(
                OffsetDateTime.now().minus(2, ChronoUnit.HOURS)))) {
      // we want to process and send events only when containers are ready and pod is ready to serve traffic
      // Pod statuses can be found here:
      // https://github.com/kubernetes-client/python/blob/master/kubernetes/docs/V1PodStatus.md
      if (newResource.getStatus().getPhase().equals(RUNNING_STATUS)) {
        if (!newResource.getMetadata().getNamespace().equals("kube-system")) {
          log.info("New pod added: {}", yaml);
        }
      }
    }
  }

  @Override
  void processAndSendUpdateEvent(V1Pod oldResource, V1Pod newResource, String oldYaml, String newYaml) {
    // Pod statuses can be found here:
    // https://github.com/kubernetes-client/python/blob/master/kubernetes/docs/V1PodStatus.md
    if (newResource.getStatus().getPhase().equals(RUNNING_STATUS)) {
      if (!newResource.getMetadata().getNamespace().equals("kube-system")) {
        log.info("Old and new Yamls are \n {} and \n {}", oldYaml, newYaml);
      }
    }
  }

  @Override
  void processAndSendDeletedEvent(V1Pod newResource, String oldYaml) {
    log.info("Deleted pod yaml {}", oldYaml);
  }

  @Override
  ChangeEventDTO buildChangeEvent(V1Pod resource) {
    // TODO: PodHandler is not complete yet. This will be completed with more testing
    return null;
  }
}
