/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.perpetualtask.datacollection.k8s;

import io.harness.cvng.beans.change.ChangeEventDTO;
import io.harness.cvng.beans.change.KubernetesChangeEventMetadata;
import io.harness.serializer.JsonUtils;

import io.kubernetes.client.openapi.models.V1ConfigMap;
import io.kubernetes.client.openapi.models.V1OwnerReference;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import lombok.experimental.SuperBuilder;
import lombok.extern.slf4j.Slf4j;

@SuperBuilder
@Slf4j
public class ChangeIntelConfigMapHandler extends BaseChangeHandler<V1ConfigMap> {
  @Override
  String getKind() {
    return "ConfigMap";
  }

  @Override
  String getApiVersion() {
    return "v1";
  }

  @Override
  boolean hasOwnerReference(V1ConfigMap v1ConfigMap) {
    if (v1ConfigMap.getMetadata().getOwnerReferences() != null) {
      for (V1OwnerReference ownerReference : v1ConfigMap.getMetadata().getOwnerReferences()) {
        if (Boolean.TRUE.equals(ownerReference.getController())) {
          return true;
        }
      }
    }
    return false;
  }

  @Override
  void processAndSendAddEvent(V1ConfigMap v1ConfigMap) {
    ChangeEventDTO eventDTO = buildChangeEvent(v1ConfigMap);
    if (!hasOwnerReference(v1ConfigMap) && v1ConfigMap.getMetadata() != null
        && v1ConfigMap.getMetadata().getCreationTimestamp().isAfter(OffsetDateTime.now().minus(2, ChronoUnit.HOURS))
        && shouldProcessEvent(eventDTO)) {
      log.info("ConfigMap doesn't have an ownerReference. Sending event Data");

      String newYaml = k8sHandlerUtils.yamlDump(v1ConfigMap);
      ((KubernetesChangeEventMetadata) eventDTO.getMetadata()).setNewYaml(newYaml);
      OffsetDateTime dateTime = v1ConfigMap.getMetadata().getCreationTimestamp();
      if (dateTime != null) {
        ((KubernetesChangeEventMetadata) eventDTO.getMetadata())
            .setTimestamp(Instant.ofEpochMilli(dateTime.toInstant().toEpochMilli()));
      }
      ((KubernetesChangeEventMetadata) eventDTO.getMetadata()).setAction(KubernetesChangeEventMetadata.Action.Add);
      sendEvent(accountId, eventDTO);
    }
  }

  @Override
  void processAndSendUpdateEvent(V1ConfigMap oldResource, V1ConfigMap newResource, String oldYaml, String newYaml) {
    // Temp solution to ignore spamming of config map events of type ingress
    // TODO: find long term solution asap.: https://harness.atlassian.net/browse/CVNG-3687
    String leaderKey = "control-plane.alpha.kubernetes.io/leader";
    if (oldResource.getMetadata().getAnnotations() != null && newResource.getMetadata().getAnnotations() != null) {
      String renewTokenKey = "renewTime";
      String oldValue = oldResource.getMetadata().getAnnotations().get(leaderKey);
      if (oldValue != null) {
        Map<String, Object> oldMap = JsonUtils.asMap(oldValue);
        oldMap.remove(renewTokenKey);
        oldResource.getMetadata().getAnnotations().put(leaderKey, JsonUtils.asJson(oldMap));
      }
      String newValue = newResource.getMetadata().getAnnotations().get(leaderKey);
      if (newValue != null) {
        Map<String, Object> newMap = JsonUtils.asMap(newValue);
        newMap.remove(renewTokenKey);
        newResource.getMetadata().getAnnotations().put(leaderKey, JsonUtils.asJson(newMap));
      }
      String updatedOldYaml = k8sHandlerUtils.yamlDump(oldResource);
      String updatedNewYaml = k8sHandlerUtils.yamlDump(newResource);
      if (!updatedNewYaml.equals(updatedOldYaml)) {
        super.processAndSendUpdateEvent(oldResource, newResource, oldYaml, newYaml);
      }
      return;
    }
    super.processAndSendUpdateEvent(oldResource, newResource, oldYaml, newYaml);
  }

  @Override
  ChangeEventDTO buildChangeEvent(V1ConfigMap v1ConfigMap) {
    String workload = v1ConfigMap.getMetadata().getName();
    String namespace = v1ConfigMap.getMetadata().getNamespace();
    ChangeEventDTO eventDTO = buildChangeEventDTOSkeleton();
    eventDTO.setMetadata(KubernetesChangeEventMetadata.builder()
                             .resourceType(KubernetesChangeEventMetadata.KubernetesResourceType.ConfigMap)
                             .namespace(namespace)
                             .workload(workload)
                             .timestamp(Instant.now())
                             .resourceVersion(v1ConfigMap.getMetadata().getResourceVersion())
                             .build());
    eventDTO.setEventTime(Instant.now().toEpochMilli());
    return eventDTO;
  }
}
