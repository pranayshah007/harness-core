/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.sm.states.azure.appservice;

import static io.harness.beans.ExecutionStatus.FAILED;
import static io.harness.beans.ExecutionStatus.SUCCESS;
import static io.harness.context.ContextElementType.AZURE_WEBAPP_SETUP;
import static io.harness.rule.OwnerRule.ANIL;
import static io.harness.rule.OwnerRule.JELENA;

import static software.wings.api.InstanceElement.Builder.anInstanceElement;
import static software.wings.beans.TaskType.AZURE_APP_SERVICE_TASK;
import static software.wings.sm.states.azure.appservices.AzureWebAppSlotSetup.APP_SERVICE_SLOT_SETUP;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import io.harness.azure.AzureEnvironmentType;
import io.harness.azure.model.AzureAppServiceConfiguration;
import io.harness.beans.DecryptableEntity;
import io.harness.beans.EmbeddedUser;
import io.harness.beans.EncryptedData;
import io.harness.beans.ExecutionStatus;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.azure.AzureConfigDTO;
import io.harness.delegate.beans.azure.registry.AzureRegistryType;
import io.harness.delegate.beans.connector.ConnectorConfigDTO;
import io.harness.delegate.task.azure.AzureTaskExecutionResponse;
import io.harness.delegate.task.azure.appservice.AzureAppServicePreDeploymentData;
import io.harness.delegate.task.azure.appservice.webapp.response.AzureAppDeploymentData;
import io.harness.delegate.task.azure.appservice.webapp.response.AzureWebAppSlotSetupResponse;
import io.harness.delegate.utils.DelegateTaskMigrationHelper;
import io.harness.exception.InvalidRequestException;
import io.harness.ff.FeatureFlagService;
import io.harness.logging.CommandExecutionStatus;
import io.harness.rule.Owner;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.tasks.ResponseData;

import software.wings.WingsBaseTest;
import software.wings.api.InstanceElement;
import software.wings.api.InstanceElementListParam;
import software.wings.beans.Activity;
import software.wings.beans.Application;
import software.wings.beans.AzureConfig;
import software.wings.beans.AzureWebAppInfrastructureMapping;
import software.wings.beans.Environment;
import software.wings.beans.Service;
import software.wings.beans.SettingAttribute;
import software.wings.beans.artifact.ArtifactStreamAttributes;
import software.wings.beans.artifact.ArtifactStreamType;
import software.wings.beans.config.ArtifactoryConfig;
import software.wings.beans.container.UserDataSpecification;
import software.wings.persistence.artifact.Artifact;
import software.wings.service.impl.servicetemplates.ServiceTemplateHelper;
import software.wings.service.intfc.DelegateService;
import software.wings.service.intfc.StateExecutionService;
import software.wings.service.intfc.security.SecretManager;
import software.wings.sm.ContextElement;
import software.wings.sm.ExecutionContextImpl;
import software.wings.sm.ExecutionResponse;
import software.wings.sm.StateExecutionData;
import software.wings.sm.states.ManagerExecutionLogCallback;
import software.wings.sm.states.azure.AzureStateHelper;
import software.wings.sm.states.azure.AzureSweepingOutputServiceHelper;
import software.wings.sm.states.azure.AzureVMSSStateHelper;
import software.wings.sm.states.azure.appservices.AzureAppServiceSlotSetupContextElement;
import software.wings.sm.states.azure.appservices.AzureAppServiceSlotSetupExecutionData;
import software.wings.sm.states.azure.appservices.AzureAppServiceSlotSetupExecutionSummary;
import software.wings.sm.states.azure.appservices.AzureAppServiceStateData;
import software.wings.sm.states.azure.appservices.AzureWebAppSlotSetup;
import software.wings.sm.states.azure.appservices.manifest.AzureAppServiceManifestUtils;
import software.wings.sm.states.azure.artifact.ArtifactConnectorMapper;
import software.wings.utils.ArtifactType;

import com.google.common.collect.ImmutableMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;

public class AzureWebAppSlotSetupTest extends WingsBaseTest {
  @Mock private DelegateService delegateService;
  @Mock private SecretManager secretManager;
  @Mock private FeatureFlagService featureFlagService;
  @Mock private AzureSweepingOutputServiceHelper azureSweepingOutputServiceHelper;
  @Mock private StateExecutionService stateExecutionService;
  @Mock private DelegateTaskMigrationHelper delegateTaskMigrationHelper;
  @Spy @InjectMocks private AzureAppServiceManifestUtils azureAppServiceManifestUtils;
  @Spy @InjectMocks private AzureVMSSStateHelper azureVMSSStateHelper;
  @Spy @InjectMocks private ServiceTemplateHelper serviceTemplateHelper;
  @Spy @InjectMocks AzureWebAppSlotSetup state = new AzureWebAppSlotSetup("Slot Setup state");

