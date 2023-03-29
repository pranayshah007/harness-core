/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cvng.perpetualtask;

import static io.harness.annotations.dev.HarnessTeam.CV;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.delegate.beans.NgSetupFields.NG;
import static io.harness.delegate.beans.NgSetupFields.OWNER;
import static io.harness.delegate.beans.TaskData.DEFAULT_SYNC_CALL_TIMEOUT;

import static software.wings.beans.CGConstants.GLOBAL_APP_ID;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.DecryptableEntity;
import io.harness.cvng.beans.CVDataCollectionInfo;
import io.harness.cvng.beans.CVNGPerpetualTaskDTO;
import io.harness.cvng.beans.CVNGPerpetualTaskDTO.CVNGPerpetualTaskDTOBuilder;
import io.harness.cvng.beans.CVNGPerpetualTaskState;
import io.harness.cvng.beans.CVNGPerpetualTaskUnassignedReason;
import io.harness.cvng.beans.DataCollectionConnectorBundle;
import io.harness.cvng.beans.DataCollectionRequest;
import io.harness.cvng.beans.K8ActivityDataCollectionInfo;
import io.harness.delegate.Capability;
import io.harness.delegate.beans.connector.k8Connector.KubernetesClusterConfigDTO;
import io.harness.delegate.beans.connector.k8Connector.KubernetesClusterDetailsDTO;
import io.harness.delegate.beans.connector.k8Connector.KubernetesCredentialDTO;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.capability.EncryptedDataDetailsCapabilityHelper;
import io.harness.exception.UnknownEnumTypeException;
import io.harness.govern.Switch;
import io.harness.ng.core.BaseNGAccess;
import io.harness.ng.core.CorrelationContext;
import io.harness.ng.core.NGAccess;
import io.harness.ng.core.NGAccessWithEncryptionConsumer;
import io.harness.perpetualtask.PerpetualTaskClientContext;
import io.harness.perpetualtask.PerpetualTaskExecutionBundle;
import io.harness.perpetualtask.PerpetualTaskSchedule;
import io.harness.perpetualtask.PerpetualTaskService;
import io.harness.perpetualtask.PerpetualTaskState;
import io.harness.perpetualtask.PerpetualTaskType;
import io.harness.perpetualtask.PerpetualTaskUnassignedReason;
import io.harness.perpetualtask.datacollection.DataCollectionPerpetualTaskParams;
import io.harness.perpetualtask.datacollection.K8ActivityCollectionPerpetualTaskParams;
import io.harness.perpetualtask.internal.PerpetualTaskRecord;
import io.harness.remote.client.NGRestUtils;
import io.harness.secrets.remote.SecretNGManagerClient;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.serializer.KryoSerializer;

import software.wings.beans.SyncTaskContext;
import software.wings.delegatetasks.DelegateProxyFactory;
import software.wings.delegatetasks.cvng.K8InfoDataService;
import software.wings.service.intfc.cvng.CVNGDataCollectionDelegateService;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import com.google.protobuf.util.Durations;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.groovy.util.Maps;
import org.jetbrains.annotations.NotNull;

@OwnedBy(CV)
public class CVDataCollectionTaskServiceImpl implements CVDataCollectionTaskService {
  @Inject private PerpetualTaskService perpetualTaskService;
  @Inject @Named("referenceFalseKryoSerializer") private KryoSerializer referenceFalseKryoSerializer;
  @Inject @Named("PRIVILEGED") private SecretNGManagerClient secretNGManagerClient;
  @Inject private DelegateProxyFactory delegateProxyFactory;

  @Override
  public void resetTask(String accountId, String orgIdentifier, String projectIdentifier, String taskId,
      DataCollectionConnectorBundle bundle) {
    PerpetualTaskExecutionBundle executionBundle =
        createExecutionBundle(accountId, orgIdentifier, projectIdentifier, bundle);
    perpetualTaskService.resetTask(accountId, taskId, executionBundle);
  }

