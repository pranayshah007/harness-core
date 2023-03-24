/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.perpetualtask;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.beans.DelegateTask.DELEGATE_QUEUE_TIMEOUT;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static software.wings.beans.CGConstants.GLOBAL_APP_ID;
import static software.wings.service.InstanceSyncConstants.HARNESS_APPLICATION_ID;
import static software.wings.service.InstanceSyncConstants.INFRASTRUCTURE_MAPPING_ID;

import static java.util.Collections.singletonList;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.Cd1SetupFields;
import io.harness.beans.DelegateTask;
import io.harness.delegate.beans.TaskData;
import io.harness.perpetualtask.instancesync.AwsAmiInstanceSyncPerpetualTaskParams;
import io.harness.security.encryption.EncryptedDataDetail;

import software.wings.beans.AwsAmiInfrastructureMapping;
import software.wings.beans.AwsConfig;
import software.wings.beans.SettingAttribute;
import software.wings.beans.TaskType;
import software.wings.service.InstanceSyncConstants;
import software.wings.service.impl.aws.model.AwsAsgListInstancesRequest;
import software.wings.service.intfc.InfrastructureMappingService;
import software.wings.service.intfc.SettingsService;
import software.wings.service.intfc.security.SecretManager;

import com.google.inject.Inject;
import com.google.protobuf.ByteString;
import com.google.protobuf.Message;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;

@FieldDefaults(level = AccessLevel.PRIVATE)
@OwnedBy(CDP)
public class AwsAmiInstanceSyncPerpetualTaskClient
    extends PerpetualTaskClientBase implements PerpetualTaskServiceClient {
  public static final String ASG_NAME = "asgName";
  @Inject SecretManager secretManager;
  @Inject SettingsService settingsService;
  @Inject InfrastructureMappingService infraMappingService;

  @Override
  public Message getTaskParams(PerpetualTaskClientContext clientContext, boolean referenceFalse) {
    final PerpetualTaskData perpetualTaskData = getPerpetualTaskData(clientContext);

    ByteString configBytes =
        ByteString.copyFrom(getKryoSerializer(referenceFalse).asBytes(perpetualTaskData.getAwsConfig()));
    ByteString encryptedConfigBytes =
        ByteString.copyFrom(getKryoSerializer(referenceFalse).asBytes(perpetualTaskData.getEncryptedDataDetails()));

    return AwsAmiInstanceSyncPerpetualTaskParams.newBuilder()
        .setRegion(perpetualTaskData.getRegion())
        .setAsgName(perpetualTaskData.getAsgName())
        .setAwsConfig(configBytes)
        .setEncryptedData(encryptedConfigBytes)
        .build();
  }

  @Override
  public DelegateTask getValidationTask(PerpetualTaskClientContext clientContext, String accountId) {
    final PerpetualTaskData perpetualTaskData = getPerpetualTaskData(clientContext);
    return DelegateTask.builder()
        .accountId(perpetualTaskData.getAwsConfig().getAccountId())
        .setupAbstraction(Cd1SetupFields.APP_ID_FIELD, GLOBAL_APP_ID)
        .tags(isNotEmpty(perpetualTaskData.getAwsConfig().getTag())
                ? singletonList(perpetualTaskData.getAwsConfig().getTag())
                : null)
        .data(TaskData.builder()
                  .async(false)
                  .taskType(TaskType.AWS_ASG_TASK.name())
                  .parameters(new Object[] {AwsAsgListInstancesRequest.builder()
                                                .awsConfig(perpetualTaskData.getAwsConfig())
                                                .encryptionDetails(perpetualTaskData.getEncryptedDataDetails())
                                                .region(perpetualTaskData.getRegion())
                                                .autoScalingGroupName(perpetualTaskData.getAsgName())
                                                .build()})
                  .timeout(TimeUnit.MINUTES.toMillis(InstanceSyncConstants.VALIDATION_TIMEOUT_MINUTES))
                  .build())
        .expiry(System.currentTimeMillis() + DELEGATE_QUEUE_TIMEOUT)
        .build();
  }

  private PerpetualTaskData getPerpetualTaskData(PerpetualTaskClientContext clientContext) {
    Map<String, String> clientParams = clientContext.getClientParams();
    String appId = clientParams.get(HARNESS_APPLICATION_ID);
    String infraMappingId = clientParams.get(INFRASTRUCTURE_MAPPING_ID);
    AwsAmiInfrastructureMapping infraMapping =
        (AwsAmiInfrastructureMapping) infraMappingService.get(appId, infraMappingId);

    SettingAttribute settingAttribute = settingsService.get(infraMapping.getComputeProviderSettingId());
    AwsConfig awsConfig = (AwsConfig) settingAttribute.getValue();
    List<EncryptedDataDetail> encryptionDetails = secretManager.getEncryptionDetails(awsConfig, null, null);

    return PerpetualTaskData.builder()
        .region(infraMapping.getRegion())
        .awsConfig(awsConfig)
        .asgName(clientContext.getClientParams().get(ASG_NAME))
        .encryptedDataDetails(encryptionDetails)
        .build();
  }

  @Data
  @Builder
  static class PerpetualTaskData {
    String region;
    AwsConfig awsConfig;
    String asgName;
    List<EncryptedDataDetail> encryptedDataDetails;
  }
}
