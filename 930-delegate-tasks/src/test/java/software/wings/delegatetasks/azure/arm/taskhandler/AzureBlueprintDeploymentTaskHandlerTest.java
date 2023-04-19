/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks.azure.arm.taskhandler;

import static io.harness.rule.OwnerRule.IVAN;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.azure.model.AzureConfig;
import io.harness.category.element.UnitTests;
import io.harness.delegate.task.azure.arm.AzureARMTaskResponse;
import io.harness.delegate.task.azure.arm.AzureARMTaskType;
import io.harness.delegate.task.azure.arm.AzureBlueprintDeploymentService;
import io.harness.delegate.task.azure.arm.request.AzureBlueprintDeploymentParameters;
import io.harness.delegate.task.azure.arm.response.AzureBlueprintDeploymentResponse;
import io.harness.delegate.task.azure.common.AzureLogCallbackProvider;
import io.harness.logging.LogCallback;
import io.harness.rule.Owner;
import io.harness.serializer.JsonUtils;

import software.wings.delegatetasks.azure.arm.deployment.context.DeploymentBlueprintContext;

import com.azure.resourcemanager.network.models.ResourceIdentityType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.HashMap;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

public class AzureBlueprintDeploymentTaskHandlerTest extends CategoryTest {
  public static final String CLIENT_ID = "CLIENT_ID";
  public static final String TENANT_ID = "TENANT_ID";
  public static final String KEY = "KEY";

  @Mock private AzureLogCallbackProvider mockLogStreamingTaskClient;
  @Mock private LogCallback mockLogCallback;
  @Mock private AzureBlueprintDeploymentService azureBlueprintDeploymentService;