  @Override
  public String create(
      String accountId, String orgIdentifier, String projectIdentifier, DataCollectionConnectorBundle bundle) {
    String taskType;
    PerpetualTaskExecutionBundle executionBundle =
        createExecutionBundle(accountId, orgIdentifier, projectIdentifier, bundle);
    switch (bundle.getDataCollectionType()) {
      case CV:
        taskType = PerpetualTaskType.DATA_COLLECTION_TASK;
        break;
      case KUBERNETES:
        taskType = PerpetualTaskType.K8_ACTIVITY_COLLECTION_TASK;
        break;
      default:
        throw new IllegalStateException("Invalid type " + bundle.getDataCollectionType());
    }
    PerpetualTaskClientContext clientContext = PerpetualTaskClientContext.builder()
                                                   .clientId(bundle.getDataCollectionWorkerId())
                                                   .executionBundle(executionBundle.toByteArray())
                                                   .build();
    PerpetualTaskSchedule schedule = PerpetualTaskSchedule.newBuilder()
                                         .setInterval(Durations.fromMinutes(1))
                                         .setTimeout(Durations.fromHours(3))
                                         .build();
    return perpetualTaskService.createTask(taskType, accountId, clientContext, schedule, false, "");
  }

  private PerpetualTaskExecutionBundle createExecutionBundle(
      String accountId, String orgIdentifier, String projectIdentifier, DataCollectionConnectorBundle bundle) {
    List<List<EncryptedDataDetail>> encryptedDataDetailList =
        getEncryptedDataDetail(accountId, orgIdentifier, projectIdentifier, bundle);
    CVDataCollectionInfo cvDataCollectionInfo = CVDataCollectionInfo.builder()
                                                    .connectorConfigDTO(bundle.getConnectorDTO().getConnectorConfig())
                                                    .encryptedDataDetails(encryptedDataDetailList)
                                                    .dataCollectionType(bundle.getDataCollectionType())
                                                    .build();
    Any perpetualTaskPack;
    switch (bundle.getDataCollectionType()) {
      case CV:
        DataCollectionPerpetualTaskParams params =
            DataCollectionPerpetualTaskParams.newBuilder()
                .setAccountId(accountId)
                .setDataCollectionWorkerId(bundle.getDataCollectionWorkerId())
                .setDataCollectionInfo(ByteString.copyFrom(referenceFalseKryoSerializer.asBytes(cvDataCollectionInfo)))
                .build();
        perpetualTaskPack = Any.pack(params);
        break;
      case KUBERNETES:
        cvDataCollectionInfo = K8ActivityDataCollectionInfo.builder()
                                   .connectorConfigDTO(bundle.getConnectorDTO().getConnectorConfig())
                                   .encryptedDataDetails(encryptedDataDetailList)
                                   .dataCollectionType(bundle.getDataCollectionType())
                                   .projectIdentifier(bundle.getProjectIdentifier())
                                   .orgIdentifier(bundle.getOrgIdentifier())
                                   .envIdentifier(bundle.getEnvIdentifier())
                                   .serviceIdentifier(bundle.getServiceIdentifier())
                                   .monitoredServiceIdentifier(bundle.getMonitoredServiceIdentifier())
                                   .changeSourceIdentifier(bundle.getSourceIdentifier())
                                   .build();
        K8ActivityCollectionPerpetualTaskParams k8ActivityCollectionPerpetualTaskParams =
            K8ActivityCollectionPerpetualTaskParams.newBuilder()
                .setAccountId(accountId)
                .setDataCollectionWorkerId(bundle.getDataCollectionWorkerId())
                .setDataCollectionInfo(ByteString.copyFrom(referenceFalseKryoSerializer.asBytes(cvDataCollectionInfo)))
                .build();
        perpetualTaskPack = Any.pack(k8ActivityCollectionPerpetualTaskParams);
        break;
      default:
        throw new IllegalStateException("Invalid type " + bundle.getDataCollectionType());
    }
    List<ExecutionCapability> executionCapabilities = new ArrayList<>();
    for (List<EncryptedDataDetail> encryptedDataDetails : encryptedDataDetailList) {
      executionCapabilities = Stream
                                  .concat(executionCapabilities.stream(),
                                      EncryptedDataDetailsCapabilityHelper
                                          .fetchExecutionCapabilitiesForEncryptedDataDetails(encryptedDataDetails, null)
                                          .stream())
                                  .collect(Collectors.toList());
    }
    executionCapabilities.addAll(bundle.fetchRequiredExecutionCapabilities(null));
    return createPerpetualTaskExecutionBundle(
        perpetualTaskPack, executionCapabilities, orgIdentifier, projectIdentifier);
  }

