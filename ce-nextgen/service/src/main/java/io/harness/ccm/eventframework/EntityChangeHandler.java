/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.eventframework;

import static io.harness.annotations.dev.HarnessTeam.CE;

import static java.lang.String.format;

import io.harness.annotations.dev.OwnedBy;
import io.harness.ccm.K8sEventCollectionBundle;
import io.harness.ccm.cluster.NGClusterRecordHandler;
import io.harness.ccm.commons.entities.ClusterRecord;
import io.harness.ccm.perpetualtask.K8sWatchTaskResourceClient;
import io.harness.connector.ConnectorDTO;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.ConnectorResourceClient;
import io.harness.delegate.beans.connector.CEFeatures;
import io.harness.delegate.beans.connector.ConnectorConfigDTO;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.beans.connector.cek8s.CEKubernetesClusterConfigDTO;
import io.harness.eventsframework.entity_crud.EntityChangeDTO;
import io.harness.exception.InvalidRequestException;
import io.harness.ff.FeatureFlagService;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.remote.client.NGRestUtils;

import com.google.inject.Inject;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import javax.ws.rs.NotFoundException;
import lombok.extern.slf4j.Slf4j;
import retrofit2.Response;

@Slf4j
@OwnedBy(CE)
public class EntityChangeHandler {
  @Inject NGClusterRecordHandler clusterRecordHandler;
  @Inject ConnectorResourceClient connectorResourceClient;
  @Inject K8sWatchTaskResourceClient k8sWatchTaskResourceClient;
  @Inject private FeatureFlagService featureFlagService;

  public void handleCreateEvent(EntityChangeDTO entityChangeDTO, String connectorEntityType) {
    // Create Events of K8s Base (CD) connectors can be safely ignored
    if (ConnectorType.CE_KUBERNETES_CLUSTER.getDisplayName().equals(connectorEntityType)) {
      handleCEK8sCreate(entityChangeDTO);
    }
  }

  public void handleUpdateEvent(EntityChangeDTO entityChangeDTO, String connectorEntityType) {
    String accountIdentifier = entityChangeDTO.getAccountIdentifier().getValue();
    String k8sConnectorIdentifier = entityChangeDTO.getIdentifier().getValue();

    if (ConnectorType.KUBERNETES_CLUSTER.getDisplayName().equals(connectorEntityType)) {
      ClusterRecord clusterRecordFromK8sBaseConnector =
          clusterRecordHandler.getClusterRecordFromK8sBaseConnector(accountIdentifier, k8sConnectorIdentifier);
      log.info("Handle K8s UpdateEvent [Cluster Record: {}]", clusterRecordFromK8sBaseConnector);
      // K8s Cluster Event is Relevant to CE/ CD Connector has CE Enabled
      if (clusterRecordFromK8sBaseConnector != null) {
        String perpetualTaskId = clusterRecordFromK8sBaseConnector.getPerpetualTaskId();
        try {
          log.info("Handle K8s UpdateEvent [Starting PT Reset]]");
          resetPerpetualTask(clusterRecordFromK8sBaseConnector, perpetualTaskId);
          log.info("Handle K8s UpdateEvent [PT Reset Complete]]");
        } catch (IOException e) {
          log.error("Exception Resetting Perpetual Task for Cluster Record: {}", clusterRecordFromK8sBaseConnector);
        }
      }
    }
    if (ConnectorType.CE_KUBERNETES_CLUSTER.getDisplayName().equals(connectorEntityType)) {
      handleCEK8sUpdate(entityChangeDTO);
    }
  }

  public void handleDeleteEvent(EntityChangeDTO entityChangeDTO, String connectorEntityType) {
    // Deleting base K8s Connector should not be allowed when referenced.
    if (ConnectorType.CE_KUBERNETES_CLUSTER.getDisplayName().equals(connectorEntityType)) {
      String accountIdentifier = entityChangeDTO.getAccountIdentifier().getValue();
      String ceK8sConnectorIdentifier = entityChangeDTO.getIdentifier().getValue();

      ClusterRecord clusterRecord = clusterRecordHandler.getClusterRecord(accountIdentifier, ceK8sConnectorIdentifier);
      log.info("Handle K8s DeleteEvent, Cluster Record: {}]", clusterRecord);
      if (clusterRecord != null) {
        log.info("Handle K8s DeleteEvent, Cleanup Start");
        clusterRecordAndPerpetualTaskCleanup(clusterRecord, accountIdentifier, ceK8sConnectorIdentifier);
        log.info("Handle K8s DeleteEvent, Cleanup Complete");
      }
    }
  }

