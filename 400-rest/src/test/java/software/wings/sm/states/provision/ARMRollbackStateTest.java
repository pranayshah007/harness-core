/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.sm.states.provision;

import static io.harness.beans.ExecutionStatus.SUCCESS;
import static io.harness.rule.OwnerRule.ANIL;
import static io.harness.rule.OwnerRule.TATHAGAT;

import static org.assertj.core.api.Assertions.assertThat;
import static org.joor.Reflect.on;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.azure.model.ARMResourceType;
import io.harness.azure.model.ARMScopeType;
import io.harness.azure.model.AzureDeploymentMode;
import io.harness.beans.DelegateTask;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.beans.azure.AzureConfigDTO;
import io.harness.delegate.task.azure.arm.AzureARMPreDeploymentData;
import io.harness.delegate.task.azure.arm.request.AzureARMDeploymentParameters;
import io.harness.ff.FeatureFlagService;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.api.arm.ARMPreExistingTemplate;
import software.wings.beans.ARMInfrastructureProvisioner;
import software.wings.beans.Activity;
import software.wings.beans.AzureConfig;
import software.wings.beans.Environment;
import software.wings.service.impl.azure.manager.AzureTaskExecutionRequest;
import software.wings.service.intfc.DelegateService;
import software.wings.service.intfc.StateExecutionService;
import software.wings.sm.ExecutionContextImpl;
import software.wings.sm.ExecutionResponse;
import software.wings.sm.states.azure.AzureVMSSStateHelper;

import java.util.Collections;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@OwnedBy(HarnessTeam.CDP)
@TargetModule(HarnessModule._870_CG_ORCHESTRATION)
public class ARMRollbackStateTest extends WingsBaseTest {
  @Mock private ARMStateHelper helper;
  @Mock private DelegateService delegateService;
  @Mock private ExecutionContextImpl mockContext;
  @Mock private AzureVMSSStateHelper azureVMSSStateHelper;
  @Mock private StateExecutionService stateExecutionService;
  @Mock private FeatureFlagService featureFlagService;

  @InjectMocks private final ARMRollbackState armRollbackState = new ARMRollbackState("ARM Rollback");

  private static final String PROVISIONER_ID = "arm-id";
  private static final String APP_ID = "app-id";
  private static final String RESOURCE_GROUP = "anil-arm-test";
  private static final String SUBSCRIPTION_ID = "harness-arm-sub-id";
  private static final String ACTIVITY_ID = "activity-id";
  private static final String TIMEOUT_EXPRESSION = "20";
  private static final String CLOUD_PROVIDER_ID = "cloud-provider-id";
  private static final String ENVIRONMENT_UUID = "env-id";

  private static final String PRE_EXISTING_TEMPLATE = "{\n"
      + "  \"$schema\": \"https://schema.management.azure.com/schemas/2019-04-01/deploymentTemplate.json#\",\n"
      + "  \"contentVersion\": \"1.0.0.0\",\n"
      + "  \"parameters\": {\n"
      + "    \"logicAppName\": {\n"
      + "      \"type\": \"string\",\n"
      + "      \"metadata\": {\n"
      + "        \"description\": \"The name of the logic app to create.\"\n"
      + "      }\n"
      + "    },\n"
      + "    \"testUri\": {\n"
      + "      \"type\": \"string\",\n"
      + "      \"defaultValue\": \"https://status.azure.com/en-us/status/\",\n"
      + "      \"metadata\": {\n"
      + "        \"description\": \"A test URI\"\n"
      + "      }\n"
      + "    },\n"
      + "    \"location\": {\n"
      + "      \"type\": \"string\",\n"
      + "      \"defaultValue\": \"[resourceGroup().location]\",\n"
      + "      \"metadata\": {\n"
      + "        \"description\": \"Location for all resources.\"\n"
      + "      }\n"
      + "    }\n"
      + "  },\n"
      + "  \"variables\": {},\n"
      + "  \"resources\": [\n"
      + "    {\n"
      + "      \"type\": \"Microsoft.Logic/workflows\",\n"
      + "      \"apiVersion\": \"2019-05-01\",\n"
      + "      \"name\": \"[parameters('logicAppName')]\",\n"
      + "      \"location\": \"[parameters('location')]\",\n"
      + "      \"tags\": {\n"
      + "        \"displayName\": \"LogicApp\"\n"
      + "      },\n"
      + "      \"properties\": {\n"
      + "        \"definition\": {\n"
      + "          \"$schema\": \"https://schema.management.azure.com/providers/Microsoft.Logic/schemas/2016-06-01/workflowdefinition.json#\",\n"
      + "          \"contentVersion\": \"1.0.0.0\",\n"
      + "          \"parameters\": {\n"
      + "            \"testUri\": {\n"
      + "              \"type\": \"string\",\n"
      + "              \"defaultValue\": \"[parameters('testUri')]\"\n"
      + "            }\n"
      + "          },\n"
      + "          \"triggers\": {\n"
      + "            \"recurrence\": {\n"
      + "              \"type\": \"recurrence\",\n"
      + "              \"recurrence\": {\n"
      + "                \"frequency\": \"Hour\",\n"
      + "                \"interval\": 1\n"
      + "              }\n"
      + "            }\n"
      + "          },\n"
      + "          \"actions\": {\n"
      + "            \"http\": {\n"
      + "              \"type\": \"Http\",\n"
      + "              \"inputs\": {\n"
      + "                \"method\": \"GET\",\n"
      + "                \"uri\": \"@parameters('testUri')\"\n"
      + "              }\n"
      + "            }\n"
      + "          }\n"
      + "        }\n"
      + "      }\n"
      + "    }\n"
      + "  ]\n"
      + "}";

