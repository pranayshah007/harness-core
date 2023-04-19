/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl;

import static io.harness.rule.OwnerRule.ADWAIT;
import static io.harness.rule.OwnerRule.ANIL;
import static io.harness.rule.OwnerRule.RIHAZ;
import static io.harness.rule.OwnerRule.SATYAM;
import static io.harness.rule.OwnerRule.VAIBHAV_SI;

import static software.wings.beans.SettingAttribute.Builder.aSettingAttribute;
import static software.wings.beans.VMSSAuthType.PASSWORD;
import static software.wings.beans.VMSSAuthType.SSH_PUBLIC_KEY;
import static software.wings.utils.WingsTestConstants.APP_ID;
import static software.wings.utils.WingsTestConstants.HOST_NAME;
import static software.wings.utils.WingsTestConstants.PROVISIONER_ID;
import static software.wings.utils.WingsTestConstants.WORKFLOW_EXECUTION_ID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.beans.AwsInfrastructureMapping;
import software.wings.beans.AzureKubernetesInfrastructureMapping;
import software.wings.beans.AzureVMSSInfrastructureMapping;
import software.wings.beans.AzureWebAppInfrastructureMapping;
import software.wings.beans.DirectKubernetesInfrastructureMapping;
import software.wings.beans.EcsInfrastructureMapping;
import software.wings.beans.GcpKubernetesInfrastructureMapping;
import software.wings.beans.HostConnectionType;
import software.wings.beans.InfrastructureMapping;
import software.wings.beans.InfrastructureType;
import software.wings.beans.PhysicalInfrastructureMapping;
import software.wings.beans.PhysicalInfrastructureMappingWinRm;
import software.wings.beans.SyncTaskContext;
import software.wings.helpers.ext.container.ContainerMasterUrlHelper;
import software.wings.service.impl.aws.model.AwsSecurityGroup;
import software.wings.service.impl.aws.model.AwsSubnet;
import software.wings.service.impl.aws.model.AwsVPC;
import software.wings.service.intfc.SettingsService;

import com.amazonaws.regions.Regions;
import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.joor.Reflect;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;

public class InfrastructureMappingServiceImplTest extends WingsBaseTest {
  InfrastructureMappingServiceImpl spyInfrastructureMappingService = spy(new InfrastructureMappingServiceImpl());
  @Mock ContainerMasterUrlHelper containerMasterUrlHelper;
  @Mock SettingsService settingsService;
  @Inject @InjectMocks InfrastructureMappingServiceImpl infrastructureMappingService;

  private static final String DEFAULT = "default";
  private static final String USER_INPUT_NAMESPACE = "USER_INPUT_NAMESPACE";
  private static final String DEFAULT_MASTER_URL = "DEFAULT_MASTER_URL";
  private static final String MASTER_URL = "MASTER_URL";
  private static List<String> hosts = ImmutableList.of("ABC ", "", HOST_NAME);

  @Before
  public void setUp() throws Exception {
    Reflect.on(spyInfrastructureMappingService).set("containerMasterUrlHelper", containerMasterUrlHelper);
  }

  @Test
  @Owner(developers = VAIBHAV_SI)
  @Category(UnitTests.class)
  public void hostsListShouldReturnEmptyWhenDynamicInfra() {
    InfrastructureMapping infraMapping = PhysicalInfrastructureMapping.Builder.aPhysicalInfrastructureMapping()
                                             .withProvisionerId(PROVISIONER_ID)
                                             .build();

    List<String> hostDisplayNames = infrastructureMappingService.getInfrastructureMappingHostDisplayNames(
        infraMapping, APP_ID, WORKFLOW_EXECUTION_ID);

    assertThat(hostDisplayNames).isEmpty();
  }

  @Test
  @Owner(developers = RIHAZ)
  @Category(UnitTests.class)
  public void setDefaultsTestPhysicalInfraWinRmTest() {
    PhysicalInfrastructureMappingWinRm physicalInfraMappingWinRm =
        PhysicalInfrastructureMappingWinRm.Builder.aPhysicalInfrastructureMappingWinRm()
            .withHostNames(hosts)
            .withInfraMappingType(InfrastructureType.PHYSICAL_INFRA_WINRM)
            .build();

    infrastructureMappingService.setDefaults(physicalInfraMappingWinRm);

    assertThat(physicalInfraMappingWinRm.getHostNames().size()).isEqualTo(2);
    assertThat(physicalInfraMappingWinRm.getHostNames().get(0)).isEqualTo("ABC");
  }

