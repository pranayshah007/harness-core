/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.instance;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.ADWAIT;

import static software.wings.service.impl.instance.InstanceSyncTestConstants.ACCOUNT_ID;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.APP_ID;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.APP_NAME;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.ASG_1;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.AWS_ACCESS_KEY;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.AWS_ENCRYPTED_SECRET_KEY;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.COMPUTE_PROVIDER_NAME;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.COMPUTE_PROVIDER_SETTING_ID;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.CONTAINER_ID;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.ENV_ID;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.ENV_NAME;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.HOST_NAME_IP1;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.HOST_NAME_IP2;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.HOST_NAME_IP3;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.HOST_NAME_IP4;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.INFRA_MAPPING_ID;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.INSTANCE_1_ID;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.INSTANCE_2_ID;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.INSTANCE_3_ID;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.INSTANCE_4_ID;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.PRIVATE_DNS_1;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.PRIVATE_DNS_2;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.PRIVATE_DNS_3;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.PUBLIC_DNS_1;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.PUBLIC_DNS_2;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.PUBLIC_DNS_3;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.PUBLIC_DNS_4;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.SERVICE_ID;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.SERVICE_NAME;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.US_EAST;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.EnvironmentType;
import io.harness.beans.PageResponse;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;
import io.harness.security.encryption.EncryptedDataDetail;

import software.wings.WingsBaseTest;
import software.wings.api.AwsAutoScalingGroupDeploymentInfo;
import software.wings.api.DeploymentSummary;
import software.wings.api.ondemandrollback.OnDemandRollbackInfo;
import software.wings.beans.Application;
import software.wings.beans.AwsAmiInfrastructureMapping;
import software.wings.beans.AwsConfig;
import software.wings.beans.Environment;
import software.wings.beans.InfrastructureMappingType;
import software.wings.beans.Service;
import software.wings.beans.SettingAttribute;
import software.wings.beans.infrastructure.instance.Instance;
import software.wings.beans.infrastructure.instance.InstanceType;
import software.wings.beans.infrastructure.instance.info.AutoScalingGroupInstanceInfo;
import software.wings.beans.infrastructure.instance.info.Ec2InstanceInfo;
import software.wings.beans.infrastructure.instance.key.ContainerInstanceKey;
import software.wings.beans.infrastructure.instance.key.HostInstanceKey;
import software.wings.cloudprovider.aws.AwsCodeDeployService;
import software.wings.service.impl.AwsHelperService;
import software.wings.service.impl.AwsInfrastructureProvider;
import software.wings.service.impl.AwsUtils;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.EnvironmentService;
import software.wings.service.intfc.InfrastructureMappingService;
import software.wings.service.intfc.ServiceResourceService;
import software.wings.service.intfc.SettingsService;
import software.wings.service.intfc.aws.manager.AwsAsgHelperServiceManager;
import software.wings.service.intfc.aws.manager.AwsEc2HelperServiceManager;
import software.wings.service.intfc.instance.DeploymentService;
import software.wings.service.intfc.instance.InstanceService;
import software.wings.service.intfc.security.SecretManager;

import com.amazonaws.services.ec2.model.DescribeInstancesResult;
import com.google.inject.Inject;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

@OwnedBy(CDP)
public class AwsAmiInstanceHandlerTest extends WingsBaseTest {
  @Mock private InfrastructureMappingService infraMappingService;
  @Mock private SecretManager secretManager;
  @Mock private EncryptedDataDetail encryptedDataDetail;
  @Mock private InstanceService instanceService;
  @Mock private SettingsService settingsService;
  @Mock private AwsHelperService awsHelperService;
  @Mock private AwsCodeDeployService awsCodeDeployService;
  @Mock private AppService appService;
  @Mock private AwsUtils mockAwsUtils;
  @Mock private AwsEc2HelperServiceManager mockAwsEc2HelperServiceManager;
  @Mock private AwsAsgHelperServiceManager mockAwsAsgHelperServiceManager;

