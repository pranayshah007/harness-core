/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.perpetualtask.instancesync;

import static software.wings.service.InstanceSyncConstants.CONTAINER_SERVICE_NAME;
import static software.wings.service.InstanceSyncConstants.NAMESPACE;
import static software.wings.service.InstanceSyncConstants.RELEASE_NAME;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.ACCOUNT_ID;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.APP_ID;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.ENV_ID;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.INFRA_MAPPING_ID;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.SERVICE_ID;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mockStatic;
import static org.powermock.api.mockito.PowerMockito.when;

import io.harness.beans.Cd1SetupFields;
import io.harness.beans.DelegateTask;
import io.harness.beans.DelegateTask.DelegateTaskKeys;
import io.harness.beans.EnvironmentType;
import io.harness.category.element.UnitTests;
import io.harness.data.structure.UUIDGenerator;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.perpetualtask.PerpetualTaskClientContext;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;

import software.wings.WingsBaseTest;
import software.wings.beans.AwsConfig;
import software.wings.beans.AzureConfig;
import software.wings.beans.AzureKubernetesInfrastructureMapping;
import software.wings.beans.ContainerInfrastructureMapping;
import software.wings.beans.DirectKubernetesInfrastructureMapping;
import software.wings.beans.EcsInfrastructureMapping;
import software.wings.beans.Environment;
import software.wings.beans.KubernetesClusterConfig;
import software.wings.beans.SettingAttribute;
import software.wings.beans.TaskType;
import software.wings.delegatetasks.aws.AwsCommandHelper;
import software.wings.helpers.ext.container.ContainerDeploymentManagerHelper;
import software.wings.helpers.ext.k8s.request.K8sClusterConfig;
import software.wings.helpers.ext.k8s.request.K8sInstanceSyncTaskParameters;
import software.wings.service.InstanceSyncConstants;
import software.wings.service.impl.ContainerServiceParams;
import software.wings.service.impl.instance.InstanceSyncTestConstants;
import software.wings.service.intfc.EnvironmentService;
import software.wings.service.intfc.InfrastructureMappingService;
import software.wings.service.intfc.SettingsService;
import software.wings.service.intfc.security.SecretManager;

import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;

@FieldDefaults(level = AccessLevel.PRIVATE)
public class ContainerInstanceSyncPerpetualTaskClientTest extends WingsBaseTest {
  @Mock InfrastructureMappingService infraMappingService;
  @Mock EnvironmentService environmentService;
  @Mock ContainerDeploymentManagerHelper containerDeploymentManagerHelper;
  @Mock AwsCommandHelper awsCommandHelper;
  @Mock SecretManager secretManager;
  @Mock SettingsService settingsService;

  @InjectMocks @Inject ContainerInstanceSyncPerpetualTaskClient client;

  @Test
  @Owner(developers = OwnerRule.ACASIAN)
  @Category(UnitTests.class)
  public void getK8sTaskParams() {
    K8sClusterConfig k8sClusterConfig =
        K8sClusterConfig.builder().cloudProviderName("Direct").clusterName("cluster").namespace("namespace").build();

    prepareK8sTaskData(k8sClusterConfig);
    final ContainerInstanceSyncPerpetualTaskParams taskParams =
        (ContainerInstanceSyncPerpetualTaskParams) client.getTaskParams(getClientContext(true));

    assertThat(taskParams.getContainerType()).isEqualTo("K8S");
    assertThat(taskParams.getK8SContainerPerpetualTaskParams()).isNotNull();
    K8sContainerInstanceSyncPerpetualTaskParams params = taskParams.getK8SContainerPerpetualTaskParams();
    assertThat(params.getAccountId()).isEqualTo(ACCOUNT_ID);
    assertThat(params.getAppId()).isEqualTo(APP_ID);
    assertThat(params.getNamespace()).isEqualTo("namespace");
    assertThat(params.getReleaseName()).isEqualTo("release_name");
    assertThat(params.getK8SClusterConfig()).isNotNull();
  }

