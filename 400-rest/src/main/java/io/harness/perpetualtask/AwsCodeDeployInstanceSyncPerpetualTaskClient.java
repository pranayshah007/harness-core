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
import static software.wings.beans.TaskType.AWS_EC2_TASK;
import static software.wings.service.InstanceSyncConstants.HARNESS_APPLICATION_ID;
import static software.wings.service.InstanceSyncConstants.INFRASTRUCTURE_MAPPING_ID;

import static java.util.Collections.singletonList;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.Cd1SetupFields;
import io.harness.beans.DelegateTask;
import io.harness.delegate.beans.TaskData;
import io.harness.perpetualtask.instancesync.AwsCodeDeployInstanceSyncPerpetualTaskParams;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.serializer.KryoSerializer;

import software.wings.api.DeploymentType;
import software.wings.beans.AwsConfig;
import software.wings.beans.AwsInfrastructureMapping;
import software.wings.beans.CodeDeployInfrastructureMapping;
import software.wings.beans.SettingAttribute;
import software.wings.service.InstanceSyncConstants;
import software.wings.service.impl.AwsUtils;
import software.wings.service.impl.aws.model.AwsEc2ListInstancesRequest;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.EnvironmentService;
import software.wings.service.intfc.InfrastructureMappingService;
import software.wings.service.intfc.ServiceResourceService;
import software.wings.service.intfc.SettingsService;
import software.wings.service.intfc.security.SecretManager;

import com.amazonaws.services.ec2.model.Filter;
import com.google.inject.Inject;
import com.google.protobuf.ByteString;
import com.google.protobuf.Message;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import lombok.Builder;
import lombok.Data;

@OwnedBy(CDP)
public class AwsCodeDeployInstanceSyncPerpetualTaskClient implements PerpetualTaskServiceClient {
  @Inject private PerpetualTaskService perpetualTaskService;
  @Inject private InfrastructureMappingService infraMappingService;
  @Inject private ServiceResourceService serviceResourceService;
  @Inject private AppService appService;
  @Inject private EnvironmentService environmentService;
  @Inject private AwsUtils awsUtils;
  @Inject private SettingsService settingsService;
  @Inject private SecretManager secretManager;
  @Inject private KryoSerializer kryoSerializer;

  @Override
  public Message getTaskParams(PerpetualTaskClientContext clientContext) {
    PerpetualTaskData taskData = getPerpetualTaskData(clientContext);
    ByteString filterBytes = ByteString.copyFrom(kryoSerializer.asBytes(taskData.getFilters()));
    ByteString configBytes = ByteString.copyFrom(kryoSerializer.asBytes(taskData.getAwsConfig()));
    ByteString encryptionDetailsBytes = ByteString.copyFrom(kryoSerializer.asBytes(taskData.getEncryptionDetails()));

    return AwsCodeDeployInstanceSyncPerpetualTaskParams.newBuilder()
        .setRegion(taskData.getRegion())
        .setFilter(filterBytes)
        .setAwsConfig(configBytes)
        .setEncryptedData(encryptionDetailsBytes)
        .build();
  }

  @Override
  public DelegateTask getValidationTask(PerpetualTaskClientContext clientContext, String accountId) {
    PerpetualTaskData taskData = getPerpetualTaskData(clientContext);
    AwsEc2ListInstancesRequest request = AwsEc2ListInstancesRequest.builder()
                                             .awsConfig(taskData.getAwsConfig())
                                             .encryptionDetails(taskData.getEncryptionDetails())
                                             .region(taskData.getRegion())
                                             .filters(taskData.getFilters())
                                             .build();

    return DelegateTask.builder()
        .accountId(accountId)
        .setupAbstraction(Cd1SetupFields.APP_ID_FIELD, GLOBAL_APP_ID)
        .tags(isNotEmpty(taskData.getAwsConfig().getTag()) ? singletonList(taskData.getAwsConfig().getTag()) : null)
        .data(TaskData.builder()
                  .async(false)
                  .taskType(AWS_EC2_TASK.name())
                  .parameters(new Object[] {request})
                  .timeout(TimeUnit.MINUTES.toMillis(InstanceSyncConstants.VALIDATION_TIMEOUT_MINUTES))
                  .build())
        .expiry(System.currentTimeMillis() + DELEGATE_QUEUE_TIMEOUT)
        .build();
  }

  private PerpetualTaskData getPerpetualTaskData(PerpetualTaskClientContext clientContext) {
    Map<String, String> clientParams = clientContext.getClientParams();
    String infraMappingId = clientParams.get(INFRASTRUCTURE_MAPPING_ID);
    String appId = clientParams.get(HARNESS_APPLICATION_ID);
    CodeDeployInfrastructureMapping infraMapping =
        (CodeDeployInfrastructureMapping) infraMappingService.get(appId, infraMappingId);
    SettingAttribute awsCloudProvider = settingsService.get(infraMapping.getComputeProviderSettingId());
    AwsConfig awsConfig = (AwsConfig) awsCloudProvider.getValue();
    DeploymentType deploymentType =
        serviceResourceService.getDeploymentType(infraMapping, null, infraMapping.getServiceId());
    List<EncryptedDataDetail> encryptedDataDetails = secretManager.getEncryptionDetails(awsConfig);

    return PerpetualTaskData.builder()
        .region(infraMapping.getRegion())
        .filters(getAwsFilters(infraMapping, deploymentType))
        .awsConfig(awsConfig)
        .encryptionDetails(encryptedDataDetails)
        .build();
  }

  private List<Filter> getAwsFilters(CodeDeployInfrastructureMapping infraMapping, DeploymentType deploymentType) {
    AwsInfrastructureMapping awsInfraMapping = AwsInfrastructureMapping.Builder.anAwsInfrastructureMapping()
                                                   .withRegion(infraMapping.getRegion())
                                                   .withDeploymentType(deploymentType.name())
                                                   .withAwsInstanceFilter(null)
                                                   .build();

    return awsUtils.getFilters(deploymentType, awsInfraMapping.getAwsInstanceFilter());
  }

  @Data
  @Builder
  private static class PerpetualTaskData {
    String region;
    AwsConfig awsConfig;
    String deploymentId;
    List<Filter> filters;
    List<EncryptedDataDetail> encryptionDetails;
  }
}