  @Before
  public void setup() {
    armRollbackState.setProvisionerId(PROVISIONER_ID);
    armRollbackState.setTimeoutExpression(TIMEOUT_EXPRESSION);
    armRollbackState.setCloudProviderId(CLOUD_PROVIDER_ID);

    Environment environment = Environment.Builder.anEnvironment().uuid(ENVIRONMENT_UUID).build();
    AzureConfig azureConfig = AzureConfig.builder().build();
    AzureConfigDTO azureConfigDTO = AzureConfigDTO.builder().build();

    doReturn(environment).when(mockContext).fetchRequiredEnvironment();
    doReturn(RESOURCE_GROUP).when(mockContext).renderExpression(eq(RESOURCE_GROUP));
    doReturn(APP_ID).when(mockContext).getAppId();
    doReturn(Activity.builder().uuid(ACTIVITY_ID).build())
        .when(helper)
        .createARMActivity(eq(mockContext), eq(false), anyString());
    doReturn(20).when(helper).renderTimeout(eq(TIMEOUT_EXPRESSION), eq(mockContext));

    doReturn(azureConfig).when(azureVMSSStateHelper).getAzureConfig(eq(CLOUD_PROVIDER_ID));
    doReturn(Collections.emptyList())
        .when(azureVMSSStateHelper)
        .getEncryptedDataDetails(eq(mockContext), eq(CLOUD_PROVIDER_ID));
    doReturn(azureConfigDTO).when(azureVMSSStateHelper).createAzureConfigDTO(eq(azureConfig));
    doNothing().when(stateExecutionService).appendDelegateTaskDetails(anyString(), any());
  }

  @Test
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void testARMRollbackProvisionerNotFound() {
    ARMRollbackState armRollbackStateNotProvisioner = new ARMRollbackState("ARM Rollback not found");
    armRollbackStateNotProvisioner.setProvisionerId("testId");
    on(armRollbackStateNotProvisioner).set("helper", helper);
    ARMInfrastructureProvisioner armInfrastructureProvisioner = getArmInfrastructureProvisioner();
    doReturn(armInfrastructureProvisioner).when(helper).getProvisioner(eq(APP_ID), eq(PROVISIONER_ID));

    ExecutionResponse response = armRollbackStateNotProvisioner.execute(mockContext);
    assertThat(response).isNotNull();
    assertThat(response.getExecutionStatus()).isEqualTo(SUCCESS);
    assertThat(response.getErrorMessage()).contains("No ARM Provisioner or scope found for provisioner id - [testId]");

    armRollbackStateNotProvisioner.setProvisionerId(PROVISIONER_ID);
    response = armRollbackStateNotProvisioner.execute(mockContext);
    assertThat(response).isNotNull();
    assertThat(response.getExecutionStatus()).isEqualTo(SUCCESS);
    assertThat(response.getErrorMessage()).contains("No ARM Provisioner or scope found for provisioner id - [arm-id]");
  }

