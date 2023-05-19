/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.sm.states.azure;

import static io.harness.deployment.InstanceDetails.InstanceType.AZURE_VMSS;
import static io.harness.deployment.InstanceDetails.InstanceType.AZURE_WEBAPP;
import static io.harness.rule.OwnerRule.TMACARI;

import static software.wings.api.InstanceElement.Builder.anInstanceElement;
import static software.wings.beans.infrastructure.Host.Builder.aHost;
import static software.wings.utils.WingsTestConstants.HOST_ID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.beans.SweepingOutput;
import io.harness.beans.SweepingOutputInstance;
import io.harness.category.element.UnitTests;
import io.harness.delegate.task.azure.appservice.webapp.response.AzureAppDeploymentData;
import io.harness.delegate.task.azure.response.AzureVMInstanceData;
import io.harness.deployment.InstanceDetails;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.api.HostElement;
import software.wings.api.InstanceElement;
import software.wings.api.PhaseElement;
import software.wings.api.ServiceElement;
import software.wings.beans.AzureVMSSInfrastructureMapping;
import software.wings.beans.AzureWebAppInfrastructureMapping;
import software.wings.beans.VMSSAuthType;
import software.wings.beans.VMSSDeploymentType;
import software.wings.beans.infrastructure.Host;
import software.wings.expression.ManagerExpressionEvaluator;
import software.wings.service.impl.servicetemplates.ServiceTemplateHelper;
import software.wings.service.intfc.HostService;
import software.wings.service.intfc.sweepingoutput.SweepingOutputInquiry;
import software.wings.service.intfc.sweepingoutput.SweepingOutputService;
import software.wings.sm.ExecutionContext;
import software.wings.sm.states.azure.appservices.AzureAppServiceSlotSetupContextElement;

import java.util.Collections;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;

public class AzureSweepingOutputServiceHelperTest extends WingsBaseTest {
  private static final String INFRA_ID = "infraId";
  private static final String INFRA_UUID = "infraUUId";
  private static final String INSTANCE_ID = "instanceId";
  private static final String SUBSCRIPTION_ID = "subscriptionId";
  private static final String RESOURCE_GROUP = "resourceGroup";
  private static final String APP_NAME = "testWebApp";
  private static final String DEPLOYMENT_SLOT = "deploymentSlot";
  private static final String DEPLOYMENT_SLOT_ID = "testWebApp/deploymentSlot";
  private static final String APP_SERVICE_PLAN_ID = "appService-plan-id";
  private static final String HOST_NAME = "hostname";
  private static final String HOST_NAME_FROM_CONVENTION = "hostnameFromConvention";
  private static final String PUBLIC_DNS_NAME = "publicDnsName";
  private static final String PRIVATE_IP_ADDRESS = "privateIpAddress";
  private static final String PRIVATE_DNS_NAME = "privateDnsName";
  private static final String APP_ID = "appId";
  private static final String ENV_ID = "envId";
  private static final String INSTANCE_NAME = "instanceName";
  private static final String INSTANCE_TYPE = "instanceType";

  @Mock private ServiceTemplateHelper serviceTemplateHelper;
  @Mock private HostService hostService;
  @Mock private ManagerExpressionEvaluator expressionEvaluator;
  @Mock private SweepingOutputService sweepingOutputService;

  @Spy @InjectMocks AzureSweepingOutputServiceHelper azureSweepingOutputServiceHelper;