  private final String ACTIVITY_ID = "activityId";
  private final String INFRA_ID = "infraId";
  private final String SUBSCRIPTION_ID = "subscriptionId";
  private final String RESOURCE_GROUP = "resourceGroup";
  private final String APP_NAME = "testWebApp";
  private final String DEPLOYMENT_SLOT = "deploymentSlot";
  private final String DEPLOYMENT_SLOT_ID = "testWebApp/deploymentSlot";
  private final String APP_SERVICE_PLAN_ID = "appService-plan-id";
  private final String HOST_NAME = "hostname";

  @Test
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void testSlotSetupExecuteSuccess() {
    String appId = "appId";
    String serviceId = "serviceId";
    String envId = "envId";
    String activityId = "activityId";
    String delegateResult = "Done";
    String appServiceName = "app-service";
    String slotName = "stage";

    Application app = Application.Builder.anApplication().uuid(appId).build();
    Environment env = Environment.Builder.anEnvironment().uuid(envId).build();
    Service service = Service.builder().uuid(serviceId).artifactType(ArtifactType.DOCKER).build();

    Artifact artifact = Artifact.Builder.anArtifact().build();
    ExecutionContextImpl context = mock(ExecutionContextImpl.class);

    doReturn(appServiceName).when(context).renderExpression("${webapp}");
    doReturn(slotName).when(context).renderExpression("${slot}");

    mockVMSSStateHelper(appId, serviceId, app, env, service, context, artifact);
    mockArtifactStreamMapper();
    mockAppServiceConfiguration(context);
    mockArtifactoryData(artifact, context);
    mockUserDataSpecification();

    doReturn(delegateResult).when(delegateService).queueTaskV2(any());
    doReturn(Collections.emptyMap())
        .when(azureAppServiceManifestUtils)
        .getAppServiceConfigurationManifests(eq(context));

    doReturn(Collections.singletonList(EncryptedDataDetail.builder().build()))
        .when(secretManager)
        .getEncryptionDetails(any(), any(), any());
    doReturn(EncryptedData.builder().uuid("encrypted-data-uuid").build())
        .when(secretManager)
        .getSecretMappedToAppByName(any(), any(), any(), any());
    doReturn("service-template-id").when(serviceTemplateHelper).fetchServiceTemplateId(any());
    doNothing().when(stateExecutionService).appendDelegateTaskDetails(any(), any());
    doReturn(appId).when(context).getAppId();

    state.setSlotSteadyStateTimeout("10");
    state.setAppService("${webapp}");
    state.setDeploymentSlot("${slot}");

    ExecutionResponse result = state.execute(context);

    assertThat(result).isNotNull();
    assertThat(result.getExecutionStatus()).isEqualTo(ExecutionStatus.SUCCESS);
    assertThat(result.getErrorMessage()).isNull();
    assertThat(result.getStateExecutionData()).isNotNull();
    assertThat(result.getStateExecutionData()).isInstanceOf(AzureAppServiceSlotSetupExecutionData.class);

    verifyStateExecutionData(activityId, appServiceName, slotName, result);
    assertThat(state.skipMessage()).isNotNull();
    verify(stateExecutionService).appendDelegateTaskDetails(any(), any());
  }