  @Test
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void testARMRollbackNotSupported() {
    ARMInfrastructureProvisioner armInfrastructureProvisioner = getArmInfrastructureProvisioner();
    doReturn(armInfrastructureProvisioner).when(helper).getProvisioner(eq(APP_ID), eq(PROVISIONER_ID));

    armInfrastructureProvisioner.setScopeType(ARMScopeType.SUBSCRIPTION);

    ExecutionResponse response = armRollbackState.execute(mockContext);
    assertThat(response).isNotNull();
    assertThat(response.getExecutionStatus()).isEqualTo(SUCCESS);
    assertThat(response.getErrorMessage())
        .contains("ARM rollback is supported only for Resource Group scope. Current scope is - [SUBSCRIPTION]");

    armInfrastructureProvisioner.setScopeType(ARMScopeType.MANAGEMENT_GROUP);
    response = armRollbackState.execute(mockContext);
    assertThat(response).isNotNull();
    assertThat(response.getExecutionStatus()).isEqualTo(SUCCESS);
    assertThat(response.getErrorMessage())
        .contains("ARM rollback is supported only for Resource Group scope. Current scope is - [MANAGEMENT_GROUP]");

    armInfrastructureProvisioner.setScopeType(ARMScopeType.TENANT);
    response = armRollbackState.execute(mockContext);
    assertThat(response).isNotNull();
    assertThat(response.getExecutionStatus()).isEqualTo(SUCCESS);
    assertThat(response.getErrorMessage())
        .contains("ARM rollback is supported only for Resource Group scope. Current scope is - [TENANT]");

    armInfrastructureProvisioner.setScopeType(ARMScopeType.MANAGEMENT_GROUP);
    armInfrastructureProvisioner.setResourceType(ARMResourceType.BLUEPRINT);
    response = armRollbackState.execute(mockContext);
    assertThat(response).isNotNull();
    assertThat(response.getExecutionStatus()).isEqualTo(SUCCESS);
    assertThat(response.getErrorMessage()).contains("Azure Blueprints rollback is not supported");

    armInfrastructureProvisioner.setScopeType(ARMScopeType.SUBSCRIPTION);
    armInfrastructureProvisioner.setResourceType(ARMResourceType.BLUEPRINT);
    response = armRollbackState.execute(mockContext);
    assertThat(response).isNotNull();
    assertThat(response.getExecutionStatus()).isEqualTo(SUCCESS);
    assertThat(response.getErrorMessage()).contains("Azure Blueprints rollback is not supported");
  }

  @Test
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void testARMRollbackNoPreviousExistingTemplate() {
    armRollbackState.setResourceGroupExpression(RESOURCE_GROUP);
    armRollbackState.setSubscriptionExpression(SUBSCRIPTION_ID);

    ARMInfrastructureProvisioner armInfrastructureProvisioner = getArmInfrastructureProvisioner();
    armInfrastructureProvisioner.setScopeType(ARMScopeType.RESOURCE_GROUP);
    doReturn(armInfrastructureProvisioner).when(helper).getProvisioner(eq(APP_ID), eq(PROVISIONER_ID));

    ExecutionResponse response = armRollbackState.execute(mockContext);
    assertThat(response).isNotNull();
    assertThat(response.getExecutionStatus()).isEqualTo(SUCCESS);
    assertThat(response.getErrorMessage())
        .contains("Skipping rollback as no previous template found for resource group - [anil-arm-test]");

    doReturn(ARMPreExistingTemplate.builder().build())
        .when(helper)
        .getPreExistingTemplate(eq(PROVISIONER_ID + "-" + SUBSCRIPTION_ID + "-" + RESOURCE_GROUP), eq(mockContext));
    armRollbackState.execute(mockContext);
    assertThat(response).isNotNull();
    assertThat(response.getExecutionStatus()).isEqualTo(SUCCESS);
    assertThat(response.getErrorMessage())
        .contains("Skipping rollback as no previous template found for resource group - [anil-arm-test]");
  }

  @Test
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void testARMRollbackNotValidPreDeploymentData() {
    armRollbackState.setResourceGroupExpression(RESOURCE_GROUP);
    armRollbackState.setSubscriptionExpression(SUBSCRIPTION_ID);
    ARMInfrastructureProvisioner armInfrastructureProvisioner = getArmInfrastructureProvisioner();
    armInfrastructureProvisioner.setScopeType(ARMScopeType.RESOURCE_GROUP);
    doReturn(armInfrastructureProvisioner).when(helper).getProvisioner(eq(APP_ID), eq(PROVISIONER_ID));

    AzureARMPreDeploymentData preDeploymentData = AzureARMPreDeploymentData.builder().build();
    ARMPreExistingTemplate armPreExistingTemplate =
        ARMPreExistingTemplate.builder().preDeploymentData(preDeploymentData).build();
    doReturn(armPreExistingTemplate)
        .when(helper)
        .getPreExistingTemplate(eq(PROVISIONER_ID + "-" + SUBSCRIPTION_ID + "-" + RESOURCE_GROUP), eq(mockContext));

    ExecutionResponse response = armRollbackState.execute(mockContext);
    assertThat(response).isNotNull();
    assertThat(response.getExecutionStatus()).isEqualTo(SUCCESS);
    assertThat(response.getErrorMessage())
        .contains("Skipping rollback as subscription id is empty for Provisioner - [ARM-Provisioner]");

    preDeploymentData.setSubscriptionId(SUBSCRIPTION_ID);
    response = armRollbackState.execute(mockContext);
    assertThat(response).isNotNull();
    assertThat(response.getExecutionStatus()).isEqualTo(SUCCESS);
    assertThat(response.getErrorMessage())
        .contains("Skipping rollback as resource group is empty for Provisioner - [ARM-Provisioner]");

    preDeploymentData.setResourceGroup(RESOURCE_GROUP);
    response = armRollbackState.execute(mockContext);
    assertThat(response).isNotNull();
    assertThat(response.getExecutionStatus()).isEqualTo(SUCCESS);
    assertThat(response.getErrorMessage())
        .contains("Skipping rollback as no previous template found for resource group - [anil-arm-test]");
  }

