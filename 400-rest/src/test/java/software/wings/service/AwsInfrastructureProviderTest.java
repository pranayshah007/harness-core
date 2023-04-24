/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.ANSHUL;
import static io.harness.rule.OwnerRule.ANUBHAW;
import static io.harness.rule.OwnerRule.SATYAM;

import static software.wings.beans.AwsInfrastructureMapping.Builder.anAwsInfrastructureMapping;
import static software.wings.beans.SettingAttribute.Builder.aSettingAttribute;
import static software.wings.beans.infrastructure.Host.Builder.aHost;
import static software.wings.utils.WingsTestConstants.ACCESS_KEY;
import static software.wings.utils.WingsTestConstants.APP_ID;
import static software.wings.utils.WingsTestConstants.ENV_ID;
import static software.wings.utils.WingsTestConstants.HOST_CONN_ATTR_ID;
import static software.wings.utils.WingsTestConstants.HOST_ID;
import static software.wings.utils.WingsTestConstants.HOST_NAME;
import static software.wings.utils.WingsTestConstants.INFRA_MAPPING_ID;
import static software.wings.utils.WingsTestConstants.SECRET_KEY;
import static software.wings.utils.WingsTestConstants.SERVICE_ID;
import static software.wings.utils.WingsTestConstants.SERVICE_TEMPLATE_ID;
import static software.wings.utils.WingsTestConstants.SETTING_ID;
import static software.wings.utils.WingsTestConstants.WORKFLOW_EXECUTION_ID;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.PageRequest;
import io.harness.beans.PageResponse;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.api.DeploymentType;
import software.wings.beans.AwsConfig;
import software.wings.beans.AwsInfrastructureMapping;
import software.wings.beans.HostConnectionType;
import software.wings.beans.ServiceTemplate;
import software.wings.beans.SettingAttribute;
import software.wings.beans.infrastructure.Host;
import software.wings.service.impl.AwsInfrastructureProvider;
import software.wings.service.impl.AwsUtils;
import software.wings.service.impl.aws.manager.AwsHelperServiceManager;
import software.wings.service.intfc.HostService;
import software.wings.service.intfc.ServiceResourceService;
import software.wings.service.intfc.ServiceTemplateService;
import software.wings.service.intfc.aws.manager.AwsAsgHelperServiceManager;
import software.wings.service.intfc.aws.manager.AwsEc2HelperServiceManager;
import software.wings.service.intfc.security.SecretManager;
import software.wings.sm.states.ManagerExecutionLogCallback;

import com.amazonaws.regions.Regions;
import com.amazonaws.services.ec2.model.Filter;
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ec2.model.InstanceState;
import com.google.inject.Inject;
import java.util.Collections;
import java.util.List;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

/**
 * Created by anubhaw on 1/24/17.
 */
@OwnedBy(CDP)
public class AwsInfrastructureProviderTest extends WingsBaseTest {
  @Mock private AwsUtils mockAwsUtils;
  @Mock private HostService hostService;
  @Mock private SecretManager secretManager;
  @Mock private AwsEc2HelperServiceManager mockAwsEc2HelperServiceManager;
  @Mock private AwsAsgHelperServiceManager mockAwsAsgHelperServiceManager;
  @Mock private ServiceResourceService serviceResourceService;
  @Mock private ServiceTemplateService serviceTemplateService;
  @Spy private AwsHelperServiceManager awsHelperServiceManager;

  @Inject @InjectMocks private AwsInfrastructureProvider infrastructureProvider = new AwsInfrastructureProvider();

