/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.integration;

import static io.harness.beans.PageRequest.PageRequestBuilder.aPageRequest;
import static io.harness.rule.OwnerRule.ANUBHAW;

import static software.wings.beans.Application.Builder.anApplication;
import static software.wings.beans.AwsInfrastructureMapping.Builder.anAwsInfrastructureMapping;
import static software.wings.beans.PhysicalInfrastructureMapping.Builder.aPhysicalInfrastructureMapping;
import static software.wings.beans.ServiceInstanceSelectionParams.Builder.aServiceInstanceSelectionParams;
import static software.wings.beans.SettingAttribute.Builder.aSettingAttribute;
import static software.wings.settings.SettingVariableTypes.PHYSICAL_DATA_CENTER;
import static software.wings.utils.WingsTestConstants.mockChecker;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.harness.beans.SearchFilter.Operator;
import io.harness.category.element.DeprecatedIntegrationTests;
import io.harness.limits.LimitCheckerFactory;
import io.harness.persistence.HPersistence;
import io.harness.rule.Owner;
import io.harness.scm.ScmSecret;
import io.harness.scm.SecretName;
import io.harness.shell.AccessType;

import software.wings.api.DeploymentType;
import software.wings.beans.Application;
import software.wings.beans.AwsConfig;
import software.wings.beans.AwsInfrastructureMapping;
import software.wings.beans.Environment;
import software.wings.beans.HostConnectionAttributes;
import software.wings.beans.HostConnectionAttributes.ConnectionType;
import software.wings.beans.PhysicalInfrastructureMapping;
import software.wings.beans.Service;
import software.wings.beans.ServiceInstance;
import software.wings.beans.ServiceInstance.ServiceInstanceKeys;
import software.wings.beans.ServiceTemplate;
import software.wings.beans.SettingAttribute;
import software.wings.beans.infrastructure.Host;
import software.wings.scheduler.BackgroundJobScheduler;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.EnvironmentService;
import software.wings.service.intfc.InfrastructureMappingService;
import software.wings.service.intfc.ServiceInstanceService;
import software.wings.service.intfc.ServiceResourceService;
import software.wings.service.intfc.ServiceTemplateService;

import com.google.inject.Inject;
import dev.morphia.Key;
import java.util.List;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;

/**
 * Created by anubhaw on 1/16/17.
 */
public class InfrastructureMappingIntegrationTest extends IntegrationTestBase {
  @Mock private BackgroundJobScheduler jobScheduler;
  @Mock private LimitCheckerFactory limitCheckerFactory;
  @Inject @InjectMocks private AppService appService;

  @Inject private HPersistence persistence;
  @Inject @InjectMocks private ServiceResourceService serviceResourceService;
  @Inject private ServiceTemplateService serviceTemplateService;
  @Inject private ServiceInstanceService serviceInstanceService;
  @Inject private EnvironmentService environmentService;
  @Inject private InfrastructureMappingService infrastructureMappingService;
  @Inject private ScmSecret scmSecret;

  private Application app;
  private Service service;
  private Environment environment;

  @Override
  @Before
  public void setUp() throws Exception {
    when(limitCheckerFactory.getInstance(Mockito.any())).thenReturn(mockChecker());

    super.setUp();
    app = appService.save(anApplication().name("AppA" + System.currentTimeMillis()).accountId(accountId).build());
    service = serviceResourceService.save(Service.builder().appId(app.getUuid()).name("Catalog").build());

    environment =
        environmentService.save(Environment.Builder.anEnvironment().appId(app.getUuid()).name("Developmenet").build());
  }

