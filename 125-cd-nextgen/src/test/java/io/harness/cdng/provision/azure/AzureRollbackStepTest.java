/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.provision.azure;

import static io.harness.rule.OwnerRule.NGONZALEZ;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;
import static org.powermock.api.mockito.PowerMockito.verifyStatic;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.azure.model.ARMScopeType;
import io.harness.azure.model.AzureDeploymentMode;
import io.harness.beans.EnvironmentType;
import io.harness.category.element.UnitTests;
import io.harness.cdng.CDStepHelper;
import io.harness.cdng.provision.azure.beans.AzureARMConfig;
import io.harness.cdng.provision.azure.beans.AzureARMTemplateDataOutput;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.delegate.TaskSelector;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.beans.connector.azureconnector.AzureConnectorDTO;
import io.harness.delegate.task.azure.appservice.settings.AppSettingsFile;
import io.harness.delegate.task.azure.arm.AzureARMTaskNGParameters;
import io.harness.exception.InvalidArgumentsException;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.execution.tasks.TaskRequest;
import io.harness.pms.sdk.core.data.OptionalSweepingOutput;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.steps.StepHelper;
import io.harness.steps.StepUtils;

import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.CDP)
public class AzureRollbackStepTest extends CategoryTest {
  AzureTestHelper azureHelperTest = new AzureTestHelper();
  @Mock AzureCommonHelper azureCommonHelper;
  @Mock ExecutionSweepingOutputService sweepingOutputService;

  @Mock AzureARMConfigDAL azureARMConfigDAL;

  @Mock CDStepHelper cdStepHelper;
  @Mock StepHelper stepHelper;

  @InjectMocks private AzureARMRollbackStep azureARMRollbackStep;