  @Test
  @Owner(developers = JELENA)
  @Category(UnitTests.class)
  public void testSlotSetupExecuteSuccess_nonContainer() {
    String appId = "appId";
    String serviceId = "serviceId";
    String envId = "envId";
    String activityId = "activityId";

    Application app = Application.Builder.anApplication().uuid(appId).build();
    Environment env = Environment.Builder.anEnvironment().uuid(envId).build();
    Service service = Service.builder().uuid(serviceId).artifactType(ArtifactType.WAR).build();

    ExecutionContextImpl context = mock(ExecutionContextImpl.class);

    Artifact artifact = Artifact.Builder.anArtifact().build();
    doReturn(artifact).when(context).getDefaultArtifactForService(serviceId);

    mockArtifactStreamMapper();
    mockVMSSStateHelper(appId, serviceId, app, env, service, context, artifact);
    mockAppServiceConfiguration(context);
    mockArtifactoryData(artifact, context);
    mockUserDataSpecification();

    doReturn(Collections.emptyMap())
        .when(azureAppServiceManifestUtils)
        .getAppServiceConfigurationManifests(eq(context));
    doReturn(Collections.singletonList(EncryptedDataDetail.builder().build()))
        .when(secretManager)
        .getEncryptionDetails(any(), any(), any());
    doReturn(EncryptedData.builder().uuid("encrypted-data-uuid").build())
        .when(secretManager)
        .getSecretMappedToAppByName(any(), any(), any(), any());
    doReturn("service-template-id").when(serviceTemplateHelper).fetchServiceTemplateId(any());
    doNothing().when(stateExecutionService).appendDelegateTaskDetails(any(), any());
    doReturn(appId).when(context).getAppId();

    ExecutionResponse result = state.execute(context);

    assertThat(result).isNotNull();
    assertThat(result.getExecutionStatus()).isEqualTo(ExecutionStatus.SUCCESS);
    assertThat(result.getErrorMessage()).isNull();
    assertThat(result.getStateExecutionData()).isNotNull();
    assertThat(result.getStateExecutionData()).isInstanceOf(AzureAppServiceSlotSetupExecutionData.class);

    verifyStateExecutionData(activityId, null, null, result);
    assertThat(state.skipMessage()).isNotNull();
    verify(stateExecutionService).appendDelegateTaskDetails(any(), any());
  }

  private AzureAppServiceStateData getAzureAppServiceStateData(
      Application app, Environment env, Service service, Artifact artifact) {
    AzureWebAppInfrastructureMapping azureWebAppInfrastructureMapping = getAzureWebAppInfraMapping();
    List<EncryptedDataDetail> encryptedDataDetails = new ArrayList<>();
    AzureConfig azureConfig = AzureConfig.builder().build();
    return AzureAppServiceStateData.builder()
        .application(app)
        .environment(env)
        .service(service)
        .infrastructureMapping(azureWebAppInfrastructureMapping)
        .resourceGroup("rg")
        .subscriptionId("subId")
        .azureConfig(azureConfig)
        .artifact(artifact)
        .azureEncryptedDataDetails(encryptedDataDetails)
        .artifact(Artifact.Builder.anArtifact().build())
        .currentUser(EmbeddedUser.builder().build())
        .serviceId("serviceId")
        .build();
  }

  private void mockAppServiceConfiguration(ExecutionContextImpl context) {
    AzureAppServiceConfiguration azureAppServiceConfiguration = new AzureAppServiceConfiguration();
    azureAppServiceConfiguration.setAppSettingsJSON(getAppSettingsJSON());
    azureAppServiceConfiguration.setConnStringsJSON(getConnStringJSON());
    doReturn("service-var-value").when(context).renderExpression("${serviceVariable.your_var_name}");
    doReturn("property-value").when(context).renderExpression("property-value");
    doReturn("https://harness.jfrog-ui.io").when(context).renderExpression("https://harness.jfrog-ui.io");
    doReturn("10").when(context).renderExpression("10");
    doReturn("var_name").when(context).renderExpression("${secrets.getValue(\\\"var_name\\\")");
    doReturn("Value2").when(context).renderExpression("Value2");
    doReturn("false").when(context).renderExpression("false");
    doReturn("jdbc:mysql://localhost/test").when(context).renderExpression("jdbc:mysql://localhost/test");
    doReturn("jdbc:sqlserver://INNOWAVE-99\\SQLEXPRESS01;databaseName=EDS")
        .when(context)
        .renderExpression("jdbc:sqlserver://INNOWAVE-99\\SQLEXPRESS01;databaseName=EDS");

    doReturn(azureAppServiceConfiguration).when(azureAppServiceManifestUtils).getAzureAppServiceConfiguration(any());
  }