  @Test
  @Owner(developers = OwnerRule.ACASIAN)
  @Category(UnitTests.class)
  public void getAzureTaskParams() {
    AzureConfig azureConfig = AzureConfig.builder().accountId(ACCOUNT_ID).tenantId("harness").build();
    prepareAzureTaskData(azureConfig);
    final ContainerInstanceSyncPerpetualTaskParams taskParams =
        (ContainerInstanceSyncPerpetualTaskParams) client.getTaskParams(getClientContext(false));

    assertThat(taskParams.getContainerType()).isEqualTo("");
    assertThat(taskParams.getContainerServicePerpetualTaskParams()).isNotNull();
    ContainerServicePerpetualTaskParams params = taskParams.getContainerServicePerpetualTaskParams();
    assertThat(params.getClusterName()).isEqualTo("cluster");
    assertThat(params.getSubscriptionId()).isEqualTo("subscription_id");
    assertThat(params.getResourceGroup()).isEqualTo("resource_group");
    assertThat(params.getMasterUrl()).isEqualTo("master_url");
    assertThat(params.getSettingAttribute()).isNotNull();
    assertThat(params.getEncryptionDetails()).isNotNull();
  }

  @Test
  @Owner(developers = OwnerRule.ACASIAN)
  @Category(UnitTests.class)
  public void getAwsTaskParams() {
    AwsConfig awsConfig = AwsConfig.builder().accountId(ACCOUNT_ID).tag("harness").build();
    prepareAwsTaskData(awsConfig);
    final ContainerInstanceSyncPerpetualTaskParams taskParams =
        (ContainerInstanceSyncPerpetualTaskParams) client.getTaskParams(getClientContext(false));

    assertThat(taskParams.getContainerType()).isEqualTo("");
    assertThat(taskParams.getContainerServicePerpetualTaskParams()).isNotNull();
    ContainerServicePerpetualTaskParams params = taskParams.getContainerServicePerpetualTaskParams();
    assertThat(params.getClusterName()).isEqualTo("cluster");
    assertThat(params.getRegion()).isEqualTo("us-east-1");
    assertThat(params.getSubscriptionId()).isEmpty();
    assertThat(params.getResourceGroup()).isEmpty();
    assertThat(params.getMasterUrl()).isEmpty();
    assertThat(params.getSettingAttribute()).isNotNull();
    assertThat(params.getEncryptionDetails()).isNotNull();
  }

  @Test
  @Owner(developers = OwnerRule.ACASIAN)
  @Category(UnitTests.class)
  public void getK8sValidationTask() {
    K8sClusterConfig k8sClusterConfig =
        K8sClusterConfig.builder()
            .cloudProviderName("Direct")
            .clusterName("cluster")
            .namespace("namespace")
            .cloudProvider(KubernetesClusterConfig.builder()
                               .delegateSelectors(new HashSet<>(singletonList("delegateSelector")))
                               .useKubernetesDelegate(true)
                               .build())
            .build();

    List<ExecutionCapability> executionCapabilities = K8sInstanceSyncTaskParameters.builder()
                                                          .accountId(ACCOUNT_ID)
                                                          .appId(APP_ID)
                                                          .k8sClusterConfig(k8sClusterConfig)
                                                          .namespace("namespace")
                                                          .releaseName("release_name")
                                                          .build()
                                                          .fetchRequiredExecutionCapabilities(null);

    prepareK8sTaskData(k8sClusterConfig);
    final DelegateTask validationTask = client.getValidationTask(getClientContext(true), ACCOUNT_ID);
    assertThat(validationTask)
        .isEqualToIgnoringGivenFields(
            DelegateTask.builder()
                .accountId(ACCOUNT_ID)
                .setupAbstraction(Cd1SetupFields.APP_ID_FIELD, APP_ID)
                .tags(ImmutableList.of("awsTag"))
                .executionCapabilities(executionCapabilities)
                .data(TaskData.builder()
                          .async(false)
                          .taskType(TaskType.CAPABILITY_VALIDATION.name())
                          .parameters(executionCapabilities.toArray())
                          .timeout(TimeUnit.MINUTES.toMillis(InstanceSyncConstants.VALIDATION_TIMEOUT_MINUTES))
                          .build())
                .setupAbstraction(Cd1SetupFields.ENV_ID_FIELD, ENV_ID)
                .setupAbstraction(Cd1SetupFields.ENV_TYPE_FIELD, EnvironmentType.PROD.name())
                .setupAbstraction(Cd1SetupFields.INFRASTRUCTURE_MAPPING_ID_FIELD, INFRA_MAPPING_ID)
                .setupAbstraction(Cd1SetupFields.SERVICE_ID_FIELD, SERVICE_ID)
                .build(),
            DelegateTaskKeys.expiry, DelegateTaskKeys.validUntil);
  }