  @Before
  public void setUpMocks() {
    MockitoAnnotations.initMocks(this);
  }
  @Test
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  public void testObtainTaskWithNoProvisionerID() {
    when(azureCommonHelper.generateIdentifier(any(), any())).thenReturn("foobar");
    AzureARMTemplateDataOutput azureOutput = AzureARMTemplateDataOutput.builder()
                                                 .resourceGroup("res")
                                                 .resourceGroupTemplateJson("{foo}")
                                                 .scopeType("ResourceGroup")
                                                 .subscriptionId("123")
                                                 .build();
    OptionalSweepingOutput output = OptionalSweepingOutput.builder().found(true).output(azureOutput).build();
    when(sweepingOutputService.resolveOptional(any(), any())).thenReturn(output);
    AzureARMConfig config = AzureARMConfig.builder().connectorRef("abc").provisionerIdentifier("123").build();
    when(azureARMConfigDAL.getRollbackAzureARMConfig(any(), any())).thenReturn(config);
    AzureConnectorDTO azureConnectorDTO = AzureConnectorDTO.builder().build();
    when(cdStepHelper.getConnector(any(), any()))
        .thenReturn(ConnectorInfoDTO.builder().connectorConfig(azureConnectorDTO).build());
    List<EncryptedDataDetail> encryptedDataDetails = new ArrayList<>();
    when(azureCommonHelper.getAzureEncryptionDetails(any(), any())).thenReturn(encryptedDataDetails);
    when(stepHelper.getEnvironmentType(any())).thenReturn(EnvironmentType.NON_PROD);
    Mockito.mockStatic(StepUtils.class);
    when(StepUtils.prepareCDTaskRequest(any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(TaskRequest.newBuilder().build());
    ArgumentCaptor<TaskData> taskDataArgumentCaptor = ArgumentCaptor.forClass(TaskData.class);

    Class<ArrayList<TaskSelector>> delegateSelectors = (Class<ArrayList<TaskSelector>>) (Class) ArrayList.class;
    ArgumentCaptor<ArrayList<TaskSelector>> taskSelectorsArgumentCaptor = ArgumentCaptor.forClass(delegateSelectors);
    StepInputPackage inputPackage = StepInputPackage.builder().build();
    StepElementParameters steps = StepElementParameters.builder().spec(getParams()).build();
    azureARMRollbackStep.obtainTaskAfterRbac(azureHelperTest.getAmbiance(), steps, inputPackage);
    verifyStatic(StepUtils.class, times(1));
    StepUtils.prepareCDTaskRequest(
        any(), taskDataArgumentCaptor.capture(), any(), any(), any(), taskSelectorsArgumentCaptor.capture(), any());
    assertThat(taskDataArgumentCaptor.getValue()).isNotNull();
    assertThat(taskDataArgumentCaptor.getValue().getParameters()).isNotNull();
    AzureARMTaskNGParameters taskNGParameters =
        (AzureARMTaskNGParameters) taskDataArgumentCaptor.getValue().getParameters()[0];
    assertThat(taskNGParameters.getResourceGroupName()).isEqualTo("res");
    assertThat(taskNGParameters.getTemplateBody()).isEqualTo(AppSettingsFile.create("{foo}"));
    assertThat(taskNGParameters.getParametersBody()).isEqualTo(AppSettingsFile.create("{}"));
    assertThat(taskNGParameters.getSubscriptionId()).isEqualTo("123");
    assertThat(taskNGParameters.getDeploymentScope()).isEqualTo(ARMScopeType.RESOURCE_GROUP);
    assertThat(taskNGParameters.getDeploymentMode()).isEqualTo(AzureDeploymentMode.COMPLETE);
  }

  @Test
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  public void testValidateRollbackWithNoOutput() {
    when(azureCommonHelper.generateIdentifier(any(), any())).thenReturn("foobar");
    OptionalSweepingOutput output = OptionalSweepingOutput.builder().found(false).output(null).build();
    when(sweepingOutputService.resolveOptional(any(), any())).thenReturn(output);
    StepInputPackage inputPackage = StepInputPackage.builder().build();
    StepElementParameters steps = StepElementParameters.builder().spec(getParams()).build();
    assertThatThrownBy(
        () -> azureARMRollbackStep.obtainTaskAfterRbac(azureHelperTest.getAmbiance(), steps, inputPackage))
        .isInstanceOf(InvalidArgumentsException.class);
  }

  @Test
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  public void testValidateRollbackWithNoResourceGroupScope() {
    AzureARMTemplateDataOutput azureOutput = AzureARMTemplateDataOutput.builder()
                                                 .resourceGroup("res")
                                                 .resourceGroupTemplateJson("{foo}")
                                                 .scopeType("foobar")
                                                 .subscriptionId("123")
                                                 .build();
    OptionalSweepingOutput output = OptionalSweepingOutput.builder().found(true).output(azureOutput).build();
    when(sweepingOutputService.resolveOptional(any(), any())).thenReturn(output);
    StepInputPackage inputPackage = StepInputPackage.builder().build();
    StepElementParameters steps = StepElementParameters.builder().spec(getParams()).build();
    assertThatThrownBy(
        () -> azureARMRollbackStep.obtainTaskAfterRbac(azureHelperTest.getAmbiance(), steps, inputPackage))
        .isInstanceOf(InvalidArgumentsException.class);
  }

  @Test
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  public void testValidateRollbackWithNoResourceGroupID() {
    AzureARMTemplateDataOutput azureOutput = AzureARMTemplateDataOutput.builder()
                                                 .resourceGroupTemplateJson("{foo}")
                                                 .scopeType("ResourceGroup")
                                                 .subscriptionId("123")
                                                 .build();
    OptionalSweepingOutput output = OptionalSweepingOutput.builder().found(true).output(azureOutput).build();
    when(sweepingOutputService.resolveOptional(any(), any())).thenReturn(output);
    StepInputPackage inputPackage = StepInputPackage.builder().build();
    StepElementParameters steps = StepElementParameters.builder().spec(getParams()).build();
    assertThatThrownBy(
        () -> azureARMRollbackStep.obtainTaskAfterRbac(azureHelperTest.getAmbiance(), steps, inputPackage))
        .isInstanceOf(InvalidArgumentsException.class);
  }

  @Test
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  public void testValidateRollbackWithNoSubscriptionId() {
    AzureARMTemplateDataOutput azureOutput = AzureARMTemplateDataOutput.builder()
                                                 .resourceGroupTemplateJson("{foo}")
                                                 .scopeType("ResourceGroup")
                                                 .resourceGroup("123")
                                                 .build();
    OptionalSweepingOutput output = OptionalSweepingOutput.builder().found(true).output(azureOutput).build();
    when(sweepingOutputService.resolveOptional(any(), any())).thenReturn(output);
    StepInputPackage inputPackage = StepInputPackage.builder().build();
    StepElementParameters steps = StepElementParameters.builder().spec(getParams()).build();
    assertThatThrownBy(
        () -> azureARMRollbackStep.obtainTaskAfterRbac(azureHelperTest.getAmbiance(), steps, inputPackage))
        .isInstanceOf(InvalidArgumentsException.class);
  }

  private AzureARMRollbackStepParameters getParams() {
    return AzureARMRollbackStepParameters.infoBuilder()
        .provisionerIdentifier(ParameterField.createValueField("abc"))
        .build();
  }
}