  private void verifyStateExecutionData(
      String activityId, String appServiceName, String slotName, ExecutionResponse result) {
    AzureAppServiceSlotSetupExecutionData stateExecutionData =
        (AzureAppServiceSlotSetupExecutionData) result.getStateExecutionData();
    assertThat(stateExecutionData.equals(new AzureAppServiceSlotSetupExecutionData())).isFalse();
    assertThat(stateExecutionData.getActivityId()).isEqualTo(activityId);
    assertThat(stateExecutionData.getAppServiceName()).isEqualTo(appServiceName);
    assertThat(stateExecutionData.getDeploySlotName()).isEqualTo(slotName);
    assertThat(stateExecutionData.getAppServiceSlotSetupTimeOut()).isNotNull();
    assertThat(stateExecutionData.getInfrastructureMappingId()).isEqualTo("infraId");

    AzureAppServiceSlotSetupExecutionSummary stepExecutionSummary = stateExecutionData.getStepExecutionSummary();
    assertThat(stepExecutionSummary.equals(AzureAppServiceSlotSetupExecutionSummary.builder().build())).isFalse();
    assertThat(stateExecutionData.getStepExecutionSummary().toString()).isNotNull();
    assertThat(stateExecutionData.getAppServiceName()).isEqualTo(appServiceName);
    assertThat(stateExecutionData.getDeploySlotName()).isEqualTo(slotName);

    assertThat(stateExecutionData.getExecutionDetails()).isNotEmpty();
    assertThat(stateExecutionData.getExecutionSummary()).isNotEmpty();
    assertThat(stateExecutionData.getStepExecutionSummary()).isNotNull();
  }

  private void mockVMSSStateHelper(String appId, String serviceId, Application app, Environment env, Service service,
      ExecutionContextImpl context, Artifact artifact) {
    ManagerExecutionLogCallback managerExecutionLogCallback = mock(ManagerExecutionLogCallback.class);
    Activity activity = Activity.builder().uuid(ACTIVITY_ID).build();
    doReturn(activity).when(azureVMSSStateHelper).createAndSaveActivity(any(), any(), any(), any(), any(), anyList());
    doReturn(managerExecutionLogCallback).when(azureVMSSStateHelper).getExecutionLogCallback(activity);
    AzureAppServiceStateData appServiceStateData = getAzureAppServiceStateData(app, env, service, artifact);
    doReturn(appServiceStateData)
        .when(azureVMSSStateHelper)
        .populateAzureAppServiceData(eq(context), any(Artifact.class));
    doReturn(service).when(azureVMSSStateHelper).getServiceByAppId(context, appId);
    doReturn(appServiceStateData).when(azureVMSSStateHelper).populateAzureAppServiceData(context);
    doReturn(serviceId).when(azureVMSSStateHelper).getServiceId(context);
  }

  private void mockArtifactoryData(Artifact artifact, ExecutionContextImpl context) {
    String accountId = "accountId";
    ArtifactoryConfig artifactoryConfig = ArtifactoryConfig.builder()
                                              .encryptedPassword("secret")
                                              .artifactoryUrl("artifactory-url")
                                              .username("username")
                                              .accountId(accountId)
                                              .build();
    SettingAttribute serverSetting = SettingAttribute.Builder.aSettingAttribute().withValue(artifactoryConfig).build();

    ArtifactStreamAttributes artifactStreamAttributes = ArtifactStreamAttributes.builder().build();
    artifactStreamAttributes.setArtifactStreamType(ArtifactStreamType.ARTIFACTORY.name());
    artifactStreamAttributes.setServerSetting(serverSetting.toDTO());
    Map<String, String> metadata = new HashMap<>();
    metadata.put("buildNo", "artifact-builder-number");
    metadata.put("url", "artifact-job-name/random-guid/artifact-name");
    artifactStreamAttributes.setMetadata(metadata);
    artifactStreamAttributes.setJobName("artifact-job-name");

    ArtifactConnectorMapper artifactConnectorMapper =
        ArtifactConnectorMapper.getArtifactConnectorMapper(artifact, artifactStreamAttributes);
    doReturn(artifactConnectorMapper).when(azureVMSSStateHelper).getConnectorMapper(context, artifact);

    doReturn(Collections.singletonList(EncryptedDataDetail.builder().build()))
        .when(azureVMSSStateHelper)
        .getEncryptedDataDetails(context, artifactoryConfig);
  }

  public void mockUserDataSpecification() {
    UserDataSpecification userDataSpecification =
        UserDataSpecification.builder().data("startup command").serviceId("service-id").build();
    doReturn(Optional.ofNullable(userDataSpecification)).when(azureVMSSStateHelper).getUserDataSpecification(any());
  }