  @Test
  @Owner(developers = VAIBHAV_SI)
  @Category(UnitTests.class)
  public void testHostConnectionTypeDefaultSetting() {
    // default for private dns
    AwsInfrastructureMapping awsInfrastructureMapping = AwsInfrastructureMapping.Builder.anAwsInfrastructureMapping()
                                                            .withInfraMappingType(InfrastructureType.AWS_INSTANCE)
                                                            .build();
    infrastructureMappingService.setDefaults(awsInfrastructureMapping);
    assertThat(awsInfrastructureMapping.getHostConnectionType()).isEqualTo(HostConnectionType.PRIVATE_DNS.name());

    // default for public dns
    awsInfrastructureMapping.setUsePublicDns(true);
    awsInfrastructureMapping.setHostConnectionType(null);
    infrastructureMappingService.setDefaults(awsInfrastructureMapping);
    assertThat(awsInfrastructureMapping.getHostConnectionType()).isEqualTo(HostConnectionType.PUBLIC_DNS.name());

    // not modify existing values
    awsInfrastructureMapping.setHostConnectionType(HostConnectionType.PRIVATE_DNS.name());
    infrastructureMappingService.setDefaults(awsInfrastructureMapping);
    assertThat(awsInfrastructureMapping.getHostConnectionType()).isEqualTo(HostConnectionType.PRIVATE_DNS.name());

    awsInfrastructureMapping.setHostConnectionType(HostConnectionType.PUBLIC_DNS.name());
    infrastructureMappingService.setDefaults(awsInfrastructureMapping);
    assertThat(awsInfrastructureMapping.getHostConnectionType()).isEqualTo(HostConnectionType.PUBLIC_DNS.name());
  }

  @Test
  @Owner(developers = RIHAZ)
  @Category(UnitTests.class)
  public void setDefaultsPhysicalInfraTest() {
    PhysicalInfrastructureMapping physicalInfraMapping =
        PhysicalInfrastructureMapping.Builder.aPhysicalInfrastructureMapping()
            .withHostNames(hosts)
            .withInfraMappingType(InfrastructureType.PHYSICAL_INFRA)
            .build();

    infrastructureMappingService.setDefaults(physicalInfraMapping);

    assertThat(physicalInfraMapping.getHostNames().size()).isEqualTo(2);
    assertThat(physicalInfraMapping.getHostNames().get(0)).isEqualTo("ABC");
  }

  @Test
  @Owner(developers = RIHAZ)
  @Category(UnitTests.class)
  public void setDefaultsGcpInfraTest() {
    ContainerServiceParams containerServiceParams = ContainerServiceParams.builder().build();
    GcpKubernetesInfrastructureMapping gcpKubernetesInfraMapping =
        GcpKubernetesInfrastructureMapping.builder().infraMappingType(InfrastructureType.GCP_KUBERNETES_ENGINE).build();

    SyncTaskContext syncTaskContext = SyncTaskContext.builder().build();

    Mockito.when(containerMasterUrlHelper.masterUrlRequiredWithProvisioner(gcpKubernetesInfraMapping)).thenReturn(true);
    doReturn(containerServiceParams)
        .when(spyInfrastructureMappingService)
        .getGcpContainerServiceParams(gcpKubernetesInfraMapping);

    doReturn(syncTaskContext).when(spyInfrastructureMappingService).getSyncTaskContext(gcpKubernetesInfraMapping);

    Mockito.when(containerMasterUrlHelper.fetchMasterUrl(containerServiceParams, syncTaskContext))
        .thenReturn(MASTER_URL);

    spyInfrastructureMappingService.setDefaults(gcpKubernetesInfraMapping);

    assertThat(gcpKubernetesInfraMapping.getMasterUrl()).isEqualTo(MASTER_URL);
  }