  @NotNull
  private PerpetualTaskExecutionBundle createPerpetualTaskExecutionBundle(Any perpetualTaskPack,
      List<ExecutionCapability> executionCapabilities, String orgIdentifier, String projectIdentifier) {
    PerpetualTaskExecutionBundle.Builder builder = PerpetualTaskExecutionBundle.newBuilder();
    executionCapabilities.forEach(executionCapability
        -> builder
               .addCapabilities(Capability.newBuilder()
                                    .setKryoCapability(ByteString.copyFrom(
                                        referenceFalseKryoSerializer.asDeflatedBytes(executionCapability)))
                                    .build())
               .build());
    return builder.setTaskParams(perpetualTaskPack)
        .putAllSetupAbstractions(Maps.of(NG, "true", OWNER, orgIdentifier + "/" + projectIdentifier))
        .build();
  }

  private List<List<EncryptedDataDetail>> getEncryptedDataDetail(
      String accountId, String orgIdentifier, String projectIdentifier, DataCollectionConnectorBundle bundle) {
    NGAccess basicNGAccessObject = BaseNGAccess.builder()
                                       .accountIdentifier(accountId)
                                       .orgIdentifier(orgIdentifier)
                                       .projectIdentifier(projectIdentifier)
                                       .build();
    List<List<EncryptedDataDetail>> encryptedData = new ArrayList<>();
    switch (bundle.getDataCollectionType()) {
      case CV:
        List<DecryptableEntity> decryptableEntities =
            bundle.getConnectorDTO().getConnectorConfig().getDecryptableEntities();

        if (isNotEmpty(decryptableEntities)) {
          for (int decryptableEntityIndex = 0; decryptableEntityIndex < decryptableEntities.size();
               decryptableEntityIndex++) {
            encryptedData.add(getEncryptedDataDetails(basicNGAccessObject,
                bundle.getConnectorDTO().getConnectorConfig().getDecryptableEntities().get(decryptableEntityIndex)));
          }
        }
        return encryptedData;
      case KUBERNETES:
        KubernetesClusterConfigDTO kubernetesClusterConfigDTO =
            (KubernetesClusterConfigDTO) bundle.getConnectorDTO().getConnectorConfig();
        KubernetesCredentialDTO credential = kubernetesClusterConfigDTO.getCredential();
        if (!credential.getKubernetesCredentialType().isDecryptable()) {
          return new ArrayList<>();
        }
        encryptedData.add(getEncryptedDataDetails(
            basicNGAccessObject, ((KubernetesClusterDetailsDTO) credential.getConfig()).getAuth().getCredentials()));
        return encryptedData;
      default:
        Switch.unhandled(bundle.getDataCollectionType());
        throw new IllegalStateException("invalid type " + bundle.getDataCollectionType());
    }
  }

  private List<EncryptedDataDetail> getEncryptedDataDetails(
      NGAccess basicNgAccessObject, DecryptableEntity decryptableEntity) {
    return NGRestUtils.getResponse(
        secretNGManagerClient.getEncryptionDetails(basicNgAccessObject.getAccountIdentifier(),
            NGAccessWithEncryptionConsumer.builder()
                .ngAccess(basicNgAccessObject)
                .decryptableEntity(decryptableEntity)
                .build()));
  }