  @Mock EnvironmentService environmentService;
  @Mock ServiceResourceService serviceResourceService;
  @Mock DeploymentService deploymentService;
  @InjectMocks @Inject AwsAmiInstanceHandler awsAmiInstanceHandler;
  @InjectMocks @Spy InstanceHelper instanceHelper;
  @InjectMocks @Spy AwsInfrastructureProvider awsInfrastructureProvider;
  @Spy InstanceUtils instanceUtil;
  private com.amazonaws.services.ec2.model.Instance instance1;
  private com.amazonaws.services.ec2.model.Instance instance2;
  private com.amazonaws.services.ec2.model.Instance instance3;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    instance1 = new com.amazonaws.services.ec2.model.Instance();
    instance1.setPrivateDnsName(InstanceSyncTestConstants.PRIVATE_DNS_1);
    instance1.setPublicDnsName(PUBLIC_DNS_1);
    instance1.setInstanceId(INSTANCE_1_ID);

    instance2 = new com.amazonaws.services.ec2.model.Instance();
    instance2.setPrivateDnsName(PRIVATE_DNS_2);
    instance2.setPublicDnsName(PUBLIC_DNS_2);
    instance2.setInstanceId(INSTANCE_2_ID);

    instance3 = new com.amazonaws.services.ec2.model.Instance();
    instance3.setPrivateDnsName(PRIVATE_DNS_3);
    instance3.setPublicDnsName(PUBLIC_DNS_3);
    instance3.setInstanceId(INSTANCE_3_ID);

    doReturn(AwsAmiInfrastructureMapping.Builder.anAwsAmiInfrastructureMapping()
                 .withUuid(INFRA_MAPPING_ID)
                 .withAccountId(ACCOUNT_ID)
                 .withAutoScalingGroupName(ASG_1)
                 .withAppId(APP_ID)
                 .withInfraMappingType(InfrastructureMappingType.AWS_AMI.getName())
                 .withComputeProviderSettingId(COMPUTE_PROVIDER_SETTING_ID)
                 .withRegion(US_EAST)
                 .withServiceId(SERVICE_ID)
                 .build())
        .when(infraMappingService)
        .get(any(), any());

    doReturn(asList(encryptedDataDetail)).when(secretManager).getEncryptionDetails(any(), any(), any());

    doReturn(SettingAttribute.Builder.aSettingAttribute()
                 .withValue(AwsConfig.builder()
                                .accessKey(AWS_ACCESS_KEY)
                                .accountId(ACCOUNT_ID)
                                .encryptedSecretKey(AWS_ENCRYPTED_SECRET_KEY)
                                .build())
                 .build())
        .when(settingsService)
        .get(any());

    // capture arg
    doReturn(true).when(instanceService).delete(anySet());
    // capture arg
    doReturn(Instance.builder().build()).when(instanceService).save(any(Instance.class));

    doReturn(Application.Builder.anApplication().name("app").uuid("app_1").accountId(ACCOUNT_ID).build())
        .when(appService)
        .get(any());

    doReturn(Environment.Builder.anEnvironment().environmentType(EnvironmentType.PROD).name(ENV_NAME).build())
        .when(environmentService)
        .get(any(), any(), anyBoolean());