  @Test
  @Owner(developers = RIHAZ)
  @Category(UnitTests.class)
  public void setDefaultsGcpInfraNoChangeTest() {
    ContainerServiceParams containerServiceParams = ContainerServiceParams.builder().build();
    SyncTaskContext syncTaskContext = SyncTaskContext.builder().build();
    GcpKubernetesInfrastructureMapping gcpKubernetesInfraMapping =
        GcpKubernetesInfrastructureMapping.builder()
            .infraMappingType(InfrastructureType.GCP_KUBERNETES_ENGINE)
            .masterUrl(DEFAULT_MASTER_URL)
            .build();

    doReturn(containerServiceParams)
        .when(spyInfrastructureMappingService)
        .getGcpContainerServiceParams(gcpKubernetesInfraMapping);

    doReturn(syncTaskContext).when(spyInfrastructureMappingService).getSyncTaskContext(gcpKubernetesInfraMapping);

    Mockito.when(containerMasterUrlHelper.fetchMasterUrl(containerServiceParams, syncTaskContext))
        .thenReturn(MASTER_URL);

    spyInfrastructureMappingService.setDefaults(gcpKubernetesInfraMapping);

    assertThat(gcpKubernetesInfraMapping.getMasterUrl()).isEqualTo(DEFAULT_MASTER_URL);
  }

  @Test
  @Owner(developers = RIHAZ)
  @Category(UnitTests.class)
  public void setDefaultsAzureKubernetesInfraTest() {
    ContainerServiceParams containerServiceParams = ContainerServiceParams.builder().build();
    AzureKubernetesInfrastructureMapping azureKubernetesInfraMapping =
        AzureKubernetesInfrastructureMapping.Builder.anAzureKubernetesInfrastructureMapping()
            .withInfraMappingType(InfrastructureType.AZURE_KUBERNETES)
            .build();
    doReturn(containerServiceParams)
        .when(spyInfrastructureMappingService)
        .getAzureContainerServiceParams(azureKubernetesInfraMapping);
    SyncTaskContext syncTaskContext = SyncTaskContext.builder().build();
    doReturn(syncTaskContext).when(spyInfrastructureMappingService).getSyncTaskContext(azureKubernetesInfraMapping);
    Mockito.when(containerMasterUrlHelper.fetchMasterUrl(containerServiceParams, syncTaskContext))
        .thenReturn(MASTER_URL);

    Mockito.when(containerMasterUrlHelper.masterUrlRequiredWithProvisioner(azureKubernetesInfraMapping))
        .thenReturn(true);

    spyInfrastructureMappingService.setDefaults(azureKubernetesInfraMapping);

    assertThat(azureKubernetesInfraMapping.getMasterUrl()).isEqualTo(MASTER_URL);
  }

  @Test
  @Owner(developers = RIHAZ)
  @Category(UnitTests.class)
  public void setDefaultsAzureKubernetesInfraNoChangeTest() {
    ContainerServiceParams containerServiceParams = ContainerServiceParams.builder().build();
    AzureKubernetesInfrastructureMapping azureKubernetesInfraMapping =
        AzureKubernetesInfrastructureMapping.Builder.anAzureKubernetesInfrastructureMapping()
            .withInfraMappingType(InfrastructureType.AZURE_KUBERNETES)
            .withMasterUrl(DEFAULT_MASTER_URL)
            .build();
    doReturn(containerServiceParams)
        .when(spyInfrastructureMappingService)
        .getAzureContainerServiceParams(azureKubernetesInfraMapping);
    SyncTaskContext syncTaskContext = SyncTaskContext.builder().build();
    doReturn(syncTaskContext).when(spyInfrastructureMappingService).getSyncTaskContext(azureKubernetesInfraMapping);
    Mockito.when(containerMasterUrlHelper.fetchMasterUrl(containerServiceParams, syncTaskContext))
        .thenReturn(MASTER_URL);

    spyInfrastructureMappingService.setDefaults(azureKubernetesInfraMapping);

    assertThat(azureKubernetesInfraMapping.getMasterUrl()).isEqualTo(DEFAULT_MASTER_URL);
  }

  @Test
  @Owner(developers = RIHAZ)
  @Category(UnitTests.class)
  public void setDefaultsDirectKubernetesTest() {
    DirectKubernetesInfrastructureMapping kubernetesInfrastructureMapping =
        DirectKubernetesInfrastructureMapping.builder().infraMappingType(InfrastructureType.DIRECT_KUBERNETES).build();

    spyInfrastructureMappingService.setDefaults(kubernetesInfrastructureMapping);

    assertThat(kubernetesInfrastructureMapping.getNamespace()).isEqualTo(DEFAULT);
  }

