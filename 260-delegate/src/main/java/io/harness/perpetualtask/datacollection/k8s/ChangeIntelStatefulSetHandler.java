/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.perpetualtask.datacollection.k8s;

import io.harness.cvng.beans.change.ChangeEventDTO;
import io.harness.cvng.beans.change.KubernetesChangeEventMetadata;

import io.kubernetes.client.openapi.models.V1OwnerReference;
import io.kubernetes.client.openapi.models.V1StatefulSet;
import java.time.Instant;
import java.time.OffsetDateTime;
import lombok.experimental.SuperBuilder;
import lombok.extern.slf4j.Slf4j;

@SuperBuilder
@Slf4j
public class ChangeIntelStatefulSetHandler extends BaseChangeHandler<V1StatefulSet> {
  @Override
  String getKind() {
    return "StatefulSet";
  }

  @Override
  String getApiVersion() {
    return "apps/v1";
  }

  @Override
  boolean hasOwnerReference(V1StatefulSet resource) {
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
  public void processAndSendAddEvent(V1StatefulSet v1StatefulSet) {
    log.info("OnAdd of StatefulSet.");
    ChangeEventDTO eventDTO = buildChangeEvent(v1StatefulSet);
    if (!hasOwnerReference(v1StatefulSet) && shouldProcessEvent(eventDTO)) {
      log.info("StatefulSet doesn't have an ownerReference. Sending event Data");
      String newYaml = k8sHandlerUtils.yamlDump(v1StatefulSet);
      ((KubernetesChangeEventMetadata) eventDTO.getMetadata()).setNewYaml(newYaml);
      OffsetDateTime dateTime = v1StatefulSet.getMetadata().getCreationTimestamp();
      if (dateTime != null) {
        ((KubernetesChangeEventMetadata) eventDTO.getMetadata())
            .setTimestamp(Instant.ofEpochMilli(dateTime.toInstant().toEpochMilli()));
      }
      ((KubernetesChangeEventMetadata) eventDTO.getMetadata()).setAction(KubernetesChangeEventMetadata.Action.Add);
      sendEvent(accountId, eventDTO);
    }
  }

  @Override
  ChangeEventDTO buildChangeEvent(V1StatefulSet v1StatefulSet) {
    String workload = v1StatefulSet.getMetadata().getName();
    String namespace = v1StatefulSet.getMetadata().getNamespace();
    ChangeEventDTO eventDTO = buildChangeEventDTOSkeleton();
    eventDTO.setMetadata(KubernetesChangeEventMetadata.builder()
                             .resourceType(KubernetesChangeEventMetadata.KubernetesResourceType.StatefulSet)
                             .namespace(namespace)
                             .workload(workload)
                             .timestamp(Instant.now())
                             .resourceVersion(v1StatefulSet.getMetadata().getResourceVersion())
                             .build());
    eventDTO.setEventTime(Instant.now().toEpochMilli());
    return eventDTO;
  }
}
