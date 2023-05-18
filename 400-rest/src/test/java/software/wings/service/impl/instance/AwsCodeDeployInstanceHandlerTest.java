/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.instance;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.ABOSII;
import static io.harness.rule.OwnerRule.ADWAIT;

import static software.wings.service.impl.instance.InstanceSyncTestConstants.ACCOUNT_ID;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.APP_ID;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.APP_NAME;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.AWS_ACCESS_KEY;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.AWS_ENCRYPTED_SECRET_KEY;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.COMPUTE_PROVIDER_NAME;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.COMPUTE_PROVIDER_SETTING_ID;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.CONTAINER_ID;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.DEPLOYMENT_ID;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.ENV_ID;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.ENV_NAME;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.HOST_NAME_IP1;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.HOST_NAME_IP2;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.HOST_NAME_IP3;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.INFRA_MAPPING_ID;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.INSTANCE_1_ID;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.INSTANCE_2_ID;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.INSTANCE_3_ID;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.PRIVATE_DNS_1;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.PRIVATE_DNS_2;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.PRIVATE_DNS_3;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.PUBLIC_DNS_1;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.PUBLIC_DNS_2;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.PUBLIC_DNS_3;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.SERVICE_ID;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.SERVICE_NAME;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.US_EAST;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.EnvironmentType;
import io.harness.beans.ExecutionStatus;
import io.harness.category.element.UnitTests;
import io.harness.ff.FeatureFlagService;
import io.harness.rule.Owner;
import io.harness.security.encryption.EncryptedDataDetail;

import software.wings.WingsBaseTest;
import software.wings.api.AwsCodeDeployDeploymentInfo;
import software.wings.api.DeploymentSummary;
import software.wings.api.ondemandrollback.OnDemandRollbackInfo;
import software.wings.beans.Application;
import software.wings.beans.AwsConfig;
import software.wings.beans.CodeDeployInfrastructureMapping;
import software.wings.beans.CodeDeployInfrastructureMapping.CodeDeployInfrastructureMappingBuilder;
import software.wings.beans.Environment;
import software.wings.beans.InfrastructureMappingType;
import software.wings.beans.Service;
import software.wings.beans.SettingAttribute;
import software.wings.beans.infrastructure.instance.Instance;
import software.wings.beans.infrastructure.instance.InstanceType;
import software.wings.beans.infrastructure.instance.info.Ec2InstanceInfo;
import software.wings.beans.infrastructure.instance.key.ContainerInstanceKey;
import software.wings.beans.infrastructure.instance.key.HostInstanceKey;
import software.wings.service.impl.AwsHelperService;
import software.wings.service.impl.AwsInfrastructureProvider;
import software.wings.service.impl.AwsUtils;
import software.wings.service.impl.aws.model.AwsCodeDeployListDeploymentInstancesResponse;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.EnvironmentService;
import software.wings.service.intfc.InfrastructureMappingService;
import software.wings.service.intfc.ServiceResourceService;
import software.wings.service.intfc.SettingsService;
import software.wings.service.intfc.aws.manager.AwsCodeDeployHelperServiceManager;
import software.wings.service.intfc.aws.manager.AwsEc2HelperServiceManager;
import software.wings.service.intfc.instance.DeploymentService;
import software.wings.service.intfc.instance.InstanceService;
import software.wings.service.intfc.security.SecretManager;

import com.amazonaws.services.ec2.model.DescribeInstancesResult;
import com.amazonaws.services.ec2.model.Reservation;
import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
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
public class AwsCodeDeployInstanceHandlerTest extends WingsBaseTest {
  @Mock private InfrastructureMappingService infraMappingService;
  @Mock private SecretManager secretManager;
  @Mock private EncryptedDataDetail encryptedDataDetail;
  @Mock private InstanceService instanceService;
  @Mock private SettingsService settingsService;
  @Mock private AwsHelperService awsHelperService;
  @Mock private AppService appService;
  @Mock private DeploymentService deploymentService;
  @Mock private AwsUtils mockAwsUtils;
  @Mock private AwsEc2HelperServiceManager mockAwsEc2HelperServiceManager;
  @Mock private AwsCodeDeployHelperServiceManager mockAwsCodeDeployHelperServiceManager;
  @Mock private FeatureFlagService mockFeatureFlagService;
  @Mock EnvironmentService environmentService;
  @Mock ServiceResourceService serviceResourceService;
  @InjectMocks @Inject AwsCodeDeployInstanceHandler awsCodeDeployInstanceHandler;
  @InjectMocks @Spy AwsInfrastructureProvider awsInfrastructureProvider;
  @InjectMocks @Spy InstanceHelper instanceHelper;
  @Spy InstanceUtils instanceUtil;
  private com.amazonaws.services.ec2.model.Instance instance1;
  private com.amazonaws.services.ec2.model.Instance instance2;
  private com.amazonaws.services.ec2.model.Instance instance3;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    instance1 = new com.amazonaws.services.ec2.model.Instance();
    instance1.setPrivateDnsName(PRIVATE_DNS_1);
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