  @Test
  @Owner(developers = RIHAZ)
  @Category(UnitTests.class)
  public void setDefaultsDirectKubernetesNoChangeTest() {
    DirectKubernetesInfrastructureMapping kubernetesInfrastructureMapping =
        DirectKubernetesInfrastructureMapping.builder()
            .infraMappingType(InfrastructureType.DIRECT_KUBERNETES)
            .namespace(USER_INPUT_NAMESPACE)
            .build();

    spyInfrastructureMappingService.setDefaults(kubernetesInfrastructureMapping);

    assertThat(kubernetesInfrastructureMapping.getNamespace()).isEqualTo(USER_INPUT_NAMESPACE);
  }

  @Test
  @Owner(developers = RIHAZ)
  @Category(UnitTests.class)
  public void setDefaultGoogleKubernetesEnginesTest() {
    GcpKubernetesInfrastructureMapping gcpKubernetesInfraMapping =
        GcpKubernetesInfrastructureMapping.builder().infraMappingType(InfrastructureType.GCP_KUBERNETES_ENGINE).build();

    spyInfrastructureMappingService.setDefaults(gcpKubernetesInfraMapping);

    assertThat(gcpKubernetesInfraMapping.getNamespace()).isEqualTo(DEFAULT);
  }

  @Test
  @Owner(developers = RIHAZ)
  @Category(UnitTests.class)
  public void setDefaultsNGoogleKubernetesEngineoChangeTest() {
    GcpKubernetesInfrastructureMapping gcpKubernetesInfraMapping =
        GcpKubernetesInfrastructureMapping.builder()
            .infraMappingType(InfrastructureType.GCP_KUBERNETES_ENGINE)
            .namespace(USER_INPUT_NAMESPACE)
            .build();

    spyInfrastructureMappingService.setDefaults(gcpKubernetesInfraMapping);

    assertThat(gcpKubernetesInfraMapping.getNamespace()).isEqualTo(USER_INPUT_NAMESPACE);
  }

  @Test
  @Owner(developers = RIHAZ)
  @Category(UnitTests.class)
  public void getVPCIdStrListTest() {
    String appId = "app123";
    String computeProviderId = "cpId";
    String region = "region1";
    String vpcId = "vpcId1";

    doReturn(new ArrayList<AwsVPC>() {
      { add(AwsVPC.builder().id(vpcId).build()); }
    })
        .when(spyInfrastructureMappingService)
        .listVPC(appId, computeProviderId, region);

    List<String> vpcList = spyInfrastructureMappingService.getVPCIds(appId, computeProviderId, region);

    assertThat(vpcList.size()).isEqualTo(1);
    assertThat(vpcList.get(0)).isEqualTo(vpcId);
  }

  @Test
  @Owner(developers = RIHAZ)
  @Category(UnitTests.class)
  public void getSubnetIdStrList() {
    String appId = "app123";
    String computeProviderId = "cpId";
    String region = "region1";
    String vpcId = "vpcId123";
    String subnetId = "subnetId123";
    List<String> vpcList = new ArrayList<String>() {
      { add(vpcId); }
    };

    doReturn(new ArrayList<AwsSubnet>() {
      { add(AwsSubnet.builder().id(subnetId).build()); }
    })
        .when(spyInfrastructureMappingService)
        .listSubnets(appId, computeProviderId, region, vpcList);

    List<String> subnetList = spyInfrastructureMappingService.getSubnetIds(appId, computeProviderId, region, vpcList);

    assertThat(subnetList.size()).isEqualTo(1);
    assertThat(subnetList.get(0)).isEqualTo(subnetId);
  }

  @Test
  @Owner(developers = RIHAZ)
  @Category(UnitTests.class)
  public void getSGIdStrList() {
    String appId = "app123";
    String computeProviderId = "cpId";
    String region = "region1";
    String vpcId = "vpcId123";
    String sgId = "sgId123";
    List<String> vpcList = new ArrayList<String>() {
      { add(vpcId); }
    };

    doReturn(new ArrayList<AwsSecurityGroup>() {
      { add(AwsSecurityGroup.builder().id(sgId).build()); }
    })
        .when(spyInfrastructureMappingService)
        .listSecurityGroups(appId, computeProviderId, region, vpcList);

    List<String> sgList = spyInfrastructureMappingService.getSGIds(appId, computeProviderId, region, vpcList);

    assertThat(sgList.size()).isEqualTo(1);
    assertThat(sgList.get(0)).isEqualTo(sgId);
  }

