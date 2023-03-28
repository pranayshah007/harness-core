/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ccm.ngperpetualtask.service;

import static io.harness.utils.DelegateOwner.getNGTaskSetupAbstractionsWithOwner;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.IdentifierRef;
import io.harness.ccm.K8sEventCollectionBundle;
import io.harness.connector.ConnectorDTO;
import io.harness.connector.ConnectorResourceClient;
import io.harness.delegate.Capability;
import io.harness.delegate.beans.ccm.K8sClusterInfo;
import io.harness.delegate.beans.connector.ConnectorConfigDTO;
import io.harness.delegate.beans.connector.cek8s.CEKubernetesClusterConfigDTO;
import io.harness.delegate.beans.connector.k8Connector.K8sTaskCapabilityHelper;
import io.harness.delegate.beans.connector.k8Connector.KubernetesClusterConfigDTO;
import io.harness.delegate.beans.connector.k8Connector.KubernetesClusterDetailsDTO;
import io.harness.delegate.beans.connector.k8Connector.KubernetesCredentialDTO;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.capability.EncryptedDataDetailsCapabilityHelper;
import io.harness.encryption.Scope;
import io.harness.ng.core.BaseNGAccess;
import io.harness.ng.core.NGAccess;
import io.harness.perpetualtask.PerpetualTaskClientContext;
import io.harness.perpetualtask.PerpetualTaskExecutionBundle;
import io.harness.perpetualtask.PerpetualTaskSchedule;
import io.harness.perpetualtask.PerpetualTaskService;
import io.harness.perpetualtask.PerpetualTaskType;
import io.harness.perpetualtask.internal.PerpetualTaskRecord;
import io.harness.perpetualtask.k8s.watch.K8sWatchTaskParams;
import io.harness.remote.client.NGRestUtils;
import io.harness.secretmanagerclient.services.api.SecretManagerClientService;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.serializer.KryoSerializer;
import io.harness.utils.IdentifierRefHelper;

import com.google.api.client.util.Preconditions;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import com.google.protobuf.util.Durations;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

/**
 * Ref: CVDataCollectionTaskServiceImpl
 */
@Slf4j
@OwnedBy(HarnessTeam.CE)
public class K8sWatchTaskServiceImpl implements K8sWatchTaskService {
  @Inject @Named("referenceFalseKryoSerializer") private KryoSerializer referenceFalseKryoSerializer;
  @Inject private PerpetualTaskService perpetualTaskService;
  @Inject private SecretManagerClientService ngSecretService;
  @Inject private ConnectorResourceClient connectorResourceClient;

  @Override
  public String create(String accountId, K8sEventCollectionBundle bundle) {
    PerpetualTaskExecutionBundle executionBundle = createExecutionBundle(accountId, bundle);

    PerpetualTaskClientContext clientContext = PerpetualTaskClientContext.builder()
                                                   .clientId(bundle.getCloudProviderId() + "/" + bundle.getClusterId())
                                                   .executionBundle(executionBundle.toByteArray())
                                                   .build();

    PerpetualTaskSchedule schedule = PerpetualTaskSchedule.newBuilder()
                                         .setInterval(Durations.fromMinutes(2))
                                         .setTimeout(Durations.fromMinutes(4))
                                         .build();

    return perpetualTaskService.createTask(
        PerpetualTaskType.K8S_WATCH, accountId, clientContext, schedule, false, "NG");
  }

  @Override
  public boolean resetTask(String accountId, String taskId, K8sEventCollectionBundle bundle) {
    PerpetualTaskExecutionBundle executionBundle = createExecutionBundle(accountId, bundle);
    return perpetualTaskService.resetTask(accountId, taskId, executionBundle);
  }

  private PerpetualTaskExecutionBundle createExecutionBundle(String accountId, K8sEventCollectionBundle bundle) {
    CEKubernetesClusterConfigDTO ceKubernetesClusterConfigDTO = (CEKubernetesClusterConfigDTO) getConnectorConfig(
        bundle.getConnectorIdentifier(), accountId, bundle.getOrgIdentifier(), bundle.getProjectIdentifier());
    log.info("CEKubernetesClusterConfigDTO: {}", ceKubernetesClusterConfigDTO); // for few releases

    ConnectorConfigDTO k8sConnectorConfigDTO = getConnectorConfig(ceKubernetesClusterConfigDTO.getConnectorRef(),
        accountId, bundle.getOrgIdentifier(), bundle.getProjectIdentifier());
    log.info("KubernetesClusterConfigDTO: {}", k8sConnectorConfigDTO); // for few releases

    List<EncryptedDataDetail> encryptedDataDetailList = getEncryptedDataDetail(accountId, bundle.getOrgIdentifier(),
        bundle.getProjectIdentifier(), (KubernetesClusterConfigDTO) k8sConnectorConfigDTO);

    List<ExecutionCapability> executionCapabilities =
        getExecutionCapabilityList(k8sConnectorConfigDTO, encryptedDataDetailList);

    Any perpetualTaskPack =
        getTaskParams((KubernetesClusterConfigDTO) k8sConnectorConfigDTO, encryptedDataDetailList, bundle);

    final Map<String, String> ngTaskSetupAbstractionsWithOwner =
        getNGTaskSetupAbstractionsWithOwner(accountId, bundle.getOrgIdentifier(), bundle.getProjectIdentifier());

    return createPerpetualTaskExecutionBundle(
        perpetualTaskPack, executionCapabilities, ngTaskSetupAbstractionsWithOwner);
  }