    doReturn(CodeDeployInfrastructureMappingBuilder.aCodeDeployInfrastructureMapping()
                 .withUuid(INFRA_MAPPING_ID)
                 .withAccountId(ACCOUNT_ID)
                 .withAppId(APP_ID)
                 .withInfraMappingType(InfrastructureMappingType.AWS_AWS_CODEDEPLOY.getName())
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

    // catpure arg
    doReturn(true).when(instanceService).delete(anySet());
    // capture arg
    doReturn(Instance.builder().build()).when(instanceService).saveOrUpdate(any(Instance.class));

    doReturn(Application.Builder.anApplication().name("app").uuid("app_1").accountId(ACCOUNT_ID).build())
        .when(appService)
        .get(any());

    doReturn(Environment.Builder.anEnvironment().environmentType(EnvironmentType.PROD).name(ENV_NAME).build())
        .when(environmentService)
        .get(any(), any(), anyBoolean());

    doReturn(Service.builder().name(SERVICE_NAME).build()).when(serviceResourceService).getWithDetails(any(), any());
  }

  // 3 existing instances
  // expected 1 delete 2 update
  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testSyncInstances_syncJob() throws Exception {
    final List<Instance> instances = asList(
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
            .infraMappingType(InfrastructureMappingType.AWS_AWS_CODEDEPLOY.getName())
            .hostInstanceKey(HostInstanceKey.builder().infraMappingId(INFRA_MAPPING_ID).hostName(HOST_NAME_IP1).build())
            .instanceType(InstanceType.EC2_CLOUD_INSTANCE)
            .containerInstanceKey(ContainerInstanceKey.builder().containerId(CONTAINER_ID).build())
            .instanceInfo(Ec2InstanceInfo.builder().ec2Instance(instance1).hostPublicDns(PUBLIC_DNS_1).build())
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
            .infraMappingType(InfrastructureMappingType.AWS_AWS_CODEDEPLOY.getName())
            .hostInstanceKey(HostInstanceKey.builder().infraMappingId(INFRA_MAPPING_ID).hostName(HOST_NAME_IP2).build())
            .instanceType(InstanceType.EC2_CLOUD_INSTANCE)
            .containerInstanceKey(ContainerInstanceKey.builder().containerId(CONTAINER_ID).build())
            .instanceInfo(Ec2InstanceInfo.builder().ec2Instance(instance2).hostPublicDns(PUBLIC_DNS_2).build())
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
            .infraMappingType(InfrastructureMappingType.AWS_AWS_CODEDEPLOY.getName())
            .hostInstanceKey(HostInstanceKey.builder().infraMappingId(INFRA_MAPPING_ID).hostName(HOST_NAME_IP3).build())
            .instanceType(InstanceType.EC2_CLOUD_INSTANCE)
            .containerInstanceKey(ContainerInstanceKey.builder().containerId(CONTAINER_ID).build())
            .instanceInfo(Ec2InstanceInfo.builder().ec2Instance(instance3).hostPublicDns(PUBLIC_DNS_3).build())
            .build());

    doReturn(instances).when(instanceService).getInstancesForAppAndInframapping(any(), any());

    DescribeInstancesResult result = new DescribeInstancesResult();
    Collection<Reservation> reservations = new ArrayList<>();
    reservations.add(new Reservation().withInstances(new com.amazonaws.services.ec2.model.Instance[] {instance3}));
    result.setReservations(reservations);
    doReturn(result).when(awsHelperService).describeEc2Instances(any(), any(), any(), any());
    doReturn(singletonList(instance3))
        .when(mockAwsEc2HelperServiceManager)
        .listEc2Instances(any(), any(), any(), any(), any());

    awsCodeDeployInstanceHandler.syncInstances(APP_ID, INFRA_MAPPING_ID, InstanceSyncFlow.MANUAL);
    ArgumentCaptor<Set> captor = ArgumentCaptor.forClass(Set.class);
    verify(instanceService).delete(captor.capture());
    Set idTobeDeleted = captor.getValue();
    assertThat(idTobeDeleted).hasSize(2);
    assertThat(idTobeDeleted.contains(instance1.getInstanceId())).isTrue();
    assertThat(idTobeDeleted.contains(instance2.getInstanceId())).isTrue();

    verify(instanceService, never()).saveOrUpdate(any(Instance.class));
  }