  @Test
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void testARMRollbackExecute() {
    armRollbackState.setResourceGroupExpression(RESOURCE_GROUP);
    armRollbackState.setSubscriptionExpression(SUBSCRIPTION_ID);
    ARMInfrastructureProvisioner armInfrastructureProvisioner = getArmInfrastructureProvisioner();
    armInfrastructureProvisioner.setScopeType(ARMScopeType.RESOURCE_GROUP);
    doReturn(armInfrastructureProvisioner).when(helper).getProvisioner(eq(APP_ID), eq(PROVISIONER_ID));

    AzureARMPreDeploymentData preDeploymentData = AzureARMPreDeploymentData.builder()
                                                      .resourceGroup(RESOURCE_GROUP)
                                                      .subscriptionId(SUBSCRIPTION_ID)
                                                      .resourceGroupTemplateJson(PRE_EXISTING_TEMPLATE)
                                                      .build();
    ARMPreExistingTemplate armPreExistingTemplate =
        ARMPreExistingTemplate.builder().preDeploymentData(preDeploymentData).build();

    doReturn(armPreExistingTemplate)
        .when(helper)
        .getPreExistingTemplate(eq(PROVISIONER_ID + "-" + SUBSCRIPTION_ID + "-" + RESOURCE_GROUP), eq(mockContext));

    ArgumentCaptor<DelegateTask> delegateTaskArgumentCaptor = ArgumentCaptor.forClass(DelegateTask.class);

    ExecutionResponse response = armRollbackState.executeInternal(mockContext);
    verify(delegateService, times(1)).queueTaskV2(delegateTaskArgumentCaptor.capture());
    DelegateTask delegateTask = delegateTaskArgumentCaptor.getValue();

    assertThat(response).isNotNull();
    assertThat(response.getExecutionStatus()).isEqualTo(SUCCESS);
    assertThat(response.getErrorMessage()).isNull();
    assertThat(delegateTask).isNotNull();

    TaskData taskData = delegateTask.getData();
    assertThat(taskData.getParameters().length).isEqualTo(1);
    assertThat(taskData.getParameters()[0]).isInstanceOf(AzureTaskExecutionRequest.class);

    AzureTaskExecutionRequest request = (AzureTaskExecutionRequest) taskData.getParameters()[0];
    assertThat(request.getAzureTaskParameters()).isInstanceOf(AzureARMDeploymentParameters.class);

    AzureARMDeploymentParameters azureTaskParameters = (AzureARMDeploymentParameters) request.getAzureTaskParameters();
    assertThat(azureTaskParameters.isRollback()).isTrue();
    assertThat(azureTaskParameters.getTimeoutIntervalInMin()).isEqualTo(Integer.valueOf(TIMEOUT_EXPRESSION));
    assertThat(azureTaskParameters.getDeploymentScope()).isEqualTo(ARMScopeType.RESOURCE_GROUP);
    assertThat(azureTaskParameters.getDeploymentMode()).isEqualTo(AzureDeploymentMode.COMPLETE);
    assertThat(azureTaskParameters.getResourceGroupName()).isEqualTo(RESOURCE_GROUP);
    assertThat(azureTaskParameters.getSubscriptionId()).isEqualTo(SUBSCRIPTION_ID);
    assertThat(azureTaskParameters.getTemplateJson()).isNotNull();
    assertThat(azureTaskParameters.getTemplateJson()).isEqualTo(PRE_EXISTING_TEMPLATE);
  }

  private ARMInfrastructureProvisioner getArmInfrastructureProvisioner() {
    return ARMInfrastructureProvisioner.builder().uuid(PROVISIONER_ID).name("ARM-Provisioner").build();
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testValidation() {
    // provision state
    ARMProvisionState armProvisionState = new ARMProvisionState("ARM Provision");
    assertThat(armProvisionState.validateFields().size()).isEqualTo(1);
    armProvisionState.setProvisionerId("test provisioner");
    assertThat(armProvisionState.validateFields().size()).isEqualTo(0);

    // rollback test
    ARMRollbackState armRollbackState = new ARMRollbackState("ARM Rollback");
    assertThat(armRollbackState.validateFields().size()).isEqualTo(1);
    armRollbackState.setProvisionerId("test provisioner");
    assertThat(armRollbackState.validateFields().size()).isEqualTo(0);
  }
}