  @Test
  @Owner(developers = OwnerRule.ACASIAN)
  @Category(UnitTests.class)
  public void getAzureValidationTask() {
    AzureConfig azureConfig = AzureConfig.builder().accountId(ACCOUNT_ID).tenantId("harness").build();
    prepareAzureTaskData(azureConfig);
    final DelegateTask validationTask = client.getValidationTask(getClientContext(false), ACCOUNT_ID);
    assertThat(validationTask)
        .isEqualToIgnoringGivenFields(
            DelegateTask.builder()
                .accountId(ACCOUNT_ID)
                .setupAbstraction(Cd1SetupFields.APP_ID_FIELD, APP_ID)
                .data(
                    TaskData.builder()
                        .async(false)
                        .taskType(TaskType.CONTAINER_VALIDATION.name())
                        .parameters(new Object[] {null, null,
                            ContainerServiceParams.builder()
                                .settingAttribute(
                                    SettingAttribute.Builder.aSettingAttribute().withValue(azureConfig).build().toDTO())
                                .containerServiceName("container_service_name")
                                .encryptionDetails(new ArrayList<>())
                                .clusterName("cluster")
                                .namespace("namespace")
                                .region("")
                                .subscriptionId("subscription_id")
                                .resourceGroup("resource_group")
                                .masterUrl("master_url")
                                .releaseName("release_name")
                                .build()})
                        .timeout(TimeUnit.MINUTES.toMillis(InstanceSyncConstants.VALIDATION_TIMEOUT_MINUTES))
                        .build())
                .setupAbstraction(Cd1SetupFields.ENV_ID_FIELD, ENV_ID)
                .setupAbstraction(Cd1SetupFields.ENV_TYPE_FIELD, EnvironmentType.PROD.name())
                .setupAbstraction(Cd1SetupFields.INFRASTRUCTURE_MAPPING_ID_FIELD, INFRA_MAPPING_ID)
                .setupAbstraction(Cd1SetupFields.SERVICE_ID_FIELD, SERVICE_ID)
                .build(),
            DelegateTaskKeys.expiry, DelegateTaskKeys.validUntil);
  }

  @Test
  @Owner(developers = OwnerRule.ACASIAN)
  @Category(UnitTests.class)
  public void getAwsValidationTask() {
    AwsConfig awsConfig = AwsConfig.builder().accountId(ACCOUNT_ID).tag("harness").build();
    prepareAwsTaskData(awsConfig);
    final DelegateTask validationTask = client.getValidationTask(getClientContext(false), ACCOUNT_ID);
    assertThat(validationTask)
        .isEqualToIgnoringGivenFields(
            DelegateTask.builder()
                .accountId(ACCOUNT_ID)
                .setupAbstraction(Cd1SetupFields.APP_ID_FIELD, APP_ID)
                .data(TaskData.builder()
                          .async(false)
                          .taskType(TaskType.CONTAINER_VALIDATION.name())
                          .parameters(new Object[] {null, null,
                              ContainerServiceParams.builder()
                                  .settingAttribute(
                                      SettingAttribute.Builder.aSettingAttribute().withValue(awsConfig).build().toDTO())
                                  .containerServiceName("container_service_name")
                                  .encryptionDetails(new ArrayList<>())
                                  .clusterName("cluster")
                                  .namespace("namespace")
                                  .region("us-east-1")
                                  .subscriptionId("")
                                  .resourceGroup("")
                                  .masterUrl("")
                                  .releaseName("release_name")
                                  .build()})
                          .timeout(TimeUnit.MINUTES.toMillis(InstanceSyncConstants.VALIDATION_TIMEOUT_MINUTES))
                          .build())
                .setupAbstraction(Cd1SetupFields.ENV_ID_FIELD, ENV_ID)
                .setupAbstraction(Cd1SetupFields.ENV_TYPE_FIELD, EnvironmentType.PROD.name())
                .setupAbstraction(Cd1SetupFields.INFRASTRUCTURE_MAPPING_ID_FIELD, INFRA_MAPPING_ID)
                .setupAbstraction(Cd1SetupFields.SERVICE_ID_FIELD, SERVICE_ID)
                .build(),
            DelegateTaskKeys.expiry, DelegateTaskKeys.validUntil);
  }