    doReturn(Service.builder().name(SERVICE_NAME).build()).when(serviceResourceService).getWithDetails(any(), any());
  }

  // 3 existing instances, 1 EC2, 2 AMI,
  // expected EC2 Delete, 2 AMI Update
  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testSyncInstances_instanceSync() throws Exception {
    doReturn(getInstances()).when(instanceService).getInstancesForAppAndInframapping(anyString(), anyString());
    doReturn(new DescribeInstancesResult()).when(awsHelperService).describeEc2Instances(any(), any(), any(), any());
    doReturn(emptyList())
        .when(mockAwsEc2HelperServiceManager)
        .listEc2Instances(any(), any(), any(), any(), anyString());

    com.amazonaws.services.ec2.model.Instance ec2Instance1 = new com.amazonaws.services.ec2.model.Instance();
    ec2Instance1.setPrivateDnsName(PRIVATE_DNS_1);
    ec2Instance1.setPublicDnsName(PUBLIC_DNS_1);
    ec2Instance1.setInstanceId(INSTANCE_1_ID);

    // ec2Instance with empty privateDnsName
    com.amazonaws.services.ec2.model.Instance ec2Instance2 = new com.amazonaws.services.ec2.model.Instance();
    ec2Instance2.setInstanceId(INSTANCE_2_ID);

    doReturn(asList(ec2Instance1, ec2Instance2))
        .when(mockAwsAsgHelperServiceManager)
        .listAutoScalingGroupInstances(any(), anyList(), anyString(), anyString(), anyString());

    awsAmiInstanceHandler.syncInstances(APP_ID, INFRA_MAPPING_ID, InstanceSyncFlow.MANUAL);

    ArgumentCaptor<Set> captor = ArgumentCaptor.forClass(Set.class);
    verify(instanceService).delete(captor.capture());
    Set idTobeDeleted = captor.getValue();
    assertThat(idTobeDeleted).hasSize(1);
    assertThat(idTobeDeleted.contains(instance3.getInstanceId())).isTrue();
  }

  private List<Instance> getInstances() {
    return asList(
        Instance.builder()
            .uuid(INSTANCE_1_ID)
            .accountId(ACCOUNT_ID)
            .appId(APP_ID)
            .computeProviderId(COMPUTE_PROVIDER_NAME)
            .appName(APP_NAME)
            .envId(ENV_ID)
            .envName(ENV_NAME)
            .envType(EnvironmentType.PROD)
            .infraMappingId(INFRA_MAPPING_ID)
            .infraMappingType(InfrastructureMappingType.AWS_AMI.getName())
            .hostInstanceKey(HostInstanceKey.builder().infraMappingId(INFRA_MAPPING_ID).hostName(HOST_NAME_IP1).build())
            .instanceType(InstanceType.EC2_CLOUD_INSTANCE)
            .containerInstanceKey(ContainerInstanceKey.builder().containerId(CONTAINER_ID).build())
            .instanceInfo(AutoScalingGroupInstanceInfo.builder()
                              .autoScalingGroupName(ASG_1)
                              .hostId(HOST_NAME_IP1)
                              .ec2Instance(instance1)
                              .hostPublicDns(PUBLIC_DNS_1)
                              .build())
            .build(),
        Instance.builder()
            .uuid(INSTANCE_2_ID)
            .accountId(ACCOUNT_ID)
            .appId(APP_ID)
            .computeProviderId(COMPUTE_PROVIDER_NAME)
            .appName(APP_NAME)
            .envId(ENV_ID)
            .envName(ENV_NAME)
            .envType(EnvironmentType.PROD)
            .infraMappingId(INFRA_MAPPING_ID)
            .infraMappingType(InfrastructureMappingType.AWS_AMI.getName())
            .hostInstanceKey(HostInstanceKey.builder().infraMappingId(INFRA_MAPPING_ID).hostName(HOST_NAME_IP2).build())
            .instanceType(InstanceType.EC2_CLOUD_INSTANCE)
            .containerInstanceKey(ContainerInstanceKey.builder().containerId(CONTAINER_ID).build())
            .instanceInfo(AutoScalingGroupInstanceInfo.builder()
                              .autoScalingGroupName(ASG_1)
                              .hostId(HOST_NAME_IP2)
                              .ec2Instance(instance2)
                              .hostPublicDns(PUBLIC_DNS_2)
                              .build())
            .build(),
        Instance.builder()
            .uuid(INSTANCE_3_ID)
            .accountId(ACCOUNT_ID)
            .appId(APP_ID)
            .computeProviderId(COMPUTE_PROVIDER_NAME)
            .appName(APP_NAME)
            .envId(ENV_ID)
            .envName(ENV_NAME)
            .envType(EnvironmentType.PROD)
            .infraMappingId(INFRA_MAPPING_ID)
            .infraMappingType(InfrastructureMappingType.AWS_AMI.getName())
            .hostInstanceKey(HostInstanceKey.builder().infraMappingId(INFRA_MAPPING_ID).hostName(HOST_NAME_IP3).build())
            .instanceType(InstanceType.EC2_CLOUD_INSTANCE)
            .containerInstanceKey(ContainerInstanceKey.builder().containerId(CONTAINER_ID).build())
            .instanceInfo(Ec2InstanceInfo.builder().ec2Instance(instance3).hostPublicDns(PUBLIC_DNS_3).build())
            .build(),
        Instance.builder()
            .uuid(INSTANCE_4_ID)
            .accountId(ACCOUNT_ID)
            .appId(APP_ID)
            .computeProviderId(COMPUTE_PROVIDER_NAME)
            .appName(APP_NAME)
            .envId(ENV_ID)
            .envName(ENV_NAME)
            .envType(EnvironmentType.PROD)
            .infraMappingId(INFRA_MAPPING_ID)
            .infraMappingType(InfrastructureMappingType.AWS_AMI.getName())
            .hostInstanceKey(HostInstanceKey.builder().infraMappingId(INFRA_MAPPING_ID).hostName(HOST_NAME_IP4).build())
            .instanceType(InstanceType.EC2_CLOUD_INSTANCE)
            .containerInstanceKey(ContainerInstanceKey.builder().containerId(CONTAINER_ID).build())
            .instanceInfo(Ec2InstanceInfo.builder()
                              .ec2Instance(null /*for NPE issue we saw in prod*/)
                              .hostPublicDns(PUBLIC_DNS_4)
                              .build())
            .build());
  }

  // 3 existing instances, 1 EC2, 2 AMI,
  // expected EC2 Delete, 2 AMI Update
  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testSyncInstances_Rollback() throws Exception {
    PageResponse<Instance> pageResponse = new PageResponse<>();

    doReturn(Optional.of(
                 DeploymentSummary.builder()
                     .deploymentInfo(
                         AwsAutoScalingGroupDeploymentInfo.builder().autoScalingGroupName("autoScalingGroup").build())
                     .accountId(ACCOUNT_ID)
                     .infraMappingId(INFRA_MAPPING_ID)
                     .workflowExecutionId("workfloeExecution_1")
                     .stateExecutionInstanceId("stateExecutionInstanceId")
                     .artifactBuildNum("1")
                     .artifactName("old")
                     .build()))
        .when(deploymentService)
        .get(any(DeploymentSummary.class));

    getInstances();

    doReturn(getInstances()).when(instanceService).getInstancesForAppAndInframapping(any(), any());
    doReturn(new DescribeInstancesResult()).when(awsHelperService).describeEc2Instances(any(), any(), any(), any());
    doReturn(emptyList()).when(mockAwsEc2HelperServiceManager).listEc2Instances(any(), any(), any(), any(), any());

    com.amazonaws.services.ec2.model.Instance ec2Instance1 = new com.amazonaws.services.ec2.model.Instance();
    ec2Instance1.setPrivateDnsName(PRIVATE_DNS_1);
    ec2Instance1.setPublicDnsName(PUBLIC_DNS_1);
    ec2Instance1.setInstanceId(INSTANCE_1_ID);

    // ec2Instance with empty privateDnsName
    com.amazonaws.services.ec2.model.Instance ec2Instance2 = new com.amazonaws.services.ec2.model.Instance();
    ec2Instance2.setInstanceId(INSTANCE_2_ID);

    doReturn(asList(ec2Instance1, ec2Instance2))
        .when(mockAwsAsgHelperServiceManager)
        .listAutoScalingGroupInstances(any(), any(), any(), any(), any());
    OnDemandRollbackInfo onDemandRollbackInfo = OnDemandRollbackInfo.builder().onDemandRollback(false).build();
    awsAmiInstanceHandler.handleNewDeployment(
        Arrays.asList(
            DeploymentSummary.builder()
                .deploymentInfo(
                    AwsAutoScalingGroupDeploymentInfo.builder().autoScalingGroupName("autoScalingGroup").build())
                .infraMappingId(INFRA_MAPPING_ID)
                .accountId(ACCOUNT_ID)
                .artifactName("New")
                .artifactBuildNum("2")
                .stateExecutionInstanceId("stateExecutionInstanceId")
                .workflowExecutionId("workflowExecutionId")
                .workflowId("workflowId")
                .build()),
        true, onDemandRollbackInfo);

    ArgumentCaptor<Set> captor = ArgumentCaptor.forClass(Set.class);
    verify(instanceService).delete(captor.capture());
    Set idTobeDeleted = captor.getValue();
    assertThat(idTobeDeleted).hasSize(1);
    assertThat(idTobeDeleted.contains(instance3.getInstanceId())).isTrue();

    ArgumentCaptor<Instance> captorInstance = ArgumentCaptor.forClass(Instance.class);
    verify(instanceService, times(2)).save(captorInstance.capture());
    List<Instance> instanceList = captorInstance.getAllValues();
    assertThat(instanceList.get(0)).isNotNull();
    assertThat(instanceList.get(0).getLastArtifactBuildNum()).isEqualTo("1");
    assertThat(instanceList.get(0).getLastArtifactName()).isEqualTo("old");
    assertThat(instanceList.get(1)).isNotNull();
    assertThat(instanceList.get(1).getLastArtifactBuildNum()).isEqualTo("1");
    assertThat(instanceList.get(1).getLastArtifactName()).isEqualTo("old");
  }
}
