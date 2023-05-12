/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.instance;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.beans.FeatureName.MOVE_AWS_AMI_SPOT_INST_INSTANCE_SYNC_TO_PERPETUAL_TASK;
import static io.harness.rule.OwnerRule.ABOSII;
import static io.harness.rule.OwnerRule.SATYAM;

import static software.wings.beans.AmiDeploymentType.SPOTINST;
import static software.wings.beans.Application.Builder.anApplication;
import static software.wings.beans.AwsAmiInfrastructureMapping.Builder.anAwsAmiInfrastructureMapping;
import static software.wings.beans.Environment.Builder.anEnvironment;
import static software.wings.beans.SettingAttribute.Builder.aSettingAttribute;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.ACCOUNT_ID;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.APP_ID;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.APP_NAME;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.COMPUTE_PROVIDER_NAME;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.COMPUTE_PROVIDER_SETTING_ID;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.CONTAINER_ID;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.EC2_INSTANCE_1_ID;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.EC2_INSTANCE_2_ID;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.EC2_INSTANCE_E_ID;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.ELASTIGROUP_ID;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.ELASTIGROUP_NAME;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.ENV_ID;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.ENV_NAME;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.HOST_NAME_IP1;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.HOST_NAME_IP2;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.INFRA_MAPPING_ID;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.INSTANCE_1_ID;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.INSTANCE_2_ID;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.PRIVATE_DNS_1;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.PRIVATE_DNS_2;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.PRIVATE_DNS_3;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.PUBLIC_DNS_1;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.PUBLIC_DNS_2;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.PUBLIC_DNS_3;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.SERVICE_ID;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.SERVICE_NAME;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.SPOTINST_CLOUD_PROVIDER;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.US_EAST;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static wiremock.com.google.common.collect.Lists.newArrayList;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.EnvironmentType;
import io.harness.category.element.UnitTests;
import io.harness.delegate.task.spotinst.response.SpotInstListElastigroupInstancesResponse;
import io.harness.delegate.task.spotinst.response.SpotInstTaskExecutionResponse;
import io.harness.ff.FeatureFlagService;
import io.harness.logging.CommandExecutionStatus;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.api.DeploymentSummary;
import software.wings.api.SpotinstAmiDeploymentInfo;
import software.wings.api.ondemandrollback.OnDemandRollbackInfo;
import software.wings.beans.AwsAmiInfrastructureMapping;
import software.wings.beans.AwsConfig;
import software.wings.beans.InfrastructureMappingType;
import software.wings.beans.Service;
import software.wings.beans.SettingAttribute;
import software.wings.beans.SpotInstConfig;
import software.wings.beans.infrastructure.instance.Instance;
import software.wings.beans.infrastructure.instance.InstanceType;
import software.wings.beans.infrastructure.instance.info.InstanceInfo;
import software.wings.beans.infrastructure.instance.info.SpotinstAmiInstanceInfo;
import software.wings.beans.infrastructure.instance.key.ContainerInstanceKey;
import software.wings.beans.infrastructure.instance.key.HostInstanceKey;
import software.wings.beans.infrastructure.instance.key.deployment.SpotinstAmiDeploymentKey;
import software.wings.service.impl.spotinst.SpotinstHelperServiceManager;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.EnvironmentService;
import software.wings.service.intfc.InfrastructureMappingService;
import software.wings.service.intfc.ServiceResourceService;
import software.wings.service.intfc.SettingsService;
import software.wings.service.intfc.instance.InstanceService;

import com.google.inject.Inject;
import java.util.List;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@OwnedBy(CDP)
public class SpotinstAmiInstanceHandlerTest extends WingsBaseTest {
  @Mock private InfrastructureMappingService mockinfraMappingService;
  @Mock private SpotinstHelperServiceManager mockSpotinstHelperServiceManager;
  @Mock private InstanceService mockInstanceService;
  @Mock private AppService mockAppService;
  @Mock private EnvironmentService mockEnvironmentService;
  @Mock private ServiceResourceService mockServiceResourceService;
  @Mock private SettingsService mockSettingService;
  @Mock private FeatureFlagService mockFeatureFlagService;

