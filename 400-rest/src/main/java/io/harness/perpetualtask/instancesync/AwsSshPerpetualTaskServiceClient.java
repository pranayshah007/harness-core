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

import static software.wings.beans.CGConstants.GLOBAL_APP_ID;

import static java.util.Collections.singletonList;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.Cd1SetupFields;
import io.harness.beans.DelegateTask;
import io.harness.delegate.beans.TaskData;
import io.harness.exception.InvalidRequestException;
import io.harness.perpetualtask.PerpetualTaskClientBase;
import io.harness.perpetualtask.PerpetualTaskClientContext;
import io.harness.perpetualtask.PerpetualTaskService;
import io.harness.perpetualtask.PerpetualTaskServiceClient;
import io.harness.security.encryption.EncryptedDataDetail;

import software.wings.api.DeploymentType;
import software.wings.beans.AwsConfig;
import software.wings.beans.AwsInfrastructureMapping;
import software.wings.beans.InfrastructureMapping;
import software.wings.beans.SettingAttribute;
import software.wings.beans.TaskType;
import software.wings.service.InstanceSyncConstants;
import software.wings.service.impl.AwsUtils;
import software.wings.service.impl.aws.model.AwsEc2ListInstancesRequest;
import software.wings.service.intfc.InfrastructureMappingService;
import software.wings.service.intfc.ServiceResourceService;
import software.wings.service.intfc.SettingsService;
import software.wings.service.intfc.security.SecretManager;

import com.amazonaws.services.ec2.model.Filter;
import com.google.inject.Inject;
import com.google.protobuf.ByteString;
import com.google.protobuf.Message;
import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(CDP)
public class AwsSshPerpetualTaskServiceClient extends PerpetualTaskClientBase implements PerpetualTaskServiceClient {
  @Inject private PerpetualTaskService perpetualTaskService;
  @Inject private SecretManager secretManager;
  @Inject private InfrastructureMappingService infrastructureMappingService;
  @Inject private ServiceResourceService serviceResourceService;
  @Inject private AwsUtils awsUtils;
  @Inject private SettingsService settingsService;

  @Override
  public Message getTaskParams(PerpetualTaskClientContext clientContext, boolean referenceFalse) {
    final PerpetualTaskData taskData = getPerpetualTaskData(clientContext);

    ByteString configBytes = ByteString.copyFrom(getKryoSerializer(referenceFalse).asBytes(taskData.getAwsConfig()));
    ByteString filterBytes = ByteString.copyFrom(getKryoSerializer(referenceFalse).asBytes(taskData.getFilters()));
    ByteString encryptionDetailsBytes =
        ByteString.copyFrom(getKryoSerializer(referenceFalse).asBytes(taskData.getEncryptionDetails()));

    return AwsSshInstanceSyncPerpetualTaskParams.newBuilder()
        .setRegion(taskData.getRegion())
        .setAwsConfig(configBytes)
        .setFilter(filterBytes)
        .setEncryptedData(encryptionDetailsBytes)
        .build();
  }

  @Override
  public DelegateTask getValidationTask(PerpetualTaskClientContext clientContext, String accountId) {
    final PerpetualTaskData taskData = getPerpetualTaskData(clientContext);

    return DelegateTask.builder()
        .accountId(accountId)
        .setupAbstraction(Cd1SetupFields.APP_ID_FIELD, GLOBAL_APP_ID)
        .tags(isNotEmpty(taskData.getAwsConfig().getTag()) ? singletonList(taskData.getAwsConfig().getTag()) : null)
        .data(TaskData.builder()
                  .async(false)
                  .taskType(TaskType.AWS_EC2_TASK.name())
                  .parameters(new Object[] {AwsEc2ListInstancesRequest.builder()
                                                .awsConfig(taskData.getAwsConfig())
                                                .encryptionDetails(taskData.getEncryptionDetails())
                                                .region(taskData.getRegion())
                                                .filters(taskData.getFilters())
                                                .build()})
                  .timeout(TimeUnit.MINUTES.toMillis(InstanceSyncConstants.VALIDATION_TIMEOUT_MINUTES))
                  .build())
        .expiry(System.currentTimeMillis() + DELEGATE_QUEUE_TIMEOUT)
        .build();
  }

  private PerpetualTaskData getPerpetualTaskData(PerpetualTaskClientContext clientContext) {
    String infraMappingId = getInfraMappingId(clientContext);
    String appId = getAppId(clientContext);

    InfrastructureMapping infraMapping = infrastructureMappingService.get(appId, infraMappingId);
    if (!(infraMapping instanceof AwsInfrastructureMapping)) {
      String msg = "Incompatible infra mapping type. Expecting AwsInfraMapping type. Found:"
          + infraMapping.getInfraMappingType();
      log.error(msg);
      throw new InvalidRequestException(msg);
    }

    DeploymentType deploymentType =
        serviceResourceService.getDeploymentType(infraMapping, null, infraMapping.getServiceId());

    List<Filter> filters =
        awsUtils.getFilters(deploymentType, ((AwsInfrastructureMapping) infraMapping).getAwsInstanceFilter());
    SettingAttribute awsCloudProvider = settingsService.get(infraMapping.getComputeProviderSettingId());
    AwsConfig awsConfig = (AwsConfig) awsCloudProvider.getValue();

    List<EncryptedDataDetail> encryptionDetails = secretManager.getEncryptionDetails(awsConfig);

    return PerpetualTaskData.builder()
        .region(((AwsInfrastructureMapping) infraMapping).getRegion())
        .awsConfig(awsConfig)
        .filters(filters)
        .encryptionDetails(encryptionDetails)
        .build();
  }

  private String getAppId(PerpetualTaskClientContext clientContext) {
    return clientContext.getClientParams().get(InstanceSyncConstants.HARNESS_APPLICATION_ID);
  }

  private String getInfraMappingId(PerpetualTaskClientContext clientContext) {
    return clientContext.getClientParams().get(InstanceSyncConstants.INFRASTRUCTURE_MAPPING_ID);
  }

  @Data
  @Builder
  private static class PerpetualTaskData {
    private String region;
    private AwsConfig awsConfig;
    private List<Filter> filters;
    private List<EncryptedDataDetail> encryptionDetails;
  }
}
