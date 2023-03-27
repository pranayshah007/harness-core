/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.perpetualtask.instancesync;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.beans.DelegateTask.DELEGATE_QUEUE_TIMEOUT;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static software.wings.service.InstanceSyncConstants.HARNESS_APPLICATION_ID;
import static software.wings.service.InstanceSyncConstants.INFRASTRUCTURE_MAPPING_ID;

import static java.lang.Boolean.FALSE;
import static java.lang.Long.parseLong;
import static java.util.Collections.singletonList;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.DelegateTask;
import io.harness.delegate.beans.TaskData;
import io.harness.perpetualtask.PerpetualTaskClientBase;
import io.harness.perpetualtask.PerpetualTaskClientContext;
import io.harness.perpetualtask.PerpetualTaskServiceClient;
import io.harness.security.encryption.EncryptedDataDetail;

import software.wings.beans.AwsConfig;
import software.wings.beans.AwsLambdaInfraStructureMapping;
import software.wings.beans.SettingAttribute;
import software.wings.beans.TaskType;
import software.wings.service.InstanceSyncConstants;
import software.wings.service.impl.aws.model.request.AwsLambdaDetailsRequest;
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
@TargetModule(HarnessModule._360_CG_MANAGER)
public class AwsLambdaInstanceSyncPerpetualTaskClient
    extends PerpetualTaskClientBase implements PerpetualTaskServiceClient {
  public static final String FUNCTION_NAME = "functionName";
  public static final String QUALIFIER = "qualifier";
  public static final String START_DATE = "startDate";

  @Inject SecretManager secretManager;
  @Inject SettingsService settingsService;
  @Inject InfrastructureMappingService infraMappingService;

  @Override
  public Message getTaskParams(PerpetualTaskClientContext clientContext, boolean referenceFalse) {
    final PerpetualTaskData perpetualTaskData = getPerpetualTaskData(clientContext);

    ByteString awsConfigBytes =
        ByteString.copyFrom(getKryoSerializer(referenceFalse).asBytes(perpetualTaskData.getAwsConfig()));
    ByteString encryptedAwsConfigBytes =
        ByteString.copyFrom(getKryoSerializer(referenceFalse).asBytes(perpetualTaskData.getEncryptedDataDetails()));

    return AwsLambdaInstanceSyncPerpetualTaskParams.newBuilder()
        .setAwsConfig(awsConfigBytes)
        .setEncryptedData(encryptedAwsConfigBytes)
        .setRegion(perpetualTaskData.getRegion())
        .setFunctionName(perpetualTaskData.getFunctionName())
        .setQualifier(perpetualTaskData.getQualifier())
        .setStartDate(perpetualTaskData.getStartDate())
        .build();
  }

  @Override
  public DelegateTask getValidationTask(PerpetualTaskClientContext clientContext, String accountId) {
    final PerpetualTaskData perpetualTaskData = getPerpetualTaskData(clientContext);
    AwsLambdaDetailsRequest request = AwsLambdaDetailsRequest.builder()
                                          .awsConfig(perpetualTaskData.getAwsConfig())
                                          .encryptionDetails(perpetualTaskData.getEncryptedDataDetails())
                                          .region(perpetualTaskData.getRegion())
                                          .functionName(perpetualTaskData.getFunctionName())
                                          .qualifier(perpetualTaskData.getQualifier())
                                          .loadAliases(FALSE)
                                          .build();

    return DelegateTask.builder()
        .accountId(accountId)
        .tags(isNotEmpty(perpetualTaskData.getAwsConfig().getTag())
                ? singletonList(perpetualTaskData.getAwsConfig().getTag())
                : null)
        .data(TaskData.builder()
                  .async(false)
                  .taskType(TaskType.AWS_LAMBDA_TASK.name())
                  .parameters(new Object[] {request})
                  .timeout(TimeUnit.MINUTES.toMillis(InstanceSyncConstants.VALIDATION_TIMEOUT_MINUTES))
                  .build())
        .expiry(System.currentTimeMillis() + DELEGATE_QUEUE_TIMEOUT)
        .build();
  }

  private PerpetualTaskData getPerpetualTaskData(PerpetualTaskClientContext clientContext) {
    Map<String, String> clientParams = clientContext.getClientParams();
    String appId = clientParams.get(HARNESS_APPLICATION_ID);
    String infraMappingId = clientParams.get(INFRASTRUCTURE_MAPPING_ID);
    AwsLambdaInfraStructureMapping infraMapping =
        (AwsLambdaInfraStructureMapping) infraMappingService.get(appId, infraMappingId);

    SettingAttribute cloudProviderSetting = settingsService.get(infraMapping.getComputeProviderSettingId());
    AwsConfig awsConfig = (AwsConfig) cloudProviderSetting.getValue();
    List<EncryptedDataDetail> encryptionDetails = secretManager.getEncryptionDetails(awsConfig, null, null);

    return PerpetualTaskData.builder()
        .awsConfig(awsConfig)
        .encryptedDataDetails(encryptionDetails)
        .region(infraMapping.getRegion())
        .functionName(clientContext.getClientParams().get(FUNCTION_NAME))
        .qualifier(clientContext.getClientParams().get(QUALIFIER))
        .startDate(parseLong(clientContext.getClientParams().get(START_DATE)))
        .build();
  }

  @Data
  @Builder
  static class PerpetualTaskData {
    AwsConfig awsConfig;
    List<EncryptedDataDetail> encryptedDataDetails;
    String region;
    String functionName;
    String qualifier;
    long startDate;
  }
}
