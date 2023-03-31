/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks.azure.arm.deployment;

import static io.harness.rule.OwnerRule.IVAN;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.azure.client.AzureAuthorizationClient;
import io.harness.azure.client.AzureBlueprintClient;
import io.harness.azure.model.AzureConfig;
import io.harness.azure.model.blueprint.Blueprint;
import io.harness.azure.model.blueprint.PublishedBlueprint;
import io.harness.azure.model.blueprint.artifact.Artifact;
import io.harness.azure.model.blueprint.assignment.Assignment;
import io.harness.azure.model.blueprint.assignment.WhoIsBlueprintContract;
import io.harness.category.element.UnitTests;
import io.harness.delegate.task.azure.arm.ARMDeploymentSteadyStateChecker;
import io.harness.delegate.task.azure.arm.AzureBlueprintDeploymentService;
import io.harness.delegate.task.azure.arm.deployment.context.DeploymentBlueprintSteadyStateContext;
import io.harness.delegate.task.azure.common.AzureLogCallbackProvider;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.runtime.azure.AzureBPDeploymentException;
import io.harness.logging.LogCallback;
import io.harness.rule.Owner;

import software.wings.delegatetasks.azure.arm.deployment.context.DeploymentBlueprintContext;

import com.azure.resourcemanager.authorization.AuthorizationManager;
import com.azure.resourcemanager.authorization.fluent.models.RoleAssignmentInner;
import com.azure.resourcemanager.authorization.models.BuiltInRole;
import com.azure.resourcemanager.authorization.models.RoleAssignment;
import com.azure.resourcemanager.network.models.ManagedServiceIdentity;
import com.azure.resourcemanager.network.models.ResourceIdentityType;
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

public class AzureBlueprintDeploymentServiceTest extends CategoryTest {
  public static final String CLIENT_ID = "CLIENT_ID";
  public static final String TENANT_ID = "TENANT_ID";
  public static final String KEY = "KEY";
  private static final String TEMPLATE_ARTIFACT_NAME = "TEMPLATE";
  private static final String TEMPLATE_ARTIFACT_JSON = "{template artifact content}";
  private static final String POLICY_ASSIGNMENT_ARTIFACT_NAME = "TEMPLATE";
  private static final String POLICY_ASSIGNMENT_ARTIFACT_JSON = "{policy assignment artifact content}";
  private static final String ROLE_ASSIGNMENT_ARTIFACT_NAME = "TEMPLATE";
  private static final String ROLE_ASSIGNMENT_ARTIFACT_JSON = "{role assignment artifact content}";
  public static final String ASSIGNMENT_NAME = "assignment-name";
  public static final String ASSIGNMENT_RESOURCE_SCOPE = "assignment-resource-scope";