  private String getAppSettingsJSON() {
    return "[\n  {\n    \"name\": \"DOCKER_REGISTRY_SERVER_URL\",\n    \"value\": \"https://harness.jfrog-ui.io\",\n    \"slotSetting\": false\n  },\n  {\n    \"name\": \"DOCKER_REGISTRY_SERVER_USERNAME\",\n    \"value\": \"${serviceVariable.your_var_name}\",\n    \"slotSetting\": false\n  },\n  {\n    \"name\": \"Key1\",\n    \"value\": \"${secrets.getValue(\\\"var_name\\\")}\",\n    \"slotSetting\": true\n  },\n  {\n    \"name\": \"Key2\",\n    \"value\": \"Value2\",\n    \"slotSetting\": false\n  },\n  {\n    \"name\": \"WEBSITES_ENABLE_APP_SERVICE_STORAGE\",\n    \"value\": \"false\",\n    \"slotSetting\": false\n  }\n]";
  }

  private String getConnStringJSON() {
    return "[\n  {\n    \"name\": \"CONN_STRING_WITH_SECRET\",\n    \"value\": \"${secrets.getValue(\\\"var_name\\\")}\",\n    \"type\": \"Custom\",\n    \"slotSetting\": false\n  },\n  {\n    \"name\": \"MY_SQL_CONN_STRING\",\n    \"value\": \"jdbc:mysql://localhost/test\",\n    \"type\": \"MySQL\",\n    \"slotSetting\": true\n  },\n  {\n    \"name\": \"SQL_SERVER_CONN_STRING\",\n    \"value\": \"jdbc:sqlserver://INNOWAVE-99\\\\SQLEXPRESS01;databaseName=EDS\",\n    \"type\": \"SQLServer\",\n    \"slotSetting\": true\n  }\n]";
  }

  private AzureWebAppInfrastructureMapping getAzureWebAppInfraMapping() {
    AzureWebAppInfrastructureMapping azureWebAppInfrastructureMapping = AzureWebAppInfrastructureMapping.builder()
                                                                            .resourceGroup(RESOURCE_GROUP)
                                                                            .subscriptionId(SUBSCRIPTION_ID)
                                                                            .build();
    azureWebAppInfrastructureMapping.setUuid(INFRA_ID);
    return azureWebAppInfrastructureMapping;
  }

  private void mockArtifactStreamMapper() {
    ArtifactConnectorMapper mockArtifactConnectorMapper = mockGetArtifactStreamMapper();
    mockGetAzureRegistryType(mockArtifactConnectorMapper);
    ConnectorConfigDTO mockConnectorConfigDTO = mockGetConnectorConfigDTO(mockArtifactConnectorMapper);
    mockGetConnectorDTOAuthCredentials(mockArtifactConnectorMapper, mockConnectorConfigDTO);
    mockGetConnectorAuthEncryptedDataDetails();
  }

  private ArtifactConnectorMapper mockGetArtifactStreamMapper() {
    ArtifactConnectorMapper mockArtifactConnectorMapper = mock(ArtifactConnectorMapper.class);
    doReturn(mockArtifactConnectorMapper).when(azureVMSSStateHelper).getConnectorMapper(any(), any());
    return mockArtifactConnectorMapper;
  }

  private void mockGetAzureRegistryType(ArtifactConnectorMapper mockArtifactConnectorMapper) {
    AzureRegistryType azureRegistryType = AzureRegistryType.DOCKER_HUB_PUBLIC;
    doReturn(azureRegistryType).when(mockArtifactConnectorMapper).getAzureRegistryType();
  }

  private ConnectorConfigDTO mockGetConnectorConfigDTO(ArtifactConnectorMapper mockArtifactConnectorMapper) {
    ConnectorConfigDTO mockConnectorConfigDTO = mock(ConnectorConfigDTO.class);
    doReturn(mockConnectorConfigDTO).when(mockArtifactConnectorMapper).getConnectorDTO();
    return mockConnectorConfigDTO;
  }

  private void mockGetConnectorDTOAuthCredentials(
      ArtifactConnectorMapper mockArtifactConnectorMapper, ConnectorConfigDTO mockConnectorConfigDTO) {
    DecryptableEntity mockDecryptableEntity = mock(DecryptableEntity.class);
    doReturn(Optional.of(mockDecryptableEntity))
        .when(mockArtifactConnectorMapper)
        .getConnectorDTOAuthCredentials(mockConnectorConfigDTO);
  }

  private void mockGetConnectorAuthEncryptedDataDetails() {
    List<EncryptedDataDetail> encryptedDataDetailList = new ArrayList<>();
    EncryptedDataDetail mockEncryptedDataDetail = mock(EncryptedDataDetail.class);
    encryptedDataDetailList.add(mockEncryptedDataDetail);
  }

