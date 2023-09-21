/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.azure.webapp;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.azure.model.AzureConstants.DEPLOY_TO_SLOT;
import static io.harness.azure.model.AzureConstants.SAVE_EXISTING_CONFIGURATIONS;
import static io.harness.azure.model.AzureConstants.UPDATE_SLOT_CONFIGURATION_SETTINGS;
import static io.harness.cdng.azure.webapp.AzureWebAppSlotDeploymentStep.FETCH_PREDEPLOYMENT_DATA_TASK_NAME;
import static io.harness.cdng.stepsdependency.constants.OutcomeExpressionConstants.APPLICATION_SETTINGS;
import static io.harness.cdng.stepsdependency.constants.OutcomeExpressionConstants.CONNECTION_STRINGS;
import static io.harness.cdng.stepsdependency.constants.OutcomeExpressionConstants.STARTUP_COMMAND;
import static io.harness.k8s.K8sCommandUnitConstants.FetchFiles;
import static io.harness.rule.OwnerRule.ABOSII;
import static io.harness.rule.OwnerRule.TMACARI;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.beans.Scope;
import io.harness.category.element.UnitTests;
import io.harness.cdng.CDNGTestBase;
import io.harness.cdng.CDStepHelper;
import io.harness.cdng.artifact.outcome.ArtifactOutcome;
import io.harness.cdng.azure.webapp.beans.AzureSlotDeploymentPassThroughData;
import io.harness.cdng.azure.webapp.beans.AzureWebAppPreDeploymentDataOutput;
import io.harness.cdng.azure.webapp.beans.AzureWebAppSlotDeploymentDataOutput;
import io.harness.cdng.execution.StageExecutionInfo.StageExecutionInfoKeys;
import io.harness.cdng.execution.azure.webapps.AzureWebAppsStageExecutionDetails;
import io.harness.cdng.execution.azure.webapps.AzureWebAppsStageExecutionDetails.AzureWebAppsStageExecutionDetailsKeys;
import io.harness.cdng.execution.service.StageExecutionInfoService;
import io.harness.cdng.featureFlag.CDFeatureFlagHelper;
import io.harness.cdng.infra.beans.AzureWebAppInfrastructureOutcome;
import io.harness.cdng.instance.info.InstanceInfoService;
import io.harness.cdng.manifest.yaml.BitbucketStore;
import io.harness.cdng.manifest.yaml.GitStoreConfig;
import io.harness.cdng.manifest.yaml.harness.HarnessStore;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfig;
import io.harness.delegate.beans.logstreaming.UnitProgressData;
import io.harness.delegate.beans.storeconfig.FetchType;
import io.harness.delegate.task.TaskParameters;
import io.harness.delegate.task.azure.appservice.AzureAppServicePreDeploymentData;
import io.harness.delegate.task.azure.appservice.settings.AppSettingsFile;
import io.harness.delegate.task.azure.appservice.webapp.ng.AzureWebAppInfraDelegateConfig;
import io.harness.delegate.task.azure.appservice.webapp.ng.request.AzureWebAppFetchPreDeploymentDataRequest;
import io.harness.delegate.task.azure.appservice.webapp.ng.request.AzureWebAppSlotDeploymentRequest;
import io.harness.delegate.task.azure.appservice.webapp.ng.response.AzureWebAppFetchPreDeploymentDataResponse;
import io.harness.delegate.task.azure.appservice.webapp.ng.response.AzureWebAppSlotDeploymentResponse;
import io.harness.delegate.task.azure.appservice.webapp.ng.response.AzureWebAppTaskResponse;
import io.harness.delegate.task.azure.appservice.webapp.response.AzureAppDeploymentData;
import io.harness.delegate.task.azure.artifact.AzureArtifactConfig;
import io.harness.delegate.task.azure.artifact.AzureContainerArtifactConfig;
import io.harness.delegate.task.azure.artifact.AzurePackageArtifactConfig;
import io.harness.delegate.task.git.GitFetchResponse;
import io.harness.git.model.FetchFilesResult;
import io.harness.git.model.GitFile;
import io.harness.logging.UnitProgress;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.tasks.TaskRequest;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.plan.execution.SetupAbstractionKeys;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.sdk.core.steps.executables.TaskChainResponse;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;

import software.wings.beans.TaskType;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.SneakyThrows;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@OwnedBy(CDP)
public class AzureWebAppSlotDeploymentStepTest extends CDNGTestBase {
  private static final String APP_SETTINGS_FILE_CONTENT = "[{\"name\": \"app\", \"value\": \"test\"}]";
  private static final String CONN_STRINGS_FILE_CONTENT =
      "[{\"name\": \"conn\", \"value\": \"test\", \"type\": \"MySql\"}]";
  private static final String STARTUP_SCRIPT_FILE_CONTENT = "echo 'test'";
  private static final String WEB_APP = "webApp";
  private static final String DEPLOYMENT_SLOT = "deploymentSlot";
  private static final String STAGE_EXECUTION_ID = "stageExecutionId";