  @Before
  public void setup() {
    doReturn(HOST_NAME_FROM_CONVENTION).when(expressionEvaluator).substitute(any(), any());
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testGenerateInstanceElements() {
    ExecutionContext executionContext = mock(ExecutionContext.class);
    AzureVMSSInfrastructureMapping azureVMSSInfrastructureMapping =
        AzureVMSSInfrastructureMapping.builder()
            .baseVMSSName("baseVMSSName")
            .resourceGroupName(RESOURCE_GROUP)
            .subscriptionId(SUBSCRIPTION_ID)
            .passwordSecretTextName("password")
            .userName("userName")
            .vmssAuthType(VMSSAuthType.PASSWORD)
            .vmssDeploymentType(VMSSDeploymentType.NATIVE_VMSS)
            .build();
    azureVMSSInfrastructureMapping.setAppId(APP_ID);
    azureVMSSInfrastructureMapping.setEnvId(ENV_ID);
    azureVMSSInfrastructureMapping.setUuid(INFRA_UUID);
    azureVMSSInfrastructureMapping.setInfrastructureDefinitionId(INFRA_ID);

    AzureVMInstanceData instanceData = AzureVMInstanceData.builder()
                                           .instanceId(INSTANCE_ID)
                                           .privateIpAddress(PRIVATE_IP_ADDRESS)
                                           .publicDnsName(PUBLIC_DNS_NAME)
                                           .privateDnsName(PRIVATE_DNS_NAME)
                                           .build();
    List<AzureVMInstanceData> instancesExisting = Collections.singletonList(instanceData);
    ArgumentCaptor<Host> captor = ArgumentCaptor.forClass(Host.class);
    Host host = aHost().withUuid(HOST_ID).build();

    doReturn(host).when(hostService).saveHost(captor.capture());

    List<InstanceElement> instanceElements = azureSweepingOutputServiceHelper.generateInstanceElements(
        executionContext, azureVMSSInfrastructureMapping, instancesExisting);

    InstanceElement instanceElement = instanceElements.get(0);

    Host savedHost = captor.getValue();
    assertThat(savedHost.getHostName()).isEqualTo(PRIVATE_DNS_NAME);
    assertThat(savedHost.getPublicDns()).isEqualTo(PUBLIC_DNS_NAME);
    assertThat(savedHost.getAppId()).isEqualTo(APP_ID);
    assertThat(savedHost.getEnvId()).isEqualTo(ENV_ID);
    assertThat(savedHost.getInfraMappingId()).isEqualTo(INFRA_UUID);
    assertThat(savedHost.getInfraDefinitionId()).isEqualTo(INFRA_ID);

    verify(hostService, times(1)).saveHost(any());
    verify(serviceTemplateHelper, times(1)).fetchServiceTemplateId(azureVMSSInfrastructureMapping);

    assertThat(instanceElements.size()).isEqualTo(1);
    assertThat(instanceElement.getUuid()).isEqualTo(INSTANCE_ID);
    assertThat(instanceElement.getDisplayName()).isEqualTo(PUBLIC_DNS_NAME);
    assertThat(instanceElement.getHostName()).isEqualTo(HOST_NAME_FROM_CONVENTION);
    assertThat(instanceElement.getHost().getUuid()).isEqualTo(HOST_ID);
    assertThat(instanceElement.getHost().getPublicDns()).isEqualTo(PUBLIC_DNS_NAME);
    assertThat(instanceElement.getHost().getAzureVMInstance()).isEqualTo(instanceData);
    assertThat(instanceElement.getHost().getInstanceId()).isEqualTo(INSTANCE_ID);
    assertThat(instanceElement.getHost().getIp()).isEqualTo(PRIVATE_IP_ADDRESS);
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testGenerateAzureVMSSInstanceDetails() {
    HostElement hostElement =
        HostElement.builder().publicDns(PUBLIC_DNS_NAME).instanceId(INSTANCE_ID).ip(PRIVATE_IP_ADDRESS).build();
    List<InstanceElement> instanceElements = Collections.singletonList(anInstanceElement()
                                                                           .uuid("uuid")
                                                                           .hostName(HOST_NAME)
                                                                           .displayName(APP_NAME)
                                                                           .newInstance(true)
                                                                           .host(hostElement)
                                                                           .build());

    List<InstanceDetails> instanceDetailsList =
        azureSweepingOutputServiceHelper.generateAzureVMSSInstanceDetails(instanceElements);

    InstanceDetails instanceDetails = instanceDetailsList.get(0);
    assertThat(instanceDetailsList.size()).isEqualTo(1);
    assertThat(instanceDetails.getInstanceType()).isEqualTo(AZURE_VMSS);
    assertThat(instanceDetails.getHostName()).isEqualTo(HOST_NAME);
    assertThat(instanceDetails.isNewInstance()).isEqualTo(true);
    assertThat(instanceDetails.getAzureVmss().getPublicDns()).isEqualTo(PUBLIC_DNS_NAME);
    assertThat(instanceDetails.getAzureVmss().getInstanceId()).isEqualTo(INSTANCE_ID);
    assertThat(instanceDetails.getAzureVmss().getIp()).isEqualTo(PRIVATE_IP_ADDRESS);
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testSaveInstanceDetails() {
    ExecutionContext executionContext = mock(ExecutionContext.class);
    List<InstanceElement> instanceElements = Collections.singletonList(
        anInstanceElement().uuid("uuid").hostName(HOST_NAME).displayName(APP_NAME).newInstance(true).build());
    List<InstanceDetails> instanceDetails = Collections.singletonList(InstanceDetails.builder().build());

    doReturn(SweepingOutputInstance.builder()).when(executionContext).prepareSweepingOutputBuilder(any());

    azureSweepingOutputServiceHelper.saveInstanceDetails(executionContext, instanceElements, instanceDetails);
    verify(sweepingOutputService, times(1)).save(any());
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testSaveTrafficShiftInfoToSweepingOutput() {
    ExecutionContext executionContext = mock(ExecutionContext.class);

    doReturn(SweepingOutputInstance.builder()).when(executionContext).prepareSweepingOutputBuilder(any());

    azureSweepingOutputServiceHelper.saveTrafficShiftInfoToSweepingOutput(executionContext, 1);

    verify(sweepingOutputService, times(1)).save(any());
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testGenerateAzureAppInstanceElements() {
    ExecutionContext executionContext = mock(ExecutionContext.class);
    AzureWebAppInfrastructureMapping infraMapping = AzureWebAppInfrastructureMapping.builder()
                                                        .resourceGroup(RESOURCE_GROUP)
                                                        .subscriptionId(SUBSCRIPTION_ID)
                                                        .build();
    infraMapping.setAppId(APP_ID);
    infraMapping.setEnvId(ENV_ID);
    infraMapping.setUuid(INFRA_UUID);
    infraMapping.setInfrastructureDefinitionId(INFRA_ID);
    AzureAppDeploymentData appDeploymentData = AzureAppDeploymentData.builder()
                                                   .subscriptionId(SUBSCRIPTION_ID)
                                                   .resourceGroup(RESOURCE_GROUP)
                                                   .appName(APP_NAME)
                                                   .deploySlot(DEPLOYMENT_SLOT)
                                                   .deploySlotId(DEPLOYMENT_SLOT_ID)
                                                   .appServicePlanId(APP_SERVICE_PLAN_ID)
                                                   .hostName(HOST_NAME)
                                                   .instanceName(INSTANCE_NAME)
                                                   .instanceIp(PRIVATE_IP_ADDRESS)
                                                   .instanceId(INSTANCE_ID)
                                                   .build();
    List<AzureAppDeploymentData> azureAppDeploymentData = Collections.singletonList(appDeploymentData);

    ArgumentCaptor<Host> captor = ArgumentCaptor.forClass(Host.class);
    Host host = aHost().withUuid(HOST_ID).build();
    doReturn(host).when(hostService).saveHost(captor.capture());
    doReturn(SweepingOutputInstance.builder()).when(executionContext).prepareSweepingOutputBuilder(any());

    List<InstanceElement> instanceElementsList = azureSweepingOutputServiceHelper.generateAzureAppInstanceElements(
        executionContext, infraMapping, azureAppDeploymentData);

    Host savedHost = captor.getValue();
    assertThat(savedHost.getHostName()).isEqualTo(APP_NAME);
    assertThat(savedHost.getPublicDns()).isEqualTo(HOST_NAME);
    assertThat(savedHost.getAppId()).isEqualTo(APP_ID);
    assertThat(savedHost.getEnvId()).isEqualTo(ENV_ID);
    assertThat(savedHost.getInfraMappingId()).isEqualTo(INFRA_UUID);
    assertThat(savedHost.getInfraDefinitionId()).isEqualTo(INFRA_ID);

    InstanceElement instanceElement = instanceElementsList.get(0);

    verify(hostService, times(1)).saveHost(any());
    verify(serviceTemplateHelper, times(1)).fetchServiceTemplateId(infraMapping);

    assertThat(instanceElementsList.size()).isEqualTo(1);
    assertThat(instanceElement.isNewInstance()).isTrue();
    assertThat(instanceElement.getUuid()).isEqualTo(INSTANCE_ID);
    assertThat(instanceElement.getDisplayName()).isEqualTo(INSTANCE_NAME);
    assertThat(instanceElement.getHostName()).isEqualTo(HOST_NAME_FROM_CONVENTION);

    assertThat(instanceElement.getHost().getUuid()).isEqualTo(HOST_ID);
    assertThat(instanceElement.getHost().getPublicDns()).isEqualTo(HOST_NAME);
    assertThat(instanceElement.getHost().getInstanceId()).isEqualTo(INSTANCE_ID);
    assertThat(instanceElement.getHost().getIp()).isEqualTo(PRIVATE_IP_ADDRESS);
    assertThat(instanceElement.getHost().getWebAppInstance()).isEqualTo(appDeploymentData);
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testGenerateAzureAppServiceInstanceDetails() {
    List<AzureAppDeploymentData> azureAppDeploymentData =
        Collections.singletonList(AzureAppDeploymentData.builder()
                                      .instanceId(INSTANCE_ID)
                                      .subscriptionId(SUBSCRIPTION_ID)
                                      .resourceGroup(RESOURCE_GROUP)
                                      .appName(APP_NAME)
                                      .deploySlot(DEPLOYMENT_SLOT)
                                      .deploySlotId(DEPLOYMENT_SLOT_ID)
                                      .appServicePlanId(APP_SERVICE_PLAN_ID)
                                      .hostName(HOST_NAME)
                                      .instanceIp(PRIVATE_IP_ADDRESS)
                                      .instanceName(INSTANCE_NAME)
                                      .instanceType(INSTANCE_TYPE)
                                      .build());

    List<InstanceDetails> instanceDetailsLis =
        azureSweepingOutputServiceHelper.generateAzureAppServiceInstanceDetails(azureAppDeploymentData);

    InstanceDetails instanceDetails = instanceDetailsLis.get(0);

    assertThat(instanceDetailsLis.size()).isEqualTo(1);
    assertThat(instanceDetails.getInstanceType()).isEqualTo(AZURE_WEBAPP);
    assertThat(instanceDetails.getHostName()).isEqualTo(HOST_NAME);
    assertThat(instanceDetails.isNewInstance()).isTrue();
    assertThat(instanceDetails.getAzureWebapp().getIp()).isEqualTo(PRIVATE_IP_ADDRESS);
    assertThat(instanceDetails.getAzureWebapp().getAppName()).isEqualTo(APP_NAME);
    assertThat(instanceDetails.getAzureWebapp().getAppServicePlanId()).isEqualTo(APP_SERVICE_PLAN_ID);
    assertThat(instanceDetails.getAzureWebapp().getDeploySlot()).isEqualTo(DEPLOYMENT_SLOT);
    assertThat(instanceDetails.getAzureWebapp().getDeploySlotId()).isEqualTo(DEPLOYMENT_SLOT_ID);
    assertThat(instanceDetails.getAzureWebapp().getInstanceHostName()).isEqualTo(HOST_NAME);
    assertThat(instanceDetails.getAzureWebapp().getInstanceId()).isEqualTo(INSTANCE_ID);
    assertThat(instanceDetails.getAzureWebapp().getInstanceName()).isEqualTo(INSTANCE_NAME);
    assertThat(instanceDetails.getAzureWebapp().getInstanceType()).isEqualTo(INSTANCE_TYPE);
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testGetSetupElementFromSweepingOutput() {
    ExecutionContext executionContext = mock(ExecutionContext.class);
    PhaseElement phaseElement =
        PhaseElement.builder().serviceElement(ServiceElement.builder().uuid("test").build()).build();

    doReturn(SweepingOutputInquiry.builder()).when(executionContext).prepareSweepingOutputInquiryBuilder();
    doReturn(phaseElement).when(executionContext).getContextElement(any(), any());

    azureSweepingOutputServiceHelper.getInfoFromSweepingOutput(executionContext, "test");

    verify(sweepingOutputService, times(1)).findSweepingOutput(any());
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testSaveToSweepingOutPut() {
    ExecutionContext executionContext = mock(ExecutionContext.class);
    SweepingOutput sweepingOutput = AzureAppServiceSlotSetupContextElement.builder().build();
    PhaseElement phaseElement =
        PhaseElement.builder().serviceElement(ServiceElement.builder().uuid("test").build()).build();

    doReturn(phaseElement).when(executionContext).getContextElement(any(), any());
    doReturn(SweepingOutputInstance.builder()).when(executionContext).prepareSweepingOutputBuilder(any());

    azureSweepingOutputServiceHelper.saveToSweepingOutPut(sweepingOutput, "test", executionContext);

    verify(sweepingOutputService, times(1)).save(any());
  }
}
