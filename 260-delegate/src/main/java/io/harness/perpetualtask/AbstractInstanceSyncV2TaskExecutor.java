/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.perpetualtask;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.network.SafeHttpCall.execute;

import static java.lang.String.format;
import static javax.servlet.http.HttpServletResponse.SC_NOT_FOUND;
import static javax.servlet.http.HttpServletResponse.SC_OK;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.DecryptableEntity;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.delegate.beans.instancesync.ServerInstanceInfo;
import io.harness.exception.sanitizer.ExceptionMessageSanitizer;
import io.harness.logging.CommandExecutionStatus;
import io.harness.managerclient.DelegateAgentManagerClient;
import io.harness.ng.beans.PageResponse;
import io.harness.perpetualtask.instancesync.DeploymentReleaseDetails;
import io.harness.perpetualtask.instancesync.InstanceSyncData;
import io.harness.perpetualtask.instancesync.InstanceSyncResponseV2;
import io.harness.perpetualtask.instancesync.InstanceSyncStatus;
import io.harness.perpetualtask.instancesync.InstanceSyncTaskDetails;
import io.harness.perpetualtask.instancesync.InstanceSyncV2Request;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.security.encryption.SecretDecryptionService;
import io.harness.serializer.KryoSerializer;

import com.google.inject.Inject;
import com.google.protobuf.ByteString;
import java.io.IOException;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@TargetModule(HarnessModule._930_DELEGATE_TASKS)
@OwnedBy(CDP)
abstract class AbstractInstanceSyncV2TaskExecutor implements PerpetualTaskExecutor {
  private static final String SUCCESS_RESPONSE_MSG = "success";
  private static final int PAGE_SIZE = 100;
  private static final String FAILURE_RESPONSE_MSG =
      "Failed to fetch InstanceSyncTaskDetails for perpetual task Id: [%s], accountId [%s]";

  @Inject private KryoSerializer kryoSerializer;
  @Inject private DelegateAgentManagerClient delegateAgentManagerClient;
  @Inject private SecretDecryptionService secretDecryptionService;

  @Override
  public PerpetualTaskResponse runOnce(
      PerpetualTaskId taskId, PerpetualTaskExecutionParams params, Instant heartbeatTime) {
    log.info("Running the K8s InstanceSync perpetual task executor for task id: {}", taskId);
    AtomicInteger batchInstanceCount = new AtomicInteger(0);
    AtomicInteger batchReleaseDetailsCount = new AtomicInteger(0);
    InstanceSyncV2Request instanceSyncV2Request = createRequest(taskId.getId(), params);
    String accountId = instanceSyncV2Request.getAccountId();
    InstanceSyncResponseV2.Builder responseBuilder =
        InstanceSyncResponseV2.newBuilder().setPerpetualTaskId(taskId.getId()).setAccountId(accountId);
    try {
      InstanceSyncTaskDetails instanceSyncTaskDetails =
          execute(delegateAgentManagerClient.fetchInstanceSyncV2TaskDetails(taskId.getId(), 0, PAGE_SIZE, accountId));

      if (Objects.isNull(instanceSyncTaskDetails) || Objects.isNull(instanceSyncTaskDetails.getDetails())
          || instanceSyncTaskDetails.getDetails().isEmpty()) {
        log.error("No deployments to track for perpetualTaskId: [{}]. Nothing to do here.", taskId.getId());
        publishInstanceSyncResult(taskId, accountId,
            InstanceSyncResponseV2.newBuilder()
                .setStatus(InstanceSyncStatus.newBuilder()
                               .setIsSuccessful(false)
                               .setExecutionStatus(CommandExecutionStatus.SKIPPED.name())
                               .build())
                .build());
        return PerpetualTaskResponse.builder().responseCode(SC_OK).responseMessage(SUCCESS_RESPONSE_MSG).build();
      }

      long totalPages = instanceSyncTaskDetails.getDetails().getTotalPages();

      for (int page = 0; page < totalPages; page++) {
        if (page != 0) {
          instanceSyncTaskDetails = execute(
              delegateAgentManagerClient.fetchInstanceSyncV2TaskDetails(taskId.getId(), page, PAGE_SIZE, accountId));
        }

        if (Objects.isNull(instanceSyncTaskDetails) || instanceSyncTaskDetails.getDetails().isEmpty()) {
          log.error("No deployments to track for perpetualTaskId: [{}]. Nothing to do here.", taskId.getId());
          continue;
        }
        PageResponse<DeploymentReleaseDetails> deploymentReleaseDetailsList = instanceSyncTaskDetails.getDetails();

        for (DeploymentReleaseDetails deploymentReleaseDetails : deploymentReleaseDetailsList.getContent()) {
          InstanceSyncData.Builder instanceSyncData = InstanceSyncData.newBuilder();
          List<ServerInstanceInfo> serverInstanceInfos =
              getServiceInstancesFromCluster(instanceSyncV2Request, deploymentReleaseDetails, instanceSyncData);

          createBatchAndPublish(batchInstanceCount, batchReleaseDetailsCount, responseBuilder, instanceSyncData.build(),
              serverInstanceInfos, instanceSyncTaskDetails, taskId, accountId);
        }
      }
      if (batchInstanceCount.get() != 0 || batchReleaseDetailsCount.get() != 0) {
        publishInstanceSyncResult(taskId, accountId,
            responseBuilder
                .setStatus(InstanceSyncStatus.newBuilder()
                               .setExecutionStatus(CommandExecutionStatus.SUCCESS.name())
                               .setIsSuccessful(true)
                               .build())
                .build());
      }
      return PerpetualTaskResponse.builder().responseCode(SC_OK).responseMessage(SUCCESS_RESPONSE_MSG).build();
    } catch (IOException ioException) {
      log.error(format(FAILURE_RESPONSE_MSG, taskId.getId(), accountId));
      return PerpetualTaskResponse.builder()
          .responseCode(SC_NOT_FOUND)
          .responseMessage(format(FAILURE_RESPONSE_MSG, taskId.getId(), accountId))
          .build();
    }
  }