  // 3 existing instances
  // expected 1 delete 2 update
  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testSyncInstances_NewDeployment() throws Exception {
    doReturn(getInstances()).when(instanceService).getInstancesForAppAndInframapping(any(), any());
    com.amazonaws.services.ec2.model.Instance ec2Instance2 = new com.amazonaws.services.ec2.model.Instance();
    ec2Instance2.setPrivateDnsName(PRIVATE_DNS_3);
    ec2Instance2.setInstanceId(INSTANCE_3_ID);
    ec2Instance2.setPublicDnsName(PUBLIC_DNS_3);

    doReturn(asList(ec2Instance2))
        .when(mockAwsCodeDeployHelperServiceManager)
        .listDeploymentInstances(any(), any(), any(), any(), any());
    doReturn(HOST_NAME_IP3).when(awsHelperService).getHostnameFromPrivateDnsName(PRIVATE_DNS_3);
    doReturn(HOST_NAME_IP3).when(mockAwsUtils).getHostnameFromPrivateDnsName(PRIVATE_DNS_3);

    DescribeInstancesResult result = new DescribeInstancesResult();
    Collection<Reservation> reservations = new ArrayList<>();
    reservations.add(
        new Reservation().withInstances(new com.amazonaws.services.ec2.model.Instance[] {instance1, instance3}));
    result.setReservations(reservations);
    doReturn(result).when(awsHelperService).describeEc2Instances(any(), any(), any(), any());
    doReturn(asList(instance1, instance3))
        .when(mockAwsEc2HelperServiceManager)
        .listEc2Instances(any(), any(), any(), any(), any());
    OnDemandRollbackInfo onDemandRollbackInfo = OnDemandRollbackInfo.builder().onDemandRollback(false).build();
    awsCodeDeployInstanceHandler.handleNewDeployment(
        Arrays.asList(DeploymentSummary.builder()
                          .deploymentInfo(AwsCodeDeployDeploymentInfo.builder().deploymentId(DEPLOYMENT_ID).build())
                          .infraMappingId(INFRA_MAPPING_ID)
                          .build()),
        false, onDemandRollbackInfo);
    ArgumentCaptor<Set> captor = ArgumentCaptor.forClass(Set.class);
    verify(instanceService).delete(captor.capture());
    Set idTobeDeleted = captor.getValue();
    assertThat(idTobeDeleted).hasSize(1);
    assertThat(idTobeDeleted.contains(instance2.getInstanceId())).isTrue();

    ArgumentCaptor<Instance> captorInstance = ArgumentCaptor.forClass(Instance.class);
    verify(instanceService, times(1)).save(captorInstance.capture());

    List<Instance> capturedInstances = captorInstance.getAllValues();
    assertThat(capturedInstances).hasSize(1);
    Set<String> hostNames = new HashSet<>(asList(HOST_NAME_IP3));
    assertThat(hostNames.contains(capturedInstances.get(0).getHostInstanceKey().getHostName())).isTrue();
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
            .infraMappingType(InfrastructureMappingType.AWS_AWS_CODEDEPLOY.getName())
            .hostInstanceKey(HostInstanceKey.builder().infraMappingId(INFRA_MAPPING_ID).hostName(HOST_NAME_IP1).build())
            .instanceType(InstanceType.EC2_CLOUD_INSTANCE)
            .containerInstanceKey(ContainerInstanceKey.builder().containerId(CONTAINER_ID).build())
            .instanceInfo(Ec2InstanceInfo.builder().ec2Instance(instance1).hostPublicDns(PUBLIC_DNS_1).build())
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
            .infraMappingType(InfrastructureMappingType.AWS_AWS_CODEDEPLOY.getName())
            .hostInstanceKey(HostInstanceKey.builder().infraMappingId(INFRA_MAPPING_ID).hostName(HOST_NAME_IP2).build())
            .instanceType(InstanceType.EC2_CLOUD_INSTANCE)
            .containerInstanceKey(ContainerInstanceKey.builder().containerId(CONTAINER_ID).build())
            .instanceInfo(Ec2InstanceInfo.builder().ec2Instance(instance2).hostPublicDns(PUBLIC_DNS_2).build())
            .build());
  }

  // 3 existing instances
  // expected 1 delete 2 update
  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testSyncInstances_NewDeployment_Rollback() throws Exception {
    doReturn(Optional.of(DeploymentSummary.builder()
                             .deploymentInfo(AwsCodeDeployDeploymentInfo.builder().deploymentId(DEPLOYMENT_ID).build())
                             .accountId(ACCOUNT_ID)
                             .infraMappingId(INFRA_MAPPING_ID)
                             .workflowExecutionId("workfloeExecution_1")
                             .stateExecutionInstanceId("stateExecutionInstanceId")
                             .artifactBuildNum("1")
                             .artifactName("old")
                             .build()))
        .when(deploymentService)
        .get(any(DeploymentSummary.class));

    doReturn(getInstances()).when(instanceService).getInstancesForAppAndInframapping(any(), any());
    com.amazonaws.services.ec2.model.Instance ec2Instance2 = new com.amazonaws.services.ec2.model.Instance();
    ec2Instance2.setPrivateDnsName(PRIVATE_DNS_3);
    ec2Instance2.setInstanceId(INSTANCE_3_ID);
    ec2Instance2.setPublicDnsName(PUBLIC_DNS_3);

    doReturn(asList(ec2Instance2))
        .when(mockAwsCodeDeployHelperServiceManager)
        .listDeploymentInstances(any(), any(), any(), any(), any());
    doReturn(HOST_NAME_IP3).when(awsHelperService).getHostnameFromPrivateDnsName(PRIVATE_DNS_3);
    doReturn(HOST_NAME_IP3).when(mockAwsUtils).getHostnameFromPrivateDnsName(PRIVATE_DNS_3);

    DescribeInstancesResult result = new DescribeInstancesResult();
    Collection<Reservation> reservations = new ArrayList<>();
    reservations.add(
        new Reservation().withInstances(new com.amazonaws.services.ec2.model.Instance[] {instance1, instance3}));
    result.setReservations(reservations);
    doReturn(result).when(awsHelperService).describeEc2Instances(any(), any(), any(), any());
    doReturn(asList(instance1, instance3))
        .when(mockAwsEc2HelperServiceManager)
        .listEc2Instances(any(), any(), any(), any(), any());
    OnDemandRollbackInfo onDemandRollbackInfo = OnDemandRollbackInfo.builder().onDemandRollback(false).build();
    awsCodeDeployInstanceHandler.handleNewDeployment(
        Arrays.asList(DeploymentSummary.builder()
                          .deploymentInfo(AwsCodeDeployDeploymentInfo.builder().deploymentId(DEPLOYMENT_ID).build())
                          .infraMappingId(INFRA_MAPPING_ID)
                          .artifactBuildNum("2")
                          .artifactName("new")
                          .build()),
        true, onDemandRollbackInfo);
    ArgumentCaptor<Set> captor = ArgumentCaptor.forClass(Set.class);
    verify(instanceService).delete(captor.capture());
    Set idTobeDeleted = captor.getValue();
    assertThat(idTobeDeleted).hasSize(1);
    assertThat(idTobeDeleted.contains(instance2.getInstanceId())).isTrue();

    ArgumentCaptor<Instance> captorInstance = ArgumentCaptor.forClass(Instance.class);
    verify(instanceService, times(1)).save(captorInstance.capture());

    List<Instance> capturedInstances = captorInstance.getAllValues();
    assertThat(capturedInstances).hasSize(1);
    Set<String> hostNames = new HashSet<>(asList(HOST_NAME_IP3));
    assertThat(capturedInstances.get(0).getLastArtifactBuildNum()).isEqualTo("1");
    assertThat(capturedInstances.get(0).getLastArtifactName()).isEqualTo("old");
    assertThat(hostNames.contains(capturedInstances.get(0).getHostInstanceKey().getHostName())).isTrue();
  }

  // 2 existing instances
  // expected 1 deleted
  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testSyncInstances_ProcessPerpetualTaskResponse() {
    doReturn(true)
        .when(mockFeatureFlagService)
        .isEnabled(awsCodeDeployInstanceHandler.getFeatureFlagToEnablePerpetualTaskForInstanceSync().get(), ACCOUNT_ID);
    doReturn(getInstances()).when(instanceService).getInstancesForAppAndInframapping(anyString(), anyString());
    AwsCodeDeployListDeploymentInstancesResponse perpetualTaskResponse =
        AwsCodeDeployListDeploymentInstancesResponse.builder()
            .instances(singletonList(instance1))
            .executionStatus(ExecutionStatus.SUCCESS)
            .build();

    CodeDeployInfrastructureMapping infrastructureMapping = getInfrastructureMapping();

    awsCodeDeployInstanceHandler.processInstanceSyncResponseFromPerpetualTask(
        infrastructureMapping, perpetualTaskResponse);
    ArgumentCaptor<Set> deletedIdsCaptor = ArgumentCaptor.forClass(Set.class);
    verify(instanceService, times(1)).delete(deletedIdsCaptor.capture());
    verify(instanceService, never()).save(any(Instance.class));

    Set<String> deletedIds = deletedIdsCaptor.getValue();
    assertThat(deletedIds).containsExactlyInAnyOrder(instance2.getInstanceId());
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testGetSuccessPerpetualTaskResponseStatus() {
    CodeDeployInfrastructureMapping infrastructureMapping = getInfrastructureMapping();

    AwsCodeDeployListDeploymentInstancesResponse perpetualTaskResponse =
        AwsCodeDeployListDeploymentInstancesResponse.builder()
            .instances(singletonList(instance1))
            .executionStatus(ExecutionStatus.SUCCESS)
            .build();

    Status status = awsCodeDeployInstanceHandler.getStatus(infrastructureMapping, perpetualTaskResponse);

    assertThat(status.isSuccess()).isTrue();
    assertThat(status.isRetryable()).isTrue();
    assertThat(status.getErrorMessage()).isNull();
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testGetFailedPerpetualTaskResponseStatus() {
    CodeDeployInfrastructureMapping infrastructureMapping = getInfrastructureMapping();

    AwsCodeDeployListDeploymentInstancesResponse perpetualTaskResponse =
        AwsCodeDeployListDeploymentInstancesResponse.builder()
            .executionStatus(ExecutionStatus.FAILED)
            .errorMessage("Failed to update instances")
            .build();

    Status status = awsCodeDeployInstanceHandler.getStatus(infrastructureMapping, perpetualTaskResponse);

    assertThat(status.isSuccess()).isFalse();
    assertThat(status.isRetryable()).isTrue();
    assertThat(status.getErrorMessage()).isEqualTo("Failed to update instances");
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testGetNonRetryablePerpetualTaskResponseStatus() {
    CodeDeployInfrastructureMapping infrastructureMapping = getInfrastructureMapping();

    AwsCodeDeployListDeploymentInstancesResponse perpetualTaskResponse =
        AwsCodeDeployListDeploymentInstancesResponse.builder()
            .executionStatus(ExecutionStatus.SUCCESS)
            .instances(emptyList())
            .build();

    Status status = awsCodeDeployInstanceHandler.getStatus(infrastructureMapping, perpetualTaskResponse);

    assertThat(status.isSuccess()).isTrue();
    assertThat(status.isRetryable()).isFalse();
    assertThat(status.getErrorMessage()).isNull();
  }

  private CodeDeployInfrastructureMapping getInfrastructureMapping() {
    return CodeDeployInfrastructureMappingBuilder.aCodeDeployInfrastructureMapping()
        .withUuid(INFRA_MAPPING_ID)
        .withAccountId(ACCOUNT_ID)
        .withAppId(APP_ID)
        .withInfraMappingType(InfrastructureMappingType.AWS_AWS_CODEDEPLOY.getName())
        .withComputeProviderSettingId(COMPUTE_PROVIDER_SETTING_ID)
        .withRegion(US_EAST)
        .withServiceId(SERVICE_ID)
        .build();
  }
}