  @Test
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void testValidateAzureWebAppInfraMapping() {
    doReturn(aSettingAttribute().build()).when(settingsService).get(any());
    final AzureWebAppInfrastructureMapping infrastructureMapping =
        AzureWebAppInfrastructureMapping.builder().subscriptionId("SubsId").resourceGroup("ResName").build();

    infrastructureMappingService.validateAzureWebAppInfraMapping(infrastructureMapping);

    infrastructureMapping.setSubscriptionId("");
    assertThatThrownBy(() -> infrastructureMappingService.validateAzureWebAppInfraMapping(infrastructureMapping))
        .isInstanceOf(InvalidRequestException.class);
    infrastructureMapping.setSubscriptionId("SubsId");

    infrastructureMapping.setResourceGroup("");
    assertThatThrownBy(() -> infrastructureMappingService.validateAzureWebAppInfraMapping(infrastructureMapping))
        .isInstanceOf(InvalidRequestException.class);
    infrastructureMapping.setResourceGroup("ResName");
  }

  @Test
  @Owner(developers = SATYAM)
  @Category(UnitTests.class)
  public void testValidateAzureVMSSInfraMapping() {
    doReturn(aSettingAttribute().build()).when(settingsService).get(any());

    final AzureVMSSInfrastructureMapping infrastructureMapping = AzureVMSSInfrastructureMapping.builder()
                                                                     .baseVMSSName("BaseVMSS")
                                                                     .subscriptionId("SubsId")
                                                                     .resourceGroupName("ResName")
                                                                     .userName("UName")
                                                                     .vmssAuthType(PASSWORD)
                                                                     .passwordSecretTextName("passwd")
                                                                     .build();
    infrastructureMappingService.validateAzureVMSSInfraMapping(infrastructureMapping);

    infrastructureMapping.setBaseVMSSName("");
    assertThatThrownBy(() -> infrastructureMappingService.validateAzureVMSSInfraMapping(infrastructureMapping))
        .isInstanceOf(InvalidRequestException.class);
    infrastructureMapping.setBaseVMSSName("BaseVMSS");

    infrastructureMapping.setSubscriptionId("");
    assertThatThrownBy(() -> infrastructureMappingService.validateAzureVMSSInfraMapping(infrastructureMapping))
        .isInstanceOf(InvalidRequestException.class);
    infrastructureMapping.setSubscriptionId("SubsId");

    infrastructureMapping.setResourceGroupName("");
    assertThatThrownBy(() -> infrastructureMappingService.validateAzureVMSSInfraMapping(infrastructureMapping))
        .isInstanceOf(InvalidRequestException.class);
    infrastructureMapping.setResourceGroupName("ResName");

    infrastructureMapping.setUserName("");
    assertThatThrownBy(() -> infrastructureMappingService.validateAzureVMSSInfraMapping(infrastructureMapping))
        .isInstanceOf(InvalidRequestException.class);
    infrastructureMapping.setUserName("UName");

    infrastructureMapping.setVmssAuthType(null);
    assertThatThrownBy(() -> infrastructureMappingService.validateAzureVMSSInfraMapping(infrastructureMapping))
        .isInstanceOf(InvalidRequestException.class);
    infrastructureMapping.setVmssAuthType(PASSWORD);

    infrastructureMapping.setPasswordSecretTextName("");
    assertThatThrownBy(() -> infrastructureMappingService.validateAzureVMSSInfraMapping(infrastructureMapping))
        .isInstanceOf(InvalidRequestException.class);
    infrastructureMapping.setPasswordSecretTextName("SubsId");

    infrastructureMapping.setVmssAuthType(SSH_PUBLIC_KEY);
    assertThatThrownBy(() -> infrastructureMappingService.validateAzureVMSSInfraMapping(infrastructureMapping))
        .isInstanceOf(InvalidRequestException.class);
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testValidateEcsInfraMapping() {
    doReturn(aSettingAttribute().build()).when(settingsService).get(any());

    final EcsInfrastructureMapping ecsInfrastructureMapping =
        EcsInfrastructureMapping.builder().clusterName("c1").region(Regions.US_EAST_1.getName()).build();

    ecsInfrastructureMapping.setLaunchType("FARGATE");
    ecsInfrastructureMapping.setVpcId("vpc");
    ecsInfrastructureMapping.setSecurityGroupIds(Arrays.asList("a1", "a2"));
    ecsInfrastructureMapping.setSubnetIds(Arrays.asList("a1", "a2"));
    ecsInfrastructureMapping.setExecutionRole("ex");
    infrastructureMappingService.validateEcsInfraMapping(ecsInfrastructureMapping);

    // if region is null, will be defaulted to "us-east-1"
    ecsInfrastructureMapping.setRegion(null);
    infrastructureMappingService.validateEcsInfraMapping(ecsInfrastructureMapping);
    ecsInfrastructureMapping.setRegion("");
    infrastructureMappingService.validateEcsInfraMapping(ecsInfrastructureMapping);

    ecsInfrastructureMapping.setRegion(Regions.US_EAST_1.getName());
    ecsInfrastructureMapping.setClusterName(null);
    assertThatThrownBy(() -> infrastructureMappingService.validateEcsInfraMapping(ecsInfrastructureMapping))
        .isInstanceOf(InvalidRequestException.class);
    ecsInfrastructureMapping.setClusterName("");
    assertThatThrownBy(() -> infrastructureMappingService.validateEcsInfraMapping(ecsInfrastructureMapping))
        .isInstanceOf(InvalidRequestException.class);
    ecsInfrastructureMapping.setClusterName("c1");

    ecsInfrastructureMapping.setLaunchType(null);
    assertThatThrownBy(() -> infrastructureMappingService.validateEcsInfraMapping(ecsInfrastructureMapping))
        .isInstanceOf(InvalidRequestException.class);
    ecsInfrastructureMapping.setLaunchType("random");
    assertThatThrownBy(() -> infrastructureMappingService.validateEcsInfraMapping(ecsInfrastructureMapping))
        .isInstanceOf(InvalidRequestException.class);
    ecsInfrastructureMapping.setLaunchType("FARGATE");

    ecsInfrastructureMapping.setVpcId(null);
    assertThatThrownBy(() -> infrastructureMappingService.validateEcsInfraMapping(ecsInfrastructureMapping))
        .isInstanceOf(InvalidRequestException.class);
    ecsInfrastructureMapping.setVpcId("");
    assertThatThrownBy(() -> infrastructureMappingService.validateEcsInfraMapping(ecsInfrastructureMapping))
        .isInstanceOf(InvalidRequestException.class);
    ecsInfrastructureMapping.setVpcId("vpc");

    ecsInfrastructureMapping.setExecutionRole(null);
    assertThatThrownBy(() -> infrastructureMappingService.validateEcsInfraMapping(ecsInfrastructureMapping))
        .isInstanceOf(InvalidRequestException.class);
    ecsInfrastructureMapping.setExecutionRole("");
    assertThatThrownBy(() -> infrastructureMappingService.validateEcsInfraMapping(ecsInfrastructureMapping))
        .isInstanceOf(InvalidRequestException.class);

    ecsInfrastructureMapping.setSubnetIds(null);
    assertThatThrownBy(() -> infrastructureMappingService.validateEcsInfraMapping(ecsInfrastructureMapping))
        .isInstanceOf(InvalidRequestException.class);
    ecsInfrastructureMapping.setSubnetIds(Collections.EMPTY_LIST);
    assertThatThrownBy(() -> infrastructureMappingService.validateEcsInfraMapping(ecsInfrastructureMapping))
        .isInstanceOf(InvalidRequestException.class);
    ecsInfrastructureMapping.setSubnetIds(Arrays.asList("a1", "a2"));

    ecsInfrastructureMapping.setSecurityGroupIds(null);
    assertThatThrownBy(() -> infrastructureMappingService.validateEcsInfraMapping(ecsInfrastructureMapping))
        .isInstanceOf(InvalidRequestException.class);
    ecsInfrastructureMapping.setSecurityGroupIds(Collections.EMPTY_LIST);
    assertThatThrownBy(() -> infrastructureMappingService.validateEcsInfraMapping(ecsInfrastructureMapping))
        .isInstanceOf(InvalidRequestException.class);
    ecsInfrastructureMapping.setSecurityGroupIds(Arrays.asList("a1", "a2"));

    ecsInfrastructureMapping.setExecutionRole(null);
    assertThatThrownBy(() -> infrastructureMappingService.validateEcsInfraMapping(ecsInfrastructureMapping))
        .isInstanceOf(InvalidRequestException.class);
    ecsInfrastructureMapping.setExecutionRole("");
    assertThatThrownBy(() -> infrastructureMappingService.validateEcsInfraMapping(ecsInfrastructureMapping))
        .isInstanceOf(InvalidRequestException.class);
    ecsInfrastructureMapping.setExecutionRole("exe");

    infrastructureMappingService.validateEcsInfraMapping(ecsInfrastructureMapping);
  }
}