  @Test
  @Owner(developers = ANUBHAW)
  @Category(DeprecatedIntegrationTests.class)
  @Ignore("skipping the integration test")
  public void shouldSelectServiceInstances() {
    SettingAttribute hostConnectionAttr = aSettingAttribute().withAccountId(app.getAccountId()).withName("hca").build();
    persistence.save(hostConnectionAttr);
    SettingAttribute computeProviderSetting =
        aSettingAttribute().withAccountId(app.getAccountId()).withName("DC").build();
    persistence.save(computeProviderSetting);

    String serviceTemplateId = (String) serviceTemplateService
                                   .getTemplateRefKeysByService(app.getUuid(), service.getUuid(), environment.getUuid())
                                   .get(0)
                                   .getId();

    PhysicalInfrastructureMapping physicalInfrastructureMapping =
        aPhysicalInfrastructureMapping()
            .withName("Name1")
            .withAppId(app.getUuid())
            .withAccountId(accountId)
            .withEnvId(environment.getUuid())
            .withServiceTemplateId(serviceTemplateId)
            .withDeploymentType(DeploymentType.SSH.name())
            .withComputeProviderType(PHYSICAL_DATA_CENTER.name())
            .withHostConnectionAttrs(hostConnectionAttr.getUuid())
            .withComputeProviderSettingId(computeProviderSetting.getUuid())
            .withHostNames(asList("host1", "host2"))
            .withInfraMappingType(PHYSICAL_DATA_CENTER.name())
            .build();
    PhysicalInfrastructureMapping infrastructureMapping =
        (PhysicalInfrastructureMapping) infrastructureMappingService.save(physicalInfrastructureMapping, null);

    assertThat(infrastructureMapping.getHostNames()).containsExactlyInAnyOrder("host1", "host2");

    // Setup done. Two Host and Two service instances

    // Test specific host
    List<ServiceInstance> serviceInstances = infrastructureMappingService.selectServiceInstances(app.getUuid(),
        infrastructureMapping.getUuid(), null,
        aServiceInstanceSelectionParams().withSelectSpecificHosts(true).withHostNames(singletonList("host1")).build());

    assertThat(serviceInstances).hasSize(1).extracting(ServiceInstance::getHostName).containsExactlyInAnyOrder("host1");

    // Test host count
    serviceInstances =
        infrastructureMappingService.selectServiceInstances(app.getUuid(), infrastructureMapping.getUuid(), null,
            aServiceInstanceSelectionParams().withSelectSpecificHosts(false).withCount(2).build());

    assertThat(serviceInstances)
        .hasSize(2)
        .extracting(ServiceInstance::getHostName)
        .containsExactlyInAnyOrder("host1", "host2");

    // Test exclusion
    List<ServiceInstance> allServiceInstances =
        serviceInstanceService
            .list(aPageRequest().addFilter(ServiceInstanceKeys.appId, Operator.EQ, app.getUuid()).build())
            .getResponse();

    // Exclude 1st service instance
    serviceInstances =
        infrastructureMappingService.selectServiceInstances(app.getUuid(), infrastructureMapping.getUuid(), null,
            aServiceInstanceSelectionParams()
                .withSelectSpecificHosts(false)
                .withCount(2)
                .withExcludedServiceInstanceIds(singletonList(allServiceInstances.get(0).getUuid()))
                .build());
    assertThat(serviceInstances).hasSize(1).containsExactly(allServiceInstances.get(1));

    // Exclude 2nd service instance
    serviceInstances =
        infrastructureMappingService.selectServiceInstances(app.getUuid(), infrastructureMapping.getUuid(), null,
            aServiceInstanceSelectionParams()
                .withSelectSpecificHosts(false)
                .withCount(2)
                .withExcludedServiceInstanceIds(singletonList(allServiceInstances.get(1).getUuid()))
                .build());
    assertThat(serviceInstances).hasSize(1).containsExactly(allServiceInstances.get(0));

    // Exclude both service instance
    serviceInstances =
        infrastructureMappingService.selectServiceInstances(app.getUuid(), infrastructureMapping.getUuid(), null,
            aServiceInstanceSelectionParams()
                .withSelectSpecificHosts(false)
                .withCount(2)
                .withExcludedServiceInstanceIds(
                    allServiceInstances.stream().map(ServiceInstance::getUuid).collect(toList()))
                .build());
    assertThat(serviceInstances).hasSize(0);
  }

