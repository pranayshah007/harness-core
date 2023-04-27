/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.instance;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.ADWAIT;

import static software.wings.beans.AwsInfrastructureMapping.Builder.anAwsInfrastructureMapping;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.ACCOUNT_ID;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.APP_NAME;
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
import static software.wings.service.impl.instance.InstanceSyncTestConstants.INSTANCE_1_ID;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.INSTANCE_2_ID;
import static software.wings.service.impl.instance.InstanceSyncTestConstants.INSTANCE_3_ID;
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
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.EnvironmentType;
import io.harness.beans.FeatureName;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;
import io.harness.security.encryption.EncryptedDataDetail;

import software.wings.WingsBaseTest;
import software.wings.beans.Application;
import software.wings.beans.AwsConfig;
import software.wings.beans.AwsInfrastructureMapping;
import software.wings.beans.AwsInstanceFilter;
import software.wings.beans.Environment;
import software.wings.beans.InfrastructureMappingType;
import software.wings.beans.Service;
import software.wings.beans.SettingAttribute;
import software.wings.beans.infrastructure.instance.Instance;
import software.wings.beans.infrastructure.instance.InstanceType;
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
import software.wings.service.intfc.aws.manager.AwsEc2HelperServiceManager;
import software.wings.service.intfc.instance.InstanceService;
import software.wings.service.intfc.security.SecretManager;

import com.amazonaws.services.ec2.model.DescribeInstancesResult;
import com.amazonaws.services.ec2.model.Reservation;
import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

@OwnedBy(CDP)
public class AwsInstanceHandlerTest extends WingsBaseTest {
  @Mock private InfrastructureMappingService infraMappingService;
  @Mock private SecretManager secretManager;
  @Mock private EncryptedDataDetail encryptedDataDetail;
  @Mock private InstanceService instanceService;
  @Mock private SettingsService settingsService;
  @Mock private AwsHelperService awsHelperService;
  @Mock private AwsCodeDeployService awsCodeDeployService;
  @Mock private AppService appService;
  @Mock EnvironmentService environmentService;
  @Mock ServiceResourceService serviceResourceService;
  @Mock private AwsUtils mockAwsUtils;
  @Mock private AwsEc2HelperServiceManager mockAwsEc2HelperServiceManager;
  @InjectMocks @Inject AwsInstanceHandler awsInstanceHandler;
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

    doReturn(anAwsInfrastructureMapping()
                 .withUuid(InstanceSyncTestConstants.INFRA_MAPPING_ID)
                 .withAccountId(ACCOUNT_ID)
                 .withAppId(InstanceSyncTestConstants.APP_ID)
                 .withInfraMappingType(InfrastructureMappingType.AWS_AMI.getName())
                 .withComputeProviderSettingId(COMPUTE_PROVIDER_SETTING_ID)
                 .withRegion(US_EAST)
                 .withServiceId(SERVICE_ID)
                 .withAwsInstanceFilter(AwsInstanceFilter.builder().build())
                 .build())
        .when(infraMappingService)
        .get(anyString(), anyString());

    doReturn(asList(encryptedDataDetail)).when(secretManager).getEncryptionDetails(any(), any(), any());

    doReturn(SettingAttribute.Builder.aSettingAttribute()
                 .withValue(AwsConfig.builder()
                                .accessKey(AWS_ACCESS_KEY)
                                .accountId(ACCOUNT_ID)
                                .encryptedSecretKey(AWS_ENCRYPTED_SECRET_KEY)
                                .build())
                 .build())
        .when(settingsService)
        .get(anyString());

    // catpure arg
    doReturn(true).when(instanceService).delete(anySet());
    // capture arg
    doReturn(Instance.builder().build()).when(instanceService).save(any(Instance.class));

    doReturn(Application.Builder.anApplication().name("app").uuid("app_1").accountId(ACCOUNT_ID).build())
        .when(appService)
        .get(anyString());

    doReturn(Environment.Builder.anEnvironment().environmentType(EnvironmentType.PROD).name(ENV_NAME).build())
        .when(environmentService)
        .get(anyString(), anyString(), anyBoolean());