  private void prepareAwsTaskData(AwsConfig awsConfig) {
    EcsInfrastructureMapping infraMapping = new EcsInfrastructureMapping();
    infraMapping.setClusterName("cluster");
    infraMapping.setAccountId(ACCOUNT_ID);
    infraMapping.setAppId(APP_ID);
    infraMapping.setRegion("us-east-1");
    infraMapping.setEnvId(ENV_ID);
    infraMapping.setServiceId(SERVICE_ID);
    infraMapping.setComputeProviderSettingId(InstanceSyncTestConstants.COMPUTE_PROVIDER_SETTING_ID);
    Environment environment = new Environment();
    environment.setEnvironmentType(EnvironmentType.PROD);
    environment.setUuid(ENV_ID);
    doReturn(environment).when(environmentService).get(APP_ID, ENV_ID);

    doReturn(infraMapping).when(infraMappingService).get(APP_ID, INFRA_MAPPING_ID);
    doReturn(SettingAttribute.Builder.aSettingAttribute().withValue(awsConfig).build())
        .when(settingsService)
        .get(InstanceSyncTestConstants.COMPUTE_PROVIDER_SETTING_ID);
    doReturn(new ArrayList<>()).when(secretManager).getEncryptionDetails(any(AwsConfig.class));
  }
  private void prepareAzureTaskData(AzureConfig azureConfig) {
    AzureKubernetesInfrastructureMapping infraMapping = new AzureKubernetesInfrastructureMapping();
    infraMapping.setClusterName("cluster");
    infraMapping.setAccountId(ACCOUNT_ID);
    infraMapping.setAppId(APP_ID);
    infraMapping.setSubscriptionId("subscription_id");
    infraMapping.setResourceGroup("resource_group");
    infraMapping.setMasterUrl("master_url");
    infraMapping.setEnvId(ENV_ID);
    infraMapping.setServiceId(SERVICE_ID);
    infraMapping.setComputeProviderSettingId(InstanceSyncTestConstants.COMPUTE_PROVIDER_SETTING_ID);
    Environment environment = new Environment();
    environment.setEnvironmentType(EnvironmentType.PROD);
    environment.setUuid(ENV_ID);
    doReturn(environment).when(environmentService).get(APP_ID, ENV_ID);

    doReturn(infraMapping).when(infraMappingService).get(APP_ID, INFRA_MAPPING_ID);
    doReturn(SettingAttribute.Builder.aSettingAttribute().withValue(azureConfig).build())
        .when(settingsService)
        .get(InstanceSyncTestConstants.COMPUTE_PROVIDER_SETTING_ID);
    doReturn(new ArrayList<>()).when(secretManager).getEncryptionDetails(any(AzureConfig.class));
  }
  private void prepareK8sTaskData(K8sClusterConfig k8sClusterConfig) {
    ContainerInfrastructureMapping infraMapping = new DirectKubernetesInfrastructureMapping();
    infraMapping.setClusterName("cluster");
    infraMapping.setAccountId(ACCOUNT_ID);
    infraMapping.setAppId(APP_ID);
    infraMapping.setServiceId(SERVICE_ID);
    infraMapping.setComputeProviderSettingId(InstanceSyncTestConstants.COMPUTE_PROVIDER_SETTING_ID);
    infraMapping.setEnvId(ENV_ID);
    Environment environment = new Environment();
    environment.setEnvironmentType(EnvironmentType.PROD);
    environment.setUuid(ENV_ID);
    doReturn(environment).when(environmentService).get(APP_ID, ENV_ID);

    doReturn(infraMapping).when(infraMappingService).get(APP_ID, INFRA_MAPPING_ID);
    doReturn(k8sClusterConfig).when(containerDeploymentManagerHelper).getK8sClusterConfig(infraMapping, null);
    doReturn(singletonList("awsTag")).when(awsCommandHelper).getAwsConfigTagsFromK8sConfig(Mockito.any());
    mockStatic(UUIDGenerator.class);
    when(UUIDGenerator.generateUuid()).thenReturn("12345");
  }

  private PerpetualTaskClientContext getClientContext(boolean isK8s) {
    Map<String, String> clientParams = new HashMap<>();
    clientParams.put(InstanceSyncConstants.INFRASTRUCTURE_MAPPING_ID, INFRA_MAPPING_ID);
    clientParams.put(InstanceSyncConstants.HARNESS_APPLICATION_ID, APP_ID);
    clientParams.put(NAMESPACE, "namespace");
    clientParams.put(RELEASE_NAME, "release_name");
    clientParams.put(CONTAINER_SERVICE_NAME, "container_service_name");
    clientParams.put(InstanceSyncConstants.CONTAINER_TYPE, isK8s ? "K8S" : "");

    return PerpetualTaskClientContext.builder().clientParams(clientParams).build();
  }
}
