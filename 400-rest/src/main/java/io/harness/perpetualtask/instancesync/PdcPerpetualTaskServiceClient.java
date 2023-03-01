/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.perpetualtask.instancesync;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.beans.DelegateTask.DELEGATE_QUEUE_TIMEOUT;

import static software.wings.beans.CGConstants.GLOBAL_APP_ID;

import static java.util.stream.Collectors.toList;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.Cd1SetupFields;
import io.harness.beans.DelegateTask;
import io.harness.delegate.beans.TaskData;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.perpetualtask.PerpetualTaskClientContext;
import io.harness.perpetualtask.PerpetualTaskServiceClient;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.serializer.KryoSerializer;

import software.wings.beans.HostConnectionAttributes;
import software.wings.beans.HostValidationTaskParameters;
import software.wings.beans.InfrastructureMapping;
import software.wings.beans.PhysicalInfrastructureMapping;
import software.wings.beans.PhysicalInfrastructureMappingBase;
import software.wings.beans.PhysicalInfrastructureMappingWinRm;
import software.wings.beans.SettingAttribute;
import software.wings.beans.TaskType;
import software.wings.beans.WinRmConnectionAttributes;
import software.wings.service.InstanceSyncConstants;
import software.wings.service.intfc.InfrastructureMappingService;
import software.wings.service.intfc.SettingsService;
import software.wings.service.intfc.security.SecretManager;

import com.google.inject.Inject;
import com.google.protobuf.ByteString;
import com.google.protobuf.Message;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(CDP)
public class PdcPerpetualTaskServiceClient implements PerpetualTaskServiceClient {
  @Inject private SecretManager secretManager;
  @Inject private InfrastructureMappingService infrastructureMappingService;
  @Inject private SettingsService settingsService;
  @Inject private KryoSerializer kryoSerializer;

  @Override
  public Message getTaskParams(PerpetualTaskClientContext clientContext) {
    final PerpetualTaskData taskData = getPerpetualTaskData(clientContext);

    ByteString settingAttributeBytes =
        ByteString.copyFrom(kryoSerializer.asBytes(taskData.getSettingAttribute().toDTO()));
    ByteString encryptionDetailsBytes = ByteString.copyFrom(kryoSerializer.asBytes(taskData.getEncryptedDataDetails()));

    return PdcInstanceSyncPerpetualTaskParams.newBuilder()
        .addAllHostNames(taskData.getHostNames())
        .setEncryptedData(encryptionDetailsBytes)
        .setSettingAttribute(settingAttributeBytes)
        .build();
  }

  @Override
  public DelegateTask getValidationTask(PerpetualTaskClientContext clientContext, String accountId) {
    final PerpetualTaskData taskData = getPerpetualTaskData(clientContext);

    HostValidationTaskParameters parameters = HostValidationTaskParameters.builder()
                                                  .hostNames(taskData.getHostNames())
                                                  .connectionSetting(taskData.getSettingAttribute().toDTO())
                                                  .encryptionDetails(taskData.getEncryptedDataDetails())
                                                  .checkOnlyReachability(true)
                                                  .checkOr(true)
                                                  .build();

    return DelegateTask.builder()
        .accountId(accountId)
        .setupAbstraction(Cd1SetupFields.APP_ID_FIELD, GLOBAL_APP_ID)
        .data(TaskData.builder()
                  .async(false)
                  .taskType(TaskType.HOST_VALIDATION.name())
                  .parameters(new Object[] {parameters})
                  .timeout(TimeUnit.MINUTES.toMillis(InstanceSyncConstants.VALIDATION_TIMEOUT_MINUTES))
                  .build())
        .expiry(System.currentTimeMillis() + DELEGATE_QUEUE_TIMEOUT)
        .build();
  }

  private PerpetualTaskData getPerpetualTaskData(PerpetualTaskClientContext clientContext) {
    String infraMappingId = getInfraMappingId(clientContext);
    String appId = getAppId(clientContext);
    List<String> hostNames = getHostNames(clientContext);

    InfrastructureMapping infrastructureMapping = infrastructureMappingService.get(appId, infraMappingId);

    if (!(infrastructureMapping instanceof PhysicalInfrastructureMappingBase)) {
      String msg = "Incompatible infra mapping type. Expecting PhysicalInfrastructureMappingBase type. Found:"
          + infrastructureMapping.getInfraMappingType();
      log.error(msg);
      throw new InvalidRequestException(msg);
    }

    SettingAttribute settingAttribute;
    List<EncryptedDataDetail> encryptedDataDetails;

    if (infrastructureMapping instanceof PhysicalInfrastructureMapping) {
      PhysicalInfrastructureMapping mapping = (PhysicalInfrastructureMapping) infrastructureMapping;
      settingAttribute = settingsService.get(mapping.getHostConnectionAttrs());
      HostConnectionAttributes value = (HostConnectionAttributes) settingAttribute.getValue();
      encryptedDataDetails = secretManager.getEncryptionDetails(value, null, null);
    } else if (infrastructureMapping instanceof PhysicalInfrastructureMappingWinRm) {
      PhysicalInfrastructureMappingWinRm mapping = (PhysicalInfrastructureMappingWinRm) infrastructureMapping;
      settingAttribute = settingsService.get(mapping.getWinRmConnectionAttributes());
      WinRmConnectionAttributes value = (WinRmConnectionAttributes) settingAttribute.getValue();
      encryptedDataDetails = secretManager.getEncryptionDetails(value, null, null);
    } else {
      String msg = "Unexpected infra mapping type found:" + infrastructureMapping.getInfraMappingType();
      log.error(msg);
      throw WingsException.builder().message(msg).build();
    }

    return PerpetualTaskData.builder()
        .hostNames(hostNames)
        .settingAttribute(settingAttribute)
        .encryptedDataDetails(encryptedDataDetails)
        .build();
  }

  private String getAppId(PerpetualTaskClientContext clientContext) {
    return clientContext.getClientParams().get(InstanceSyncConstants.HARNESS_APPLICATION_ID);
  }

  private String getInfraMappingId(PerpetualTaskClientContext clientContext) {
    return clientContext.getClientParams().get(InstanceSyncConstants.INFRASTRUCTURE_MAPPING_ID);
  }

  private List<String> getHostNames(PerpetualTaskClientContext clientContext) {
    String hostNames = clientContext.getClientParams().get(InstanceSyncConstants.HOSTNAMES);
    return Stream.of(hostNames.split(",")).map(String::trim).collect(toList());
  }

  @Data
  @Builder
  private static class PerpetualTaskData {
    private List<String> hostNames;
    private SettingAttribute settingAttribute;
    private List<EncryptedDataDetail> encryptedDataDetails;
  }
}