  @Override
  public void delete(String accountId, String taskId) {
    perpetualTaskService.deleteTask(accountId, taskId);
  }

  @Override
  public CVNGPerpetualTaskDTO getCVNGPerpetualTaskDTO(String taskId) {
    PerpetualTaskRecord perpetualTaskRecord = perpetualTaskService.getTaskRecord(taskId);
    CVNGPerpetualTaskDTOBuilder cvngPerpetualTaskDTOBuilder =
        CVNGPerpetualTaskDTO.builder().accountId(perpetualTaskRecord.getAccountId());
    if (perpetualTaskRecord.getDelegateId() != null) {
      cvngPerpetualTaskDTOBuilder.delegateId(perpetualTaskRecord.getDelegateId());
    }
    if (perpetualTaskRecord.getUnassignedReason() != null) {
      cvngPerpetualTaskDTOBuilder.cvngPerpetualTaskUnassignedReason(
          mapUnassignedReasonFromPerpetualTaskToCVNG(perpetualTaskRecord.getUnassignedReason()));
    }
    if (perpetualTaskRecord.getState() != null) {
      cvngPerpetualTaskDTOBuilder.cvngPerpetualTaskState(
          mapStateFromPerpetualTaskToCVNG(perpetualTaskRecord.getState()));
    }
    return cvngPerpetualTaskDTOBuilder.build();
  }

  private CVNGPerpetualTaskUnassignedReason mapUnassignedReasonFromPerpetualTaskToCVNG(
      PerpetualTaskUnassignedReason perpetualTaskUnassignedReason) {
    switch (perpetualTaskUnassignedReason) {
      case NO_DELEGATE_AVAILABLE:
        return CVNGPerpetualTaskUnassignedReason.NO_DELEGATE_AVAILABLE;
      case NO_DELEGATE_INSTALLED:
        return CVNGPerpetualTaskUnassignedReason.NO_DELEGATE_INSTALLED;
      case NO_ELIGIBLE_DELEGATES:
        return CVNGPerpetualTaskUnassignedReason.NO_ELIGIBLE_DELEGATES;
      case MULTIPLE_FAILED_PERPETUAL_TASK:
        return CVNGPerpetualTaskUnassignedReason.MULTIPLE_FAILED_PERPETUAL_TASK;
      case VALIDATION_TASK_FAILED:
        return CVNGPerpetualTaskUnassignedReason.VALIDATION_TASK_FAILED;
      case PT_TASK_FAILED:
        return CVNGPerpetualTaskUnassignedReason.PT_TASK_FAILED;
      case TASK_EXPIRED:
        return CVNGPerpetualTaskUnassignedReason.TASK_EXPIRED;
      case TASK_VALIDATION_FAILED:
        return CVNGPerpetualTaskUnassignedReason.TASK_VALIDATION_FAILED;
      default:
        throw new UnknownEnumTypeException("Task Unassigned Reason", String.valueOf(perpetualTaskUnassignedReason));
    }
  }

  private CVNGPerpetualTaskState mapStateFromPerpetualTaskToCVNG(PerpetualTaskState perpetualTaskState) {
    switch (perpetualTaskState) {
      case TASK_ASSIGNED:
        return CVNGPerpetualTaskState.TASK_ASSIGNED;
      case TASK_UNASSIGNED:
        return CVNGPerpetualTaskState.TASK_UNASSIGNED;
      case TASK_PAUSED:
        return CVNGPerpetualTaskState.TASK_PAUSED;
      case TASK_TO_REBALANCE:
        return CVNGPerpetualTaskState.TASK_TO_REBALANCE;
      case TASK_NON_ASSIGNABLE:
        return CVNGPerpetualTaskState.TASK_NON_ASSIGNABLE;
      case TASK_INVALID:
        return CVNGPerpetualTaskState.TASK_INVALID;
      case NO_DELEGATE_INSTALLED:
      case NO_DELEGATE_AVAILABLE:
      case NO_ELIGIBLE_DELEGATES:
      case TASK_RUN_SUCCEEDED:
      case TASK_RUN_FAILED:
        return null;
      default:
        throw new UnknownEnumTypeException("Perpetual task state", String.valueOf(perpetualTaskState));
    }
  }

