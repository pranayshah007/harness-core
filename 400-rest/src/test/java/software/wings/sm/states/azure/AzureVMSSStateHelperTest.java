/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.sm.states.azure;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.azure.AzureEnvironmentType.AZURE;
import static io.harness.beans.ExecutionStatus.RUNNING;
import static io.harness.beans.ExecutionStatus.SUCCESS;
import static io.harness.beans.OrchestrationWorkflowType.BLUE_GREEN;
import static io.harness.context.ContextElementType.AZURE_VMSS_SETUP;
import static io.harness.delegate.beans.azure.AzureMachineImageArtifactDTO.ImageType.IMAGE_GALLERY;
import static io.harness.delegate.beans.azure.AzureMachineImageArtifactDTO.OSType.LINUX;

import static software.wings.beans.Environment.Builder.anEnvironment;
import static software.wings.persistence.artifact.Artifact.Builder.anArtifact;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.azure.model.AzureAppServiceApplicationSetting;
import io.harness.azure.model.AzureAppServiceConnectionString;
import io.harness.azure.model.AzureAppServiceConnectionStringType;
import io.harness.azure.model.AzureConstants;
import io.harness.beans.EmbeddedUser;
import io.harness.beans.EncryptedData;
import io.harness.beans.ExecutionStatus;
import io.harness.category.element.UnitTests;
import io.harness.context.ContextElementType;
import io.harness.delegate.beans.azure.AzureConfigDTO;
import io.harness.delegate.beans.azure.AzureMachineImageArtifactDTO;
import io.harness.delegate.beans.azure.AzureVMAuthDTO;
import io.harness.delegate.beans.azure.AzureVMAuthType;
import io.harness.delegate.task.azure.AzureTaskExecutionResponse;
import io.harness.delegate.task.azure.appservice.webapp.response.AzureAppDeploymentData;
import io.harness.delegate.task.azure.response.AzureVMInstanceData;
import io.harness.delegate.task.azure.response.AzureVMSSTaskExecutionResponse;
import io.harness.deployment.InstanceDetails;
import io.harness.encryption.Scope;
import io.harness.encryption.SecretRefData;
import io.harness.exception.InvalidRequestException;
import io.harness.ff.FeatureFlagService;
import io.harness.logging.CommandExecutionStatus;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;
import io.harness.security.encryption.EncryptedDataDetail;

import software.wings.api.InstanceElement;
import software.wings.api.PhaseElement;
import software.wings.api.ServiceElement;
import software.wings.beans.Activity;
import software.wings.beans.Application;
import software.wings.beans.AzureConfig;
import software.wings.beans.AzureVMSSInfrastructureMapping;
import software.wings.beans.AzureWebAppInfrastructureMapping;
import software.wings.beans.DeploymentExecutionContext;
import software.wings.beans.Environment;
import software.wings.beans.InfrastructureMapping;
import software.wings.beans.Service;
import software.wings.beans.SettingAttribute;
import software.wings.beans.VMSSAuthType;
import software.wings.beans.artifact.ArtifactStream;
import software.wings.beans.artifact.ArtifactStreamAttributes;
import software.wings.beans.artifact.ArtifactStreamType;
import software.wings.beans.artifact.DockerArtifactStream;
import software.wings.beans.artifact.JenkinsArtifactStream;
import software.wings.beans.command.AzureWebAppCommandUnit;
import software.wings.beans.command.Command;
import software.wings.beans.command.CommandUnit;
import software.wings.beans.command.CommandUnitDetails;
import software.wings.beans.command.ServiceCommand;
import software.wings.beans.container.UserDataSpecification;
import software.wings.persistence.artifact.Artifact;
import software.wings.service.intfc.ActivityService;
import software.wings.service.intfc.ArtifactService;
import software.wings.service.intfc.ArtifactStreamService;
import software.wings.service.intfc.InfrastructureMappingService;
import software.wings.service.intfc.LogService;
import software.wings.service.intfc.ServiceResourceService;
import software.wings.service.intfc.SettingsService;
import software.wings.service.intfc.WorkflowExecutionService;
import software.wings.service.intfc.security.SecretManager;
import software.wings.sm.ExecutionContext;
import software.wings.sm.ExecutionContextImpl;
import software.wings.sm.InstanceStatusSummary;
import software.wings.sm.WorkflowStandardParams;
import software.wings.sm.WorkflowStandardParamsExtensionService;
import software.wings.sm.states.ManagerExecutionLogCallback;
import software.wings.sm.states.azure.appservices.AzureAppServiceStateData;
import software.wings.sm.states.azure.artifact.ArtifactConnectorMapper;
import software.wings.sm.states.azure.artifact.container.DockerArtifactConnectorMapper;
import software.wings.utils.ArtifactType;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

@OwnedBy(CDP)
public class AzureVMSSStateHelperTest extends CategoryTest {
  @Mock private ServiceResourceService serviceResourceService;
  @Mock private ActivityService activityService;
  @Mock private InfrastructureMappingService infrastructureMappingService;
  @Mock private SettingsService settingsService;
  @Mock private SecretManager secretManager;
  @Mock private LogService logService;
  @Mock private ArtifactStreamService artifactStreamService;
  @Mock private AzureSweepingOutputServiceHelper azureSweepingOutputServiceHelper;
  @Mock private WorkflowExecutionService workflowExecutionService;
  @Mock private ArtifactService artifactService;
  @Mock private FeatureFlagService featureFlagService;
  @Mock private WorkflowStandardParamsExtensionService workflowStandardParamsExtensionService;