  public void handleCEK8sCreate(EntityChangeDTO entityChangeDTO) {
    String accountIdentifier = entityChangeDTO.getAccountIdentifier().getValue();
    String ceK8sConnectorIdentifier = entityChangeDTO.getIdentifier().getValue();

    ConnectorInfoDTO ceK8sConnectorInfoDTO = getConnectorConfigDTO(entityChangeDTO);
    ConnectorConfigDTO ceK8sConnectorConfigDTO = ceK8sConnectorInfoDTO.getConnectorConfig();
    CEKubernetesClusterConfigDTO ceKubernetesClusterConfigDTO = (CEKubernetesClusterConfigDTO) ceK8sConnectorConfigDTO;
    if (isVisibilityFeatureEnabled(ceKubernetesClusterConfigDTO)) {
      String k8sBaseConnectorRef = ceKubernetesClusterConfigDTO.getConnectorRef();
      onboardNewCEK8sConnector(getClusterRecord(
          accountIdentifier, ceK8sConnectorIdentifier, ceK8sConnectorInfoDTO.getName(), k8sBaseConnectorRef));
    }
  }

  private void onboardNewCEK8sConnector(ClusterRecord clusterRecord) {
    String taskId;
    try {
      log.info("Oboarding New K8s Connector[Cluster Record: {}]", clusterRecord);
      clusterRecord = clusterRecordHandler.handleNewCEK8sConnectorCreate(clusterRecord);
      log.info("Oboarding New K8s Connector[Cluster Record Upserted]");

      log.info("Oboarding New K8s Connector[creating PT]");
      taskId = createPerpetualTask(clusterRecord);
      log.info("Oboarding New K8s Connector[taskId: {}]", taskId);

      log.info("Oboarding New K8s Connector[Attach PT Starting]", taskId);
      clusterRecordHandler.attachPerpetualTask(clusterRecord, taskId);
      log.info("Oboarding New K8s Connector[Attach PT Complete]", taskId);
    } catch (IOException e) {
      log.error("Exception Creating Perpetual Task for Cluster Record: {}", clusterRecord);
    }
  }

  private void clusterRecordAndPerpetualTaskCleanup(
      ClusterRecord clusterRecord, String accountIdentifier, String ceK8sConnectorIdentifier) {
    String perpetualTaskId = clusterRecord.getPerpetualTaskId();
    // Delete the Perpetual Task
    try {
      log.info("Handle K8s DeleteEvent, Delete PT");
      deletePerpetualTask(clusterRecord, perpetualTaskId);
      log.info("Handle K8s DeleteEvent, Delete PT Complete");
    } catch (IOException e) {
      log.error("Exception Deleting Perpetual Task for CLuster Record: {}", clusterRecord);
    }
    log.info("Handle K8s DeleteEvent, Delete CR");
    clusterRecordHandler.deleteClusterRecord(accountIdentifier, ceK8sConnectorIdentifier);
    log.info("Handle K8s DeleteEvent, Delete CR Complete");
  }

  public void handleCEK8sUpdate(EntityChangeDTO entityChangeDTO) {
    String accountIdentifier = entityChangeDTO.getAccountIdentifier().getValue();
    String ceK8sConnectorIdentifier = entityChangeDTO.getIdentifier().getValue();

    ConnectorInfoDTO ceK8sConnectorInfoDTO = getConnectorConfigDTO(entityChangeDTO);
    CEKubernetesClusterConfigDTO ceKubernetesClusterConfigDTO =
        (CEKubernetesClusterConfigDTO) ceK8sConnectorInfoDTO.getConnectorConfig();
    ClusterRecord clusterRecord;
    // Get Cluster Record from DB
    clusterRecord = clusterRecordHandler.getClusterRecord(accountIdentifier, ceK8sConnectorIdentifier);
    log.info("Handle CE K8s Connector[Cluster Record: {}]", clusterRecord);

    boolean visibilityFeatureEnabled = isVisibilityFeatureEnabled(ceKubernetesClusterConfigDTO);
    log.info("Handle CE K8s Connector[visibilityFeatureEnabled: {}]", visibilityFeatureEnabled);

    // If Visibility was enabled Previously and Disabled in the Update, we need to clean up PT, CR
    if (!visibilityFeatureEnabled && clusterRecord != null) {
      log.info("Handle CE K8s Connector[Visibility was enabled Previously and Disabled in the Update]");
      log.info("Handle CE K8s Connector[Cleanup PT and CR Started]");
      clusterRecordAndPerpetualTaskCleanup(clusterRecord, accountIdentifier, ceK8sConnectorIdentifier);
      log.info("Handle CE K8s Connector[Cleanup PT and CR Started]");
    }

    if (visibilityFeatureEnabled) {
      if (clusterRecord == null) {
        /* If Visibility was enabled as a part of connector Update (Cluster record didn't exist previously)
        - Then we create Cluster Record and Assign a Perpetual Task */
        log.info("Handle CE K8s Connector[Visibility was enabled as a part of connector Update]");
        String k8sBaseConnectorRef = ceKubernetesClusterConfigDTO.getConnectorRef();
        onboardNewCEK8sConnector(getClusterRecord(
            accountIdentifier, ceK8sConnectorIdentifier, ceK8sConnectorInfoDTO.getName(), k8sBaseConnectorRef));
      } else {
        log.info(
            "Handle CE K8s Connector[Only fields that can change on a CE K8s Entity Update (Name, Connector Ref)]");
        // Only fields that can change on a CE K8s Entity Update (Name, Connector Ref)
        clusterRecord.setClusterName(ceK8sConnectorInfoDTO.getName());
        clusterRecord.setK8sBaseConnectorRefIdentifier(ceKubernetesClusterConfigDTO.getConnectorRef());
        // find all the existing perpetual Tasks for these clusters
        String perpetualTaskId = clusterRecord.getPerpetualTaskId();
        // Reset the Perpetual Task
        try {
          log.info("Handle CE K8s Connector[Reset PT with cluster Record: {}]", clusterRecord);
          resetPerpetualTask(clusterRecord, perpetualTaskId);
          log.info("Handle CE K8s Connector[Reset PT with cluster Record complete]");
        } catch (IOException e) {
          log.error("Exception Resetting Perpetual Task for CLuster Record: {}", clusterRecord);
        }
      }
    }
  }

