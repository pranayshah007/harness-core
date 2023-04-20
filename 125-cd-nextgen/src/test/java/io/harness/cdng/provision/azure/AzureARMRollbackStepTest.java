/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.provision.azure;

import static io.harness.rule.OwnerRule.NGONZALEZ;
import static io.harness.rule.OwnerRule.TMACARI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.azure.model.ARMScopeType;
import io.harness.azure.model.AzureDeploymentMode;
import io.harness.beans.EnvironmentType;
import io.harness.category.element.UnitTests;
import io.harness.cdng.CDStepHelper;
import io.harness.cdng.provision.azure.beans.AzureARMConfig;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.delegate.TaskSelector;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.beans.connector.azureconnector.AzureConnectorDTO;
import io.harness.delegate.beans.logstreaming.UnitProgressData;
import io.harness.delegate.task.azure.appservice.settings.AppSettingsFile;
import io.harness.delegate.task.azure.arm.AzureARMPreDeploymentData;
import io.harness.delegate.task.azure.arm.AzureARMTaskNGParameters;
import io.harness.delegate.task.azure.arm.AzureARMTaskNGResponse;
import io.harness.logging.CommandExecutionStatus;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.execution.tasks.TaskRequest;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.steps.StepHelper;
import io.harness.steps.TaskRequestsUtils;

import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.CDP)
public class AzureARMRollbackStepTest extends CategoryTest {
  private AzureTestHelper azureHelperTest = new AzureTestHelper();
  @Mock private AzureCommonHelper azureCommonHelper;
  @Mock private AzureARMConfigDAL azureARMConfigDAL;
  @Mock private CDStepHelper cdStepHelper;
  @Mock private StepHelper stepHelper;
  @InjectMocks private AzureARMRollbackStep azureARMRollbackStep;

  @Before
  public void setUpMocks() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  public void testObtainTask() {
    AzureARMPreDeploymentData azureARMPreDeploymentData = AzureARMPreDeploymentData.builder()
                                                              .resourceGroup("res")
                                                              .resourceGroupTemplateJson("{foo}")
                                                              .subscriptionId("123")
                                                              .build();
    AzureARMConfig config = AzureARMConfig.builder()
                                .connectorRef("abc")
                                .provisionerIdentifier("123")
                                .azureARMPreDeploymentData(azureARMPreDeploymentData)
                                .scopeType(AzureScopeTypesNames.ResourceGroup)
                                .build();
    when(azureARMConfigDAL.getAzureARMConfig(any(), any())).thenReturn(config);
    AzureConnectorDTO azureConnectorDTO = AzureConnectorDTO.builder().build();
    when(cdStepHelper.getConnector(any(), any()))
        .thenReturn(ConnectorInfoDTO.builder().connectorConfig(azureConnectorDTO).build());
    List<EncryptedDataDetail> encryptedDataDetails = new ArrayList<>();
    when(azureCommonHelper.getAzureEncryptionDetails(any(), any())).thenReturn(encryptedDataDetails);
    when(stepHelper.getEnvironmentType(any())).thenReturn(EnvironmentType.NON_PROD);
    MockedStatic<TaskRequestsUtils> aStatic = Mockito.mockStatic(TaskRequestsUtils.class);
    when(TaskRequestsUtils.prepareCDTaskRequest(any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(TaskRequest.newBuilder().build());
    ArgumentCaptor<TaskData> taskDataArgumentCaptor = ArgumentCaptor.forClass(TaskData.class);

    ArgumentCaptor<List<TaskSelector>> taskSelectorsArgumentCaptor = ArgumentCaptor.forClass(List.class);
    StepInputPackage inputPackage = StepInputPackage.builder().build();
    StepElementParameters steps = StepElementParameters.builder().spec(getParams()).build();

    azureARMRollbackStep.obtainTaskAfterRbac(azureHelperTest.getAmbiance(), steps, inputPackage);

    verify(azureARMConfigDAL).getAzureARMConfig(any(), eq("abc"));
    aStatic.verify(()
                       -> TaskRequestsUtils.prepareCDTaskRequest(any(), taskDataArgumentCaptor.capture(), any(), any(),
                           any(), taskSelectorsArgumentCaptor.capture(), any()),
        times(1));
    assertThat(taskDataArgumentCaptor.getValue()).isNotNull();
    assertThat(taskDataArgumentCaptor.getValue().getParameters()).isNotNull();
    AzureARMTaskNGParameters taskNGParameters =
        (AzureARMTaskNGParameters) taskDataArgumentCaptor.getValue().getParameters()[0];
    assertThat(taskNGParameters.getResourceGroupName()).isEqualTo("res");
    assertThat(taskNGParameters.getTemplateBody()).isEqualTo(AppSettingsFile.create("{foo}"));
    assertThat(taskNGParameters.getSubscriptionId()).isEqualTo("123");
    assertThat(taskNGParameters.getDeploymentScope()).isEqualTo(ARMScopeType.RESOURCE_GROUP);
    assertThat(taskNGParameters.getDeploymentMode()).isEqualTo(AzureDeploymentMode.COMPLETE);
  }

  @Test
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  public void testValidateRollbackWithNoOutput() {
    StepInputPackage inputPackage = StepInputPackage.builder().build();
    StepElementParameters steps = StepElementParameters.builder().spec(getParams()).build();
    TaskRequest request = azureARMRollbackStep.obtainTaskAfterRbac(azureHelperTest.getAmbiance(), steps, inputPackage);
    assertThat(request.getSkipTaskRequest().getMessage())
        .isEqualTo("There is no rollback data saved for the provisioner identifier: abc");
  }

  @Test
  @Owner(developers = NGONZALEZ)
  @Category(UnitTests.class)
  public void testValidateRollbackWithNoResourceGroupScope() {
    doReturn(AzureARMConfig.builder().scopeType(AzureScopeTypesNames.Subscription).build())
        .when(azureARMConfigDAL)
        .getAzureARMConfig(any(), any());
    StepInputPackage inputPackage = StepInputPackage.builder().build();
    StepElementParameters steps = StepElementParameters.builder().spec(getParams()).build();

    TaskRequest request = azureARMRollbackStep.obtainTaskAfterRbac(azureHelperTest.getAmbiance(), steps, inputPackage);

    verify(azureARMConfigDAL).getAzureARMConfig(any(), eq("abc"));
    assertThat(request.getSkipTaskRequest().getMessage())
        .isEqualTo("The only scope allowed to do rollback is ResourceGroup. Subscription is not supported");
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testHandleTaskResultWithSecurityContext() throws Exception {
    StepElementParameters stepElementParameters = StepElementParameters.builder().spec(getParams()).build();
    AzureARMTaskNGResponse azureARMTaskNGResponse =
        AzureARMTaskNGResponse.builder()
            .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
            .unitProgressData(UnitProgressData.builder().unitProgresses(new ArrayList<>()).build())
            .build();

    azureARMRollbackStep.handleTaskResultWithSecurityContext(
        azureHelperTest.getAmbiance(), stepElementParameters, () -> azureARMTaskNGResponse);

    verify(azureARMConfigDAL).clearAzureARMConfig(any(), eq("abc"));
  }

  private AzureARMRollbackStepParameters getParams() {
    return AzureARMRollbackStepParameters.infoBuilder()
        .provisionerIdentifier(ParameterField.createValueField("abc"))
        .build();
  }
}