  @Mock private AzureWebAppStepHelper azureWebAppStepHelper;
  @Mock private CDStepHelper cdStepHelper;
  @Mock private ExecutionSweepingOutputService executionSweepingOutputService;
  @Mock private InstanceInfoService instanceInfoService;
  @Mock private StageExecutionInfoService stageExecutionInfoService;
  @Mock private CDFeatureFlagHelper cdFeatureFlagHelper;

  @InjectMocks private AzureWebAppSlotDeploymentStep slotDeploymentStep;

  @Mock private AzureArtifactConfig azureArtifactConfig;
  @Mock private AzureWebAppInfraDelegateConfig infraDelegateConfig;
  @Mock private ArtifactOutcome primaryArtifactOutcome;
  private final AzureWebAppInfrastructureOutcome infrastructure = AzureWebAppInfrastructureOutcome.builder().build();
  private final Ambiance ambiance = Ambiance.newBuilder()
                                        .putSetupAbstractions(SetupAbstractionKeys.accountId, "accountId")
                                        .setStageExecutionId(STAGE_EXECUTION_ID)
                                        .build();
  private final StepInputPackage stepInputPackage = StepInputPackage.builder().build();
  private final TaskRequest taskRequest = TaskRequest.newBuilder().build();
  private final TaskRequest gitTaskRequest = TaskRequest.newBuilder().build();

