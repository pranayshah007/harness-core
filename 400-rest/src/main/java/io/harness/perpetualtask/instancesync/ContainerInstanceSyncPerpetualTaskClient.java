/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.perpetualtask.instancesync;

import static io.harness.beans.DelegateTask.DELEGATE_QUEUE_TIMEOUT;

import static software.wings.service.InstanceSyncConstants.CLUSTER_NAME;
import static software.wings.service.InstanceSyncConstants.CONTAINER_SERVICE_NAME;
import static software.wings.service.InstanceSyncConstants.CONTAINER_TYPE;
import static software.wings.service.InstanceSyncConstants.HARNESS_APPLICATION_ID;
import static software.wings.service.InstanceSyncConstants.INFRASTRUCTURE_MAPPING_ID;
import static software.wings.service.InstanceSyncConstants.NAMESPACE;
import static software.wings.service.InstanceSyncConstants.RELEASE_NAME;
import static software.wings.service.InstanceSyncConstants.VALIDATION_TIMEOUT_MINUTES;
import static software.wings.utils.Utils.emptyIfNull;

import static java.util.Objects.nonNull;

import io.harness.beans.Cd1SetupFields;
import io.harness.beans.DelegateTask;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.ff.FeatureFlagService;
import io.harness.perpetualtask.PerpetualTaskClientContext;
import io.harness.perpetualtask.PerpetualTaskServiceClient;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.serializer.KryoSerializer;

