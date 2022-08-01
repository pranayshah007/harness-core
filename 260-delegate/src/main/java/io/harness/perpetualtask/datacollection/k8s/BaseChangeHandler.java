/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.perpetualtask.datacollection.k8s;

import io.harness.cvng.CVNGRequestExecutor;
import io.harness.cvng.beans.K8ActivityDataCollectionInfo;
import io.harness.cvng.beans.change.ChangeEventDTO;
import io.harness.cvng.beans.change.ChangeSourceType;
import io.harness.cvng.beans.change.KubernetesChangeEventMetadata;
import io.harness.verificationclient.CVNextGenServiceClient;

import com.google.inject.Inject;
import io.kubernetes.client.common.KubernetesObject;
import io.kubernetes.client.informer.ResourceEventHandler;
import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.experimental.SuperBuilder;
import lombok.extern.slf4j.Slf4j;
import org.joor.Reflect;

@SuperBuilder
@Data
@AllArgsConstructor
@Slf4j
public abstract class BaseChangeHandler<ApiType extends KubernetesObject> implements ResourceEventHandler<ApiType> {
  String accountId;
  K8ActivityDataCollectionInfo dataCollectionInfo;
  @Inject CVNGRequestExecutor cvngRequestExecutor;
  @Inject CVNextGenServiceClient cvNextGenServiceClient;
  @Inject K8sHandlerUtils k8sHandlerUtils;

  public BaseChangeHandler(String accountId, K8ActivityDataCollectionInfo dataCollectionInfo) {
    this.accountId = accountId;
    this.dataCollectionInfo = dataCollectionInfo;
  }

  @Override
  public void onAdd(ApiType apiType) {
    handleMissingKindAndApiVersion(apiType);
    processAndSendAddEvent(apiType);
  }
  void processAndSendAddEvent(ApiType newResource) {}

  @Override
  public void onUpdate(ApiType oldResource, ApiType newResource) {
    try {
      handleMissingKindAndApiVersion(oldResource);
      handleMissingKindAndApiVersion(newResource);
      String oldYaml = k8sHandlerUtils.yamlDump(oldResource);
      String newYaml = k8sHandlerUtils.yamlDump(newResource);
      if (oldYaml.equals(newYaml)) {
        log.debug("Old and New Yamls are same so not sending a change event");
        return;
      }
      processAndSendUpdateEvent(oldResource, newResource, oldYaml, newYaml);
    } catch (Exception ex) {
      log.error("Exception", ex);
    }
  }
  void processAndSendUpdateEvent(ApiType oldResource, ApiType newResource, String oldYaml, String newYaml) {
    ChangeEventDTO eventDTO = buildChangeEvent(newResource);
    if (shouldProcessEvent(eventDTO)) {
      ((KubernetesChangeEventMetadata) eventDTO.getMetadata()).setOldYaml(oldYaml);
      ((KubernetesChangeEventMetadata) eventDTO.getMetadata()).setNewYaml(newYaml);
      ((KubernetesChangeEventMetadata) eventDTO.getMetadata()).setAction(KubernetesChangeEventMetadata.Action.Update);
      sendEvent(accountId, eventDTO);
    }
  }

  @Override
  public void onDelete(ApiType resource, boolean finalStateUnknown) {
    handleMissingKindAndApiVersion(resource);
    String oldYaml = k8sHandlerUtils.yamlDump(resource);
    log.debug("Delete resource: {}, finalStateUnknown: {}", oldYaml, finalStateUnknown);

    if (hasOwnerReference(resource)) {
      log.info("Skipping publish for resource deleted as it has controller.");
    } else {
      if (!finalStateUnknown) {
        processAndSendDeletedEvent(resource, oldYaml);
      } else {
        log.warn("Deletion with finalStateUnknown");
      }
    }
  }

  void processAndSendDeletedEvent(ApiType newResource, String oldYaml) {
    ChangeEventDTO eventDTO = buildChangeEvent(newResource);
    if (shouldProcessEvent(eventDTO)) {
      ((KubernetesChangeEventMetadata) eventDTO.getMetadata()).setOldYaml(oldYaml);
      ((KubernetesChangeEventMetadata) eventDTO.getMetadata()).setAction(KubernetesChangeEventMetadata.Action.Delete);
      OffsetDateTime deletionTime = k8sHandlerUtils.getMetadata(newResource).getDeletionTimestamp();
      if (deletionTime != null) {
        eventDTO.setEventTime(deletionTime.toInstant().toEpochMilli());
      }
      sendEvent(accountId, eventDTO);
    }
  }

  boolean shouldProcessEvent(ChangeEventDTO eventDTO) {
    KubernetesChangeEventMetadata eventMetadata = (KubernetesChangeEventMetadata) eventDTO.getMetadata();
    if (eventMetadata.getNamespace() != null && eventMetadata.getNamespace().equals("kube-system")) {
      return false;
    }
    return true;
  }

  abstract String getKind();
  abstract String getApiVersion();
  abstract boolean hasOwnerReference(ApiType resource);
  abstract ChangeEventDTO buildChangeEvent(ApiType resource);

  private void handleMissingKindAndApiVersion(ApiType resource) {
    if (Reflect.on(resource).get("kind") == null) {
      Reflect.on(resource).set("kind", getKind());
    }
    if (Reflect.on(resource).get("apiVersion") == null) {
      Reflect.on(resource).set("apiVersion", getApiVersion());
    }
  }

  protected ChangeEventDTO buildChangeEventDTOSkeleton() {
    return ChangeEventDTO.builder()
        .accountId(accountId)
        .type(ChangeSourceType.KUBERNETES)
        .changeSourceIdentifier(dataCollectionInfo.getChangeSourceIdentifier())
        .monitoredServiceIdentifier(dataCollectionInfo.getMonitoredServiceIdentifier())
        .serviceIdentifier(dataCollectionInfo.getServiceIdentifier())
        .envIdentifier(dataCollectionInfo.getEnvIdentifier())
        .projectIdentifier(dataCollectionInfo.getProjectIdentifier())
        .orgIdentifier(dataCollectionInfo.getOrgIdentifier())
        .build();
  }

  protected void sendEvent(String accountId, ChangeEventDTO changeEventDTO) {
    Boolean resp =
        cvngRequestExecutor.executeWithRetry(cvNextGenServiceClient.saveChangeEvent(accountId, changeEventDTO))
            .getResource();
    if (resp) {
      log.info("ChangeEvent sent to CVNG for source {}", changeEventDTO.getChangeSourceIdentifier());
    }
  }
}