  @Spy @Inject @InjectMocks AzureVMSSStateHelper azureVMSSStateHelper;

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = OwnerRule.IVAN)
  @Category(UnitTests.class)
  public void testIsBlueGreenWorkflow() {
    ExecutionContext context = Mockito.mock(ExecutionContextImpl.class);

    when(context.getOrchestrationWorkflowType()).thenReturn(BLUE_GREEN);

    boolean isBlueGreen = azureVMSSStateHelper.isBlueGreenWorkflow(context);

    assertThat(isBlueGreen).isEqualTo(true);
  }

  @Test
  @Owner(developers = OwnerRule.IVAN)
  @Category(UnitTests.class)
  public void testGetArtifact() {
    String serviceId = "serviceId";
    DeploymentExecutionContext context = Mockito.mock(DeploymentExecutionContext.class);
    Artifact artifact = Mockito.mock(Artifact.class);

    when(context.getDefaultArtifactForService(serviceId)).thenReturn(artifact);

    Artifact result = azureVMSSStateHelper.getArtifact(context, serviceId);

    assertThat(result).isNotNull();
  }

  @Test
  @Owner(developers = OwnerRule.IVAN)
  @Category(UnitTests.class)
  public void testGetArtifactWithException() {
    String serviceId = "serviceId";
    DeploymentExecutionContext context = Mockito.mock(DeploymentExecutionContext.class);

    when(context.getDefaultArtifactForService(serviceId)).thenReturn(null);

    assertThatThrownBy(() -> azureVMSSStateHelper.getArtifact(context, serviceId))
        .hasMessage("Unable to find artifact for service id: serviceId")
        .isInstanceOf(InvalidRequestException.class);
  }

  @Test
  @Owner(developers = OwnerRule.IVAN)
  @Category(UnitTests.class)
  public void testGetServiceByAppId() {
    String serviceId = "serviceId";
    String appId = "applicationId";
    DeploymentExecutionContext context = Mockito.mock(DeploymentExecutionContext.class);
    PhaseElement phaseElement = Mockito.mock(PhaseElement.class);
    Service service = Service.builder().uuid(serviceId).build();

    when(phaseElement.getServiceElement()).thenReturn(ServiceElement.builder().uuid(serviceId).build());
    when(context.getContextElement(ContextElementType.PARAM, PhaseElement.PHASE_PARAM)).thenReturn(phaseElement);
    doReturn(service).when(serviceResourceService).getWithDetails(appId, serviceId);

    ArgumentCaptor<String> appIdCaptor = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<String> serviceIdCaptor = ArgumentCaptor.forClass(String.class);

    Service result = azureVMSSStateHelper.getServiceByAppId(context, appId);

    verify(serviceResourceService).getWithDetails(appIdCaptor.capture(), serviceIdCaptor.capture());
    assertThat(appId).isEqualTo(appIdCaptor.getValue());
    assertThat(serviceId).isEqualTo(serviceIdCaptor.getValue());
    assertThat(result).isNotNull();
    assertThat(result.getUuid()).isEqualTo(serviceId);
  }

  @Test
  @Owner(developers = OwnerRule.IVAN)
  @Category(UnitTests.class)
  public void testGetWorkflowStandardParams() {
    ExecutionContext context = Mockito.mock(ExecutionContextImpl.class);
    WorkflowStandardParams workflowStandardParams = Mockito.mock(WorkflowStandardParams.class);

    when(context.getContextElement(ContextElementType.STANDARD)).thenReturn(workflowStandardParams);

    WorkflowStandardParams result = azureVMSSStateHelper.getWorkflowStandardParams(context);

    assertThat(result).isNotNull();
  }

  @Test
  @Owner(developers = OwnerRule.IVAN)
  @Category(UnitTests.class)
  public void testGetWorkflowStandardParamsWithException() {
    ExecutionContext context = Mockito.mock(ExecutionContextImpl.class);

    when(context.getContextElement(ContextElementType.STANDARD)).thenReturn(null);

    assertThatThrownBy(() -> azureVMSSStateHelper.getWorkflowStandardParams(context))
        .hasMessage("WorkflowStandardParams can't be null or empty, accountId: null")
        .isInstanceOf(InvalidRequestException.class);
  }

  @Test
  @Owner(developers = OwnerRule.IVAN)
  @Category(UnitTests.class)
  public void testGetApplication() {
    String appId = "applicationId";
    ExecutionContextImpl context = mock(ExecutionContextImpl.class);
    WorkflowStandardParams workflowStandardParams = Mockito.mock(WorkflowStandardParams.class);

    when(context.getContextElement(ContextElementType.STANDARD)).thenReturn(workflowStandardParams);
    when(workflowStandardParamsExtensionService.getApp(workflowStandardParams))
        .thenReturn(Application.Builder.anApplication().uuid(appId).build());

    Application result = azureVMSSStateHelper.getApplication(context);

    assertThat(result).isNotNull();
    assertThat(result.getUuid()).isEqualTo(appId);
  }

  @Test
  @Owner(developers = OwnerRule.IVAN)
  @Category(UnitTests.class)
  public void testGetApplicationWithException() {
    ExecutionContextImpl context = mock(ExecutionContextImpl.class);

    when(context.getContextElement(ContextElementType.STANDARD)).thenReturn(null);

    assertThatThrownBy(() -> azureVMSSStateHelper.getApplication(context))
        .hasMessage("WorkflowStandardParams can't be null or empty, accountId: null")
        .isInstanceOf(InvalidRequestException.class);
  }

  @Test
  @Owner(developers = OwnerRule.IVAN)
  @Category(UnitTests.class)
  public void testGetApplicationWithExceptionWhenAppIsNull() {
    ExecutionContextImpl context = mock(ExecutionContextImpl.class);
    WorkflowStandardParams workflowStandardParams = Mockito.mock(WorkflowStandardParams.class);

    when(context.getContextElement(ContextElementType.STANDARD)).thenReturn(workflowStandardParams);
    when(workflowStandardParamsExtensionService.getApp(workflowStandardParams)).thenReturn(null);

    assertThatThrownBy(() -> azureVMSSStateHelper.getApplication(context))
        .hasMessage("Application can't be null or empty, accountId: null")
        .isInstanceOf(InvalidRequestException.class);
  }

  @Test
  @Owner(developers = OwnerRule.IVAN)
  @Category(UnitTests.class)
  public void testGetEnvironment() {
    String envId = "environmentId";
    ExecutionContextImpl context = mock(ExecutionContextImpl.class);
    WorkflowStandardParams workflowStandardParams = Mockito.mock(WorkflowStandardParams.class);

    when(context.getContextElement(ContextElementType.STANDARD)).thenReturn(workflowStandardParams);
    when(workflowStandardParamsExtensionService.getEnv(workflowStandardParams))
        .thenReturn(anEnvironment().uuid(envId).build());

    Environment result = azureVMSSStateHelper.getEnvironment(context);

    assertThat(result).isNotNull();
    assertThat(result.getUuid()).isEqualTo(envId);
  }

  @Test
  @Owner(developers = OwnerRule.IVAN)
  @Category(UnitTests.class)
  public void testGetEnvironmentWithException() {
    ExecutionContextImpl context = mock(ExecutionContextImpl.class);

    when(context.getContextElement(ContextElementType.STANDARD)).thenReturn(null);

    assertThatThrownBy(() -> azureVMSSStateHelper.getEnvironment(context))
        .hasMessage("WorkflowStandardParams can't be null or empty, accountId: null")
        .isInstanceOf(InvalidRequestException.class);
  }

  @Test
  @Owner(developers = OwnerRule.IVAN)
  @Category(UnitTests.class)
  public void testGetEnvironmentWithExceptionWhenAppIsNull() {
    ExecutionContextImpl context = mock(ExecutionContextImpl.class);
    WorkflowStandardParams workflowStandardParams = Mockito.mock(WorkflowStandardParams.class);

    when(context.getContextElement(ContextElementType.STANDARD)).thenReturn(workflowStandardParams);
    when(workflowStandardParamsExtensionService.getApp(workflowStandardParams)).thenReturn(null);

    assertThatThrownBy(() -> azureVMSSStateHelper.getEnvironment(context))
        .hasMessage("Env can't be null or empty, accountId: null")
        .isInstanceOf(InvalidRequestException.class);
  }

  @Test
  @Owner(developers = OwnerRule.IVAN)
  @Category(UnitTests.class)
  public void testGetCommand() {
    String appId = "appId";
    String serviceId = "serviceId";
    String envId = "envId";
    String commandName = "commandName";
    Command command = Command.Builder.aCommand().withName(commandName).build();
    ServiceCommand serviceCommand = ServiceCommand.Builder.aServiceCommand().withCommand(command).build();

    when(serviceResourceService.getCommandByName(appId, serviceId, envId, commandName)).thenReturn(serviceCommand);

    Command result = azureVMSSStateHelper.getCommand(appId, serviceId, envId, commandName);

    assertThat(result).isNotNull();
    assertThat(result.getName()).isEqualTo(commandName);
  }

  @Test
  @Owner(developers = OwnerRule.IVAN)
  @Category(UnitTests.class)
  public void testGetCommandUnit() {
    String appId = "appId";
    String serviceId = "serviceId";
    String envId = "envId";
    String commandName = "commandName";
    Command command = Command.Builder.aCommand().withName(commandName).build();
    List<CommandUnit> commandUnitList = new ArrayList<>();
    commandUnitList.add(command);

    when(serviceResourceService.getFlattenCommandUnitList(appId, serviceId, envId, commandName))
        .thenReturn(commandUnitList);

    List<CommandUnit> result = azureVMSSStateHelper.getCommandUnitList(appId, serviceId, envId, commandName);

    assertThat(result).isNotNull();
    assertThat(result.get(0).getName()).isEqualTo(commandName);
  }

  @Test
  @Owner(developers = OwnerRule.IVAN)
  @Category(UnitTests.class)
  public void testRenderExpressionOrGetDefault() {
    String expr = "2";
    int defaultValue = 1;
    ExecutionContextImpl context = mock(ExecutionContextImpl.class);

    when(context.renderExpression(expr)).thenReturn(expr);

    int result = azureVMSSStateHelper.renderExpressionOrGetDefault(expr, context, defaultValue);

    assertThat(result).isEqualTo(2);
  }

  @Test
  @Owner(developers = OwnerRule.ANIL)
  @Category(UnitTests.class)
  public void testRenderDoubleExpression() {
    String expr1 = "10.5";
    String expr2 = "${workflow.variables.trafficPercent}";
    String expr3 = "non-integer";

    ExecutionContextImpl context = mock(ExecutionContextImpl.class);
    when(context.renderExpression(expr1)).thenReturn(expr1);
    when(context.renderExpression(expr2)).thenReturn("20");
    when(context.renderExpression(expr3)).thenReturn(expr3);

    double renderExpr1 = azureVMSSStateHelper.renderDoubleExpression(expr1, context, AzureConstants.INVALID_TRAFFIC);
    double renderExpr2 = azureVMSSStateHelper.renderDoubleExpression(expr2, context, AzureConstants.INVALID_TRAFFIC);
    double renderExpr3 = azureVMSSStateHelper.renderDoubleExpression(expr3, context, AzureConstants.INVALID_TRAFFIC);

    assertThat(renderExpr1).isEqualTo(10.5);
    assertThat(renderExpr2).isEqualTo(20);
    assertThat(renderExpr3).isEqualTo(AzureConstants.INVALID_TRAFFIC);
  }

  @Test
  @Owner(developers = OwnerRule.ANIL)
  @Category(UnitTests.class)
  public void testValidateAppSettings() {
    List<AzureAppServiceApplicationSetting> appSettings = new ArrayList<>();
    azureVMSSStateHelper.validateAppSettings(appSettings);

    appSettings.add(AzureAppServiceApplicationSetting.builder().name("appSetting1").value("value1").build());
    azureVMSSStateHelper.validateAppSettings(appSettings);

    appSettings.add(AzureAppServiceApplicationSetting.builder().name("").value("value2").build());
    assertThatThrownBy(() -> azureVMSSStateHelper.validateAppSettings(appSettings))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Application setting name cannot be empty or null");

    appSettings.remove(appSettings.size() - 1);
    appSettings.add(AzureAppServiceApplicationSetting.builder().name("appSetting2").value("").build());
    assertThatThrownBy(() -> azureVMSSStateHelper.validateAppSettings(appSettings))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Application setting value cannot be empty or null for [appSetting2]");

    appSettings.remove(appSettings.size() - 1);
    appSettings.add(AzureAppServiceApplicationSetting.builder().name("appSetting1").value("value2").build());
    assertThatThrownBy(() -> azureVMSSStateHelper.validateAppSettings(appSettings))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Duplicate application string names [appSetting1]");
  }

  @Test
  @Owner(developers = OwnerRule.ANIL)
  @Category(UnitTests.class)
  public void testValidateConnStrings() {
    List<AzureAppServiceConnectionString> connectionStrings = new ArrayList<>();
    azureVMSSStateHelper.validateConnStrings(connectionStrings);

    connectionStrings.add(AzureAppServiceConnectionString.builder()
                              .name("connString1")
                              .value("value1")
                              .type(AzureAppServiceConnectionStringType.SQL_AZURE)
                              .build());
    azureVMSSStateHelper.validateConnStrings(connectionStrings);

    connectionStrings.add(AzureAppServiceConnectionString.builder().name("").value("value2").build());
    assertThatThrownBy(() -> azureVMSSStateHelper.validateConnStrings(connectionStrings))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Connection string name cannot be empty or null");

    connectionStrings.remove(connectionStrings.size() - 1);
    connectionStrings.add(AzureAppServiceConnectionString.builder().name("connString2").value("").build());
    assertThatThrownBy(() -> azureVMSSStateHelper.validateConnStrings(connectionStrings))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Connection string value cannot be empty or null for [connString2]");

    connectionStrings.remove(connectionStrings.size() - 1);
    connectionStrings.add(AzureAppServiceConnectionString.builder().name("connString2").value("value2").build());
    assertThatThrownBy(() -> azureVMSSStateHelper.validateConnStrings(connectionStrings))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Connection string type cannot be null");

    connectionStrings.remove(connectionStrings.size() - 1);
    connectionStrings.add(AzureAppServiceConnectionString.builder().name("connString1").value("value2").build());
    assertThatThrownBy(() -> azureVMSSStateHelper.validateConnStrings(connectionStrings))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Duplicate connection string names [connString1]");
  }

  @Test
  @Owner(developers = OwnerRule.IVAN)
  @Category(UnitTests.class)
  public void testRenderExpressionOrGetDefaultWithDefaultValue() {
    String expr = "two";
    int defaultValue = 1;
    ExecutionContextImpl context = mock(ExecutionContextImpl.class);

    when(context.renderExpression(expr)).thenReturn(expr);

    int result = azureVMSSStateHelper.renderExpressionOrGetDefault(expr, context, defaultValue);

    assertThat(result).isEqualTo(defaultValue);
  }

  @Test
  @Owner(developers = OwnerRule.IVAN)
  @Category(UnitTests.class)
  public void testRenderTimeoutExpressionOrGetDefault() {
    String expr = "-2";
    int defaultValue = 1;
    ExecutionContextImpl context = mock(ExecutionContextImpl.class);

    when(context.renderExpression(expr)).thenReturn(expr);

    int result = azureVMSSStateHelper.renderTimeoutExpressionOrGetDefault(expr, context, defaultValue);

    assertThat(result).isEqualTo(defaultValue);
  }

  @Test
  @Owner(developers = OwnerRule.IVAN)
  @Category(UnitTests.class)
  public void testFixNamePrefix() {
    String name = "name";
    String appName = "appName";
    String serviceName = "serviceName";
    String envName = "envName";
    ExecutionContextImpl context = mock(ExecutionContextImpl.class);

    when(context.renderExpression(name)).thenReturn(name);

    String result = azureVMSSStateHelper.fixNamePrefix(context, name, appName, serviceName, envName);

    assertThat(result).isEqualTo(name);
  }

  @Test
  @Owner(developers = OwnerRule.IVAN)
  @Category(UnitTests.class)
  public void testFixNamePrefixWithNameNull() {
    String appName = "appName";
    String serviceName = "serviceName";
    String envName = "envName";
    ExecutionContextImpl context = mock(ExecutionContextImpl.class);

    String result = azureVMSSStateHelper.fixNamePrefix(context, null, appName, serviceName, envName);

    assertThat(result).isEqualTo("appName__serviceName__envName");
  }

  @Test
  @Owner(developers = OwnerRule.IVAN)
  @Category(UnitTests.class)
  public void testGetAzureVMSSInfrastructureMapping() {
    String infraMappingId = "infraMappingId";
    String appId = "appId";
    InfrastructureMapping infrastructureMapping = AzureVMSSInfrastructureMapping.builder().build();

    when(infrastructureMappingService.get(appId, infraMappingId)).thenReturn(infrastructureMapping);

    AzureVMSSInfrastructureMapping result =
        azureVMSSStateHelper.getAzureVMSSInfrastructureMapping(infraMappingId, appId);

    assertThat(result).isNotNull();
    assertThat(result).isInstanceOf(AzureVMSSInfrastructureMapping.class);
  }

  @Test
  @Owner(developers = OwnerRule.IVAN)
  @Category(UnitTests.class)
  public void testGetAzureConfig() {
    String infraMappingId = "infraMappingId";
    String appId = "appId";
    String computeProviderSettingId = "computeProviderSettingId";
    String accountId = "accountId";
    SettingAttribute settingAttribute = mock(SettingAttribute.class);
    InfrastructureMapping infrastructureMapping = mock(InfrastructureMapping.class);

    when(infrastructureMappingService.get(appId, infraMappingId)).thenReturn(infrastructureMapping);
    when(infrastructureMapping.getComputeProviderSettingId()).thenReturn(computeProviderSettingId);
    when(settingsService.get(computeProviderSettingId)).thenReturn(settingAttribute);
    when(settingAttribute.getValue()).thenReturn(AzureConfig.builder().accountId(accountId).build());

    AzureConfig result = azureVMSSStateHelper.getAzureConfig(infrastructureMapping.getComputeProviderSettingId());

    assertThat(result).isNotNull();
    assertThat(result).isInstanceOf(AzureConfig.class);
    assertThat(result.getAccountId()).isEqualTo(accountId);
  }

  @Test
  @Owner(developers = OwnerRule.IVAN)
  @Category(UnitTests.class)
  public void testGetAzureVMSSStateTimeoutFromContext() {
    int autoScalingSteadyStateVMSSTimeout = 2;
    AzureVMSSSetupContextElement azureVMSSSetupContextElement =
        AzureVMSSSetupContextElement.builder()
            .autoScalingSteadyStateVMSSTimeout(autoScalingSteadyStateVMSSTimeout)
            .build();
    ExecutionContextImpl context = mock(ExecutionContextImpl.class);

    when(context.getContextElement(AZURE_VMSS_SETUP)).thenReturn(azureVMSSSetupContextElement);

    Integer result = azureVMSSStateHelper.getAzureVMSSStateTimeoutFromContext(context);

    assertThat(result).isNotNull();
    assertThat(result.intValue()).isEqualTo(2 * 60 * 1000);
  }

  @Test
  @Owner(developers = OwnerRule.IVAN)
  @Category(UnitTests.class)
  public void testGetAzureVMSSStateTimeoutFromContextWithNegativeTimeout() {
    int autoScalingSteadyStateVMSSTimeout = -1;
    AzureVMSSSetupContextElement azureVMSSSetupContextElement =
        AzureVMSSSetupContextElement.builder()
            .autoScalingSteadyStateVMSSTimeout(autoScalingSteadyStateVMSSTimeout)
            .build();
    ExecutionContextImpl context = mock(ExecutionContextImpl.class);

    when(context.getContextElement(AZURE_VMSS_SETUP)).thenReturn(azureVMSSSetupContextElement);

    Integer result = azureVMSSStateHelper.getAzureVMSSStateTimeoutFromContext(context);

    assertThat(result).isNull();
  }

  @Test
  @Owner(developers = OwnerRule.IVAN)
  @Category(UnitTests.class)
  public void testBuildActivity() {
    String commandName = "commandName";
    String appId = "appId";
    String appName = "appName";
    String serviceId = "serviceId";
    String userName = "userName";
    String userEmail = "test@email.com";
    String envId = "envId";
    String commandType = "commandType";
    String activityId = "activityId";
    CommandUnitDetails.CommandUnitType commandUnitType = CommandUnitDetails.CommandUnitType.AZURE_VMSS_SETUP;
    Application app = Application.Builder.anApplication().uuid(appId).name(appName).build();
    Environment env = anEnvironment().uuid(envId).build();
    Service service = Service.builder().uuid(serviceId).build();
    Activity activity = Activity.builder().uuid(activityId).build();
    EmbeddedUser embeddedUser = EmbeddedUser.builder().email(userEmail).name(userName).build();
    Command command = Command.Builder.aCommand().withName(commandName).build();
    List<CommandUnit> commandUnitList = new ArrayList<>();
    commandUnitList.add(command);
    Artifact artifact = anArtifact().withDisplayName("dysplayName").withUuid("uuid").build();

    ExecutionContextImpl context = mock(ExecutionContextImpl.class);
    PhaseElement phaseElement = Mockito.mock(PhaseElement.class);
    WorkflowStandardParams workflowStandardParams = Mockito.mock(WorkflowStandardParams.class);
    doReturn(workflowStandardParams).when(context).getContextElement(ContextElementType.STANDARD);
    doReturn(app).when(workflowStandardParamsExtensionService).getApp(workflowStandardParams);
    doReturn(env).when(workflowStandardParamsExtensionService).getEnv(workflowStandardParams);
    when(phaseElement.getServiceElement()).thenReturn(ServiceElement.builder().uuid(serviceId).build());
    doReturn(phaseElement).when(context).getContextElement(ContextElementType.PARAM, PhaseElement.PHASE_PARAM);
    doReturn(service).when(serviceResourceService).getWithDetails(appId, serviceId);
    doReturn(activity).when(activityService).save(any());
    doReturn(embeddedUser).when(workflowStandardParams).getCurrentUser();

    Activity result = azureVMSSStateHelper.createAndSaveActivity(
        context, artifact, commandName, commandType, commandUnitType, commandUnitList);

    assertThat(result).isNotNull();
    assertThat(result.getUuid()).isEqualTo(activityId);
    assertThat(result.getStatus()).isEqualTo(RUNNING);
  }

  @Test
  @Owner(developers = OwnerRule.ANIL)
  @Category(UnitTests.class)
  public void testPopulateAzureAppServiceData() {
    String appId = "applicationId";
    String serviceId = "serviceId";
    String envId = "environmentId";
    String accountId = "accountId";
    String computeProviderSettingId = "computeProviderSettingId";
    String infraMappingId = "infraMappingId";
    String subscriptionId = "subscriptionId";
    String resourceGroup = "resourceGroup";
    String harnessUser = "harnessUser";

    Service service = Service.builder().uuid(serviceId).build();
    AzureWebAppInfrastructureMapping webAppInfrastructureMapping =
        AzureWebAppInfrastructureMapping.builder().subscriptionId(subscriptionId).resourceGroup(resourceGroup).build();
    webAppInfrastructureMapping.setComputeProviderSettingId(computeProviderSettingId);

    ExecutionContextImpl context = Mockito.mock(ExecutionContextImpl.class);
    WorkflowStandardParams workflowStandardParams = Mockito.mock(WorkflowStandardParams.class);
    PhaseElement phaseElement = Mockito.mock(PhaseElement.class);
    Artifact artifact = Mockito.mock(Artifact.class);
    SettingAttribute settingAttribute = mock(SettingAttribute.class);

    when(context.getContextElement(ContextElementType.STANDARD)).thenReturn(workflowStandardParams);
    when(workflowStandardParamsExtensionService.getApp(workflowStandardParams))
        .thenReturn(Application.Builder.anApplication().uuid(appId).build());
    when(workflowStandardParams.getCurrentUser()).thenReturn(EmbeddedUser.builder().name(harnessUser).build());
    when(context.getContextElement(ContextElementType.PARAM, PhaseElement.PHASE_PARAM)).thenReturn(phaseElement);
    when(phaseElement.getServiceElement()).thenReturn(ServiceElement.builder().uuid(serviceId).build());
    doReturn(service).when(serviceResourceService).getWithDetails(appId, serviceId);
    when(context.getDefaultArtifactForService(serviceId)).thenReturn(artifact);
    when(workflowStandardParamsExtensionService.getEnv(workflowStandardParams))
        .thenReturn(anEnvironment().uuid(envId).build());
    when(settingsService.get(computeProviderSettingId)).thenReturn(settingAttribute);
    when(settingAttribute.getValue()).thenReturn(AzureConfig.builder().accountId(accountId).build());
    when(context.fetchInfraMappingId()).thenReturn(infraMappingId);
    when(context.renderExpression(resourceGroup)).thenReturn(resourceGroup);
    when(context.renderExpression(subscriptionId)).thenReturn(subscriptionId);
    when(infrastructureMappingService.get(appId, infraMappingId)).thenReturn(webAppInfrastructureMapping);

    AzureAppServiceStateData azureAppServiceStateData = azureVMSSStateHelper.populateAzureAppServiceData(context);

    assertThat(azureAppServiceStateData).isNotNull();
    assertThat(azureAppServiceStateData.getArtifact()).isNotNull();
    assertThat(azureAppServiceStateData.getService()).isNotNull();
    assertThat(azureAppServiceStateData.getEnvironment()).isNotNull();
    assertThat(azureAppServiceStateData.toString()).isNotNull();
    assertThat(azureAppServiceStateData.getCurrentUser().getName()).isEqualTo(harnessUser);

    assertThat(azureAppServiceStateData.getServiceId()).isEqualTo(serviceId);
    assertThat(azureAppServiceStateData.getResourceGroup()).isEqualTo(resourceGroup);
    assertThat(azureAppServiceStateData.getSubscriptionId()).isEqualTo(subscriptionId);
  }

  @Test
  @Owner(developers = OwnerRule.TMACARI)
  @Category(UnitTests.class)
  public void testGetAzureMachineImageArtifactDTO() {
    DeploymentExecutionContext context = Mockito.mock(DeploymentExecutionContext.class);
    Artifact artifact = anArtifact().withArtifactStreamId("artifactStreamId").withRevision("v1").build();
    ArtifactStream artifactStream = Mockito.mock(ArtifactStream.class);
    ArtifactStreamAttributes artifactStreamAttributes = ArtifactStreamAttributes.builder()
                                                            .osType("LINUX")
                                                            .imageType("IMAGE_GALLERY")
                                                            .azureImageGalleryName("galleryName")
                                                            .azureImageDefinition("imageDefinition")
                                                            .build();

    doReturn(artifact).when(context).getDefaultArtifactForService("serviceId");
    doReturn(artifactStream).when(artifactStreamService).get("artifactStreamId");
    doReturn(artifactStreamAttributes).when(artifactStream).fetchArtifactStreamAttributes(any());
    doReturn(true).when(featureFlagService).isEnabled(any(), anyString());

    AzureMachineImageArtifactDTO azureMachineImageArtifactDTO =
        azureVMSSStateHelper.getAzureMachineImageArtifactDTO(context, "serviceId");

    assertThat(azureMachineImageArtifactDTO.getImageOSType()).isEqualTo(LINUX);
    assertThat(azureMachineImageArtifactDTO.getImageType()).isEqualTo(IMAGE_GALLERY);
    assertThat(azureMachineImageArtifactDTO.getImageDefinition().getDefinitionName()).isEqualTo("imageDefinition");
    assertThat(azureMachineImageArtifactDTO.getImageDefinition().getGalleryName()).isEqualTo("galleryName");
    assertThat(azureMachineImageArtifactDTO.getImageDefinition().getVersion()).isEqualTo("v1");
  }

  @Test
  @Owner(developers = OwnerRule.TMACARI)
  @Category(UnitTests.class)
  public void testGetAzureMachineImageArtifactDTOnoArtifactFound() {
    DeploymentExecutionContext context = Mockito.mock(DeploymentExecutionContext.class);
    doReturn(null).when(context).getDefaultArtifactForService("serviceId");

    assertThatThrownBy(() -> azureVMSSStateHelper.getAzureMachineImageArtifactDTO(context, "serviceId"))
        .hasMessageContaining("Unable to find artifact for service id")
        .isInstanceOf(InvalidRequestException.class);
  }

  @Test
  @Owner(developers = OwnerRule.TMACARI)
  @Category(UnitTests.class)
  public void testGetAzureMachineImageArtifactDTOnoArtifactStreamFound() {
    DeploymentExecutionContext context = Mockito.mock(DeploymentExecutionContext.class);

    doReturn(anArtifact().withArtifactStreamId("artifactStreamId").build())
        .when(context)
        .getDefaultArtifactForService("serviceId");
    doReturn(null).when(artifactStreamService).get("artifactStreamId");

    assertThatThrownBy(() -> azureVMSSStateHelper.getAzureMachineImageArtifactDTO(context, "serviceId"))
        .hasMessageContaining("Unable to find artifact stream for artifact stream id:")
        .isInstanceOf(InvalidRequestException.class);
  }
  @Test
  @Owner(developers = OwnerRule.TMACARI)
  @Category(UnitTests.class)
  public void testGetExecutionLogCallback() {
    Activity activity = Activity.builder()
                            .appId("appId")
                            .uuid("uuId")
                            .commandUnits(Collections.singletonList(new AzureWebAppCommandUnit("name")))
                            .build();
    ManagerExecutionLogCallback logCallback = azureVMSSStateHelper.getExecutionLogCallback(activity);
    assertThat(logCallback.getLogService()).isEqualTo(logService);
  }

  @Test
  @Owner(developers = OwnerRule.TMACARI)
  @Category(UnitTests.class)
  public void testUpdateActivityStatus() {
    azureVMSSStateHelper.updateActivityStatus("appId", "activityId", RUNNING);
    verify(activityService, times(1)).updateStatus("activityId", "appId", RUNNING);
  }

  @Test
  @Owner(developers = OwnerRule.TMACARI)
  @Category(UnitTests.class)
  public void testGetBase64EncodedUserData() {
    ExecutionContext executionContext = mock(ExecutionContext.class);

    doReturn(UserDataSpecification.builder().data("data").build())
        .when(serviceResourceService)
        .getUserDataSpecification("appId", "serviceId");
    doReturn("renderedData").when(executionContext).renderExpression("data");

    String encodedData = azureVMSSStateHelper.getBase64EncodedUserData(executionContext, "appId", "serviceId");

    assertThat(encodedData).isEqualTo("cmVuZGVyZWREYXRh");
  }

  @Test
  @Owner(developers = OwnerRule.TMACARI)
  @Category(UnitTests.class)
  public void testGetNonAzureVMSSInfrastructureMapping() {
    String infraMappingId = "infraMappingId";
    String appId = "appId";
    InfrastructureMapping infrastructureMapping = AzureWebAppInfrastructureMapping.builder().build();

    when(infrastructureMappingService.get(appId, infraMappingId)).thenReturn(infrastructureMapping);

    assertThatThrownBy(() -> azureVMSSStateHelper.getAzureVMSSInfrastructureMapping(infraMappingId, appId))
        .hasMessageContaining("Infrastructure Mapping is not instance of AzureVMSSInfrastructureMapping")
        .isInstanceOf(InvalidRequestException.class);
  }

  @Test
  @Owner(developers = OwnerRule.TMACARI)
  @Category(UnitTests.class)
  public void testGetNonAzureWebAppInfrastructureMapping() {
    String infraMappingId = "infraMappingId";
    String appId = "appId";
    InfrastructureMapping infrastructureMapping = AzureVMSSInfrastructureMapping.builder().build();

    when(infrastructureMappingService.get(appId, infraMappingId)).thenReturn(infrastructureMapping);

    assertThatThrownBy(() -> azureVMSSStateHelper.getAzureWebAppInfrastructureMapping(infraMappingId, appId))
        .hasMessageContaining("Infrastructure Mapping is not instance of AzureVMSSInfrastructureMapping")
        .isInstanceOf(InvalidRequestException.class);
  }

  @Test
  @Owner(developers = OwnerRule.TMACARI)
  @Category(UnitTests.class)
  public void testGetStateTimeOutFromContext() {
    ExecutionContext executionContext = mock(ExecutionContext.class);
    AzureVMSSSetupContextElement azureVMSSSetupContextElement =
        AzureVMSSSetupContextElement.builder().autoScalingSteadyStateVMSSTimeout(1).build();

    doReturn(azureVMSSSetupContextElement).when(executionContext).getContextElement(AZURE_VMSS_SETUP);

    Integer stateTimeout = azureVMSSStateHelper.getStateTimeOutFromContext(executionContext, AZURE_VMSS_SETUP);
    assertThat(stateTimeout).isEqualTo(60000);
  }

  @Test
  @Owner(developers = OwnerRule.TMACARI)
  @Category(UnitTests.class)
  public void testGetNewInstance() {
    List<InstanceElement> newInstanceElements = Arrays.asList(new InstanceElement(), new InstanceElement());

    azureVMSSStateHelper.setNewInstance(newInstanceElements, true);

    newInstanceElements.forEach(instanceElement -> assertThat(instanceElement.isNewInstance()).isTrue());
  }

  @Test
  @Owner(developers = OwnerRule.TMACARI)
  @Category(UnitTests.class)
  public void testGetInstanceStatusSummaries() {
    List<InstanceElement> newInstanceElements = Arrays.asList(new InstanceElement(), new InstanceElement());
    List<InstanceStatusSummary> instanceStatusSummaries =
        azureVMSSStateHelper.getInstanceStatusSummaries(SUCCESS, newInstanceElements);

    assertThat(instanceStatusSummaries.size()).isEqualTo(2);
    instanceStatusSummaries.forEach(
        instanceStatusSummary -> assertThat(instanceStatusSummary.getStatus()).isEqualTo(SUCCESS));
  }

  @Test
  @Owner(developers = OwnerRule.TMACARI)
  @Category(UnitTests.class)
  public void testGenerateInstanceElements() {
    ExecutionContext executionContext = mock(ExecutionContext.class);
    AzureVMSSInfrastructureMapping azureVMSSInfrastructureMapping = AzureVMSSInfrastructureMapping.builder().build();
    List<AzureVMInstanceData> vmInstances = new ArrayList<>();

    azureVMSSStateHelper.generateInstanceElements(executionContext, azureVMSSInfrastructureMapping, vmInstances);

    verify(azureSweepingOutputServiceHelper, times(1))
        .generateInstanceElements(executionContext, azureVMSSInfrastructureMapping, vmInstances);
  }

  @Test
  @Owner(developers = OwnerRule.TMACARI)
  @Category(UnitTests.class)
  public void testSaveInstanceInfoToSweepingOutput() {
    ExecutionContext executionContext = mock(ExecutionContext.class);
    List<InstanceElement> instanceElements = Collections.singletonList(new InstanceElement());
    List<InstanceDetails> instanceDetails = new ArrayList<>();

    doReturn(instanceDetails).when(azureSweepingOutputServiceHelper).generateAzureVMSSInstanceDetails(instanceElements);

    azureVMSSStateHelper.saveInstanceInfoToSweepingOutput(executionContext, instanceElements);

    verify(azureSweepingOutputServiceHelper, times(1)).generateAzureVMSSInstanceDetails(instanceElements);
    verify(azureSweepingOutputServiceHelper, times(1))
        .saveInstanceDetails(executionContext, instanceElements, instanceDetails);
  }

  @Test
  @Owner(developers = OwnerRule.TMACARI)
  @Category(UnitTests.class)
  public void testSaveAzureAppInfoToSweepingOutput() {
    ExecutionContext executionContext = mock(ExecutionContext.class);
    List<InstanceElement> instanceElements = new ArrayList<>();
    List<AzureAppDeploymentData> appDeploymentData =
        Collections.singletonList(AzureAppDeploymentData.builder().build());
    List<InstanceDetails> instanceDetails = new ArrayList<>();

    doReturn(instanceDetails)
        .when(azureSweepingOutputServiceHelper)
        .generateAzureAppServiceInstanceDetails(appDeploymentData);

    azureVMSSStateHelper.saveAzureAppInfoToSweepingOutput(executionContext, instanceElements, appDeploymentData);

    verify(azureSweepingOutputServiceHelper, times(1)).generateAzureAppServiceInstanceDetails(appDeploymentData);
    verify(azureSweepingOutputServiceHelper, times(1))
        .saveInstanceDetails(executionContext, instanceElements, instanceDetails);
  }

  @Test
  @Owner(developers = OwnerRule.TMACARI)
  @Category(UnitTests.class)
  public void testGetExecutionStatus() {
    ExecutionStatus successExecutionStatus = azureVMSSStateHelper.getExecutionStatus(
        AzureVMSSTaskExecutionResponse.builder().commandExecutionStatus(CommandExecutionStatus.SUCCESS).build());
    assertThat(successExecutionStatus).isEqualTo(ExecutionStatus.SUCCESS);

    ExecutionStatus nonSuccessExecutionStatus = azureVMSSStateHelper.getExecutionStatus(
        AzureVMSSTaskExecutionResponse.builder().commandExecutionStatus(CommandExecutionStatus.FAILURE).build());
    assertThat(nonSuccessExecutionStatus).isEqualTo(ExecutionStatus.FAILED);
  }

  @Test
  @Owner(developers = OwnerRule.TMACARI)
  @Category(UnitTests.class)
  public void testGetAppServiceExecutionStatus() {
    ExecutionStatus successExecutionStatus = azureVMSSStateHelper.getAppServiceExecutionStatus(
        AzureTaskExecutionResponse.builder().commandExecutionStatus(CommandExecutionStatus.SUCCESS).build());
    assertThat(successExecutionStatus).isEqualTo(ExecutionStatus.SUCCESS);

    ExecutionStatus nonSuccessExecutionStatus = azureVMSSStateHelper.getAppServiceExecutionStatus(
        AzureTaskExecutionResponse.builder().commandExecutionStatus(CommandExecutionStatus.FAILURE).build());
    assertThat(nonSuccessExecutionStatus).isEqualTo(ExecutionStatus.FAILED);
  }

  @Test
  @Owner(developers = OwnerRule.TMACARI)
  @Category(UnitTests.class)
  public void testPopulateStateData() {
    DeploymentExecutionContext executionContext = mock(DeploymentExecutionContext.class);
    Application application = new Application();
    application.setUuid("appUUID");
    Service service = Service.builder().uuid("serviceUUID").build();
    PhaseElement phaseElement =
        PhaseElement.builder().serviceElement(ServiceElement.builder().uuid("serviceElementUUID").build()).build();
    Artifact artifact = anArtifact().build();
    Environment environment = anEnvironment().build();
    AzureVMSSInfrastructureMapping azureVMSSInfrastructureMapping = mock(AzureVMSSInfrastructureMapping.class);
    AzureConfig azureConfig = AzureConfig.builder().build();
    List<EncryptedDataDetail> encryptedDataDetails = new ArrayList<>();

    doReturn(application).when(azureVMSSStateHelper).getApplication(executionContext);
    doReturn(service).when(azureVMSSStateHelper).getServiceByAppId(executionContext, "appUUID");
    doReturn(phaseElement).when(executionContext).getContextElement(any(), any());
    doReturn(artifact).when(azureVMSSStateHelper).getArtifact(any(), eq("serviceUUID"));
    doReturn(environment).when(azureVMSSStateHelper).getEnvironment(executionContext);
    doReturn(azureVMSSInfrastructureMapping)
        .when(azureVMSSStateHelper)
        .getAzureVMSSInfrastructureMapping(any(), eq("appUUID"));
    doReturn("computeProviderSettingId").when(azureVMSSInfrastructureMapping).getComputeProviderSettingId();
    doReturn(azureConfig).when(azureVMSSStateHelper).getAzureConfig("computeProviderSettingId");
    doReturn(encryptedDataDetails)
        .when(azureVMSSStateHelper)
        .getEncryptedDataDetails(executionContext, "computeProviderSettingId");

    AzureVMSSStateData azureVMSSStateData = azureVMSSStateHelper.populateStateData(executionContext);

    assertThat(azureVMSSStateData.getApplication()).isEqualTo(application);
    assertThat(azureVMSSStateData.getService()).isEqualTo(service);
    assertThat(azureVMSSStateData.getServiceId()).isEqualTo("serviceElementUUID");
    assertThat(azureVMSSStateData.getArtifact()).isEqualTo(artifact);
    assertThat(azureVMSSStateData.getEnvironment()).isEqualTo(environment);
    assertThat(azureVMSSStateData.getInfrastructureMapping()).isEqualTo(azureVMSSInfrastructureMapping);
    assertThat(azureVMSSStateData.getAzureConfig()).isEqualTo(azureConfig);
    assertThat(azureVMSSStateData.getAzureEncryptedDataDetails()).isEqualTo(encryptedDataDetails);
    assertThat(azureVMSSStateData.toString()).isNotEmpty();
  }

  @Test
  @Owner(developers = OwnerRule.TMACARI)
  @Category(UnitTests.class)
  public void testCreateAzureConfigDTO() {
    AzureConfig azureConfig = AzureConfig.builder()
                                  .tenantId("tenantId")
                                  .azureEnvironmentType(AZURE)
                                  .encryptedKey("encryptionKey")
                                  .clientId("clientId")
                                  .build();

    AzureConfigDTO azureConfigDTO = azureVMSSStateHelper.createAzureConfigDTO(azureConfig);

    assertThat(azureConfigDTO.getClientId()).isEqualTo("clientId");
    assertThat(azureConfigDTO.getTenantId()).isEqualTo("tenantId");
    assertThat(azureConfigDTO.getAzureEnvironmentType()).isEqualTo(AZURE);
    assertThat(azureConfigDTO.getKey().getIdentifier()).isEqualTo("encryptionKey");
    assertThat(azureConfigDTO.getKey().getScope()).isEqualTo(Scope.ACCOUNT);
  }

  @Test
  @Owner(developers = OwnerRule.TMACARI)
  @Category(UnitTests.class)
  public void testCreateVMAuthDTO() {
    AzureVMSSInfrastructureMapping azureVMSSInfrastructureMapping = AzureVMSSInfrastructureMapping.builder()
                                                                        .passwordSecretTextName("password")
                                                                        .hostConnectionAttrs("hostConnectionAttr")
                                                                        .userName("username")
                                                                        .vmssAuthType(VMSSAuthType.PASSWORD)
                                                                        .build();

    AzureVMAuthDTO azureVMAuthDTO = azureVMSSStateHelper.createVMAuthDTO(azureVMSSInfrastructureMapping);

    assertThat(azureVMAuthDTO.getUserName()).isEqualTo("username");
    assertThat(azureVMAuthDTO.getAzureVmAuthType()).isEqualTo(AzureVMAuthType.PASSWORD);
    assertThat(azureVMAuthDTO.getSecretRef().getIdentifier()).isEqualTo("password");
    assertThat(azureVMAuthDTO.getSecretRef().getScope()).isEqualTo(Scope.ACCOUNT);
  }

  @Test
  @Owner(developers = OwnerRule.TMACARI)
  @Category(UnitTests.class)
  public void testCreateVMAuthDTOUnsupportedAuthType() {
    AzureVMSSInfrastructureMapping azureVMSSInfrastructureMapping =
        AzureVMSSInfrastructureMapping.builder().vmssAuthType(null).build();

    assertThatThrownBy(() -> azureVMSSStateHelper.createVMAuthDTO(azureVMSSInfrastructureMapping))
        .hasMessageContaining("Unsupported Azure VMSS Auth type")
        .isInstanceOf(InvalidRequestException.class);
  }

  @Test
  @Owner(developers = OwnerRule.TMACARI)
  @Category(UnitTests.class)
  public void testGetVMAuthDTOEncryptionDetailsPassword() {
    ExecutionContext executionContext = mock(ExecutionContext.class);
    AzureVMAuthDTO azureVMAuthDTO = AzureVMAuthDTO.builder()
                                        .azureVmAuthType(AzureVMAuthType.PASSWORD)
                                        .secretRef(new SecretRefData("secretRefIdentifier", Scope.ACCOUNT, null))
                                        .build();
    List<EncryptedDataDetail> encryptedDataDetails = new ArrayList<>();

    doReturn("appId").when(executionContext).getAppId();
    doReturn("accountId").when(executionContext).getAccountId();
    doReturn("executionId").when(executionContext).getWorkflowExecutionId();
    doReturn(EncryptedData.builder().uuid("secretUUID").build())
        .when(secretManager)
        .getSecretMappedToAppByName(eq("accountId"), eq("appId"), eq("envId"), eq("secretRefIdentifier"));
    doReturn(encryptedDataDetails).when(secretManager).getEncryptionDetails(any(), eq("appId"), eq("executionId"));

    List<EncryptedDataDetail> resultEncryptedDataDetails =
        azureVMSSStateHelper.getVMAuthDTOEncryptionDetails(executionContext, azureVMAuthDTO, "accountId", "envId");

    assertThat(resultEncryptedDataDetails).isEqualTo(encryptedDataDetails);
  }

  @Test
  @Owner(developers = OwnerRule.TMACARI)
  @Category(UnitTests.class)
  public void testGetVMAuthDTOEncryptionDetailsSSH() {
    ExecutionContext executionContext = mock(ExecutionContext.class);
    AzureVMAuthDTO azureVMAuthDTO = AzureVMAuthDTO.builder()
                                        .azureVmAuthType(AzureVMAuthType.SSH_PUBLIC_KEY)
                                        .secretRef(new SecretRefData("secretRefIdentifier", Scope.ACCOUNT, null))
                                        .build();
    List<EncryptedDataDetail> encryptedDataDetails = new ArrayList<>();

    doReturn("appId").when(executionContext).getAppId();
    doReturn("accountId").when(executionContext).getAccountId();
    doReturn("executionId").when(executionContext).getWorkflowExecutionId();
    doReturn(new SettingAttribute())
        .when(settingsService)
        .getSettingAttributeByName(eq("accountId"), eq("secretRefIdentifier"));
    doReturn(encryptedDataDetails).when(secretManager).getEncryptionDetails(any(), eq("appId"), eq("executionId"));

    List<EncryptedDataDetail> resultEncryptedDataDetails =
        azureVMSSStateHelper.getVMAuthDTOEncryptionDetails(executionContext, azureVMAuthDTO, "accountId", "envId");

    assertThat(resultEncryptedDataDetails).isEqualTo(encryptedDataDetails);
  }

  @Test
  @Owner(developers = OwnerRule.TMACARI)
  @Category(UnitTests.class)
  public void testGetVMAuthDTOEncryptionDetailsSSHSettingsNotFound() {
    ExecutionContext executionContext = mock(ExecutionContext.class);
    AzureVMAuthDTO azureVMAuthDTO = AzureVMAuthDTO.builder()
                                        .azureVmAuthType(AzureVMAuthType.SSH_PUBLIC_KEY)
                                        .secretRef(new SecretRefData("secretRefIdentifier", Scope.ACCOUNT, null))
                                        .build();

    doReturn("appId").when(executionContext).getAppId();
    doReturn("accountId").when(executionContext).getAccountId();
    doReturn("executionId").when(executionContext).getWorkflowExecutionId();
    doReturn(null).when(settingsService).getSettingAttributeByName(eq("accountId"), eq("secretRefIdentifier"));

    assertThatThrownBy(()
                           -> azureVMSSStateHelper.getVMAuthDTOEncryptionDetails(
                               executionContext, azureVMAuthDTO, "accountId", "envId"))
        .hasMessageContaining("Unable to find setting by accountId")
        .isInstanceOf(InvalidRequestException.class);
  }

  @Test
  @Owner(developers = OwnerRule.TMACARI)
  @Category(UnitTests.class)
  public void testGetVMAuthDTOEncryptionDetailsSecretNotFound() {
    ExecutionContext executionContext = mock(ExecutionContext.class);
    AzureVMAuthDTO azureVMAuthDTO = AzureVMAuthDTO.builder()
                                        .azureVmAuthType(AzureVMAuthType.PASSWORD)
                                        .secretRef(new SecretRefData("secretRefIdentifier", Scope.ACCOUNT, null))
                                        .build();

    doReturn("appId").when(executionContext).getAppId();
    doReturn("accountId").when(executionContext).getAccountId();
    doReturn("executionId").when(executionContext).getWorkflowExecutionId();
    doReturn(null).when(settingsService).getSettingAttributeByName(eq("accountId"), eq("secretRefIdentifier"));

    assertThatThrownBy(()
                           -> azureVMSSStateHelper.getVMAuthDTOEncryptionDetails(
                               executionContext, azureVMAuthDTO, "accountId", "envId"))
        .hasMessageContaining("No secret found with name")
        .isInstanceOf(InvalidRequestException.class);
  }

  @Test
  @Owner(developers = OwnerRule.TMACARI)
  @Category(UnitTests.class)
  public void testUpdateEncryptedDataDetailSecretFieldName() {
    List<EncryptedDataDetail> encryptedDataDetails =
        Arrays.asList(EncryptedDataDetail.builder().build(), EncryptedDataDetail.builder().build());
    AzureVMAuthDTO azureVMAuthDTO = AzureVMAuthDTO.builder().build();
    azureVMAuthDTO.setSecretRefFieldName("secretRefFieldName");

    azureVMSSStateHelper.updateEncryptedDataDetailSecretFieldName(azureVMAuthDTO, encryptedDataDetails);

    encryptedDataDetails.forEach(
        encryptedDataDetail -> assertThat(encryptedDataDetail.getFieldName()).isEqualTo("secretRefFieldName"));
  }

  @Test
  @Owner(developers = OwnerRule.TMACARI)
  @Category(UnitTests.class)
  public void testGetVMSSIdFromName() {
    String name =
        azureVMSSStateHelper.getVMSSIdFromName("subscriptionId", "resourceGroupName", "newVirtualMachineScaleSetName");
    assertThat(name).isEqualTo(
        "/subscriptions/subscriptionId/resourceGroups/resourceGroupName/providers/Microsoft.Compute/virtualMachineScaleSets/newVirtualMachineScaleSetName");

    String emptyName = azureVMSSStateHelper.getVMSSIdFromName("subscriptionId", "resourceGroupName", "");
    assertThat(emptyName).isEqualTo("");
  }

  @Test
  @Owner(developers = OwnerRule.TMACARI)
  @Category(UnitTests.class)
  public void testGetConnectorMapper() {
    String appId = "appUUID";
    DeploymentExecutionContext executionContext = mock(DeploymentExecutionContext.class);
    Application application = new Application();
    application.setUuid(appId);
    Service service = Service.builder().uuid("serviceUUID").artifactType(ArtifactType.DOCKER).build();
    Artifact artifact = anArtifact().withArtifactStreamId("artifactStreamId").build();
    doReturn(service).when(azureVMSSStateHelper).getServiceByAppId(executionContext, appId);
    doReturn(appId).when(executionContext).getAppId();

    ArtifactStream artifactStream = mock(ArtifactStream.class);
    doReturn(artifactStream).when(azureVMSSStateHelper).getArtifactStream("artifactStreamId");

    ArtifactStreamAttributes artifactStreamAttributes = mock(ArtifactStreamAttributes.class);
    doReturn(ArtifactType.DOCKER).when(artifactStreamAttributes).getArtifactType();
    doReturn(ArtifactStreamType.DOCKER.name()).when(artifactStreamAttributes).getArtifactStreamType();
    doReturn(artifactStreamAttributes).when(artifactStream).fetchArtifactStreamAttributes(any());
    when(settingsService.get(artifact.getSettingId())).thenReturn(SettingAttribute.Builder.aSettingAttribute().build());
    doReturn(true).when(featureFlagService).isEnabled(any(), anyString());

    ArtifactConnectorMapper artifactConnectorMapper =
        azureVMSSStateHelper.getConnectorMapper(executionContext, artifact);
    assertThat(artifactConnectorMapper).isInstanceOf(DockerArtifactConnectorMapper.class);
  }

  @Test
  @Owner(developers = OwnerRule.JELENA)
  @Category(UnitTests.class)
  public void testIsWebAppNonContainerDeployment() {
    String serviceId = "serviceUUID";
    String appId = "appId";
    DeploymentExecutionContext context = mock(DeploymentExecutionContext.class);
    Service service = Service.builder().uuid(serviceId).artifactType(ArtifactType.DOCKER).build();
    PhaseElement phaseElement =
        PhaseElement.builder().serviceElement(ServiceElement.builder().uuid(serviceId).build()).build();
    when(context.getContextElement(ContextElementType.PARAM, PhaseElement.PHASE_PARAM)).thenReturn(phaseElement);
    when(context.getAppId()).thenReturn(appId);
    doReturn(service).when(serviceResourceService).getWithDetails(appId, serviceId);

    assertThat(azureVMSSStateHelper.isWebAppDockerDeployment(context)).isTrue();
  }

  @Test
  @Owner(developers = OwnerRule.JELENA)
  @Category(UnitTests.class)
  public void testGetArtifactForRollback_onDemand() {
    String serviceId = "serviceUUID";
    String appId = "appId";
    String workflowExecutionId = "workflowExecutionId";
    DeploymentExecutionContext context = mock(DeploymentExecutionContext.class);

    when(context.getAppId()).thenReturn(appId);
    when(context.getWorkflowExecutionId()).thenReturn(workflowExecutionId);
    WorkflowStandardParams workflowStandardParams = Mockito.mock(WorkflowStandardParams.class);
    when(context.getContextElement(ContextElementType.INSTANCE)).thenReturn(workflowStandardParams);

    Artifact rollbackArtifact = new Artifact();
    when(workflowExecutionService.checkIfOnDemand(appId, workflowExecutionId)).thenReturn(true);
    when(serviceResourceService.findArtifactForOnDemandWorkflow(appId, workflowExecutionId))
        .thenReturn(Optional.of(rollbackArtifact));

    Optional<Artifact> artifact = azureVMSSStateHelper.getWebAppPackageArtifactForRollback(context, serviceId);
    assertThat(artifact.isPresent()).isTrue();
    assertThat(artifact.get()).isEqualTo(rollbackArtifact);
  }

  @Test
  @Owner(developers = OwnerRule.VAIBHAV_KUMAR)
  @Category(UnitTests.class)
  public void testMetadataOnlyArtifactStreamTypes() {
    String serviceId = "serviceUUID";
    String appId = "appId";
    List<String> artifactPaths = Collections.singletonList("artifactPath");
    ExecutionContext context = mock(ExecutionContext.class);
    Artifact artifact = new Artifact();
    ServiceElement serviceElement = ServiceElement.builder().uuid(serviceId).build();
    PhaseElement phaseElement = PhaseElement.builder().serviceElement(serviceElement).build();

    when(context.getAppId()).thenReturn(appId);
    when(context.getContextElement(any(), any())).thenReturn(phaseElement);
    when(serviceResourceService.getWithDetails(any(), any())).thenReturn(Service.builder().build());

    // Case 1: JENKINS
    // Expected behaviour: metadataOnly flag must be set to true
    JenkinsArtifactStream jenkinsArtifactStream =
        JenkinsArtifactStream.builder().artifactPaths(artifactPaths).metadataOnly(true).build();
    when(artifactStreamService.get(any())).thenReturn(jenkinsArtifactStream);
    when(settingsService.get(artifact.getSettingId())).thenReturn(SettingAttribute.Builder.aSettingAttribute().build());
    ArtifactConnectorMapper jenkinsArtifactConnectorMapper = azureVMSSStateHelper.getConnectorMapper(context, artifact);

    assertThat(jenkinsArtifactConnectorMapper.artifactStreamAttributes().isMetadataOnly()).isTrue();

    // Case 2: DOCKER
    // Expected behaviour: metadataOnly flag must be set to false
    DockerArtifactStream dockerArtifactStream = DockerArtifactStream.builder().metadataOnly(true).build();
    Service service = Service.builder().artifactType(ArtifactType.DOCKER).build();
    when(artifactStreamService.get(any())).thenReturn(dockerArtifactStream);
    when(serviceResourceService.getWithDetails(any(), any())).thenReturn(service);

    ArtifactConnectorMapper dockerArtifactConnectorMapper = azureVMSSStateHelper.getConnectorMapper(context, artifact);

    assertThat(dockerArtifactConnectorMapper.artifactStreamAttributes().isMetadataOnly()).isFalse();
  }
}