  private List<EncryptedDataDetail> getEncryptedDataDetail(String accountId, String orgIdentifier,
      String projectIdentifier, KubernetesClusterConfigDTO kubernetesClusterConfig) {
    NGAccess basicNGAccessObject = BaseNGAccess.builder()
                                       .accountIdentifier(accountId)
                                       .orgIdentifier(orgIdentifier)
                                       .projectIdentifier(projectIdentifier)
                                       .build();

    KubernetesCredentialDTO credential = kubernetesClusterConfig.getCredential();
    if (!credential.getKubernetesCredentialType().isDecryptable()) {
      return new ArrayList<>();
    }
    return ngSecretService.getEncryptionDetails(
        basicNGAccessObject, ((KubernetesClusterDetailsDTO) credential.getConfig()).getAuth().getCredentials());
  }

  private Any getTaskParams(KubernetesClusterConfigDTO k8sConnectorConfigDTO,
      List<EncryptedDataDetail> encryptedDataDetailList, K8sEventCollectionBundle bundle) {
    K8sClusterInfo k8sClusterInfo = K8sClusterInfo.builder()
                                        .connectorConfigDTO(k8sConnectorConfigDTO)
                                        .encryptedDataDetails(encryptedDataDetailList)
                                        .build();

    K8sWatchTaskParams k8sWatchTaskParams =
        K8sWatchTaskParams.newBuilder()
            .setClusterId(bundle.getClusterId())
            .setClusterName(bundle.getClusterName())
            .setCloudProviderId(bundle.getCloudProviderId())
            .setK8SClusterInfo(ByteString.copyFrom(referenceFalseKryoSerializer.asBytes(k8sClusterInfo)))
            .build();

    return Any.pack(k8sWatchTaskParams);
  }

  private List<ExecutionCapability> getExecutionCapabilityList(
      ConnectorConfigDTO k8sConnectorConfigDTO, List<EncryptedDataDetail> encryptedDataDetailList) {
    List<ExecutionCapability> executionCapabilities =
        EncryptedDataDetailsCapabilityHelper.fetchExecutionCapabilitiesForEncryptedDataDetails(
            encryptedDataDetailList, null);

    executionCapabilities.addAll(
        K8sTaskCapabilityHelper.fetchRequiredExecutionCapabilities(k8sConnectorConfigDTO, null));

    return executionCapabilities;
  }

  @NotNull
  private PerpetualTaskExecutionBundle createPerpetualTaskExecutionBundle(Any perpetualTaskPack,
      List<ExecutionCapability> executionCapabilities, Map<String, String> ngTaskSetupAbstractionsWithOwner) {
    PerpetualTaskExecutionBundle.Builder builder = PerpetualTaskExecutionBundle.newBuilder();
    executionCapabilities.forEach(executionCapability
        -> builder
               .addCapabilities(Capability.newBuilder()
                                    .setKryoCapability(ByteString.copyFrom(
                                        referenceFalseKryoSerializer.asDeflatedBytes(executionCapability)))
                                    .build())
               .build());
    return builder.setTaskParams(perpetualTaskPack).putAllSetupAbstractions(ngTaskSetupAbstractionsWithOwner).build();
  }

  // TODO(UTSAV): Move it to sharable module, currently k8s connector validator also uses below implementation
  private ConnectorConfigDTO getConnectorConfig(@NotNull String scopedConnectorIdentifier, String accountIdentifier,
      String orgIdentifier, String projectIdentifier) {
    final String sanitizedScopedConnectorIdentifier = sanitizeK8sConnectorScope(scopedConnectorIdentifier);

    IdentifierRef connectorRef = IdentifierRefHelper.getIdentifierRef(
        sanitizedScopedConnectorIdentifier, accountIdentifier, orgIdentifier, projectIdentifier);

    Optional<ConnectorDTO> connectorDTO =
        NGRestUtils.getResponse(connectorResourceClient.get(connectorRef.getIdentifier(), accountIdentifier,
            connectorRef.getOrgIdentifier(), connectorRef.getProjectIdentifier()));

    Preconditions.checkArgument(
        connectorDTO.isPresent(), String.format("referenced connector %s not found", connectorRef.toString()));
    return connectorDTO.get().getConnectorInfo().getConnectorConfig();
  }

  /**
   * Should return Account scoped connector identifier when the scope is not explicitly defined,
   * since by default an identifier without an explicit scope is assumed at Project level.
   */
  @NotNull
  private String sanitizeK8sConnectorScope(@NotNull String scopedConnectorIdentifier) {
    String[] scopedIdentifier = scopedConnectorIdentifier.split("\\.");
    if (scopedIdentifier.length == 1) {
      return String.format("%s.%s", Scope.ACCOUNT.getYamlRepresentation(), scopedConnectorIdentifier);
    }

    return scopedConnectorIdentifier;
  }

  @Override
  public boolean delete(String accountId, String taskId) {
    return perpetualTaskService.deleteTask(accountId, taskId);
  }

  @Override
  public PerpetualTaskRecord getStatus(String taskId) {
    return perpetualTaskService.getTaskRecord(taskId);
  }
}