  @Spy @InjectMocks AzureBlueprintDeploymentTaskHandler azureBlueprintDeploymentTaskHandler;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
    doReturn(mockLogCallback).when(mockLogStreamingTaskClient).obtainLogCallback(anyString());
    doNothing().when(mockLogCallback).saveExecutionLog(anyString(), any(), any());
    doNothing().when(mockLogCallback).saveExecutionLog(anyString(), any());
    doNothing().when(mockLogCallback).saveExecutionLog(anyString());
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testExecuteTaskInternalAtResourceGroupScope() {
    AzureConfig azureConfig = buildAzureConfig();

    AzureBlueprintDeploymentParameters blueprintDeploymentParameters = getAzureBlueprintDeploymentParameters();

    ArgumentCaptor<DeploymentBlueprintContext> deploymentContextCaptor =
        ArgumentCaptor.forClass(DeploymentBlueprintContext.class);
    doNothing().when(azureBlueprintDeploymentService).deployBlueprintAtResourceScope(any());

    AzureARMTaskResponse azureARMTaskResponse = azureBlueprintDeploymentTaskHandler.executeTaskInternal(
        blueprintDeploymentParameters, azureConfig, mockLogStreamingTaskClient);

    verify(azureBlueprintDeploymentService, times(1)).deployBlueprintAtResourceScope(deploymentContextCaptor.capture());

    DeploymentBlueprintContext capturedDeploymentBlueprintContext = deploymentContextCaptor.getValue();

    assertThat(azureARMTaskResponse).isNotNull();
    assertThat(azureARMTaskResponse).isInstanceOf(AzureBlueprintDeploymentResponse.class);
    assertThat(capturedDeploymentBlueprintContext).isNotNull();
    assertThat(capturedDeploymentBlueprintContext.getAzureConfig()).isNotNull();
    assertThat(capturedDeploymentBlueprintContext.getDefinitionResourceScope())
        .isEqualTo("/providers/Microsoft.Management/managementGroups/HarnessARMTest");
    assertThat(capturedDeploymentBlueprintContext.getBlueprintName()).isEqualTo("101-boilerplate-mng");
    assertThat(capturedDeploymentBlueprintContext.getBlueprintJSON()).isNotNull();
    assertThat(capturedDeploymentBlueprintContext.getBlueprintJSON()).contains("genericBlueprintParameter");

    assertThat(capturedDeploymentBlueprintContext.getArtifacts()).isNotNull();
    assertThat(capturedDeploymentBlueprintContext.getArtifacts().size()).isEqualTo(3);

    assertThat(capturedDeploymentBlueprintContext.getArtifacts().get("policyAssignment")).contains("policyAssignment");
    assertThat(capturedDeploymentBlueprintContext.getArtifacts().get("rbacAssignment")).contains("rbacAssignment");
    assertThat(capturedDeploymentBlueprintContext.getArtifacts().get("myTemplate")).contains("myTemplate");

    assertThat(capturedDeploymentBlueprintContext.getAssignment()).isNotNull();
    assertThat(capturedDeploymentBlueprintContext.getAssignment().getName())
        .startsWith("Assignment-101-boilerplate-mng");
    assertThat(capturedDeploymentBlueprintContext.getAssignment().getIdentity().type())
        .isEqualTo(ResourceIdentityType.SYSTEM_ASSIGNED);
    assertThat(capturedDeploymentBlueprintContext.getAssignment().getName())
        .startsWith("Assignment-101-boilerplate-mng");
    assertThat(capturedDeploymentBlueprintContext.getAssignmentJSON()).contains("569613dc-7ad8-4fad-ab03-d3117fcb6298");

    assertThat(capturedDeploymentBlueprintContext.getRoleAssignmentName()).isNotEmpty();
    assertThat(capturedDeploymentBlueprintContext.getAssignmentSubscriptionId())
        .isEqualTo("5aef6b46-7daa-45ea-a8d0-783aab69dea3");

    assertThat(capturedDeploymentBlueprintContext.getAssignmentResourceScope())
        .isEqualTo("/subscriptions/5aef6b46-7daa-45ea-a8d0-783aab69dea3");
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testExecuteTaskInternalWithNotValidBlueprintId() {
    AzureConfig azureConfig = buildAzureConfig();
    AzureBlueprintDeploymentParameters blueprintDeploymentParameters = getAzureBlueprintDeploymentParameters();
    JsonNode assignmentJson = JsonUtils.readTree(blueprintDeploymentParameters.getAssignmentJson());
    JsonNode properties = assignmentJson.get("properties");
    ((ObjectNode) properties)
        .put("blueprintId",
            "/providers/Microsoft.Management/managementGroups/HarnessARMTest/providers/Microsoft.Blueprint/blueprints");
    blueprintDeploymentParameters.setAssignmentJson(assignmentJson.toString());

    AzureARMTaskResponse azureARMTaskResponse = azureBlueprintDeploymentTaskHandler.executeTaskInternal(
        blueprintDeploymentParameters, azureConfig, mockLogStreamingTaskClient);

    assertThat(azureARMTaskResponse).isNotNull();
    assertThat(azureARMTaskResponse).isInstanceOf(AzureBlueprintDeploymentResponse.class);
    assertThat(azureARMTaskResponse.getErrorMsg())
        .isEqualTo("Not valid value of properties.blueprintId property. "
            + "Required format /{resourceScope}/providers/Microsoft.Blueprint/blueprints/{blueprintName}/versions/{versionId}, "
            + "but found - blueprintId: /providers/Microsoft.Management/managementGroups/HarnessARMTest/providers/Microsoft.Blueprint/blueprints");
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testExecuteTaskInternalWithEmptyScope() {
    AzureConfig azureConfig = buildAzureConfig();
    AzureBlueprintDeploymentParameters blueprintDeploymentParameters = getAzureBlueprintDeploymentParameters();
    JsonNode assignmentJson = JsonUtils.readTree(blueprintDeploymentParameters.getAssignmentJson());
    JsonNode properties = assignmentJson.get("properties");
    ((ObjectNode) properties).put("scope", "");
    blueprintDeploymentParameters.setAssignmentJson(assignmentJson.toString());

    AzureARMTaskResponse azureARMTaskResponse = azureBlueprintDeploymentTaskHandler.executeTaskInternal(
        blueprintDeploymentParameters, azureConfig, mockLogStreamingTaskClient);

    assertThat(azureARMTaskResponse).isNotNull();
    assertThat(azureARMTaskResponse).isInstanceOf(AzureBlueprintDeploymentResponse.class);
    assertThat(azureARMTaskResponse.getErrorMsg())
        .isEqualTo("Assignment properties.scope cannot be null or empty for management group resource scope");
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testExecuteTaskInternalWithNotValidScope() {
    AzureConfig azureConfig = buildAzureConfig();
    AzureBlueprintDeploymentParameters blueprintDeploymentParameters = getAzureBlueprintDeploymentParameters();
    JsonNode assignmentJson = JsonUtils.readTree(blueprintDeploymentParameters.getAssignmentJson());
    JsonNode properties = assignmentJson.get("properties");
    ((ObjectNode) properties).put("scope", "/subscriptions");
    blueprintDeploymentParameters.setAssignmentJson(assignmentJson.toString());

    AzureARMTaskResponse azureARMTaskResponse = azureBlueprintDeploymentTaskHandler.executeTaskInternal(
        blueprintDeploymentParameters, azureConfig, mockLogStreamingTaskClient);

    assertThat(azureARMTaskResponse).isNotNull();
    assertThat(azureARMTaskResponse).isInstanceOf(AzureBlueprintDeploymentResponse.class);
    assertThat(azureARMTaskResponse.getErrorMsg())
        .isEqualTo("Not valid value of properties.scope property. "
            + "Required format /subscriptions/{subscriptionId}, but found - scope: /subscriptions");
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testExecuteTaskInternalWithNotValidAssignJson() {
    AzureConfig azureConfig = buildAzureConfig();
    AzureBlueprintDeploymentParameters blueprintDeploymentParameters = getAzureBlueprintDeploymentParameters();
    blueprintDeploymentParameters.setAssignmentJson("{not valid assign json content}");

    AzureARMTaskResponse azureARMTaskResponse = azureBlueprintDeploymentTaskHandler.executeTaskInternal(
        blueprintDeploymentParameters, azureConfig, mockLogStreamingTaskClient);

    assertThat(azureARMTaskResponse).isNotNull();
    assertThat(azureARMTaskResponse).isInstanceOf(AzureBlueprintDeploymentResponse.class);
    assertThat(azureARMTaskResponse.getErrorMsg()).contains("JsonParseException");
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testExecuteTaskInternalWithNotValidBlueprintJson() {
    AzureConfig azureConfig = buildAzureConfig();
    AzureBlueprintDeploymentParameters blueprintDeploymentParameters = getAzureBlueprintDeploymentParameters();
    blueprintDeploymentParameters.setBlueprintJson("{not valid blueprint json content}");

    AzureARMTaskResponse azureARMTaskResponse = azureBlueprintDeploymentTaskHandler.executeTaskInternal(
        blueprintDeploymentParameters, azureConfig, mockLogStreamingTaskClient);

    assertThat(azureARMTaskResponse).isNotNull();
    assertThat(azureARMTaskResponse).isInstanceOf(AzureBlueprintDeploymentResponse.class);
    assertThat(azureARMTaskResponse.getErrorMsg()).isEqualTo("Invalid Blueprint JSON file");
  }

  private AzureBlueprintDeploymentParameters getAzureBlueprintDeploymentParameters() {
    return AzureBlueprintDeploymentParameters.builder()
        .accountId("ACCOUNT_ID")
        .activityId("ACTIVITY_ID")
        .appId("APP_ID")
        .blueprintJson(getBlueprintJson())
        .assignmentJson(getAssignmentJson())
        .artifacts(getArtifacts())
        .commandName(AzureARMTaskType.BLUEPRINT_DEPLOYMENT.name())
        .timeoutIntervalInMin(20)
        .build();
  }

  private String getBlueprintJson() {
    return "{\n"
        + "    \"properties\": {\n"
        + "        \"description\": \"This will be displayed in the essentials, so make it good\",\n"
        + "        \"targetScope\": \"subscription\",\n"
        + "        \"parameters\": { \n"
        + "            \"principalIds\": {\n"
        + "                \"type\": \"string\", \n"
        + "                \"metadata\": {\n"
        + "                    \"displayName\": \"Principal IDs\",\n"
        + "                    \"description\": \"This is a blueprint parameter that any artifact can reference.\",\n"
        + "                    \"strongType\": \"PrincipalId\"\n"
        + "                }\n"
        + "            },\n"
        + "            \"genericBlueprintParameter\": {\n"
        + "                \"type\": \"string\",\n"
        + "                \"defaultValue\": \"MyDefaultParamValue\"\n"
        + "            }\n"
        + "        },\n"
        + "        \"resourceGroups\": {\n"
        + "            \"SingleRG\": {\n"
        + "                \"description\": \"An optional description for your RG artifact.\"\n"
        + "            }\n"
        + "        }\n"
        + "    },\n"
        + "    \"type\": \"Microsoft.Blueprint/blueprints\" \n"
        + "}";
  }
  private String getAssignmentJson() {
    return "{\n"
        + "    \"identity\": {\n"
        + "      \"type\": \"SystemAssigned\"\n"
        + "    },\n"
        + "    \"location\": \"westus2\",\n"
        + "    \"properties\": {\n"
        + "      \"blueprintId\": \"/providers/Microsoft.Management/managementGroups/HarnessARMTest/providers/Microsoft.Blueprint/blueprints/101-boilerplate-mng/versions/v1\",\n"
        + "      \"resourceGroups\": {\n"
        + "        \"SingleRG\": {\n"
        + "          \"name\": \"mng-001\",\n"
        + "          \"location\": \"eastus\"\n"
        + "        }\n"
        + "      },\n"
        + "      \"locks\": {\n"
        + "        \"mode\": \"none\"\n"
        + "      },\n"
        + "      \"parameters\": {\n"
        + "        \"principalIds\": {\n"
        + "          \"value\": \"569613dc-7ad8-4fad-ab03-d3117fcb6298\"\n"
        + "        },\n"
        + "        \"genericBlueprintParameter\": {\n"
        + "          \"value\": \"test\"\n"
        + "        }\n"
        + "      },\n"
        + "      \"scope\": \"/subscriptions/5aef6b46-7daa-45ea-a8d0-783aab69dea3\"\n"
        + "    }\n"
        + "  }";
  }

  private Map<String, String> getArtifacts() {
    Map<String, String> artifacts = new HashMap<>();
    artifacts.put("policyAssignment",
        "{\n"
            + "    \"properties\": {\n"
            + "        \"policyDefinitionId\": \"/providers/Microsoft.Authorization/policyDefinitions/a451c1ef-c6ca-483d-87ed-f49761e3ffb5\",\n"
            + "        \"parameters\": {},\n"
            + "        \"resourceGroup\": \"SingleRG\",\n"
            + "        \"dependsOn\": [],\n"
            + "        \"displayName\": \"My Policy Definition that will be assigned (Currently auditing usage of custom roles)\",\n"
            + "        \"metadata\": {\n"
            + "            \"COMMENT\": \"specify the policy definition ID. Can be either custom or built-in. This boilerplate example points to the built-in 'allowed locations' policy\"\n"
            + "        }\n"
            + "    },\n"
            + "    \"kind\": \"policyAssignment\",\n"
            + "    \"type\": \"Microsoft.Blueprint/blueprints/artifacts\"\n"
            + "}\n");
    artifacts.put("rbacAssignment",
        "{\n"
            + "    \"kind\": \"roleAssignment\",\n"
            + "    \"properties\": {\n"
            + "        \"roleDefinitionId\": \"/providers/Microsoft.Authorization/roleDefinitions/8e3af657-a8ff-443c-a75c-2fe8c4bcb635\",\n"
            + "        \"principalIds\": [\"[parameters('principalIds')]\"],\n"
            + "        \"displayName\": \"<user or group TBD> : Owner\"\n"
            + "    },\n"
            + "    \"type\": \"Microsoft.Blueprint/blueprints/artifacts\",\n"
            + "    \"name\": \"rbacAssignment\"\n"
            + "}");
    artifacts.put("template",
        "{\n"
            + "    \"kind\": \"template\",\n"
            + "    \"properties\": {\n"
            + "      \"dependsOn\": [\"policyAssignment\"],\n"
            + "      \"template\": {\n"
            + "          \"$schema\": \"https://schema.management.azure.com/schemas/2015-01-01/deploymentTemplate.json#\",\n"
            + "          \"contentVersion\": \"1.0.0.0\",\n"
            + "          \"parameters\": {\n"
            + "            \"myTemplateParameter\": {\n"
            + "              \"type\": \"string\",\n"
            + "              \"metadata\": {\n"
            + "                \"displayName\": \"single template parameter\"\n"
            + "              }\n"
            + "            }\n"
            + "          },\n"
            + "          \"variables\": {},\n"
            + "          \"resources\": []\n"
            + "      },\n"
            + "      \"resourceGroup\": \"SingleRG\",\n"
            + "      \"displayName\": \"My ARM Template\",\n"
            + "      \"parameters\": {\n"
            + "        \"myTemplateParameter\": {\n"
            + "          \"value\": \"[parameters('genericBlueprintParameter')]\"\n"
            + "        }\n"
            + "      }\n"
            + "    },\n"
            + "    \"type\": \"Microsoft.Blueprint/blueprints/artifacts\",\n"
            + "    \"name\": \"myTemplate\"\n"
            + "}");

    return artifacts;
  }

  private AzureConfig buildAzureConfig() {
    return AzureConfig.builder().clientId(CLIENT_ID).key(KEY.toCharArray()).tenantId(TENANT_ID).build();
  }
}