    doReturn(Service.builder().name(SERVICE_NAME).build())
        .when(serviceResourceService)
        .getWithDetails(anyString(), anyString());
  }

  // 3 existing instances, 1 EC2, 2 AMI,
  // expected EC2 Delete, 2 AMI Update
  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testSyncInstances_instanceSync() throws Exception {
    final List<Instance> instances =
        asList(Instance.builder()
                   .uuid(INSTANCE_1_ID)
                   .accountId(ACCOUNT_ID)
                   .appId(InstanceSyncTestConstants.APP_ID)
                   .computeProviderId(COMPUTE_PROVIDER_NAME)
                   .appName(APP_NAME)
                   .envId(ENV_ID)
                   .envName(ENV_NAME)
                   .envType(EnvironmentType.PROD)
                   .infraMappingId(InstanceSyncTestConstants.INFRA_MAPPING_ID)
                   .infraMappingType(InfrastructureMappingType.AWS_AMI.getName())
                   .hostInstanceKey(HostInstanceKey.builder()
                                        .infraMappingId(InstanceSyncTestConstants.INFRA_MAPPING_ID)
                                        .hostName(HOST_NAME_IP1)
                                        .build())
                   .instanceType(InstanceType.EC2_CLOUD_INSTANCE)
                   .containerInstanceKey(ContainerInstanceKey.builder().containerId(CONTAINER_ID).build())
                   .instanceInfo(Ec2InstanceInfo.builder().ec2Instance(instance1).hostPublicDns(PUBLIC_DNS_1).build())
                   .build(),
            Instance.builder()
                .uuid(INSTANCE_2_ID)
                .accountId(ACCOUNT_ID)
                .appId(InstanceSyncTestConstants.APP_ID)
                .computeProviderId(COMPUTE_PROVIDER_NAME)
                .appName(APP_NAME)
                .envId(ENV_ID)
                .envName(ENV_NAME)
                .envType(EnvironmentType.PROD)
                .infraMappingId(InstanceSyncTestConstants.INFRA_MAPPING_ID)
                .infraMappingType(InfrastructureMappingType.AWS_AMI.getName())
                .hostInstanceKey(HostInstanceKey.builder()
                                     .infraMappingId(InstanceSyncTestConstants.INFRA_MAPPING_ID)
                                     .hostName(HOST_NAME_IP2)
                                     .build())
                .instanceType(InstanceType.EC2_CLOUD_INSTANCE)
                .containerInstanceKey(ContainerInstanceKey.builder().containerId(CONTAINER_ID).build())
                .instanceInfo(Ec2InstanceInfo.builder().ec2Instance(instance2).hostPublicDns(PUBLIC_DNS_2).build())
                .build(),
            Instance.builder()
                .uuid(INSTANCE_3_ID)
                .accountId(ACCOUNT_ID)
                .appId(InstanceSyncTestConstants.APP_ID)
                .computeProviderId(COMPUTE_PROVIDER_NAME)
                .appName(APP_NAME)
                .envId(ENV_ID)
                .envName(ENV_NAME)
                .envType(EnvironmentType.PROD)
                .infraMappingId(InstanceSyncTestConstants.INFRA_MAPPING_ID)
                .infraMappingType(InfrastructureMappingType.AWS_AMI.getName())
                .hostInstanceKey(HostInstanceKey.builder()
                                     .infraMappingId(InstanceSyncTestConstants.INFRA_MAPPING_ID)
                                     .hostName(HOST_NAME_IP3)
                                     .build())
                .instanceType(InstanceType.EC2_CLOUD_INSTANCE)
                .containerInstanceKey(ContainerInstanceKey.builder().containerId(CONTAINER_ID).build())
                .instanceInfo(Ec2InstanceInfo.builder().ec2Instance(instance3).hostPublicDns(PUBLIC_DNS_3).build())
                .build());

    doReturn(instances).when(instanceService).getInstancesForAppAndInframapping(anyString(), anyString());
    DescribeInstancesResult result = new DescribeInstancesResult();
    Collection<Reservation> reservations = new ArrayList<>();
    reservations.add(new Reservation().withInstances(new com.amazonaws.services.ec2.model.Instance[] {instance1}));
    result.setReservations(reservations);
    doReturn(result).when(awsHelperService).describeEc2Instances(any(), any(), any(), any());
    doReturn(singletonList(instance1))
        .when(mockAwsEc2HelperServiceManager)
        .listEc2Instances(any(), any(), any(), any(), anyString());

    awsInstanceHandler.syncInstances(
        InstanceSyncTestConstants.APP_ID, InstanceSyncTestConstants.INFRA_MAPPING_ID, InstanceSyncFlow.MANUAL);

    ArgumentCaptor<Set> captor = ArgumentCaptor.forClass(Set.class);
    verify(instanceService).delete(captor.capture());
    Set idTobeDeleted = captor.getValue();
    assertThat(idTobeDeleted).hasSize(2);
    assertThat(idTobeDeleted.contains(instance3.getInstanceId())).isTrue();
    assertThat(idTobeDeleted.contains(instance2.getInstanceId())).isTrue();

    verify(instanceService, never()).saveOrUpdate(any(Instance.class));
  }

  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  public void handleEc2InstanceSyncWithAwsInfraMappingIterator() {
    Map<String, Instance> ec2InstanceIdInstanceMap =
        Stream.of(Instance.builder().uuid("id-1").build(), Instance.builder().uuid("id-2").build())
            .collect(Collectors.toMap(Instance::getUuid, Function.identity()));

    com.amazonaws.services.ec2.model.Instance ec2Instance = new com.amazonaws.services.ec2.model.Instance();
    ec2Instance.setInstanceId("id-2");
    doReturn(asList(ec2Instance))
        .when(awsInfrastructureProvider)
        .listFilteredInstances(any(AwsInfrastructureMapping.class), any(AwsConfig.class), anyList());

    awsInstanceHandler.handleEc2InstanceSyncWithAwsInfraMapping(ec2InstanceIdInstanceMap, mock(AwsConfig.class),
        emptyList(), "us-east-1", mock(AwsInfrastructureMapping.class), Optional.empty(), true);

    ArgumentCaptor<Set> captor = ArgumentCaptor.forClass(Set.class);
    verify(instanceService, times(1)).delete(captor.capture());
    @SuppressWarnings("unchecked") Set<String> instanceDeleted = captor.getValue();
    assertThat(instanceDeleted).containsOnly("id-1");
  }

  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  public void handleEc2InstanceSyncWithAwsInfraMappingForPerpTask() {
    Map<String, Instance> ec2InstanceIdInstanceMap =
        Stream.of(Instance.builder().uuid("id-1").build(), Instance.builder().uuid("id-2").build())
            .collect(Collectors.toMap(Instance::getUuid, Function.identity()));

    com.amazonaws.services.ec2.model.Instance ec2Instance = new com.amazonaws.services.ec2.model.Instance();
    ec2Instance.setInstanceId("id-2");
    Optional<List<com.amazonaws.services.ec2.model.Instance>> ec2Instances = Optional.of(Arrays.asList(ec2Instance));

    awsInstanceHandler.handleEc2InstanceSyncWithAwsInfraMapping(ec2InstanceIdInstanceMap, mock(AwsConfig.class),
        emptyList(), "us-east-1", mock(AwsInfrastructureMapping.class), ec2Instances, true);

    ArgumentCaptor<Set> captor = ArgumentCaptor.forClass(Set.class);
    verify(instanceService, times(1)).delete(captor.capture());
    @SuppressWarnings("unchecked") Set<String> instanceDeleted = captor.getValue();
    assertThat(instanceDeleted).containsOnly("id-1");
  }

  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  public void handleEc2InstanceSyncWithAwsInfraMappingForPerpTaskNoInstance() {
    Map<String, Instance> ec2InstanceIdInstanceMap =
        Stream.of(Instance.builder().uuid("id-1").build(), Instance.builder().uuid("id-2").build())
            .collect(Collectors.toMap(Instance::getUuid, Function.identity()));

    Optional<List<com.amazonaws.services.ec2.model.Instance>> ec2Instances = Optional.of(emptyList());

    awsInstanceHandler.handleEc2InstanceSyncWithAwsInfraMapping(ec2InstanceIdInstanceMap, mock(AwsConfig.class),
        emptyList(), "us-east-1", mock(AwsInfrastructureMapping.class), ec2Instances, true);

    ArgumentCaptor<Set> captor = ArgumentCaptor.forClass(Set.class);
    verify(instanceService, times(1)).delete(captor.capture());
    @SuppressWarnings("unchecked") Set<String> instanceDeleted = captor.getValue();
    assertThat(instanceDeleted).containsExactlyInAnyOrder("id-1", "id-2");
  }

  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  public void handleEc2InstanceSyncIterator() {
    Map<String, Instance> ec2InstanceIdInstanceMap =
        Stream.of(Instance.builder().uuid("id-1").build(), Instance.builder().uuid("id-2").build())
            .collect(Collectors.toMap(Instance::getUuid, Function.identity()));

    com.amazonaws.services.ec2.model.Instance ec2Instance = new com.amazonaws.services.ec2.model.Instance();
    ec2Instance.setInstanceId("id-2");
    doReturn(asList(ec2Instance))
        .when(awsInfrastructureProvider)
        .listFilteredInstances(any(AwsInfrastructureMapping.class), any(AwsConfig.class), anyList());

    awsInstanceHandler.handleEc2InstanceSync(
        ec2InstanceIdInstanceMap, mock(AwsConfig.class), emptyList(), "us-east-1", Optional.empty(), true);

    ArgumentCaptor<Set> captor = ArgumentCaptor.forClass(Set.class);
    verify(instanceService, times(1)).delete(captor.capture());
    @SuppressWarnings("unchecked") Set<String> instanceDeleted = captor.getValue();
    assertThat(instanceDeleted).containsOnly("id-1");
  }

  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  public void handleEc2InstanceSyncForPerpTask() {
    Map<String, Instance> ec2InstanceIdInstanceMap =
        Stream.of(Instance.builder().uuid("id-1").build(), Instance.builder().uuid("id-2").build())
            .collect(Collectors.toMap(Instance::getUuid, Function.identity()));

    com.amazonaws.services.ec2.model.Instance ec2Instance = new com.amazonaws.services.ec2.model.Instance();
    ec2Instance.setInstanceId("id-2");
    Optional<List<com.amazonaws.services.ec2.model.Instance>> ec2Instances = Optional.of(Arrays.asList(ec2Instance));

    doThrow(new RuntimeException())
        .when(awsInfrastructureProvider)
        .listFilteredInstances(any(AwsInfrastructureMapping.class), any(AwsConfig.class), anyList());
    awsInstanceHandler.handleEc2InstanceSync(
        ec2InstanceIdInstanceMap, mock(AwsConfig.class), emptyList(), "us-east-1", ec2Instances, true);

    ArgumentCaptor<Set> captor = ArgumentCaptor.forClass(Set.class);
    verify(instanceService, times(1)).delete(captor.capture());
    @SuppressWarnings("unchecked") Set<String> instanceDeleted = captor.getValue();
    assertThat(instanceDeleted).containsOnly("id-1");
  }

  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  public void handleEc2InstanceSyncForPerpTaskNoInstance() {
    Map<String, Instance> ec2InstanceIdInstanceMap =
        Stream.of(Instance.builder().uuid("id-1").build(), Instance.builder().uuid("id-2").build())
            .collect(Collectors.toMap(Instance::getUuid, Function.identity()));

    Optional<List<com.amazonaws.services.ec2.model.Instance>> ec2Instances = Optional.of(emptyList());
    doThrow(new RuntimeException())
        .when(awsInfrastructureProvider)
        .listFilteredInstances(any(AwsInfrastructureMapping.class), any(AwsConfig.class), anyList());

    awsInstanceHandler.handleEc2InstanceSync(
        ec2InstanceIdInstanceMap, mock(AwsConfig.class), emptyList(), "us-east-1", ec2Instances, true);

    ArgumentCaptor<Set> captor = ArgumentCaptor.forClass(Set.class);
    verify(instanceService, times(1)).delete(captor.capture());
    @SuppressWarnings("unchecked") Set<String> instanceDeleted = captor.getValue();
    assertThat(instanceDeleted).containsExactlyInAnyOrder("id-1", "id-2");
  }

  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  public void getFeatureFlagToStopIteratorBasedInstanceSync() {
    assertThat(awsInstanceHandler.getFeatureFlagToStopIteratorBasedInstanceSync())
        .isEqualTo(Optional.of(FeatureName.STOP_INSTANCE_SYNC_VIA_ITERATOR_FOR_AWS_SSH_DEPLOYMENTS));
  }

  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  public void getFeatureFlagToEnablePerpetualTaskForInstanceSync() {
    assertThat(awsInstanceHandler.getFeatureFlagToEnablePerpetualTaskForInstanceSync())
        .isEqualTo(Optional.of(FeatureName.MOVE_AWS_SSH_INSTANCE_SYNC_TO_PERPETUAL_TASK));
  }
}