  private List<ServerInstanceInfo> getServiceInstancesFromCluster(InstanceSyncV2Request instanceSyncV2Request,
      DeploymentReleaseDetails deploymentReleaseDetails, InstanceSyncData.Builder instanceSyncData) {
    try {
      List<ServerInstanceInfo> serverInstanceInfos =
          retrieveServiceInstances(instanceSyncV2Request, deploymentReleaseDetails);
      instanceSyncData.setServerInstanceInfo(ByteString.copyFrom(kryoSerializer.asBytes(serverInstanceInfos)))
          .setStatus(InstanceSyncStatus.newBuilder()
                         .setIsSuccessful(true)
                         .setExecutionStatus(CommandExecutionStatus.SUCCESS.name())
                         .build())
          .setTaskInfoId(deploymentReleaseDetails.getTaskInfoId())
          .setDeploymentType(deploymentReleaseDetails.getDeploymentType())
          .build();
      return serverInstanceInfos;
    } catch (Exception ex) {
      log.error("Failed to fetch InstanceSyncTaskDetails for perpetual task Id: {} for accountId: {}",
          instanceSyncV2Request.getPerpetualTaskId(), instanceSyncV2Request.getAccountId());
      instanceSyncData
          .setStatus(
              InstanceSyncStatus.newBuilder()
                  .setIsSuccessful(false)
                  .setErrorMessage(format("Failed to fetch serverInstanceInfos for DeploymentReleaseDetails [%s]",
                      deploymentReleaseDetails))
                  .setExecutionStatus(CommandExecutionStatus.FAILURE.name())
                  .build())
          .setTaskInfoId(deploymentReleaseDetails.getTaskInfoId())
          .build();
      return Collections.emptyList();
    }
  }

  private void createBatchAndPublish(AtomicInteger batchInstanceCount, AtomicInteger batchReleaseDetailsCount,
      InstanceSyncResponseV2.Builder responseBuilder, InstanceSyncData instanceSyncData,
      List<ServerInstanceInfo> serverInstanceInfos, InstanceSyncTaskDetails instanceSyncTaskDetails,
      PerpetualTaskId taskId, String accountId) {
    responseBuilder.addInstanceData(instanceSyncData);
    batchInstanceCount.addAndGet(serverInstanceInfos.size());
    batchReleaseDetailsCount.incrementAndGet();

    if (batchInstanceCount.get() > instanceSyncTaskDetails.getResponseBatchConfig().getInstanceCount()
        || batchReleaseDetailsCount.get() > instanceSyncTaskDetails.getResponseBatchConfig().getReleaseCount()) {
      publishInstanceSyncResult(taskId, accountId,
          responseBuilder
              .setStatus(InstanceSyncStatus.newBuilder()
                             .setIsSuccessful(true)
                             .setExecutionStatus(CommandExecutionStatus.SUCCESS.name())
                             .build())
              .build());
      responseBuilder.clearInstanceData();
      batchInstanceCount.set(0);
      batchReleaseDetailsCount.set(0);
    }
  }

  protected void decryptConnector(ConnectorInfoDTO connectorInfoDTO, List<EncryptedDataDetail> encryptedDataDetails) {
    List<DecryptableEntity> decryptableEntities = connectorInfoDTO.getConnectorConfig().getDecryptableEntities();
    if (isNotEmpty(decryptableEntities)) {
      decryptableEntities.forEach(entity -> {
        secretDecryptionService.decrypt(entity, encryptedDataDetails);
        ExceptionMessageSanitizer.storeAllSecretsForSanitizing(entity, encryptedDataDetails);
      });
    }
  }

  @Override
  public boolean cleanup(PerpetualTaskId taskId, PerpetualTaskExecutionParams params) {
    return false;
  }

  protected abstract InstanceSyncV2Request createRequest(String perpetualTaskId, PerpetualTaskExecutionParams params);

  protected abstract List<ServerInstanceInfo> retrieveServiceInstances(
      InstanceSyncV2Request instanceSyncV2Request, DeploymentReleaseDetails details);

  private void publishInstanceSyncResult(PerpetualTaskId taskId, String accountId, InstanceSyncResponseV2 response) {
    try {
      execute(delegateAgentManagerClient.processInstanceSyncNGResultV2(taskId.getId(), accountId, response));
    } catch (Exception e) {
      String errorMsg = format(
          "Failed to publish Instance Sync v2 result PerpetualTaskId [%s], accountId [%s]", taskId.getId(), accountId);
      log.error(errorMsg + ", InstanceSyncResponseV2: {}", response, e);
    }
  }
}
