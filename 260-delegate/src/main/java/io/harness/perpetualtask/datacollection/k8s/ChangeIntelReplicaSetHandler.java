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
import io.kubernetes.client.openapi.models.V1ReplicaSet;
import java.time.Instant;
import lombok.experimental.SuperBuilder;
import lombok.extern.slf4j.Slf4j;

@SuperBuilder
@Slf4j
public class ChangeIntelReplicaSetHandler extends BaseChangeHandler<V1ReplicaSet> {
  @Override
  String getKind() {
    return "ReplicaSet";
  }

  @Override
  String getApiVersion() {
    return "apps/v1";
  }

  @Override
  boolean hasOwnerReference(V1ReplicaSet v1ReplicaSet) {
    if (v1ReplicaSet.getMetadata().getOwnerReferences() != null) {
      for (V1OwnerReference ownerReference : v1ReplicaSet.getMetadata().getOwnerReferences()) {
        if (Boolean.TRUE.equals(ownerReference.getController())) {
          return true;
        }
      }
    }
    return false;
  }

  @Override
  public void processAndSendAddEvent(V1ReplicaSet v1ReplicaSet) {
    log.info("OnAdd of ReplicaSet.");
    ChangeEventDTO eventDTO = buildChangeEvent(v1ReplicaSet);
    if (!hasOwnerReference(v1ReplicaSet) && shouldProcessEvent(eventDTO)) {
      log.info("ReplicaSet doesn't have an ownerReference. Sending event Data");
      String newYaml = k8sHandlerUtils.yamlDump(v1ReplicaSet);
      ((KubernetesChangeEventMetadata) eventDTO.getMetadata()).setNewYaml(newYaml);
      ((KubernetesChangeEventMetadata) eventDTO.getMetadata())
          .setTimestamp(
              Instant.ofEpochMilli(v1ReplicaSet.getMetadata().getCreationTimestamp().toInstant().toEpochMilli()));
      ((KubernetesChangeEventMetadata) eventDTO.getMetadata()).setAction(KubernetesChangeEventMetadata.Action.Add);
      sendEvent(accountId, eventDTO);
    }
  }

  @Override
  ChangeEventDTO buildChangeEvent(V1ReplicaSet v1ReplicaSet) {
    String workload = v1ReplicaSet.getMetadata().getName();
    String namespace = v1ReplicaSet.getMetadata().getNamespace();
    ChangeEventDTO eventDTO = buildChangeEventDTOSkeleton();
    eventDTO.setMetadata(KubernetesChangeEventMetadata.builder()
                             .resourceType(KubernetesChangeEventMetadata.KubernetesResourceType.ReplicaSet)
                             .namespace(namespace)
                             .workload(workload)
                             .timestamp(Instant.now())
                             .resourceVersion(v1ReplicaSet.getMetadata().getResourceVersion())
                             .build());
    eventDTO.setEventTime(Instant.now().toEpochMilli());
    return eventDTO;
  }
}