  @Override
  public String getDataCollectionResult(
      String accountId, String orgIdentifier, String projectIdentifier, DataCollectionRequest dataCollectionRequest) {
    NGAccess basicNGAccessObject = BaseNGAccess.builder()
                                       .accountIdentifier(accountId)
                                       .orgIdentifier(orgIdentifier)
                                       .projectIdentifier(projectIdentifier)
                                       .build();
    List<List<EncryptedDataDetail>> encryptedDataDetails = new ArrayList<>();
    List<DecryptableEntity> decryptableEntities =
        dataCollectionRequest.getConnectorConfigDTO().getDecryptableEntities();

    if (isNotEmpty(decryptableEntities)) {
      decryptableEntities.forEach(decryptableEntity
          -> encryptedDataDetails.add(getEncryptedDataDetails(basicNGAccessObject, decryptableEntity)));
    }

    SyncTaskContext taskContext = getSyncTaskContext(accountId, orgIdentifier, projectIdentifier);
    return delegateProxyFactory.getV2(CVNGDataCollectionDelegateService.class, taskContext)
        .getDataCollectionResult(accountId, dataCollectionRequest, encryptedDataDetails);
  }

  private SyncTaskContext getSyncTaskContext(String accountId, String orgIdentifier, String projectIdentifier) {
    return SyncTaskContext.builder()
        .accountId(accountId)
        .orgIdentifier(orgIdentifier)
        .projectIdentifier(projectIdentifier)
        .correlationId(CorrelationContext.getCorrelationId())
        .appId(GLOBAL_APP_ID)
        .timeout(DEFAULT_SYNC_CALL_TIMEOUT)
        .ngTask(true)
        .build();
  }

  public List<String> getNamespaces(String accountId, String orgIdentifier, String projectIdentifier, String filter,
      DataCollectionConnectorBundle bundle) {
    List<List<EncryptedDataDetail>> encryptedDataDetails =
        getEncryptedDataDetail(accountId, orgIdentifier, projectIdentifier, bundle);
    SyncTaskContext syncTaskContext = getSyncTaskContext(accountId, orgIdentifier, projectIdentifier);
    return delegateProxyFactory.getV2(K8InfoDataService.class, syncTaskContext)
        .getNameSpaces(bundle, isNotEmpty(encryptedDataDetails) ? encryptedDataDetails.get(0) : null, filter);
  }

  @Override
  public List<String> getWorkloads(String accountId, String orgIdentifier, String projectIdentifier, String namespace,
      String filter, DataCollectionConnectorBundle bundle) {
    List<List<EncryptedDataDetail>> encryptedDataDetails =
        getEncryptedDataDetail(accountId, orgIdentifier, projectIdentifier, bundle);
    SyncTaskContext syncTaskContext = getSyncTaskContext(accountId, orgIdentifier, projectIdentifier);
    return delegateProxyFactory.getV2(K8InfoDataService.class, syncTaskContext)
        .getWorkloads(namespace, bundle, isNotEmpty(encryptedDataDetails) ? encryptedDataDetails.get(0) : null, filter);
  }

  @Override
  public List<String> checkCapabilityToGetEvents(
      String accountId, String orgIdentifier, String projectIdentifier, DataCollectionConnectorBundle bundle) {
    List<List<EncryptedDataDetail>> encryptedDataDetails =
        getEncryptedDataDetail(accountId, orgIdentifier, projectIdentifier, bundle);
    SyncTaskContext syncTaskContext = getSyncTaskContext(accountId, orgIdentifier, projectIdentifier);
    return delegateProxyFactory.getV2(K8InfoDataService.class, syncTaskContext)
        .checkCapabilityToGetEvents(bundle, isNotEmpty(encryptedDataDetails) ? encryptedDataDetails.get(0) : null);
  }
}