  @Test
  @Owner(developers = ANUBHAW)
  @Category(DeprecatedIntegrationTests.class)
  @Ignore("TODO: please provide clear motivation why this test is ignored")
  public void shouldSelectAwsInfrastructureInstances() {
    List<Key<ServiceTemplate>> templateRefKeysByService =
        serviceTemplateService.getTemplateRefKeysByService(app.getUuid(), service.getUuid(), environment.getUuid());
    String serviceTemplateId = (String) templateRefKeysByService.get(0).getId();

    SettingAttribute hostConnectionAttr = aSettingAttribute()
                                              .withAppId(app.getUuid())
                                              .withValue(HostConnectionAttributes.Builder.aHostConnectionAttributes()
                                                             .withAccessType(AccessType.KEY)
                                                             .withConnectionType(ConnectionType.SSH)
                                                             .withKey("wingsKey".toCharArray())
                                                             .build())
                                              .build();
    persistence.save(hostConnectionAttr);

    SettingAttribute computeProviderSetting =
        aSettingAttribute()
            .withAppId(app.getUuid())
            .withValue(AwsConfig.builder()
                           .accessKey(scmSecret.decryptToCharArray(new SecretName("aws_config_access_key")))
                           .secretKey(scmSecret.decryptToCharArray(new SecretName("aws_setting_attribute_secret_key")))
                           .build())
            .build();

    persistence.save(computeProviderSetting);

    AwsInfrastructureMapping awsInfrastructureMapping =
        anAwsInfrastructureMapping()
            .withAppId(app.getUuid())
            .withEnvId(environment.getUuid())
            .withServiceTemplateId(serviceTemplateId)
            .withComputeProviderSettingId(computeProviderSetting.getUuid())
            .withHostConnectionAttrs(hostConnectionAttr.getUuid())
            .build();

    awsInfrastructureMapping =
        (AwsInfrastructureMapping) infrastructureMappingService.save(awsInfrastructureMapping, null);
  }

  @Test
  @Owner(developers = ANUBHAW)
  @Category(DeprecatedIntegrationTests.class)
  @Ignore("TODO: please provide clear motivation why this test is ignored")
  public void shouldProvisionAwsNodes() {
    List<Key<ServiceTemplate>> templateRefKeysByService =
        serviceTemplateService.getTemplateRefKeysByService(app.getUuid(), service.getUuid(), environment.getUuid());
    String serviceTemplateId = (String) templateRefKeysByService.get(0).getId();

    SettingAttribute hostConnectionAttr =
        aSettingAttribute()
            .withAppId(app.getUuid())
            .withValue(HostConnectionAttributes.Builder.aHostConnectionAttributes()
                           .withAccessType(AccessType.KEY)
                           .withConnectionType(ConnectionType.SSH)
                           .withKey(scmSecret.decryptToCharArray(new SecretName("generic_ssh_key")))
                           .build())
            .build();

    persistence.save(hostConnectionAttr);
    SettingAttribute computeProviderSetting =
        aSettingAttribute()
            .withAppId(app.getUuid())
            .withValue(AwsConfig.builder()
                           .accessKey(scmSecret.decryptToCharArray(new SecretName("aws_config_access_key")))
                           .secretKey(scmSecret.decryptToCharArray(new SecretName("aws_setting_attribute_secret_key")))
                           .build())
            .build();

    persistence.save(computeProviderSetting);

    // TODO(brett): Create aws autoscaling group and reference in inframapping
    AwsInfrastructureMapping awsInfrastructureMapping =
        anAwsInfrastructureMapping()
            .withAppId(app.getUuid())
            .withEnvId(environment.getUuid())
            .withServiceTemplateId(serviceTemplateId)
            .withComputeProviderSettingId(computeProviderSetting.getUuid())
            .withHostConnectionAttrs(hostConnectionAttr.getUuid())
            .withProvisionInstances(true)
            .withAutoScalingGroupName("autoscalingGroupName")
            .withSetDesiredCapacity(true)
            .withDesiredCapacity(5)
            .build();

    awsInfrastructureMapping =
        (AwsInfrastructureMapping) infrastructureMappingService.save(awsInfrastructureMapping, null);

    List<Host> hosts =
        infrastructureMappingService.getAutoScaleGroupNodes(app.getUuid(), awsInfrastructureMapping.getUuid(), null);
  }
}
