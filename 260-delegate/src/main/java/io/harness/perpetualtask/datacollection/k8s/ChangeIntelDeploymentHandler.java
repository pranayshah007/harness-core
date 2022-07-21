/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.perpetualtask.datacollection.k8s;

import io.harness.cvng.beans.change.ChangeEventDTO;
import io.harness.cvng.beans.change.KubernetesChangeEventMetadata;
import io.harness.cvng.beans.change.KubernetesChangeEventMetadata.Action;
import io.harness.cvng.beans.change.KubernetesChangeEventMetadata.KubernetesResourceType;

import io.kubernetes.client.openapi.models.V1Deployment;
import io.kubernetes.client.openapi.models.V1DeploymentCondition;
import io.kubernetes.client.openapi.models.V1OwnerReference;
import java.time.Instant;
import java.time.OffsetDateTime;
import lombok.experimental.SuperBuilder;
import lombok.extern.slf4j.Slf4j;

@SuperBuilder
@Slf4j
public class ChangeIntelDeploymentHandler extends BaseChangeHandler<V1Deployment> {
  @Override
  String getKind() {
    return "Deployment";
  }

  @Override
  String getApiVersion() {
    return "apps/v1";
  }

  @Override
  boolean hasOwnerReference(V1Deployment v1Deployment) {
    if (v1Deployment.getMetadata().getOwnerReferences() != null) {
      for (V1OwnerReference ownerReference : v1Deployment.getMetadata().getOwnerReferences()) {
        if (Boolean.TRUE.equals(ownerReference.getController())) {
          return true;
        }
      }
    }
    return false;
  }

  @Override
  void processAndSendAddEvent(V1Deployment v1Deployment) {
    log.info("OnAdd of DeploymentChange.");
    ChangeEventDTO eventDTO = buildChangeEvent(v1Deployment);
    if (!hasOwnerReference(v1Deployment) && shouldProcessEvent(eventDTO)) {
      log.info("Deployment doesn't have an ownerReference. Sending event Data");

      String newYaml = k8sHandlerUtils.yamlDump(v1Deployment);
      ((KubernetesChangeEventMetadata) eventDTO.getMetadata()).setNewYaml(newYaml);
      OffsetDateTime dateTime = v1Deployment.getMetadata().getCreationTimestamp();
      if (dateTime != null) {
        ((KubernetesChangeEventMetadata) eventDTO.getMetadata())
            .setTimestamp(Instant.ofEpochMilli(dateTime.toInstant().toEpochMilli()));
      }
      ((KubernetesChangeEventMetadata) eventDTO.getMetadata()).setAction(Action.Add);
      sendEvent(accountId, eventDTO);
    }
  }

  @Override
  void processAndSendUpdateEvent(V1Deployment oldResource, V1Deployment newResource, String oldYaml, String newYaml) {
    for (V1DeploymentCondition v1DeploymentCondition : newResource.getStatus().getConditions()) {
      if (v1DeploymentCondition.getType().equals("Available") && v1DeploymentCondition.getStatus().equals("True")) {
        ChangeEventDTO eventDTO = buildChangeEvent(newResource);
        ((KubernetesChangeEventMetadata) eventDTO.getMetadata()).setOldYaml(oldYaml);
        ((KubernetesChangeEventMetadata) eventDTO.getMetadata()).setNewYaml(newYaml);
        ((KubernetesChangeEventMetadata) eventDTO.getMetadata()).setAction(Action.Update);
        sendEvent(accountId, eventDTO);
        return;
      }
    }
  }

  @Override
  ChangeEventDTO buildChangeEvent(V1Deployment deployment) {
    String workload = deployment.getMetadata().getName();
    String namespace = deployment.getMetadata().getNamespace();
    ChangeEventDTO eventDTO = buildChangeEventDTOSkeleton();
    eventDTO.setMetadata(KubernetesChangeEventMetadata.builder()
                             .resourceType(KubernetesResourceType.Deployment)
                             .namespace(namespace)
                             .workload(workload)
                             .timestamp(Instant.now())
                             .resourceVersion(deployment.getMetadata().getResourceVersion())
                             .build());
    eventDTO.setEventTime(Instant.now().toEpochMilli());
    return eventDTO;
  }
}