  @Before
  public void setupTest() {
    doReturn(primaryArtifactOutcome).when(azureWebAppStepHelper).getPrimaryArtifactOutcome(ambiance);
    doReturn(azureArtifactConfig)
        .when(azureWebAppStepHelper)
        .getPrimaryArtifactConfig(ambiance, primaryArtifactOutcome);
    doReturn(infrastructure).when(cdStepHelper).getInfrastructureOutcome(ambiance);
    doReturn(infraDelegateConfig)
        .when(azureWebAppStepHelper)
        .getInfraDelegateConfig(ambiance, infrastructure, WEB_APP, DEPLOYMENT_SLOT);
    doReturn(taskRequest)
        .when(azureWebAppStepHelper)
        .prepareTaskRequest(
            any(StepElementParameters.class), eq(ambiance), any(TaskParameters.class), any(TaskType.class), anyList());
    doReturn(taskRequest)
        .when(azureWebAppStepHelper)
        .prepareTaskRequest(any(StepElementParameters.class), eq(ambiance), any(TaskParameters.class),
            any(TaskType.class), anyString(), anyList());
    doReturn(gitTaskRequest)
        .when(azureWebAppStepHelper)
        .prepareGitFetchTaskRequest(any(StepElementParameters.class), eq(ambiance), anyMap(), anyList());
    doAnswer(invocation -> {
      GitFetchResponse fetchResponse = invocation.getArgument(1);
      return fetchResponse.getFilesFromMultipleRepo().entrySet().stream().collect(Collectors.toMap(
          Map.Entry::getKey, entry -> AppSettingsFile.create(entry.getValue().getFiles().get(0).getFileContent())));
    })
        .when(azureWebAppStepHelper)
        .getConfigValuesFromGitFetchResponse(eq(ambiance), any(GitFetchResponse.class));
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testStartChainLinkNoConfigs() {
    final StepElementParameters stepElementParameters = createTestStepElementParameters();
    final List<String> expectedCommandUnits =
        asList(SAVE_EXISTING_CONFIGURATIONS, UPDATE_SLOT_CONFIGURATION_SETTINGS, DEPLOY_TO_SLOT);
    doReturn(emptyMap()).when(azureWebAppStepHelper).fetchWebAppConfig(ambiance);

    ArgumentCaptor<TaskParameters> taskParametersArgumentCaptor = ArgumentCaptor.forClass(TaskParameters.class);
    TaskChainResponse taskChainResponse =
        slotDeploymentStep.startChainLinkAfterRbac(ambiance, stepElementParameters, stepInputPackage);

    verify(azureWebAppStepHelper)
        .prepareTaskRequest(eq(stepElementParameters), eq(ambiance), taskParametersArgumentCaptor.capture(),
            eq(TaskType.AZURE_WEB_APP_TASK_NG), eq(FETCH_PREDEPLOYMENT_DATA_TASK_NAME), eq(expectedCommandUnits));
    TaskParameters taskParameters = taskParametersArgumentCaptor.getValue();
    assertThat(taskParameters).isInstanceOf(AzureWebAppFetchPreDeploymentDataRequest.class);
    AzureWebAppFetchPreDeploymentDataRequest dataRequest = (AzureWebAppFetchPreDeploymentDataRequest) taskParameters;
    assertThat(dataRequest.getArtifact()).isSameAs(azureArtifactConfig);
    assertThat(dataRequest.getInfrastructure()).isSameAs(infraDelegateConfig);
    assertThat(dataRequest.getApplicationSettings()).isNull();
    assertThat(dataRequest.getConnectionStrings()).isNull();
    assertThat(dataRequest.getStartupCommand()).isNull();
    assertThat(dataRequest.getTimeoutIntervalInMin()).isEqualTo(10);
    assertThat(taskChainResponse.isChainEnd()).isFalse();
    assertThat(taskChainResponse.getTaskRequest()).isSameAs(taskRequest);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testStartChainLinkGitConfigs() {
    final StepElementParameters stepElementParameters = createTestStepElementParameters();
    final Map<String, GitStoreConfig> gitStoreConfigMap =
        ImmutableMap.of(APPLICATION_SETTINGS, createTestGitStoreConfig());
    final Map<String, StoreConfig> otherTypesConfigMap = ImmutableMap.of(CONNECTION_STRINGS, createTestHarnessStore());
    final Map<String, StoreConfig> configsMap =
        ImmutableMap.<String, StoreConfig>builder().putAll(gitStoreConfigMap).putAll(otherTypesConfigMap).build();

    doReturn(configsMap).when(azureWebAppStepHelper).fetchWebAppConfig(ambiance);

    TaskChainResponse taskChainResponse =
        slotDeploymentStep.startChainLinkAfterRbac(ambiance, stepElementParameters, stepInputPackage);
    verify(azureWebAppStepHelper)
        .prepareGitFetchTaskRequest(eq(stepElementParameters), eq(ambiance), eq(gitStoreConfigMap),
            eq(asList(FetchFiles, SAVE_EXISTING_CONFIGURATIONS, UPDATE_SLOT_CONFIGURATION_SETTINGS, DEPLOY_TO_SLOT)));
    assertThat(taskChainResponse.isChainEnd()).isFalse();
    assertThat(taskChainResponse.getTaskRequest()).isSameAs(gitTaskRequest);
    assertThat(taskChainResponse.getPassThroughData()).isInstanceOf(AzureSlotDeploymentPassThroughData.class);
    AzureSlotDeploymentPassThroughData passThroughData =
        (AzureSlotDeploymentPassThroughData) taskChainResponse.getPassThroughData();
    assertThat(passThroughData.getUnprocessedConfigs()).isEqualTo(otherTypesConfigMap);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testStartChainLinkHarnessStoreConfigs() {
    final StepElementParameters stepElementParameters = createTestStepElementParameters();
    final Map<String, HarnessStore> harnessStoreConfigs =
        ImmutableMap.of(APPLICATION_SETTINGS, createTestHarnessStore(), CONNECTION_STRINGS, createTestHarnessStore());
    final Map<String, StoreConfig> configsMap =
        ImmutableMap.<String, StoreConfig>builder().putAll(harnessStoreConfigs).build();
    doReturn(configsMap).when(azureWebAppStepHelper).fetchWebAppConfig(ambiance);

    ArgumentCaptor<TaskParameters> taskParametersArgumentCaptor = ArgumentCaptor.forClass(TaskParameters.class);
    TaskChainResponse taskChainResponse =
        slotDeploymentStep.startChainLinkAfterRbac(ambiance, stepElementParameters, stepInputPackage);

    verify(azureWebAppStepHelper)
        .prepareTaskRequest(eq(stepElementParameters), eq(ambiance), taskParametersArgumentCaptor.capture(),
            eq(TaskType.AZURE_WEB_APP_TASK_NG), eq(FETCH_PREDEPLOYMENT_DATA_TASK_NAME), anyList());
    verify(azureWebAppStepHelper).fetchWebAppConfigsFromHarnessStore(ambiance, harnessStoreConfigs);

    // After we fetch files from harness store we're going to queue fetch predeployment data task
    assertThat(taskParametersArgumentCaptor.getValue()).isInstanceOf(AzureWebAppFetchPreDeploymentDataRequest.class);
    assertThat(taskChainResponse.isChainEnd()).isFalse();
    assertThat(taskChainResponse.getTaskRequest()).isSameAs(taskRequest);
    assertThat(taskChainResponse.getPassThroughData()).isInstanceOf(AzureSlotDeploymentPassThroughData.class);
    AzureSlotDeploymentPassThroughData passThroughData =
        (AzureSlotDeploymentPassThroughData) taskChainResponse.getPassThroughData();
    assertThat(passThroughData.getUnprocessedConfigs()).isEmpty();
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testExecuteNextLinkGitFetchResponse() throws Exception {
    final StepElementParameters stepElementParameters = createTestStepElementParameters();
    final GitFetchResponse gitFetchResponse =
        GitFetchResponse.builder()
            .filesFromMultipleRepo(ImmutableMap.of(APPLICATION_SETTINGS,
                FetchFilesResult.builder()
                    .files(singletonList(GitFile.builder().fileContent(APP_SETTINGS_FILE_CONTENT).build()))
                    .build(),
                CONNECTION_STRINGS,
                FetchFilesResult.builder()
                    .files(singletonList(GitFile.builder().fileContent(CONN_STRINGS_FILE_CONTENT).build()))
                    .build()))
            .build();
    final AzureSlotDeploymentPassThroughData passThroughData =
        AzureSlotDeploymentPassThroughData.builder()
            .configs(ImmutableMap.of(STARTUP_COMMAND, AppSettingsFile.create("echo 'test'")))
            .unprocessedConfigs(emptyMap())
            .build();

    ArgumentCaptor<TaskParameters> taskParametersArgumentCaptor = ArgumentCaptor.forClass(TaskParameters.class);
    TaskChainResponse taskChainResponse = slotDeploymentStep.executeNextLinkWithSecurityContext(
        ambiance, stepElementParameters, stepInputPackage, passThroughData, () -> gitFetchResponse);

    verify(azureWebAppStepHelper)
        .prepareTaskRequest(eq(stepElementParameters), eq(ambiance), taskParametersArgumentCaptor.capture(),
            eq(TaskType.AZURE_WEB_APP_TASK_NG), eq(FETCH_PREDEPLOYMENT_DATA_TASK_NAME), anyList());

    // Next task will fetch predeployment data since no unprocessed configs left
    assertThat(taskParametersArgumentCaptor.getValue()).isInstanceOf(AzureWebAppFetchPreDeploymentDataRequest.class);
    assertThat(taskChainResponse.isChainEnd()).isFalse();
    assertThat(taskChainResponse.getTaskRequest()).isSameAs(taskRequest);
    assertThat(taskChainResponse.getPassThroughData()).isInstanceOf(AzureSlotDeploymentPassThroughData.class);
    AzureSlotDeploymentPassThroughData newPassThroughData =
        (AzureSlotDeploymentPassThroughData) taskChainResponse.getPassThroughData();
    assertThat(newPassThroughData.getConfigs()).containsKeys(APPLICATION_SETTINGS, CONNECTION_STRINGS, STARTUP_COMMAND);
    assertThat(newPassThroughData.getConfigs().values().stream().map(AppSettingsFile::fetchFileContent))
        .containsExactlyInAnyOrder(APP_SETTINGS_FILE_CONTENT, CONN_STRINGS_FILE_CONTENT, STARTUP_SCRIPT_FILE_CONTENT);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testExecuteNextLinkUnprocessedGitConfigs() throws Exception {
    final StepElementParameters stepElementParameters = createTestStepElementParameters();
    final Map<String, GitStoreConfig> unprocessedGitConfigs =
        ImmutableMap.of(APPLICATION_SETTINGS, createTestGitStoreConfig());
    final Map<String, StoreConfig> allUnprocessedConfigs = ImmutableMap.<String, StoreConfig>builder()
                                                               .putAll(unprocessedGitConfigs)
                                                               .put(STARTUP_COMMAND, createTestHarnessStore())
                                                               .build();

    final AzureSlotDeploymentPassThroughData passThroughData =
        AzureSlotDeploymentPassThroughData.builder()
            .unprocessedConfigs(allUnprocessedConfigs)
            .configs(ImmutableMap.of(CONNECTION_STRINGS, AppSettingsFile.create(CONN_STRINGS_FILE_CONTENT)))
            .build();
    final AzureWebAppTaskResponse azureWebAppTaskResponse = AzureWebAppTaskResponse.builder().build();

    TaskChainResponse taskChainResponse = slotDeploymentStep.executeNextLinkWithSecurityContext(
        ambiance, stepElementParameters, stepInputPackage, passThroughData, () -> azureWebAppTaskResponse);

    verify(azureWebAppStepHelper)
        .prepareGitFetchTaskRequest(stepElementParameters, ambiance, unprocessedGitConfigs,
            asList(FetchFiles, SAVE_EXISTING_CONFIGURATIONS, UPDATE_SLOT_CONFIGURATION_SETTINGS, DEPLOY_TO_SLOT));
    assertThat(taskChainResponse.getPassThroughData()).isInstanceOf(AzureSlotDeploymentPassThroughData.class);
    AzureSlotDeploymentPassThroughData newPassThroughData =
        (AzureSlotDeploymentPassThroughData) taskChainResponse.getPassThroughData();
    assertThat(newPassThroughData.getUnprocessedConfigs()).containsKeys(STARTUP_COMMAND);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testExecuteNextLinkUnprocessedHarnessStoreWithPreDeploymentData() throws Exception {
    final StepElementParameters stepElementParameters = createTestStepElementParameters();
    final Map<String, HarnessStore> unprocessedHarnessStore =
        ImmutableMap.of(APPLICATION_SETTINGS, createTestHarnessStore());
    final Map<String, StoreConfig> allUnprocessedConfigs =
        ImmutableMap.<String, StoreConfig>builder()
            .putAll(unprocessedHarnessStore) // make compiler understand generics
            .build();

    final AzureSlotDeploymentPassThroughData passThroughData =
        AzureSlotDeploymentPassThroughData.builder()
            .unprocessedConfigs(allUnprocessedConfigs)
            .configs(ImmutableMap.of(CONNECTION_STRINGS, AppSettingsFile.create(CONN_STRINGS_FILE_CONTENT)))
            .preDeploymentData(AzureAppServicePreDeploymentData.builder().build())
            .build();
    final AzureWebAppTaskResponse azureWebAppTaskResponse = AzureWebAppTaskResponse.builder().build();
    final ArgumentCaptor<TaskParameters> taskParametersArgumentCaptor = ArgumentCaptor.forClass(TaskParameters.class);

    doReturn(ImmutableMap.of(APPLICATION_SETTINGS, AppSettingsFile.create(APP_SETTINGS_FILE_CONTENT)))
        .when(azureWebAppStepHelper)
        .fetchWebAppConfigsFromHarnessStore(ambiance, unprocessedHarnessStore);

    TaskChainResponse taskChainResponse = slotDeploymentStep.executeNextLinkWithSecurityContext(
        ambiance, stepElementParameters, stepInputPackage, passThroughData, () -> azureWebAppTaskResponse);

    verify(azureWebAppStepHelper)
        .prepareTaskRequest(eq(stepElementParameters), eq(ambiance), taskParametersArgumentCaptor.capture(),
            eq(TaskType.AZURE_WEB_APP_TASK_NG), anyList());

    // Next task will be slot deployment since no unprocessed configs left and predeployment data is already present
    assertThat(taskParametersArgumentCaptor.getValue()).isInstanceOf(AzureWebAppSlotDeploymentRequest.class);

    verify(azureWebAppStepHelper).fetchWebAppConfigsFromHarnessStore(ambiance, unprocessedHarnessStore);
    assertThat(taskChainResponse.isChainEnd()).isTrue();
    assertThat(taskChainResponse.getPassThroughData()).isInstanceOf(AzureSlotDeploymentPassThroughData.class);
    AzureSlotDeploymentPassThroughData newPassThroughData =
        (AzureSlotDeploymentPassThroughData) taskChainResponse.getPassThroughData();
    assertThat(newPassThroughData.getUnprocessedConfigs()).isEmpty();
    assertThat(newPassThroughData.getConfigs()).containsKeys(APPLICATION_SETTINGS, CONNECTION_STRINGS);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testExecuteNextLinkWithFetchPreDeploymentDataResponse() throws Exception {
    testExecuteNextLinkWithFetchPreDeploymentDataResponse(null);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testExecuteNextLinkWithFetchPreDeploymentDataResponsePrevExecutionDetails() throws Exception {
    final AzureWebAppsStageExecutionDetails prevExecutionDetails =
        AzureWebAppsStageExecutionDetails.builder()
            .userAddedAppSettingNames(ImmutableSet.of("APP1", "APP2"))
            .userAddedConnStringNames(ImmutableSet.of("CONN1", "CONN2"))
            .userChangedStartupCommand(true)
            .build();

    AzureWebAppSlotDeploymentRequest slotDeploymentRequest =
        testExecuteNextLinkWithFetchPreDeploymentDataResponse(prevExecutionDetails);
    assertThat(slotDeploymentRequest.getPrevExecUserAddedAppSettingNames())
        .isEqualTo(prevExecutionDetails.getUserAddedAppSettingNames());
    assertThat(slotDeploymentRequest.getPrevExecUserAddedConnStringNames())
        .isEqualTo(prevExecutionDetails.getUserAddedConnStringNames());
    assertThat(slotDeploymentRequest.isPrevExecUserChangedStartupCommand())
        .isEqualTo(prevExecutionDetails.getUserChangedStartupCommand());
    assertThat(slotDeploymentRequest.isCleanDeployment()).isFalse();
  }

  private AzureWebAppSlotDeploymentRequest testExecuteNextLinkWithFetchPreDeploymentDataResponse(
      AzureWebAppsStageExecutionDetails prevExecutionDetails) throws Exception {
    final StepElementParameters stepElementParameters = createTestStepElementParameters();
    return runExecuteNextLinkToQueueDeploymentTask(prevExecutionDetails, stepElementParameters);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testFinalizeExecution() throws Exception {
    final StepElementParameters stepElementParameters = createTestStepElementParameters();
    final String deploymentProgressMarker = "TestSlotDeployProgressMarker";
    final List<AzureAppDeploymentData> appDeploymentData =
        asList(AzureAppDeploymentData.builder().build(), AzureAppDeploymentData.builder().build());
    final List<UnitProgress> unitProgresses = singletonList(UnitProgress.newBuilder().build());
    final UnitProgressData unitProgressData = UnitProgressData.builder().unitProgresses(unitProgresses).build();
    final Set<String> userAddedAppSettings = ImmutableSet.of("APP1", "APP2");
    final Set<String> userAddedConnStrings = ImmutableSet.of("CONN1", "CONN2");
    final AzureWebAppTaskResponse azureWebAppTaskResponse =
        AzureWebAppTaskResponse.builder()
            .requestResponse(AzureWebAppSlotDeploymentResponse.builder()
                                 .deploymentProgressMarker(deploymentProgressMarker)
                                 .azureAppDeploymentData(appDeploymentData)
                                 .userChangedStartupCommand(true)
                                 .userAddedAppSettingNames(userAddedAppSettings)
                                 .userAddedConnStringNames(userAddedConnStrings)
                                 .build())
            .commandUnitsProgress(unitProgressData)
            .build();

    final AzureAppServicePreDeploymentData preDeploymentData = AzureAppServicePreDeploymentData.builder().build();
    final AzureSlotDeploymentPassThroughData passThroughData =
        AzureSlotDeploymentPassThroughData.builder().preDeploymentData(preDeploymentData).build();
    final ArgumentCaptor<AzureWebAppSlotDeploymentDataOutput> slotDeploymentDataOutputArgumentCaptor =
        ArgumentCaptor.forClass(AzureWebAppSlotDeploymentDataOutput.class);
    doReturn(AzureContainerArtifactConfig.builder().build())
        .when(azureWebAppStepHelper)
        .getPrimaryArtifactConfig(any(), any());

    doReturn("deploymentInfoKey").when(azureWebAppStepHelper).getDeploymentIdentifier(any(), any(), any());

    StepResponse stepResponse = slotDeploymentStep.finalizeExecutionWithSecurityContext(
        ambiance, stepElementParameters, passThroughData, () -> azureWebAppTaskResponse);

    verify(executionSweepingOutputService)
        .consume(eq(ambiance), eq(AzureWebAppSlotDeploymentDataOutput.OUTPUT_NAME),
            slotDeploymentDataOutputArgumentCaptor.capture(), eq(StepCategory.STEP.name()));

    assertThat(stepResponse.getStatus()).isEqualTo(Status.SUCCEEDED);
    assertThat(stepResponse.getUnitProgressList()).isEqualTo(unitProgresses);
    AzureWebAppSlotDeploymentDataOutput slotDeploymentDataOutput = slotDeploymentDataOutputArgumentCaptor.getValue();
    assertThat(slotDeploymentDataOutput.getDeploymentProgressMarker()).isEqualTo(deploymentProgressMarker);
    verify(instanceInfoService, times(1)).saveServerInstancesIntoSweepingOutput(any(), anyList());

    ArgumentCaptor<Map<String, Object>> updatesCaptor = ArgumentCaptor.forClass(Map.class);
    verify(stageExecutionInfoService).update(any(Scope.class), eq(STAGE_EXECUTION_ID), updatesCaptor.capture());
    Map<String, Object> executionUpdates = updatesCaptor.getValue();
    assertThat(executionUpdates)
        .containsAllEntriesOf(ImmutableMap.of(StageExecutionInfoKeys.deploymentIdentifier, "deploymentInfoKey",
            String.format("%s.%s", StageExecutionInfoKeys.executionDetails,
                AzureWebAppsStageExecutionDetailsKeys.userChangedStartupCommand),
            true,
            String.format("%s.%s", StageExecutionInfoKeys.executionDetails,
                AzureWebAppsStageExecutionDetailsKeys.userAddedAppSettingNames),
            userAddedAppSettings,
            String.format("%s.%s", StageExecutionInfoKeys.executionDetails,
                AzureWebAppsStageExecutionDetailsKeys.userAddedConnStringNames),
            userAddedConnStrings));
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testFinalizeExecutionWithPackageArtifact() throws Exception {
    AzurePackageArtifactConfig artifactConfig = AzurePackageArtifactConfig.builder().build();
    final StepElementParameters stepElementParameters = createTestStepElementParameters();
    final String deploymentProgressMarker = "TestSlotDeployProgressMarker";
    final List<AzureAppDeploymentData> appDeploymentData =
        asList(AzureAppDeploymentData.builder().build(), AzureAppDeploymentData.builder().build());
    final List<UnitProgress> unitProgresses = singletonList(UnitProgress.newBuilder().build());
    final UnitProgressData unitProgressData = UnitProgressData.builder().unitProgresses(unitProgresses).build();
    final AzureWebAppTaskResponse azureWebAppTaskResponse =
        AzureWebAppTaskResponse.builder()
            .requestResponse(AzureWebAppSlotDeploymentResponse.builder()
                                 .deploymentProgressMarker(deploymentProgressMarker)
                                 .azureAppDeploymentData(appDeploymentData)
                                 .build())
            .commandUnitsProgress(unitProgressData)
            .build();
    final AzureSlotDeploymentPassThroughData passThroughData =
        AzureSlotDeploymentPassThroughData.builder()
            .preDeploymentData(AzureAppServicePreDeploymentData.builder().build())
            .build();
    final ArgumentCaptor<AzureWebAppSlotDeploymentDataOutput> slotDeploymentDataOutputArgumentCaptor =
        ArgumentCaptor.forClass(AzureWebAppSlotDeploymentDataOutput.class);
    doReturn(artifactConfig).when(azureWebAppStepHelper).getPrimaryArtifactConfig(any(), any());
    doReturn("rollbackDeploymentInfoKey").when(azureWebAppStepHelper).getDeploymentIdentifier(any(), any(), any());

    StepResponse stepResponse = slotDeploymentStep.finalizeExecutionWithSecurityContext(
        ambiance, stepElementParameters, passThroughData, () -> azureWebAppTaskResponse);

    verify(executionSweepingOutputService)
        .consume(eq(ambiance), eq(AzureWebAppSlotDeploymentDataOutput.OUTPUT_NAME),
            slotDeploymentDataOutputArgumentCaptor.capture(), eq(StepCategory.STEP.name()));
    verify(instanceInfoService, times(1)).saveServerInstancesIntoSweepingOutput(any(), anyList());
    verify(stageExecutionInfoService, times(1)).update(any(), any(), any());

    assertThat(stepResponse.getStatus()).isEqualTo(Status.SUCCEEDED);
    assertThat(stepResponse.getUnitProgressList()).isEqualTo(unitProgresses);
    AzureWebAppSlotDeploymentDataOutput slotDeploymentDataOutput = slotDeploymentDataOutputArgumentCaptor.getValue();
    assertThat(slotDeploymentDataOutput.getDeploymentProgressMarker()).isEqualTo(deploymentProgressMarker);
  }

  @Test
  @SneakyThrows
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testQueueSlotDeploymentWithCleanOptionFFDisabled() {
    StepElementParameters stepElementParameters = createTestStepElementParameters(true);
    doReturn(false).when(cdFeatureFlagHelper).isEnabled(anyString(), eq(FeatureName.CDS_WEBAPP_ENABLE_CLEAN_OPTION));
    AzureWebAppSlotDeploymentRequest queuedRequest =
        runExecuteNextLinkToQueueDeploymentTask(null, stepElementParameters);
    assertThat(queuedRequest.isCleanDeployment()).isFalse();
  }

  @Test
  @SneakyThrows
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testQueueSlotDeploymentWithCleanOptionDisabledAndFFDisabled() {
    StepElementParameters stepElementParameters = createTestStepElementParameters(false);
    doReturn(false).when(cdFeatureFlagHelper).isEnabled(anyString(), eq(FeatureName.CDS_WEBAPP_ENABLE_CLEAN_OPTION));
    AzureWebAppSlotDeploymentRequest queuedRequest =
        runExecuteNextLinkToQueueDeploymentTask(null, stepElementParameters);
    assertThat(queuedRequest.isCleanDeployment()).isFalse();
  }

  @Test
  @SneakyThrows
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testQueueSlotDeploymentWithCleanOptionFFEnabled() {
    StepElementParameters stepElementParameters = createTestStepElementParameters(true);
    doReturn(true).when(cdFeatureFlagHelper).isEnabled(anyString(), eq(FeatureName.CDS_WEBAPP_ENABLE_CLEAN_OPTION));
    AzureWebAppSlotDeploymentRequest queuedRequest =
        runExecuteNextLinkToQueueDeploymentTask(null, stepElementParameters);
    assertThat(queuedRequest.isCleanDeployment()).isTrue();
  }

  @Test
  @SneakyThrows
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testQueueSlotDeploymentWithCleanOptionDisabledAndFFEnabled() {
    StepElementParameters stepElementParameters = createTestStepElementParameters(false);
    doReturn(true).when(cdFeatureFlagHelper).isEnabled(anyString(), eq(FeatureName.CDS_WEBAPP_ENABLE_CLEAN_OPTION));
    AzureWebAppSlotDeploymentRequest queuedRequest =
        runExecuteNextLinkToQueueDeploymentTask(null, stepElementParameters);
    assertThat(queuedRequest.isCleanDeployment()).isFalse();
  }

  private AzureWebAppSlotDeploymentRequest runExecuteNextLinkToQueueDeploymentTask(
      AzureWebAppsStageExecutionDetails prevExecutionDetails, StepElementParameters stepElementParameters)
      throws Exception {
    final AzureSlotDeploymentPassThroughData passThroughData = AzureSlotDeploymentPassThroughData.builder()
                                                                   .configs(emptyMap())
                                                                   .unprocessedConfigs(emptyMap())
                                                                   .infrastructure(infrastructure)
                                                                   .primaryArtifactOutcome(primaryArtifactOutcome)
                                                                   .build();
    final AzureAppServicePreDeploymentData preDeploymentData = AzureAppServicePreDeploymentData.builder().build();
    final AzureWebAppTaskResponse azureWebAppTaskResponse =
        AzureWebAppTaskResponse.builder()
            .requestResponse(
                AzureWebAppFetchPreDeploymentDataResponse.builder().preDeploymentData(preDeploymentData).build())
            .build();
    final ArgumentCaptor<TaskParameters> taskParametersArgumentCaptor = ArgumentCaptor.forClass(TaskParameters.class);
    final ArgumentCaptor<AzureWebAppPreDeploymentDataOutput> preDeploymentDataOutputArgumentCaptor =
        ArgumentCaptor.forClass(AzureWebAppPreDeploymentDataOutput.class);

    doReturn(prevExecutionDetails)
        .when(azureWebAppStepHelper)
        .findLastSuccessfulStageExecutionDetails(ambiance, infraDelegateConfig);

    TaskChainResponse taskChainResponse = slotDeploymentStep.executeNextLinkWithSecurityContext(
        ambiance, stepElementParameters, stepInputPackage, passThroughData, () -> azureWebAppTaskResponse);

    verify(azureWebAppStepHelper)
        .prepareTaskRequest(eq(stepElementParameters), eq(ambiance), taskParametersArgumentCaptor.capture(),
            eq(TaskType.AZURE_WEB_APP_TASK_NG), anyList());
    verify(executionSweepingOutputService)
        .consume(eq(ambiance), eq(AzureWebAppPreDeploymentDataOutput.OUTPUT_NAME),
            preDeploymentDataOutputArgumentCaptor.capture(), eq(StepCategory.STEP.name()));

    assertThat(taskChainResponse.isChainEnd()).isTrue();
    assertThat(taskChainResponse.getTaskRequest()).isSameAs(taskRequest);
    assertThat(taskParametersArgumentCaptor.getValue()).isInstanceOf(AzureWebAppSlotDeploymentRequest.class);
    AzureWebAppSlotDeploymentRequest slotDeploymentRequest =
        (AzureWebAppSlotDeploymentRequest) taskParametersArgumentCaptor.getValue();
    assertThat(slotDeploymentRequest.getPreDeploymentData()).isSameAs(preDeploymentData);
    assertThat(slotDeploymentRequest.getArtifact()).isSameAs(azureArtifactConfig);
    assertThat(slotDeploymentRequest.getInfrastructure()).isSameAs(infraDelegateConfig);

    return slotDeploymentRequest;
  }

  private StepElementParameters createTestStepElementParameters() {
    return createTestStepElementParameters(null);
  }

  private StepElementParameters createTestStepElementParameters(Boolean clean) {
    return StepElementParameters.builder()
        .spec(AzureWebAppSlotDeploymentStepParameters.infoBuilder()
                  .webApp(ParameterField.createValueField(WEB_APP))
                  .deploymentSlot(ParameterField.createValueField(DEPLOYMENT_SLOT))
                  .clean(clean != null ? ParameterField.createValueField(clean) : null)
                  .build())
        .timeout(ParameterField.createValueField("10m"))
        .build();
  }

  private GitStoreConfig createTestGitStoreConfig() {
    return BitbucketStore.builder()
        .branch(ParameterField.createValueField("main"))
        .gitFetchType(FetchType.BRANCH)
        .paths(ParameterField.createValueField(singletonList("test")))
        .build();
  }

  private HarnessStore createTestHarnessStore() {
    return HarnessStore.builder().files(ParameterField.createValueField(singletonList("project:/test"))).build();
  }
}