  @Test
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void testSlotSetupValidateFields() {
    AzureWebAppSlotSetup state = new AzureWebAppSlotSetup("Validate fields state");
    assertThat(state.validateFields().size()).isEqualTo(2);

    state.setDeploymentSlot(DEPLOYMENT_SLOT);
    state.setAppService(APP_NAME);
    state.setTargetSlot(DEPLOYMENT_SLOT);
    assertThat(state.validateFields().size()).isEqualTo(1);

    state.setDeploymentSlot(APP_NAME);
    assertThat(state.validateFields().size()).isEqualTo(0);
  }

  @Test
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void testSlotSetupExecuteFailure() {
    Activity activity = Activity.builder().uuid(ACTIVITY_ID).build();
    ExecutionContextImpl context = mock(ExecutionContextImpl.class);
    ManagerExecutionLogCallback managerExecutionLogCallback = mock(ManagerExecutionLogCallback.class);

    doReturn(activity).when(azureVMSSStateHelper).createAndSaveActivity(any(), any(), any(), any(), any(), anyList());
    doReturn(managerExecutionLogCallback).when(azureVMSSStateHelper).getExecutionLogCallback(activity);
    doAnswer(invocation -> { throw new Exception(); })
        .when(azureVMSSStateHelper)
        .populateAzureAppServiceData(eq(context), any(Artifact.class));

    assertThatThrownBy(() -> state.execute(context)).isInstanceOf(InvalidRequestException.class);
  }

  @Test
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void testSlotSetupHandleAsyncResponseSuccess() {
    ExecutionContextImpl context = mock(ExecutionContextImpl.class);
    doNothing().when(azureVMSSStateHelper).updateActivityStatus(any(), any(), any());
    doReturn(SUCCESS).when(azureVMSSStateHelper).getAppServiceExecutionStatus(any());

    List<AzureAppDeploymentData> azureAppDeploymentData = getAzureAppDeploymentData();
    AzureWebAppInfrastructureMapping azureWebAppInfrastructureMapping = getAzureWebAppInfraMapping();
    Map<String, ResponseData> responseMap = getResponseDataMap(azureAppDeploymentData);
    AzureAppServiceSlotSetupExecutionData stateExecutionData = getStateExecutionData();
    List<InstanceElement> instanceElements = Collections.singletonList(
        anInstanceElement().uuid("uuid").hostName(HOST_NAME).displayName(HOST_NAME).newInstance(true).build());

    doReturn(stateExecutionData).when(context).getStateExecutionData();
    doReturn(azureWebAppInfrastructureMapping)
        .when(azureVMSSStateHelper)
        .getAzureWebAppInfrastructureMapping(any(), any());

    doReturn(instanceElements)
        .when(azureSweepingOutputServiceHelper)
        .generateAzureAppInstanceElements(any(), any(), any());

    ArgumentCaptor<AzureAppServiceSlotSetupContextElement> contextElementArgumentCaptor =
        ArgumentCaptor.forClass(AzureAppServiceSlotSetupContextElement.class);

    ExecutionResponse executionResponse = state.handleAsyncResponse(context, responseMap);

    assertThat(executionResponse).isNotNull();
    assertThat(executionResponse.getExecutionStatus()).isEqualTo(SUCCESS);
    verifySetupContextElement(context, contextElementArgumentCaptor);
    verifyContextAndNotifyElements(executionResponse);
    verifyPostStateExecutionData(executionResponse);
  }

  @Test
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void testSlotSetupHandleAsyncResponseSuccessWithEmptyInstance() {
    ExecutionContextImpl context = mock(ExecutionContextImpl.class);
    doNothing().when(azureVMSSStateHelper).updateActivityStatus(any(), any(), any());
    doReturn(SUCCESS).when(azureVMSSStateHelper).getAppServiceExecutionStatus(any());

    AzureWebAppInfrastructureMapping azureWebAppInfrastructureMapping = getAzureWebAppInfraMapping();

    AzureAppServiceSlotSetupExecutionData stateExecutionData = getStateExecutionData();
    doReturn(stateExecutionData).when(context).getStateExecutionData();
    doReturn(azureWebAppInfrastructureMapping)
        .when(azureVMSSStateHelper)
        .getAzureWebAppInfrastructureMapping(any(), any());

    doReturn(Collections.emptyList())
        .when(azureSweepingOutputServiceHelper)
        .generateAzureAppInstanceElements(any(), any(), any());
    ExecutionResponse executionResponse =
        state.handleAsyncResponse(context, getResponseDataMapWithEmptyDeploymentData());

    assertThat(executionResponse).isNotNull();
    StateExecutionData postExecutionData = executionResponse.getStateExecutionData();
    assertThat(postExecutionData instanceof AzureAppServiceSlotSetupExecutionData).isTrue();
    AzureAppServiceSlotSetupExecutionData executionData = (AzureAppServiceSlotSetupExecutionData) postExecutionData;
    assertThat(executionData.getWebAppUrl()).isEmpty();
  }