import software.wings.annotation.EncryptableSetting;
import software.wings.beans.AzureKubernetesInfrastructureMapping;
import software.wings.beans.ContainerInfrastructureMapping;
import software.wings.beans.EcsInfrastructureMapping;
import software.wings.beans.Environment;
import software.wings.beans.TaskType;
import software.wings.beans.dto.SettingAttribute;
import software.wings.delegatetasks.aws.AwsCommandHelper;
import software.wings.helpers.ext.container.ContainerDeploymentManagerHelper;
import software.wings.helpers.ext.k8s.request.K8sClusterConfig;
import software.wings.helpers.ext.k8s.request.K8sInstanceSyncTaskParameters;
import software.wings.service.impl.ContainerMetadataType;
import software.wings.service.impl.ContainerServiceParams;
import software.wings.service.intfc.EnvironmentService;
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
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@Slf4j
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ContainerInstanceSyncPerpetualTaskClient implements PerpetualTaskServiceClient {
  @Inject InfrastructureMappingService infraMappingService;
  @Inject EnvironmentService environmentService;
  @Inject ContainerDeploymentManagerHelper containerDeploymentManagerHelper;
  @Inject transient AwsCommandHelper awsCommandHelper;
  @Inject SecretManager secretManager;
  @Inject SettingsService settingsService;
  @Inject KryoSerializer kryoSerializer;
  @Inject FeatureFlagService featureFlagService;

  @Override
  public Message getTaskParams(PerpetualTaskClientContext clientContext) {
    final ContainerInstanceSyncPerpetualTaskData taskData = getPerpetualTaskData(clientContext);
    return isK8sContainerType(clientContext.getClientParams()) ? buildK8ContainerInstanceSyncTaskParams(taskData)
                                                               : buildContainerInstanceSyncTaskParams(taskData);
  }

  private Message buildContainerInstanceSyncTaskParams(ContainerInstanceSyncPerpetualTaskData taskData) {
    ByteString settingAttribute = ByteString.copyFrom(kryoSerializer.asBytes(taskData.getSettingAttribute()));
    ByteString encryptionDetails = ByteString.copyFrom(kryoSerializer.asBytes(taskData.getEncryptionDetails()));

    return ContainerInstanceSyncPerpetualTaskParams.newBuilder()
        .setContainerType(taskData.getContainerType())
        .setContainerServicePerpetualTaskParams(ContainerServicePerpetualTaskParams.newBuilder()
                                                    .setSettingAttribute(settingAttribute)
                                                    .setContainerSvcName(taskData.getContainerServiceName())
                                                    .setEncryptionDetails(encryptionDetails)
                                                    .setClusterName(taskData.getClusterName())
                                                    .setNamespace(taskData.getNamespace())
                                                    .setRegion(taskData.getRegion())
                                                    .setSubscriptionId(taskData.getSubscriptionId())
                                                    .setResourceGroup(taskData.getResourceGroup())
                                                    .setMasterUrl(taskData.getMasterUrl())
                                                    .setReleaseName(taskData.getReleaseName())
                                                    .build())
        .build();
  }

  private Message buildK8ContainerInstanceSyncTaskParams(ContainerInstanceSyncPerpetualTaskData taskData) {
    ByteString clusterConfig = ByteString.copyFrom(kryoSerializer.asBytes(taskData.getK8sClusterConfig()));

    return ContainerInstanceSyncPerpetualTaskParams.newBuilder()
        .setContainerType(taskData.getContainerType())
        .setK8SContainerPerpetualTaskParams(K8sContainerInstanceSyncPerpetualTaskParams.newBuilder()
                                                .setAccountId(taskData.getAccountId())
                                                .setAppId(taskData.getAppId())
                                                .setK8SClusterConfig(clusterConfig)
                                                .setNamespace(taskData.getNamespace())
                                                .setReleaseName(taskData.getReleaseName())
                                                .build())
        .build();
  }

  @Override
  public DelegateTask getValidationTask(PerpetualTaskClientContext clientContext, String accountId) {
    Map<String, String> clientParams = clientContext.getClientParams();
    final ContainerInstanceSyncPerpetualTaskData taskData = getPerpetualTaskData(clientContext);

    return isK8sContainerType(clientParams) ? buildPTValidationTask(clientParams, taskData)
                                            : buildNonK8sDelegateTask(clientParams, taskData);
  }

  private DelegateTask buildNonK8sDelegateTask(
      Map<String, String> clientParams, ContainerInstanceSyncPerpetualTaskData taskData) {
    ContainerServiceParams delegateTaskParams = ContainerServiceParams.builder()
                                                    .settingAttribute(taskData.getSettingAttribute())
                                                    .containerServiceName(taskData.getContainerServiceName())
                                                    .encryptionDetails(taskData.getEncryptionDetails())
                                                    .clusterName(taskData.getClusterName())
                                                    .namespace(taskData.getNamespace())
                                                    .region(taskData.getRegion())
                                                    .subscriptionId(taskData.getSubscriptionId())
                                                    .resourceGroup(taskData.getResourceGroup())
                                                    .masterUrl(taskData.getMasterUrl())
                                                    .releaseName(taskData.getReleaseName())
                                                    .build();

    return DelegateTask.builder()
        .data(TaskData.builder()
                  .async(false)
                  .taskType(TaskType.CONTAINER_VALIDATION.name())
                  .parameters(new Object[] {null, null, delegateTaskParams})
                  .timeout(TimeUnit.MINUTES.toMillis(VALIDATION_TIMEOUT_MINUTES))
                  .build())
        .accountId(taskData.getAccountId())
        .setupAbstraction(Cd1SetupFields.APP_ID_FIELD, taskData.getAppId())
        .setupAbstraction(Cd1SetupFields.ENV_ID_FIELD, taskData.getEnvId())
        .setupAbstraction(Cd1SetupFields.ENV_TYPE_FIELD, taskData.getEnvType())
        .setupAbstraction(Cd1SetupFields.INFRASTRUCTURE_MAPPING_ID_FIELD, clientParams.get(INFRASTRUCTURE_MAPPING_ID))
        .setupAbstraction(Cd1SetupFields.SERVICE_ID_FIELD, taskData.getServiceId())
        .expiry(System.currentTimeMillis() + DELEGATE_QUEUE_TIMEOUT)
        .build();
  }

  private DelegateTask buildPTValidationTask(
      Map<String, String> clientParams, ContainerInstanceSyncPerpetualTaskData taskData) {
    String infraMappingId = clientParams.get(INFRASTRUCTURE_MAPPING_ID);

    K8sInstanceSyncTaskParameters delegateTaskParams = K8sInstanceSyncTaskParameters.builder()
                                                           .accountId(taskData.getAccountId())
                                                           .appId(taskData.getAppId())
                                                           .k8sClusterConfig(taskData.getK8sClusterConfig())
                                                           .namespace(taskData.getNamespace())
                                                           .releaseName(taskData.getReleaseName())
                                                           .build();

    List<ExecutionCapability> executionCapabilities = delegateTaskParams.fetchRequiredExecutionCapabilities(null);

    return DelegateTask.builder()
        .accountId(taskData.getAccountId())
        .setupAbstraction(Cd1SetupFields.APP_ID_FIELD, taskData.getAppId())
        .tags(awsCommandHelper.getAwsConfigTagsFromK8sConfig(delegateTaskParams))
        .executionCapabilities(executionCapabilities)
        .data(TaskData.builder()
                  .async(false)
                  .taskType(TaskType.CAPABILITY_VALIDATION.name())
                  .parameters(executionCapabilities.toArray())
                  .timeout(TimeUnit.MINUTES.toMillis(VALIDATION_TIMEOUT_MINUTES))
                  .build())
        .setupAbstraction(Cd1SetupFields.ENV_ID_FIELD, taskData.getEnvId())
        .setupAbstraction(Cd1SetupFields.ENV_TYPE_FIELD, taskData.getEnvType())
        .setupAbstraction(Cd1SetupFields.INFRASTRUCTURE_MAPPING_ID_FIELD, infraMappingId)
        .setupAbstraction(Cd1SetupFields.SERVICE_ID_FIELD, taskData.getServiceId())
        .expiry(System.currentTimeMillis() + DELEGATE_QUEUE_TIMEOUT)
        .build();
  }

  private ContainerInstanceSyncPerpetualTaskData getPerpetualTaskData(PerpetualTaskClientContext clientContext) {
    Map<String, String> clientParams = clientContext.getClientParams();
    String appId = clientParams.get(HARNESS_APPLICATION_ID);
    String infraMappingId = clientParams.get(INFRASTRUCTURE_MAPPING_ID);
    ContainerInfrastructureMapping containerInfrastructureMapping =
        (ContainerInfrastructureMapping) infraMappingService.get(appId, infraMappingId);
    Environment environment = environmentService.get(appId, containerInfrastructureMapping.getEnvId());

    boolean isK8sContainerType = isK8sContainerType(clientParams);
    SettingAttribute settingAttribute = getSettingAttribute(isK8sContainerType, containerInfrastructureMapping);

    String clusterName = clientParams.getOrDefault(
        CLUSTER_NAME, emptyIfNull(getClusterName(isK8sContainerType, containerInfrastructureMapping)));
    return ContainerInstanceSyncPerpetualTaskData.builder()
        .containerType(emptyIfNull(clientParams.get(CONTAINER_TYPE)))
        .accountId(containerInfrastructureMapping.getAccountId())
        .appId(containerInfrastructureMapping.getAppId())
        .namespace(emptyIfNull(clientParams.get(NAMESPACE)))
        .releaseName(emptyIfNull(clientParams.get(RELEASE_NAME)))
        .containerServiceName(emptyIfNull(clientParams.get(CONTAINER_SERVICE_NAME)))
        .k8sClusterConfig(getK8sClusterConfig(isK8sContainerType, containerInfrastructureMapping, clusterName))
        .clusterName(clusterName)
        .settingAttribute(settingAttribute)
        .encryptionDetails(getEncryptedDataDetails(settingAttribute, containerInfrastructureMapping))
        .region(emptyIfNull(getRegion(containerInfrastructureMapping)))
        .subscriptionId(emptyIfNull(getSubscriptionId(containerInfrastructureMapping)))
        .resourceGroup(emptyIfNull(getResourceGroup(containerInfrastructureMapping)))
        .masterUrl(emptyIfNull(getMasterUrl(containerInfrastructureMapping)))
        .envId(emptyIfNull(environment.getUuid()))
        .envType(environment.getEnvironmentType().name())
        .serviceId(containerInfrastructureMapping.getServiceId())
        .build();
  }

  private boolean isK8sContainerType(Map<String, String> clientParams) {
    String containerType = clientParams.get(CONTAINER_TYPE);
    return ContainerMetadataType.K8S.name().equals(containerType);
  }

  private SettingAttribute getSettingAttribute(
      boolean isK8sContainerType, ContainerInfrastructureMapping containerInfrastructureMapping) {
    return !isK8sContainerType
        ? settingsService.get(containerInfrastructureMapping.getComputeProviderSettingId()).toDTO()
        : null;
  }
  private K8sClusterConfig getK8sClusterConfig(
      boolean isK8sContainerType, ContainerInfrastructureMapping containerInfrastructureMapping, String clusterName) {
    if (!isK8sContainerType) {
      return null;
    }

    K8sClusterConfig k8sClusterConfig =
        containerDeploymentManagerHelper.getK8sClusterConfig(containerInfrastructureMapping, null);

    if (StringUtils.isNotBlank(clusterName)) {
      k8sClusterConfig.setClusterName(clusterName);
    }

    return k8sClusterConfig;
  }

  private List<EncryptedDataDetail> getEncryptedDataDetails(
      SettingAttribute settingAttribute, ContainerInfrastructureMapping containerInfraMapping) {
    return nonNull(settingAttribute) ? secretManager.getEncryptionDetails(
               (EncryptableSetting) settingAttribute.getValue(), containerInfraMapping.getAppId(), null)
                                     : null;
  }

  private String getClusterName(
      boolean isK8sContainerType, ContainerInfrastructureMapping containerInfrastructureMapping) {
    return !isK8sContainerType ? containerInfrastructureMapping.getClusterName() : null;
  }

  private String getRegion(ContainerInfrastructureMapping containerInfrastructureMapping) {
    return (containerInfrastructureMapping instanceof EcsInfrastructureMapping)
        ? ((EcsInfrastructureMapping) containerInfrastructureMapping).getRegion()
        : null;
  }

  private String getSubscriptionId(ContainerInfrastructureMapping containerInfrastructureMapping) {
    return containerInfrastructureMapping instanceof AzureKubernetesInfrastructureMapping
        ? ((AzureKubernetesInfrastructureMapping) containerInfrastructureMapping).getSubscriptionId()
        : null;
  }

  private String getResourceGroup(ContainerInfrastructureMapping containerInfrastructureMapping) {
    return containerInfrastructureMapping instanceof AzureKubernetesInfrastructureMapping
        ? ((AzureKubernetesInfrastructureMapping) containerInfrastructureMapping).getResourceGroup()
        : null;
  }

  private String getMasterUrl(ContainerInfrastructureMapping containerInfrastructureMapping) {
    return containerInfrastructureMapping instanceof AzureKubernetesInfrastructureMapping
        ? ((AzureKubernetesInfrastructureMapping) containerInfrastructureMapping).getMasterUrl()
        : null;
  }

  @Data
  @Builder
  static class ContainerInstanceSyncPerpetualTaskData {
    String containerType;
    String accountId;
    String appId;
    String namespace;
    String releaseName;
    String containerServiceName;
    String envId;
    String envType;
    K8sClusterConfig k8sClusterConfig;
    SettingAttribute settingAttribute;
    List<EncryptedDataDetail> encryptionDetails;
    String clusterName;
    String region;
    String subscriptionId;
    String resourceGroup;
    String masterUrl;
    String serviceId;
  }
}