  @InjectMocks @Inject private SpotinstAmiInstanceHandler spotinstAmiInstanceHandler;

  @Before
  public void setup() {
    SpotInstConfig spotInstConfig = SpotInstConfig.builder().accountId(ACCOUNT_ID).build();
    SettingAttribute spotInstSettingAttribute = aSettingAttribute()
                                                    .withName(SPOTINST_CLOUD_PROVIDER)
                                                    .withAccountId(ACCOUNT_ID)
                                                    .withValue(spotInstConfig)
                                                    .build();
    AwsConfig awsConfig = AwsConfig.builder().accountId(ACCOUNT_ID).build();
    SettingAttribute awsSettingAttribute = aSettingAttribute()
                                               .withName(COMPUTE_PROVIDER_SETTING_ID)
                                               .withAccountId(ACCOUNT_ID)
                                               .withValue(awsConfig)
                                               .build();

    doReturn(spotInstSettingAttribute).when(mockSettingService).get(SPOTINST_CLOUD_PROVIDER);
    doReturn(awsSettingAttribute).when(mockSettingService).get(COMPUTE_PROVIDER_SETTING_ID);

    doReturn(anApplication().name(APP_NAME).uuid(APP_ID).accountId(ACCOUNT_ID).build()).when(mockAppService).get(any());
    doReturn(anEnvironment().environmentType(EnvironmentType.PROD).name(ENV_NAME).build())
        .when(mockEnvironmentService)
        .get(any(), any(), anyBoolean());
    doReturn(Service.builder().name(SERVICE_NAME).build())
        .when(mockServiceResourceService)
        .getWithDetails(any(), any());
  }

  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void testIteratorSyncInstancesForElastigroup() {
    com.amazonaws.services.ec2.model.Instance ec2Instance1 = new com.amazonaws.services.ec2.model.Instance();
    ec2Instance1.setPrivateDnsName(PRIVATE_DNS_1);
    ec2Instance1.setPublicDnsName(PUBLIC_DNS_1);
    ec2Instance1.setInstanceId(EC2_INSTANCE_1_ID);

    com.amazonaws.services.ec2.model.Instance ec2Instance2 = new com.amazonaws.services.ec2.model.Instance();
    ec2Instance2.setPrivateDnsName(PRIVATE_DNS_2);
    ec2Instance2.setPublicDnsName(PUBLIC_DNS_2);
    ec2Instance2.setInstanceId(EC2_INSTANCE_2_ID);

    com.amazonaws.services.ec2.model.Instance ec2Instance3 = new com.amazonaws.services.ec2.model.Instance();
    ec2Instance3.setPrivateDnsName(PRIVATE_DNS_3);
    ec2Instance3.setPublicDnsName(PUBLIC_DNS_3);
    ec2Instance3.setInstanceId(EC2_INSTANCE_E_ID);

    List<Instance> currentInstancesInDb =
        newArrayList(newInstance(INSTANCE_1_ID, HOST_NAME_IP1, ec2Instance1, PUBLIC_DNS_1, PRIVATE_DNS_1),
            newInstance(INSTANCE_2_ID, HOST_NAME_IP2, ec2Instance2, PUBLIC_DNS_2, PRIVATE_DNS_2));

    doReturn(getInfrastructureMapping()).when(mockinfraMappingService).get(APP_ID, INFRA_MAPPING_ID);
    doReturn(currentInstancesInDb)
        .when(mockInstanceService)
        .getInstancesForAppAndInframapping(APP_ID, INFRA_MAPPING_ID);
    doReturn(newArrayList(ec2Instance2, ec2Instance3))
        .when(mockSpotinstHelperServiceManager)
        .listElastigroupInstances(any(), anyList(), any(), anyList(), any(), any(), any());

    spotinstAmiInstanceHandler.syncInstances(APP_ID, INFRA_MAPPING_ID, InstanceSyncFlow.ITERATOR);

    assertInstancesSync();
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testPerpetualTaskSyncInstances() {
    com.amazonaws.services.ec2.model.Instance ec2Instance1 = new com.amazonaws.services.ec2.model.Instance();
    ec2Instance1.setPrivateDnsName(PRIVATE_DNS_1);
    ec2Instance1.setPublicDnsName(PUBLIC_DNS_1);
    ec2Instance1.setInstanceId(EC2_INSTANCE_1_ID);

    com.amazonaws.services.ec2.model.Instance ec2Instance2 = new com.amazonaws.services.ec2.model.Instance();
    ec2Instance2.setPrivateDnsName(PRIVATE_DNS_2);
    ec2Instance2.setPublicDnsName(PUBLIC_DNS_2);
    ec2Instance2.setInstanceId(EC2_INSTANCE_2_ID);

    com.amazonaws.services.ec2.model.Instance ec2Instance3 = new com.amazonaws.services.ec2.model.Instance();
    ec2Instance3.setPrivateDnsName(PRIVATE_DNS_3);
    ec2Instance3.setPublicDnsName(PUBLIC_DNS_3);
    ec2Instance3.setInstanceId(EC2_INSTANCE_E_ID);

    List<Instance> currentInstancesInDb =
        newArrayList(newInstance(INSTANCE_1_ID, HOST_NAME_IP1, ec2Instance1, PUBLIC_DNS_1, PRIVATE_DNS_1),
            newInstance(INSTANCE_2_ID, HOST_NAME_IP2, ec2Instance2, PUBLIC_DNS_2, PRIVATE_DNS_2));
    getInfrastructureMapping();

    SpotInstTaskExecutionResponse spotInstTaskExecutionResponse =
        SpotInstTaskExecutionResponse.builder()
            .spotInstTaskResponse(SpotInstListElastigroupInstancesResponse.builder()
                                      .elastigroupId(ELASTIGROUP_ID)
                                      .elastigroupInstances(newArrayList(ec2Instance2, ec2Instance3))
                                      .build())
            .build();

    doReturn(true)
        .when(mockFeatureFlagService)
        .isEnabled(MOVE_AWS_AMI_SPOT_INST_INSTANCE_SYNC_TO_PERPETUAL_TASK, ACCOUNT_ID);
    doReturn(currentInstancesInDb)
        .when(mockInstanceService)
        .getInstancesForAppAndInframapping(APP_ID, INFRA_MAPPING_ID);

    spotinstAmiInstanceHandler.processInstanceSyncResponseFromPerpetualTask(
        getInfrastructureMapping(), spotInstTaskExecutionResponse);

    assertInstancesSync();
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testHandleNewDeployment() {
    com.amazonaws.services.ec2.model.Instance ec2Instance1 = new com.amazonaws.services.ec2.model.Instance();
    ec2Instance1.setPrivateDnsName(PRIVATE_DNS_1);
    ec2Instance1.setPublicDnsName(PUBLIC_DNS_1);
    ec2Instance1.setInstanceId(EC2_INSTANCE_1_ID);

    com.amazonaws.services.ec2.model.Instance ec2Instance2 = new com.amazonaws.services.ec2.model.Instance();
    ec2Instance2.setPrivateDnsName(PRIVATE_DNS_2);
    ec2Instance2.setPublicDnsName(PUBLIC_DNS_2);
    ec2Instance2.setInstanceId(EC2_INSTANCE_2_ID);

    com.amazonaws.services.ec2.model.Instance ec2Instance3 = new com.amazonaws.services.ec2.model.Instance();
    ec2Instance3.setPrivateDnsName(PRIVATE_DNS_3);
    ec2Instance3.setPublicDnsName(PUBLIC_DNS_3);
    ec2Instance3.setInstanceId(EC2_INSTANCE_E_ID);

    List<Instance> currentInstancesInDb =
        newArrayList(newInstance(INSTANCE_1_ID, HOST_NAME_IP1, ec2Instance1, PUBLIC_DNS_1, PRIVATE_DNS_1),
            newInstance(INSTANCE_2_ID, HOST_NAME_IP2, ec2Instance2, PUBLIC_DNS_2, PRIVATE_DNS_2));
    getInfrastructureMapping();

    DeploymentSummary deploymentSummary =
        DeploymentSummary.builder()
            .deploymentInfo(SpotinstAmiDeploymentInfo.builder()
                                .elastigroupId(ELASTIGROUP_ID)
                                .elastigroupName(ELASTIGROUP_NAME)
                                .build())
            .spotinstAmiDeploymentKey(SpotinstAmiDeploymentKey.builder().elastigroupId(ELASTIGROUP_ID).build())
            .appId(APP_ID)
            .accountId(ACCOUNT_ID)
            .infraMappingId(INFRA_MAPPING_ID)
            .workflowExecutionId("workflowExecution_1")
            .stateExecutionInstanceId("stateExecutionInstanceId")
            .artifactBuildNum("1")
            .artifactName("new")
            .build();

    doReturn(getInfrastructureMapping()).when(mockinfraMappingService).get(APP_ID, INFRA_MAPPING_ID);
    doReturn(currentInstancesInDb)
        .when(mockInstanceService)
        .getInstancesForAppAndInframapping(APP_ID, INFRA_MAPPING_ID);
    doReturn(newArrayList(ec2Instance2, ec2Instance3))
        .when(mockSpotinstHelperServiceManager)
        .listElastigroupInstances(any(), anyList(), any(), anyList(), any(), any(), any());

    spotinstAmiInstanceHandler.handleNewDeployment(
        singletonList(deploymentSummary), false, OnDemandRollbackInfo.builder().build());

    assertInstancesSync();
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void getStatusForSuccessfulCommandExecutionAndNotEmptyInstances() {
    com.amazonaws.services.ec2.model.Instance ec2Instance = new com.amazonaws.services.ec2.model.Instance();
    ec2Instance.setPrivateDnsName(PRIVATE_DNS_1);
    ec2Instance.setPublicDnsName(PUBLIC_DNS_1);
    ec2Instance.setInstanceId(EC2_INSTANCE_1_ID);

    SpotInstTaskExecutionResponse spotInstTaskExecutionResponse =
        SpotInstTaskExecutionResponse.builder()
            .spotInstTaskResponse(SpotInstListElastigroupInstancesResponse.builder()
                                      .elastigroupId(ELASTIGROUP_ID)
                                      .elastigroupInstances(newArrayList(ec2Instance))
                                      .build())
            .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
            .build();

    Status status = spotinstAmiInstanceHandler.getStatus(getInfrastructureMapping(), spotInstTaskExecutionResponse);

    assertThat(status.isSuccess()).isTrue();
    assertThat(status.isRetryable()).isTrue();
    assertThat(status.getErrorMessage()).isNull();
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void getStatusForSuccessfulCommandExecutionAndEmptyInstances() {
    com.amazonaws.services.ec2.model.Instance ec2Instance = new com.amazonaws.services.ec2.model.Instance();
    ec2Instance.setPrivateDnsName(PRIVATE_DNS_1);
    ec2Instance.setPublicDnsName(PUBLIC_DNS_1);
    ec2Instance.setInstanceId(EC2_INSTANCE_1_ID);

    SpotInstTaskExecutionResponse spotInstTaskExecutionResponse =
        SpotInstTaskExecutionResponse.builder()
            .spotInstTaskResponse(SpotInstListElastigroupInstancesResponse.builder()
                                      .elastigroupId(ELASTIGROUP_ID)
                                      .elastigroupInstances(emptyList())
                                      .build())
            .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
            .build();

    Status status = spotinstAmiInstanceHandler.getStatus(getInfrastructureMapping(), spotInstTaskExecutionResponse);

    assertThat(status.isSuccess()).isTrue();
    assertThat(status.isRetryable()).isFalse();
    assertThat(status.getErrorMessage()).isNull();
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void getStatusForFailedCommandExecution() {
    com.amazonaws.services.ec2.model.Instance ec2Instance = new com.amazonaws.services.ec2.model.Instance();
    ec2Instance.setPrivateDnsName(PRIVATE_DNS_1);
    ec2Instance.setPublicDnsName(PUBLIC_DNS_1);
    ec2Instance.setInstanceId(EC2_INSTANCE_1_ID);

    SpotInstTaskExecutionResponse spotInstTaskExecutionResponse =
        SpotInstTaskExecutionResponse.builder()
            .commandExecutionStatus(CommandExecutionStatus.FAILURE)
            .errorMessage("Unable to fetch instances list")
            .build();

    Status status = spotinstAmiInstanceHandler.getStatus(getInfrastructureMapping(), spotInstTaskExecutionResponse);

    assertThat(status.isSuccess()).isFalse();
    assertThat(status.isRetryable()).isTrue();
    assertThat(status.getErrorMessage()).isEqualTo("Unable to fetch instances list");
  }

  private void assertInstancesSync() {
    ArgumentCaptor<Set> captor = ArgumentCaptor.forClass(Set.class);
    verify(mockInstanceService).delete(captor.capture());
    Set idsTobeDeleted = captor.getValue();
    assertThat(idsTobeDeleted.size()).isEqualTo(1);
    assertThat(idsTobeDeleted.contains(INSTANCE_1_ID)).isTrue();

    ArgumentCaptor<Instance> captorInstance = ArgumentCaptor.forClass(Instance.class);
    verify(mockInstanceService).save(captorInstance.capture());
    Instance instance = captorInstance.getValue();
    assertThat(instance).isNotNull();
    InstanceInfo instanceInfo = instance.getInstanceInfo();
    assertThat(instanceInfo).isNotNull();
    assertThat(instanceInfo instanceof SpotinstAmiInstanceInfo).isTrue();
    SpotinstAmiInstanceInfo spotinstAmiInstanceInfo = (SpotinstAmiInstanceInfo) instanceInfo;
    assertThat(spotinstAmiInstanceInfo.getEc2Instance().getInstanceId()).isEqualTo(EC2_INSTANCE_E_ID);
  }

  private AwsAmiInfrastructureMapping getInfrastructureMapping() {
    return anAwsAmiInfrastructureMapping()
        .withUuid(INFRA_MAPPING_ID)
        .withAccountId(ACCOUNT_ID)
        .withAmiDeploymentType(SPOTINST)
        .withAppId(APP_ID)
        .withInfraMappingType(InfrastructureMappingType.AWS_AMI.getName())
        .withComputeProviderSettingId(COMPUTE_PROVIDER_SETTING_ID)
        .withSpotinstCloudProvider(SPOTINST_CLOUD_PROVIDER)
        .withRegion(US_EAST)
        .withServiceId(SERVICE_ID)
        .build();
  }

  private Instance newInstance(String uuid, String hostname, com.amazonaws.services.ec2.model.Instance ec2Instance,
      String publicDns, String privateDns) {
    return Instance.builder()
        .uuid(uuid)
        .accountId(ACCOUNT_ID)
        .appId(APP_ID)
        .computeProviderId(COMPUTE_PROVIDER_NAME)
        .appName(APP_NAME)
        .envId(ENV_ID)
        .envName(ENV_NAME)
        .envType(EnvironmentType.PROD)
        .infraMappingId(INFRA_MAPPING_ID)
        .infraMappingType(InfrastructureMappingType.AWS_AMI.getName())
        .hostInstanceKey(HostInstanceKey.builder().infraMappingId(INFRA_MAPPING_ID).hostName(hostname).build())
        .instanceType(InstanceType.EC2_CLOUD_INSTANCE)
        .containerInstanceKey(ContainerInstanceKey.builder().containerId(CONTAINER_ID).build())
        .instanceInfo(SpotinstAmiInstanceInfo.builder()
                          .ec2Instance(ec2Instance)
                          .elastigroupId(ELASTIGROUP_ID)
                          .hostPublicDns(publicDns)
                          .hostName(privateDns)
                          .build())
        .lastWorkflowExecutionId("workflow-execution-id")
        .build();
  }
}