  @Test
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void testSlotSetupHandleAsyncResponseFailure() {
    ExecutionContextImpl context = mock(ExecutionContextImpl.class);
    doNothing().when(azureVMSSStateHelper).updateActivityStatus(any(), any(), any());
    doReturn(FAILED).when(azureVMSSStateHelper).getAppServiceExecutionStatus(any());
    doReturn(getStateExecutionData()).when(context).getStateExecutionData();
    ArgumentCaptor<AzureAppServiceSlotSetupContextElement> contextElementArgumentCaptor =
        ArgumentCaptor.forClass(AzureAppServiceSlotSetupContextElement.class);

    ExecutionResponse executionResponse =
        state.handleAsyncResponse(context, getResponseDataMap(getAzureAppDeploymentData()));

    assertThat(executionResponse.getExecutionStatus()).isEqualTo(FAILED);
    verifySetupContextElement(context, contextElementArgumentCaptor);
  }

  @Test
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void testSlotSetupHandleAsyncResponseFailureDueToNullResponse() {
    ExecutionContextImpl context = mock(ExecutionContextImpl.class);
    doNothing().when(azureVMSSStateHelper).updateActivityStatus(any(), any(), any());
    doReturn(FAILED).when(azureVMSSStateHelper).getAppServiceExecutionStatus(any());
    doReturn(getStateExecutionData()).when(context).getStateExecutionData();

    ExecutionResponse executionResponse = state.handleAsyncResponse(context, getResponseDataMapWithNullTaskResponse());
    assertThat(executionResponse.getExecutionStatus()).isEqualTo(FAILED);

    doReturn(SUCCESS).when(azureVMSSStateHelper).getAppServiceExecutionStatus(any());
    assertThatThrownBy(() -> state.handleAsyncResponse(context, getResponseDataMapWithNullTaskResponseSuccess()))
        .isInstanceOf(InvalidRequestException.class);
  }

  @Test
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void testAzureStateHelper() {
    AzureConfig azureConfig = AzureConfig.builder()
                                  .azureEnvironmentType(AzureEnvironmentType.AZURE)
                                  .clientId("clientId")
                                  .tenantId("tenantId")
                                  .build();
    AzureStateHelper stateHelper = new AzureStateHelper();
    AzureConfigDTO azureConfigDTO = stateHelper.createAzureConfigDTO(azureConfig);
    assertThat(azureConfigDTO).isNotNull();
    assertThat(azureConfigDTO.getAzureEnvironmentType()).isEqualTo(AzureEnvironmentType.AZURE);
    assertThat(azureConfigDTO.getClientId()).isEqualTo("clientId");
    assertThat(azureConfigDTO.getTenantId()).isEqualTo("tenantId");
  }

  @NotNull
  private List<AzureAppDeploymentData> getAzureAppDeploymentData() {
    return Collections.singletonList(AzureAppDeploymentData.builder()
                                         .subscriptionId(SUBSCRIPTION_ID)
                                         .resourceGroup(RESOURCE_GROUP)
                                         .appName(APP_NAME)
                                         .deploySlot(DEPLOYMENT_SLOT)
                                         .deploySlotId(DEPLOYMENT_SLOT_ID)
                                         .appServicePlanId(APP_SERVICE_PLAN_ID)
                                         .hostName(HOST_NAME)
                                         .build());
  }

  @NotNull
  private ImmutableMap<String, ResponseData> getResponseDataMap(List<AzureAppDeploymentData> azureAppDeploymentData) {
    return ImmutableMap.of(ACTIVITY_ID,
        AzureTaskExecutionResponse.builder()
            .azureTaskResponse(AzureWebAppSlotSetupResponse.builder()
                                   .azureAppDeploymentData(azureAppDeploymentData)
                                   .preDeploymentData(AzureAppServicePreDeploymentData.builder()
                                                          .appName(APP_NAME)
                                                          .appSettingsToAdd(Collections.emptyMap())
                                                          .connStringsToAdd(Collections.emptyMap())
                                                          .slotName(DEPLOYMENT_SLOT)
                                                          .build())
                                   .build())
            .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
            .build());
  }