  @Mock private AzureBlueprintClient azureBlueprintClient;
  @Mock private AzureAuthorizationClient azureAuthorizationClient;
  @Mock private ARMDeploymentSteadyStateChecker deploymentSteadyStateChecker;
  @Mock private AzureLogCallbackProvider mockLogStreamingTaskClient;
  @Mock private LogCallback mockLogCallback;
  @Spy @InjectMocks AzureBlueprintDeploymentService azureBlueprintDeploymentService;

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
  public void testDeployBlueprintAtResourceScopeWithoutPublishedVersion() {
    DeploymentBlueprintContext deploymentBlueprintContext = getDeploymentBlueprintContext();
    mockCheckExistingBlueprint(deploymentBlueprintContext);
    mockIsBlueprintPublishedWithoutPublishedBlueprint(deploymentBlueprintContext);
    mockCreateBlueprintDefinition(deploymentBlueprintContext);
    mockCreateArtifacts(deploymentBlueprintContext);
    mockPublishBlueprintDefinition(deploymentBlueprintContext);
    mockGrantAzureBlueprintsSPOwnerRole(deploymentBlueprintContext);
    mockCreateAssignment(deploymentBlueprintContext);
    mockPerformSteadyStateCheck();

    ArgumentCaptor<DeploymentBlueprintSteadyStateContext> deploymentContextCaptor =
        ArgumentCaptor.forClass(DeploymentBlueprintSteadyStateContext.class);

    azureBlueprintDeploymentService.deployBlueprintAtResourceScope(deploymentBlueprintContext);

    verify(deploymentSteadyStateChecker, times(1))
        .waitUntilCompleteWithTimeout(
            deploymentContextCaptor.capture(), any(AzureBlueprintClient.class), any(LogCallback.class));

    DeploymentBlueprintSteadyStateContext deploymentContextCaptorValue = deploymentContextCaptor.getValue();

    assertThat(deploymentContextCaptorValue).isNotNull();
    assertThat(deploymentContextCaptorValue.getAzureConfig()).isNotNull();
    assertThat(deploymentContextCaptorValue.getAssignmentName()).isNotNull();
    assertThat(deploymentContextCaptorValue.getAssignmentName()).isEqualTo(ASSIGNMENT_NAME);
    assertThat(deploymentContextCaptorValue.getAssignmentResourceScope()).isNotNull();
    assertThat(deploymentContextCaptorValue.getAssignmentResourceScope()).isEqualTo(ASSIGNMENT_RESOURCE_SCOPE);
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testDeployBlueprintAtResourceScopeWithPublishedVersion() {
    DeploymentBlueprintContext deploymentBlueprintContext = getDeploymentBlueprintContext();
    mockCheckExistingBlueprint(deploymentBlueprintContext);
    mockIsBlueprintPublishedWithPublishedBlueprint(deploymentBlueprintContext);
    mockGrantAzureBlueprintsSPOwnerRole(deploymentBlueprintContext);
    mockCreateAssignment(deploymentBlueprintContext);
    mockPerformSteadyStateCheck();

    ArgumentCaptor<DeploymentBlueprintSteadyStateContext> deploymentContextCaptor =
        ArgumentCaptor.forClass(DeploymentBlueprintSteadyStateContext.class);

    azureBlueprintDeploymentService.deployBlueprintAtResourceScope(deploymentBlueprintContext);

    verify(deploymentSteadyStateChecker, times(1))
        .waitUntilCompleteWithTimeout(
            deploymentContextCaptor.capture(), any(AzureBlueprintClient.class), any(LogCallback.class));

    DeploymentBlueprintSteadyStateContext deploymentContextCaptorValue = deploymentContextCaptor.getValue();

    assertThat(deploymentContextCaptorValue).isNotNull();
    assertThat(deploymentContextCaptorValue.getAzureConfig()).isNotNull();
    assertThat(deploymentContextCaptorValue.getAssignmentName()).isNotNull();
    assertThat(deploymentContextCaptorValue.getAssignmentName()).isEqualTo(ASSIGNMENT_NAME);
    assertThat(deploymentContextCaptorValue.getAssignmentResourceScope()).isNotNull();
    assertThat(deploymentContextCaptorValue.getAssignmentResourceScope()).isEqualTo(ASSIGNMENT_RESOURCE_SCOPE);
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testDeployBlueprintAtResourceScopeWithException() {
    DeploymentBlueprintContext deploymentBlueprintContext = getDeploymentBlueprintContext();

    doThrow(new InvalidRequestException("Unable to get blueprint by name"))
        .when(azureBlueprintClient)
        .getBlueprint(deploymentBlueprintContext.getAzureConfig(),
            deploymentBlueprintContext.getDefinitionResourceScope(), deploymentBlueprintContext.getBlueprintName());

    assertThatThrownBy(() -> azureBlueprintDeploymentService.deployBlueprintAtResourceScope(deploymentBlueprintContext))
        .isInstanceOf(AzureBPDeploymentException.class)
        .hasMessage(
            "Unable to deploy blueprint, Definition Scope: definition-resource-scope, Blueprint Name: blueprint-name,"
            + " Assignment Name: assignment-name, Assignment Scope: assignment-resource-scope,"
            + " Error msg: Unable to get blueprint by name");
  }

  private void mockCheckExistingBlueprint(DeploymentBlueprintContext deploymentBlueprintContext) {
    Blueprint blueprint = new Blueprint();
    blueprint.setName(deploymentBlueprintContext.getBlueprintName());

    doReturn(blueprint)
        .when(azureBlueprintClient)
        .getBlueprint(deploymentBlueprintContext.getAzureConfig(),
            deploymentBlueprintContext.getDefinitionResourceScope(), deploymentBlueprintContext.getBlueprintName());
  }

  private void mockIsBlueprintPublishedWithoutPublishedBlueprint(
      DeploymentBlueprintContext deploymentBlueprintContext) {
    doReturn(null)
        .when(azureBlueprintClient)
        .getPublishedBlueprintVersion(deploymentBlueprintContext.getAzureConfig(),
            deploymentBlueprintContext.getDefinitionResourceScope(), deploymentBlueprintContext.getBlueprintName(),
            deploymentBlueprintContext.getVersionId());
  }

  private void mockIsBlueprintPublishedWithPublishedBlueprint(DeploymentBlueprintContext deploymentBlueprintContext) {
    PublishedBlueprint publishedBlueprint = new PublishedBlueprint();
    publishedBlueprint.setName(deploymentBlueprintContext.getBlueprintName());
    doReturn(publishedBlueprint)
        .when(azureBlueprintClient)
        .getPublishedBlueprintVersion(deploymentBlueprintContext.getAzureConfig(),
            deploymentBlueprintContext.getDefinitionResourceScope(), deploymentBlueprintContext.getBlueprintName(),
            deploymentBlueprintContext.getVersionId());
  }

  private void mockCreateBlueprintDefinition(DeploymentBlueprintContext deploymentBlueprintContext) {
    Blueprint blueprint = new Blueprint();
    blueprint.setName(deploymentBlueprintContext.getBlueprintName());

    doReturn(blueprint)
        .when(azureBlueprintClient)
        .createOrUpdateBlueprint(deploymentBlueprintContext.getAzureConfig(),
            deploymentBlueprintContext.getDefinitionResourceScope(), deploymentBlueprintContext.getBlueprintName(),
            deploymentBlueprintContext.getBlueprintJSON());
  }

  private void mockCreateArtifacts(DeploymentBlueprintContext deploymentBlueprintContext) {
    Artifact templateArtifact = new Artifact();
    templateArtifact.setName(TEMPLATE_ARTIFACT_NAME);
    doReturn(templateArtifact)
        .when(azureBlueprintClient)
        .createOrUpdateArtifact(eq(deploymentBlueprintContext.getAzureConfig()),
            eq(deploymentBlueprintContext.getDefinitionResourceScope()),
            eq(deploymentBlueprintContext.getBlueprintName()), eq(TEMPLATE_ARTIFACT_NAME), eq(TEMPLATE_ARTIFACT_JSON));

    Artifact policyAssignmentArtifact = new Artifact();
    policyAssignmentArtifact.setName(POLICY_ASSIGNMENT_ARTIFACT_NAME);
    doReturn(policyAssignmentArtifact)
        .when(azureBlueprintClient)
        .createOrUpdateArtifact(eq(deploymentBlueprintContext.getAzureConfig()),
            eq(deploymentBlueprintContext.getDefinitionResourceScope()),
            eq(deploymentBlueprintContext.getBlueprintName()), eq(POLICY_ASSIGNMENT_ARTIFACT_NAME),
            eq(POLICY_ASSIGNMENT_ARTIFACT_JSON));

    Artifact roleAssignmentArtifact = new Artifact();
    roleAssignmentArtifact.setName(ROLE_ASSIGNMENT_ARTIFACT_NAME);
    doReturn(roleAssignmentArtifact)
        .when(azureBlueprintClient)
        .createOrUpdateArtifact(eq(deploymentBlueprintContext.getAzureConfig()),
            eq(deploymentBlueprintContext.getDefinitionResourceScope()),
            eq(deploymentBlueprintContext.getBlueprintName()), eq(ROLE_ASSIGNMENT_ARTIFACT_NAME),
            eq(ROLE_ASSIGNMENT_ARTIFACT_JSON));
  }

  private void mockPublishBlueprintDefinition(DeploymentBlueprintContext deploymentBlueprintContext) {
    PublishedBlueprint publishedBlueprint = new PublishedBlueprint();
    publishedBlueprint.setName(deploymentBlueprintContext.getBlueprintName());
    doReturn(publishedBlueprint)
        .when(azureBlueprintClient)
        .publishBlueprintDefinition(deploymentBlueprintContext.getAzureConfig(),
            deploymentBlueprintContext.getDefinitionResourceScope(), deploymentBlueprintContext.getBlueprintName(),
            deploymentBlueprintContext.getVersionId());
  }

  private void mockGrantAzureBlueprintsSPOwnerRole(DeploymentBlueprintContext deploymentBlueprintContext) {
    WhoIsBlueprintContract whoIsBlueprintContract = new WhoIsBlueprintContract();
    whoIsBlueprintContract.setObjectId("f71766dc-90d9-4b7d-bd9d-4499c4331c3f");

    String assignmentName = deploymentBlueprintContext.getAssignment().getName();
    doReturn(whoIsBlueprintContract)
        .when(azureBlueprintClient)
        .whoIsBlueprint(deploymentBlueprintContext.getAzureConfig(),
            deploymentBlueprintContext.getAssignmentResourceScope(), assignmentName);

    RoleAssignment roleAssignment =
        getRoleAssignment("role-id", "role-name", "role-principal-id", "role-definition-id");

    doReturn(roleAssignment)
        .when(azureAuthorizationClient)
        .roleAssignmentAtSubscriptionScope(deploymentBlueprintContext.getAzureConfig(),
            deploymentBlueprintContext.getAssignmentSubscriptionId(), whoIsBlueprintContract.getObjectId(),
            deploymentBlueprintContext.getRoleAssignmentName(), BuiltInRole.OWNER);
  }

  private void mockCreateAssignment(DeploymentBlueprintContext deploymentBlueprintContext) {
    Assignment assignment = new Assignment();
    String assignmentName = deploymentBlueprintContext.getAssignment().getName();
    assignment.setName(assignmentName);
    doReturn(assignment)
        .when(azureBlueprintClient)
        .beginCreateOrUpdateAssignment(deploymentBlueprintContext.getAzureConfig(),
            deploymentBlueprintContext.getAssignmentResourceScope(), assignmentName,
            deploymentBlueprintContext.getAssignmentJSON());
  }

  private void mockPerformSteadyStateCheck() {
    doNothing()
        .when(deploymentSteadyStateChecker)
        .waitUntilCompleteWithTimeout(
            any(DeploymentBlueprintSteadyStateContext.class), any(AzureBlueprintClient.class), any(LogCallback.class));
  }

  private DeploymentBlueprintContext getDeploymentBlueprintContext() {
    Assignment assignment = new Assignment();
    assignment.setName(ASSIGNMENT_NAME);
    ManagedServiceIdentity managedServiceIdentity = new ManagedServiceIdentity();
    managedServiceIdentity.withType(ResourceIdentityType.SYSTEM_ASSIGNED);
    assignment.setIdentity(managedServiceIdentity);
    Map<String, String> artifacts = new HashMap<>();
    artifacts.put(TEMPLATE_ARTIFACT_NAME, TEMPLATE_ARTIFACT_JSON);
    artifacts.put(POLICY_ASSIGNMENT_ARTIFACT_NAME, POLICY_ASSIGNMENT_ARTIFACT_JSON);
    artifacts.put(ROLE_ASSIGNMENT_ARTIFACT_NAME, ROLE_ASSIGNMENT_ARTIFACT_JSON);

    return DeploymentBlueprintContext.builder()
        .blueprintJSON("{blueprint json content}")
        .blueprintName("blueprint-name")
        .assignment(assignment)
        .assignmentJSON("{assignment json content}")
        .assignmentResourceScope(ASSIGNMENT_RESOURCE_SCOPE)
        .artifacts(artifacts)
        .assignmentSubscriptionId("assignment-subscription-id")
        .azureConfig(getAzureConfig())
        .versionId("version-id")
        .roleAssignmentName("role-assignment-name")
        .definitionResourceScope("definition-resource-scope")
        .steadyStateTimeoutInMin(20)
        .logStreamingTaskClient(mockLogStreamingTaskClient)
        .build();
  }

  private AzureConfig getAzureConfig() {
    return AzureConfig.builder().tenantId(TENANT_ID).clientId(CLIENT_ID).key(KEY.toCharArray()).build();
  }

  private RoleAssignment getRoleAssignment(
      final String id, final String name, final String principalId, final String roleDefinitionId) {
    return new RoleAssignment() {
      @Override
      public RoleAssignmentInner innerModel() {
        return null;
      }
      @Override
      public AuthorizationManager manager() {
        return null;
      }
      @Override
      public String scope() {
        return null;
      }
      @Override
      public String roleDefinitionId() {
        return roleDefinitionId;
      }
      @Override
      public String principalId() {
        return principalId;
      }
      @Override
      public String condition() {
        return null;
      }
      public String id() {
        return id;
      }
      @Override
      public String name() {
        return name;
      }
      @Override
      public String key() {
        return null;
      }
    };
  }
}
