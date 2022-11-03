/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.perpetualtask;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.network.SafeHttpCall.execute;

import static java.lang.String.format;
import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toList;
import static javax.servlet.http.HttpServletResponse.SC_OK;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.connector.task.spot.SpotConfig;
import io.harness.connector.task.spot.SpotNgConfigMapper;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.beans.connector.spotconnector.SpotConnectorDTO;
import io.harness.delegate.beans.instancesync.InstanceSyncPerpetualTaskResponse;
import io.harness.delegate.beans.instancesync.ServerInstanceInfo;
import io.harness.delegate.beans.instancesync.SpotInstanceSyncPerpetualTaskResponse;
import io.harness.delegate.beans.instancesync.info.SpotServerInstanceInfo;
import io.harness.exception.sanitizer.ExceptionMessageSanitizer;
import io.harness.grpc.utils.AnyUtils;
import io.harness.logging.CommandExecutionStatus;
import io.harness.managerclient.DelegateAgentManagerClient;
import io.harness.perpetualtask.instancesync.SpotinstAmiInstanceSyncPerpetualTaskParamsNg;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.security.encryption.SecretDecryptionService;
import io.harness.serializer.KryoSerializer;
import io.harness.spotinst.SpotInstHelperServiceDelegate;
import io.harness.spotinst.model.ElastiGroupInstanceHealth;
import io.harness.yaml.infra.HostConnectionTypeKind;

import software.wings.service.impl.aws.model.AwsEC2Instance;

import com.google.inject.Inject;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@TargetModule(HarnessModule._930_DELEGATE_TASKS)
@OwnedBy(CDP)
public class SpotinstPerpetualTaskExecutorNg implements PerpetualTaskExecutor {
  @Inject private DelegateAgentManagerClient delegateAgentManagerClient;
  @Inject private KryoSerializer kryoSerializer;
  @Inject protected SpotInstHelperServiceDelegate spotInstHelperServiceDelegate;
  @Inject private SpotNgConfigMapper ngConfigMapper;

  @Override
  public PerpetualTaskResponse runOnce(
      PerpetualTaskId taskId, PerpetualTaskExecutionParams params, Instant heartbeatTime) {
    log.info("Running the Aws InstanceSync perpetual task executor for task id: {}", taskId);
    SpotinstAmiInstanceSyncPerpetualTaskParamsNg taskParams =
        AnyUtils.unpack(params.getCustomizedParams(), SpotinstAmiInstanceSyncPerpetualTaskParamsNg.class);

    return executeTask(taskId, taskParams);
  }

  private PerpetualTaskResponse executeTask(
      PerpetualTaskId taskId, SpotinstAmiInstanceSyncPerpetualTaskParamsNg taskParams) {
    List<String> instanceIds = getInstanceIds(taskParams);

    List<ServerInstanceInfo> serverInstanceInfos =
        getServerInstanceInfoList(instanceIds, taskParams.getInfrastructureKey(), taskParams.getElastigroupId());

    log.info("Spot Instance sync Instances: {}, task id: {}",
        serverInstanceInfos == null ? 0 : serverInstanceInfos.size(), taskId);

    String instanceSyncResponseMsg = publishInstanceSyncResult(taskId, taskParams.getAccountId(), serverInstanceInfos);
    return PerpetualTaskResponse.builder().responseCode(SC_OK).responseMessage(instanceSyncResponseMsg).build();
  }

  private List<String> getInstanceIds(SpotinstAmiInstanceSyncPerpetualTaskParamsNg taskParams) {
    SpotConnectorDTO spotConnectorDTO =
        (SpotConnectorDTO) kryoSerializer.asObject(taskParams.getSpotinstConfig().toByteArray());
    List<EncryptedDataDetail> encryptionDetails =
        (List<EncryptedDataDetail>) kryoSerializer.asObject(taskParams.getSpotinstEncryptedData().toByteArray());
    SpotConfig spotConfig = ngConfigMapper.mapSpotConfigWithDecryption(spotConnectorDTO, encryptionDetails);
    String spotAccountId = spotConfig.getCredential().getSpotAccountId();
    String spotGroupId = taskParams.getElastigroupId();

    try {
      List<ElastiGroupInstanceHealth> instanceHealths = spotInstHelperServiceDelegate.listElastiGroupInstancesHealth(
          spotConfig.getCredential().getAppTokenId(), spotAccountId, spotGroupId);

      if (isEmpty(instanceHealths)) {
        return emptyList();
      }

      return instanceHealths.stream().map(ElastiGroupInstanceHealth::getInstanceId).collect(toList());
    } catch (Exception e) {
      log.error(
          "Unable to get list of Spot instances for spotAccountId:{} and groupId:{}", spotAccountId, spotGroupId, e);
      return emptyList();
    }
  }

  private List<ServerInstanceInfo> getServerInstanceInfoList(
      List<String> ec2Instances, String infrastructureKey, String elastigroupId) {
    return ec2Instances.stream()
        .map(ec2Instance -> mapToServerInstanceInfo(ec2Instance, infrastructureKey, elastigroupId))
        .collect(Collectors.toList());
  }
  private String publishInstanceSyncResult(
      PerpetualTaskId taskId, String accountId, List<ServerInstanceInfo> serverInstanceInfos) {
    InstanceSyncPerpetualTaskResponse instanceSyncResponse = SpotInstanceSyncPerpetualTaskResponse.builder()
                                                                 .serverInstanceDetails(serverInstanceInfos)
                                                                 .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
                                                                 .build();
    try {
      execute(delegateAgentManagerClient.processInstanceSyncNGResult(taskId.getId(), accountId, instanceSyncResponse));
    } catch (Exception e) {
      String errorMsg = format("Failed to publish Spot instance sync result PerpetualTaskId [%s], accountId [%s]",
          taskId.getId(), accountId);
      log.error(errorMsg + ", serverInstanceInfos: {}", serverInstanceInfos, e);
      return errorMsg;
    }
    return SUCCESS_RESPONSE_MSG;
  }

  private ServerInstanceInfo mapToServerInstanceInfo(
      String ec2InstanceId, String infrastructureKey, String elastigroupId) {
    return SpotServerInstanceInfo.builder()
        .infrastructureKey(infrastructureKey)
        .ec2InstanceId(ec2InstanceId)
        .elastigroupId(elastigroupId)
        .build();
  }

  @Override
  public boolean cleanup(PerpetualTaskId taskId, PerpetualTaskExecutionParams params) {
    return false;
  }
}