  @NotNull
  private ImmutableMap<String, ResponseData> getResponseDataMapWithEmptyDeploymentData() {
    return ImmutableMap.of(ACTIVITY_ID,
        AzureTaskExecutionResponse.builder()
            .azureTaskResponse(AzureWebAppSlotSetupResponse.builder()
                                   .azureAppDeploymentData(Collections.emptyList())
                                   .preDeploymentData(AzureAppServicePreDeploymentData.builder()
                                                          .appName(APP_NAME)
                                                          .appSettingsToAdd(Collections.emptyMap())
                                                          .connStringsToAdd(Collections.emptyMap())
                                                          .slotName(DEPLOYMENT_SLOT)
                                                          .build())
                                   .build())
            .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
            .build());
  }

  @NotNull
  private ImmutableMap<String, ResponseData> getResponseDataMapWithNullTaskResponse() {
    return ImmutableMap.of(ACTIVITY_ID,
        AzureTaskExecutionResponse.builder().commandExecutionStatus(CommandExecutionStatus.FAILURE).build());
  }

  @NotNull
  private ImmutableMap<String, ResponseData> getResponseDataMapWithNullTaskResponseSuccess() {
    return ImmutableMap.of(ACTIVITY_ID,
        AzureTaskExecutionResponse.builder().commandExecutionStatus(CommandExecutionStatus.SUCCESS).build());
  }

  private AzureAppServiceSlotSetupExecutionData getStateExecutionData() {
    return AzureAppServiceSlotSetupExecutionData.builder()
        .infrastructureMappingId(INFRA_ID)
        .deploySlotName(DEPLOYMENT_SLOT)
        .appServiceName(APP_NAME)
        .activityId(ACTIVITY_ID)
        .taskType(AZURE_APP_SERVICE_TASK)
        .build();
  }

  private void verifySetupContextElement(ExecutionContextImpl context,
      ArgumentCaptor<AzureAppServiceSlotSetupContextElement> contextElementArgumentCaptor) {
    verify(azureSweepingOutputServiceHelper).saveToSweepingOutPut(contextElementArgumentCaptor.capture(), any(), any());
    AzureAppServiceSlotSetupContextElement setupContextElement = contextElementArgumentCaptor.getValue();
    assertThat(setupContextElement).isNotNull();
    assertThat(setupContextElement.equals(new AzureAppServiceSlotSetupContextElement())).isFalse();
    assertThat(setupContextElement.getCommandName()).isEqualTo(APP_SERVICE_SLOT_SETUP);
    assertThat(setupContextElement.getWebApp()).isEqualTo(APP_NAME);
    assertThat(setupContextElement.getDeploymentSlot()).isEqualTo(DEPLOYMENT_SLOT);
    assertThat(setupContextElement.getInfraMappingId()).isEqualTo(INFRA_ID);
    assertThat(setupContextElement.getPreDeploymentData()).isNotNull();

    assertThat(setupContextElement.getName()).isNull();
    assertThat(setupContextElement.getUuid()).isNull();
    assertThat(setupContextElement.cloneMin()).isNull();
    assertThat(setupContextElement.toString()).isNotNull();
    assertThat(setupContextElement.getElementType()).isEqualTo(AZURE_WEBAPP_SETUP);
    assertThat(setupContextElement.getType()).isNotNull();
  }

  private void verifyContextAndNotifyElements(ExecutionResponse executionResponse) {
    List<ContextElement> notifyElements = executionResponse.getNotifyElements();
    assertThat(notifyElements).isNotNull();
    assertThat(notifyElements.size()).isEqualTo(1);

    ContextElement contextElement = notifyElements.get(0);
    assertThat(contextElement).isNotNull();
    assertThat(contextElement instanceof InstanceElementListParam).isTrue();

    ContextElement instanceElementParam = executionResponse.getContextElements().get(0);
    assertThat(instanceElementParam instanceof InstanceElementListParam).isTrue();
  }

  private void verifyPostStateExecutionData(ExecutionResponse executionResponse) {
    StateExecutionData stateExecutionData = executionResponse.getStateExecutionData();
    assertThat(stateExecutionData instanceof AzureAppServiceSlotSetupExecutionData).isTrue();
    AzureAppServiceSlotSetupExecutionData executionData = (AzureAppServiceSlotSetupExecutionData) stateExecutionData;
    assertThat(executionData.getNewInstanceStatusSummaries().size()).isEqualTo(1);
    assertThat(executionData.getWebAppUrl()).isEqualTo(HOST_NAME);
  }
}