  private SettingAttribute awsSetting =
      aSettingAttribute()
          .withUuid(SETTING_ID)
          .withValue(AwsConfig.builder().secretKey(SECRET_KEY).accessKey(ACCESS_KEY.toCharArray()).build())
          .build();
  private AwsConfig awsConfig = (AwsConfig) awsSetting.getValue();

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
    when(secretManager.getEncryptionDetails(any(), anyString(), anyString())).thenReturn(Collections.emptyList());
    when(serviceResourceService.getDeploymentType(any(), any(), any())).thenReturn(null);
    FieldUtils.writeField(infrastructureProvider, "secretManager", secretManager, true);
    FieldUtils.writeField(infrastructureProvider, "serviceResourceService", serviceResourceService, true);
  }

  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void shouldListHostsPublicDns() {
    Filter filter = new Filter("instance-state-name", asList("running"));
    List<Instance> resultList =
        asList(new Instance().withPublicDnsName("HOST_NAME_1"), new Instance().withPublicDnsName("HOST_NAME_2"));

    doReturn(resultList)
        .when(mockAwsEc2HelperServiceManager)
        .listEc2Instances((AwsConfig) awsSetting.getValue(), Collections.emptyList(), Regions.US_EAST_1.getName(),
            singletonList(filter), APP_ID);
    AwsInfrastructureMapping awsInfrastructureMapping =
        anAwsInfrastructureMapping()
            .withRegion(Regions.US_EAST_1.getName())
            .withUsePublicDns(true)
            .withHostConnectionType(HostConnectionType.PUBLIC_DNS.name())
            .withAppId(APP_ID)
            .build();
    doReturn(singletonList(new Filter("instance-state-name", asList("running"))))
        .when(mockAwsUtils)
        .getFilters(null, awsInfrastructureMapping.getAwsInstanceFilter());
    PageResponse<Host> hosts = infrastructureProvider.listHosts(
        awsInfrastructureMapping, awsSetting, Collections.emptyList(), new PageRequest<>());
    assertThat(hosts)
        .hasSize(2)
        .hasOnlyElementsOfType(Host.class)
        .extracting(Host::getPublicDns)
        .isEqualTo(asList("HOST_NAME_1", "HOST_NAME_2"));
    verify(mockAwsEc2HelperServiceManager)
        .listEc2Instances((AwsConfig) awsSetting.getValue(), Collections.emptyList(), Regions.US_EAST_1.getName(),
            singletonList(filter), APP_ID);
  }

  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void shouldListHostsPrivateDns() {
    Filter filter = new Filter("instance-state-name", asList("running"));
    List<Instance> resultList =
        asList(new Instance().withPrivateDnsName("HOST_NAME_1"), new Instance().withPrivateDnsName("HOST_NAME_2"));
    doReturn(resultList)
        .when(mockAwsEc2HelperServiceManager)
        .listEc2Instances((AwsConfig) awsSetting.getValue(), Collections.emptyList(), Regions.US_EAST_1.getName(),
            singletonList(filter), APP_ID);
    AwsInfrastructureMapping awsInfrastructureMapping =
        anAwsInfrastructureMapping()
            .withRegion(Regions.US_EAST_1.getName())
            .withUsePublicDns(false)
            .withHostConnectionType(HostConnectionType.PRIVATE_DNS.name())
            .withAppId(APP_ID)
            .build();
    doReturn(singletonList(new Filter("instance-state-name", asList("running"))))
        .when(mockAwsUtils)
        .getFilters(null, awsInfrastructureMapping.getAwsInstanceFilter());
    PageResponse<Host> hosts = infrastructureProvider.listHosts(
        awsInfrastructureMapping, awsSetting, Collections.emptyList(), new PageRequest<>());
    assertThat(hosts)
        .hasSize(2)
        .hasOnlyElementsOfType(Host.class)
        .extracting(Host::getPublicDns)
        .isEqualTo(asList("HOST_NAME_1", "HOST_NAME_2"));
    verify(mockAwsEc2HelperServiceManager)
        .listEc2Instances((AwsConfig) awsSetting.getValue(), Collections.emptyList(), Regions.US_EAST_1.getName(),
            singletonList(filter), APP_ID);
  }

  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void shouldListHostsEmpty() {
    Filter filter = new Filter("instance-state-name", asList("running"));
    doReturn(emptyList())
        .when(mockAwsEc2HelperServiceManager)
        .listEc2Instances((AwsConfig) awsSetting.getValue(), Collections.emptyList(), Regions.US_EAST_1.getName(),
            singletonList(filter), APP_ID);
    AwsInfrastructureMapping awsInfrastructureMapping =
        anAwsInfrastructureMapping()
            .withRegion(Regions.US_EAST_1.getName())
            .withUsePublicDns(true)
            .withHostConnectionType(HostConnectionType.PUBLIC_DNS.name())
            .withAppId(APP_ID)
            .build();
    doReturn(singletonList(new Filter("instance-state-name", asList("running"))))
        .when(mockAwsUtils)
        .getFilters(null, awsInfrastructureMapping.getAwsInstanceFilter());
    PageResponse<Host> hosts = infrastructureProvider.listHosts(
        awsInfrastructureMapping, awsSetting, Collections.emptyList(), new PageRequest<>());
    assertThat(hosts).hasSize(0);
    verify(mockAwsEc2HelperServiceManager)
        .listEc2Instances(
            awsConfig, Collections.emptyList(), Regions.US_EAST_1.getName(), singletonList(filter), APP_ID);
  }

  @Test
  @Owner(developers = ANUBHAW)
  @Category(UnitTests.class)
  public void shouldSaveHost() {
    Host reqHost = aHost().withHostName(HOST_NAME).build();
    Host savedHost = aHost().withUuid(HOST_ID).withHostName(HOST_NAME).build();

    when(hostService.saveHost(reqHost)).thenReturn(savedHost);

    Host host = infrastructureProvider.saveHost(reqHost);
    assertThat(host).isNotNull().isEqualTo(savedHost);
    verify(hostService).saveHost(reqHost);
  }

  @Test
  @Owner(developers = ANUBHAW)
  @Category(UnitTests.class)
  public void shouldDeleteHost() {
    infrastructureProvider.deleteHost(APP_ID, INFRA_MAPPING_ID, HOST_NAME);
    verify(hostService).deleteByDnsName(APP_ID, INFRA_MAPPING_ID, HOST_NAME);
  }

  @Test
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void shouldUpdateHostConnAttrs() {
    AwsInfrastructureMapping awsInfrastructureMapping = anAwsInfrastructureMapping()
                                                            .withAppId(APP_ID)
                                                            .withUuid(INFRA_MAPPING_ID)
                                                            .withDeploymentType(DeploymentType.SSH.toString())
                                                            .build();

    infrastructureProvider.updateHostConnAttrs(awsInfrastructureMapping, HOST_CONN_ATTR_ID);
    verify(hostService).updateHostConnectionAttrByInfraMapping(awsInfrastructureMapping, HOST_CONN_ATTR_ID);
  }

  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void shouldProvisionHosts() {
    String region = Regions.US_EAST_1.getName();
    AwsInfrastructureMapping infrastructureMapping = anAwsInfrastructureMapping()
                                                         .withRegion(region)
                                                         .withProvisionInstances(true)
                                                         .withAutoScalingGroupName("AUTOSCALING_GROUP")
                                                         .withAppId(APP_ID)
                                                         .withSetDesiredCapacity(true)
                                                         .withDesiredCapacity(1)
                                                         .withEnvId(ENV_ID)
                                                         .withServiceId(SERVICE_ID)
                                                         .build();
    doReturn(asList(new Instance()
                        .withPrivateDnsName(HOST_NAME)
                        .withPublicDnsName(HOST_NAME)
                        .withInstanceId("INSTANCE_ID")
                        .withState(new InstanceState().withName("running"))))
        .when(mockAwsAsgHelperServiceManager)
        .listAutoScalingGroupInstances(awsConfig, Collections.emptyList(), infrastructureMapping.getRegion(),
            infrastructureMapping.getAutoScalingGroupName(), APP_ID);
    when(serviceTemplateService.getOrCreate(
             infrastructureMapping.getAppId(), infrastructureMapping.getServiceId(), infrastructureMapping.getEnvId()))
        .thenReturn(ServiceTemplate.Builder.aServiceTemplate().withUuid(SERVICE_TEMPLATE_ID).build());
    doReturn(HOST_NAME).when(mockAwsUtils).getHostnameFromPrivateDnsName(HOST_NAME);
    doNothing()
        .when(awsHelperServiceManager)
        .setAutoScalingGroupCapacityAndWaitForInstancesReadyState(awsConfig, Collections.emptyList(),
            infrastructureMapping.getRegion(), infrastructureMapping.getAutoScalingGroupName(),
            infrastructureMapping.getDesiredCapacity(), new ManagerExecutionLogCallback());
    List<Host> hosts =
        infrastructureProvider.maybeSetAutoScaleCapacityAndGetHosts(null, null, infrastructureMapping, awsSetting);
    assertThat(hosts).isNotNull();
    assertThat(hosts.size()).isEqualTo(1);
    Host host = hosts.get(0);
    assertThat(host).isNotNull();
    assertThat(host.getHostName()).isEqualTo(HOST_NAME);
    Instance instance = host.getEc2Instance();
    assertThat(instance).isNotNull();
    assertThat(instance.getInstanceId()).isEqualTo("INSTANCE_ID");
    verify(awsHelperServiceManager)
        .setAutoScalingGroupCapacityAndWaitForInstancesReadyState(awsConfig, Collections.emptyList(),
            infrastructureMapping.getRegion(), infrastructureMapping.getAutoScalingGroupName(),
            infrastructureMapping.getDesiredCapacity(), new ManagerExecutionLogCallback());
    verify(mockAwsAsgHelperServiceManager)
        .listAutoScalingGroupInstances(awsConfig, Collections.emptyList(), infrastructureMapping.getRegion(),
            infrastructureMapping.getAutoScalingGroupName(), APP_ID);
  }

  @Test
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testMaybeSetAutoScaleCapacityAndGetHosts() {
    String region = Regions.US_EAST_1.getName();
    AwsInfrastructureMapping infrastructureMapping = anAwsInfrastructureMapping()
                                                         .withRegion(region)
                                                         .withProvisionInstances(true)
                                                         .withAppId(APP_ID)
                                                         .withAutoScalingGroupName("AUTOSCALING_GROUP")
                                                         .withSetDesiredCapacity(false)
                                                         .withDesiredCapacity(1)
                                                         .withHostConnectionAttrs("hostConnectionAttr")
                                                         .withServiceId(SERVICE_ID)
                                                         .withEnvId(ENV_ID)
                                                         .build();
    when(mockAwsAsgHelperServiceManager.listAutoScalingGroupInstances(awsConfig, Collections.emptyList(),
             infrastructureMapping.getRegion(), infrastructureMapping.getAutoScalingGroupName(), APP_ID))
        .thenReturn(asList(new Instance()
                               .withPrivateDnsName(HOST_NAME)
                               .withPublicDnsName(HOST_NAME)
                               .withInstanceId("INSTANCE_ID")
                               .withState(new InstanceState().withName("running"))));
    when(serviceTemplateService.getOrCreate(
             infrastructureMapping.getAppId(), infrastructureMapping.getServiceId(), infrastructureMapping.getEnvId()))
        .thenReturn(ServiceTemplate.Builder.aServiceTemplate().withUuid(SERVICE_TEMPLATE_ID).build());

    SettingAttribute computeProvider = aSettingAttribute().withValue(awsConfig).build();
    List<Host> hosts = infrastructureProvider.maybeSetAutoScaleCapacityAndGetHosts(
        APP_ID, WORKFLOW_EXECUTION_ID, infrastructureMapping, computeProvider);
    assertThat(hosts.get(0).getWinrmConnAttr()).isNull();

    infrastructureMapping.setDeploymentType(DeploymentType.WINRM.name());
    when(serviceResourceService.getDeploymentType(infrastructureMapping, null, SERVICE_ID))
        .thenReturn(DeploymentType.WINRM);
    hosts = infrastructureProvider.maybeSetAutoScaleCapacityAndGetHosts(
        APP_ID, WORKFLOW_EXECUTION_ID, infrastructureMapping, computeProvider);
    assertThat(hosts.get(0).getWinrmConnAttr()).isEqualTo("hostConnectionAttr");
  }
}