  private String createPerpetualTask(ClusterRecord clusterRecord) throws IOException {
    Response<ResponseDTO<String>> createResponse =
        k8sWatchTaskResourceClient
            .create(clusterRecord.getAccountId(),
                K8sEventCollectionBundle.builder()
                    .cloudProviderId(clusterRecord.getCeK8sConnectorIdentifier())
                    .clusterId(clusterRecord.getUuid())
                    .clusterName(clusterRecord.getClusterName())
                    .connectorIdentifier(clusterRecord.getCeK8sConnectorIdentifier())
                    .build())
            .execute();
    if (!createResponse.isSuccessful()) {
      log.error("Create perpetual Task Failed {}", createResponse);
    }
    return createResponse.body().getData();
  }

  private Boolean resetPerpetualTask(ClusterRecord clusterRecord, String taskId) throws IOException {
    Response<ResponseDTO<Boolean>> resetResponse =
        k8sWatchTaskResourceClient
            .reset(clusterRecord.getAccountId(), taskId,
                K8sEventCollectionBundle.builder()
                    .cloudProviderId(clusterRecord.getCeK8sConnectorIdentifier())
                    .clusterId(clusterRecord.getUuid())
                    .clusterName(clusterRecord.getClusterName())
                    .connectorIdentifier(clusterRecord.getCeK8sConnectorIdentifier())
                    .build())
            .execute();
    if (!resetResponse.isSuccessful()) {
      log.error("Reset perpetual Task Failed {}", resetResponse);
    }
    return resetResponse.body().getData();
  }

  Boolean deletePerpetualTask(ClusterRecord clusterRecord, String taskId) throws IOException {
    Response<ResponseDTO<Boolean>> deleteResponse =
        k8sWatchTaskResourceClient.delete(clusterRecord.getAccountId(), taskId).execute();
    if (!deleteResponse.isSuccessful()) {
      log.error("Delete perpetual Task Failed {}", deleteResponse);
    }
    return deleteResponse.body().getData();
  }

  private ClusterRecord getClusterRecord(
      String accountIdentifier, String ceK8sConnectorIdentifier, String clusterName, String k8sBaseConnectorRef) {
    return ClusterRecord.builder()
        .accountId(accountIdentifier)
        .ceK8sConnectorIdentifier(ceK8sConnectorIdentifier)
        .k8sBaseConnectorRefIdentifier(k8sBaseConnectorRef)
        .clusterName(clusterName)
        .build();
  }

  public ConnectorInfoDTO getConnectorConfigDTO(EntityChangeDTO entityChangeDTO) {
    ConnectorInfoDTO connectorInfoDTO;
    String connectorIdentifierRef = null;
    try {
      connectorIdentifierRef = entityChangeDTO.getIdentifier().getValue();
      Optional<ConnectorDTO> connectorDTO = NGRestUtils.getResponse(
          connectorResourceClient.get(connectorIdentifierRef, entityChangeDTO.getAccountIdentifier().getValue(),
              entityChangeDTO.getOrgIdentifier().getValue(), entityChangeDTO.getProjectIdentifier().getValue()));

      if (!connectorDTO.isPresent()) {
        throw new InvalidRequestException(
            String.format("Connector not found for identifier : [%s]", connectorIdentifierRef));
      }

      connectorInfoDTO = connectorDTO.get().getConnectorInfo();
    } catch (Exception e) {
      throw new NotFoundException(
          format("Error while getting connector information : [%s]", connectorIdentifierRef), e);
    }
    return connectorInfoDTO;
  }

  private boolean isVisibilityFeatureEnabled(CEKubernetesClusterConfigDTO ceKubernetesClusterConfigDTO) {
    List<CEFeatures> featuresEnabled = ceKubernetesClusterConfigDTO.getFeaturesEnabled();
    return featuresEnabled.contains(CEFeatures.VISIBILITY);
  }
}
