/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.azure.webapp;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.cdng.manifest.yaml.harness.HarnessStoreConstants.HARNESS_STORE_TYPE;
import static io.harness.cdng.stepsdependency.constants.OutcomeExpressionConstants.APPLICATION_SETTINGS;
import static io.harness.cdng.stepsdependency.constants.OutcomeExpressionConstants.CONNECTION_STRINGS;
import static io.harness.cdng.stepsdependency.constants.OutcomeExpressionConstants.STARTUP_COMMAND;
import static io.harness.delegate.task.artifacts.ArtifactSourceConstants.AMAZON_S3_NAME;
import static io.harness.delegate.task.artifacts.ArtifactSourceConstants.ARTIFACTORY_REGISTRY_NAME;
import static io.harness.delegate.task.artifacts.ArtifactSourceConstants.AZURE_ARTIFACTS_NAME;
import static io.harness.delegate.task.artifacts.ArtifactSourceConstants.DOCKER_REGISTRY_NAME;
import static io.harness.delegate.task.artifacts.ArtifactSourceConstants.JENKINS_NAME;
import static io.harness.delegate.task.artifacts.ArtifactSourceConstants.NEXUS3_REGISTRY_NAME;
import static io.harness.delegate.task.artifacts.ArtifactSourceType.AMAZONS3;
import static io.harness.delegate.task.artifacts.ArtifactSourceType.AZURE_ARTIFACTS;
import static io.harness.delegate.task.artifacts.ArtifactSourceType.JENKINS;
import static io.harness.rule.OwnerRule.ABOSII;
import static io.harness.rule.OwnerRule.TMACARI;
import static io.harness.rule.OwnerRule.VLAD;
import static io.harness.rule.OwnerRule.VLICA;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.Scope;
import io.harness.category.element.UnitTests;
import io.harness.cdng.CDNGTestBase;
import io.harness.cdng.CDStepHelper;
import io.harness.cdng.artifact.outcome.ArtifactoryArtifactOutcome;
import io.harness.cdng.artifact.outcome.AzureArtifactsOutcome;
import io.harness.cdng.artifact.outcome.DockerArtifactOutcome;
import io.harness.cdng.artifact.outcome.JenkinsArtifactOutcome;
import io.harness.cdng.artifact.outcome.NexusArtifactOutcome;
import io.harness.cdng.artifact.outcome.S3ArtifactOutcome;
import io.harness.cdng.azure.AzureHelperService;
import io.harness.cdng.azure.config.ApplicationSettingsOutcome;
import io.harness.cdng.azure.config.ConnectionStringsOutcome;
import io.harness.cdng.azure.config.StartupCommandOutcome;
import io.harness.cdng.azure.webapp.beans.AzureWebAppPreDeploymentDataOutput;
import io.harness.cdng.azure.webapp.steps.NgAppSettingsSweepingOutput;
import io.harness.cdng.azure.webapp.steps.NgConnectionStringsSweepingOutput;
import io.harness.cdng.execution.ExecutionInfoKey;
import io.harness.cdng.execution.StageExecutionInfo;
import io.harness.cdng.execution.azure.webapps.AzureWebAppsStageExecutionDetails;
import io.harness.cdng.execution.service.StageExecutionInfoService;
import io.harness.cdng.expressions.CDExpressionResolver;
import io.harness.cdng.featureFlag.CDFeatureFlagHelper;
import io.harness.cdng.infra.beans.AzureWebAppInfrastructureOutcome;
import io.harness.cdng.infra.beans.InfrastructureOutcome;
import io.harness.cdng.manifest.ManifestStoreType;
import io.harness.cdng.manifest.yaml.GitStore;
import io.harness.cdng.manifest.yaml.GitStoreConfig;
import io.harness.cdng.manifest.yaml.harness.HarnessStore;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfig;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfigWrapper;
import io.harness.cdng.service.steps.ServiceStepOutcome;
import io.harness.cdng.service.steps.constants.ServiceStepV3Constants;
import io.harness.cdng.stepsdependency.constants.OutcomeExpressionConstants;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.beans.azure.registry.AzureRegistryType;
import io.harness.delegate.beans.connector.ConnectorConfigDTO;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.beans.connector.artifactoryconnector.ArtifactoryAuthType;
import io.harness.delegate.beans.connector.artifactoryconnector.ArtifactoryAuthenticationDTO;
import io.harness.delegate.beans.connector.artifactoryconnector.ArtifactoryConnectorDTO;
import io.harness.delegate.beans.connector.artifactoryconnector.ArtifactoryUsernamePasswordAuthDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsConnectorDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsCredentialDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsCredentialType;
import io.harness.delegate.beans.connector.awsconnector.AwsManualConfigSpecDTO;
import io.harness.delegate.beans.connector.azureartifacts.AzureArtifactsAuthenticationDTO;
import io.harness.delegate.beans.connector.azureartifacts.AzureArtifactsAuthenticationType;
import io.harness.delegate.beans.connector.azureartifacts.AzureArtifactsConnectorDTO;
import io.harness.delegate.beans.connector.azureartifacts.AzureArtifactsCredentialsDTO;
import io.harness.delegate.beans.connector.azureartifacts.AzureArtifactsTokenDTO;
import io.harness.delegate.beans.connector.azureconnector.AzureConnectorDTO;
import io.harness.delegate.beans.connector.docker.DockerAuthType;
import io.harness.delegate.beans.connector.docker.DockerAuthenticationDTO;
import io.harness.delegate.beans.connector.docker.DockerConnectorDTO;
import io.harness.delegate.beans.connector.jenkins.JenkinsAuthType;
import io.harness.delegate.beans.connector.jenkins.JenkinsAuthenticationDTO;
import io.harness.delegate.beans.connector.jenkins.JenkinsConnectorDTO;
import io.harness.delegate.beans.connector.jenkins.JenkinsUserNamePasswordDTO;
import io.harness.delegate.beans.connector.nexusconnector.NexusAuthType;
import io.harness.delegate.beans.connector.nexusconnector.NexusAuthenticationDTO;
import io.harness.delegate.beans.connector.nexusconnector.NexusConnectorDTO;
import io.harness.delegate.beans.storeconfig.FetchType;
import io.harness.delegate.task.azure.appservice.AzureAppServicePreDeploymentData;
import io.harness.delegate.task.azure.appservice.settings.AppSettingsFile;
import io.harness.delegate.task.azure.appservice.webapp.ng.AzureWebAppInfraDelegateConfig;
import io.harness.delegate.task.azure.appservice.webapp.ng.request.AzureWebAppFetchPreDeploymentDataRequest;
import io.harness.delegate.task.azure.artifact.AwsS3AzureArtifactRequestDetails;
import io.harness.delegate.task.azure.artifact.AzureArtifactConfig;
import io.harness.delegate.task.azure.artifact.AzureArtifactType;
import io.harness.delegate.task.azure.artifact.AzureContainerArtifactConfig;
import io.harness.delegate.task.azure.artifact.AzureDevOpsArtifactRequestDetails;
import io.harness.delegate.task.azure.artifact.AzurePackageArtifactConfig;
import io.harness.delegate.task.azure.artifact.JenkinsAzureArtifactRequestDetails;
import io.harness.delegate.task.azure.artifact.NexusAzureArtifactRequestDetails;
import io.harness.delegate.task.git.GitFetchFilesConfig;
import io.harness.delegate.task.git.GitFetchRequest;
import io.harness.delegate.task.git.GitFetchResponse;
import io.harness.encryption.SecretRefData;
import io.harness.exception.InvalidArgumentsException;
import io.harness.filestore.dto.node.FileNodeDTO;
import io.harness.filestore.service.FileStoreService;
import io.harness.git.model.FetchFilesResult;
import io.harness.git.model.GitFile;
import io.harness.ng.core.NGAccess;
import io.harness.ng.core.api.NGEncryptedDataService;
import io.harness.plancreator.steps.TaskSelectorYaml;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.expression.EngineExpressionService;
import io.harness.pms.plan.execution.SetupAbstractionKeys;
import io.harness.pms.sdk.core.data.OptionalOutcome;
import io.harness.pms.sdk.core.data.OptionalSweepingOutput;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outcome.OutcomeService;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;
import io.harness.secretmanagerclient.services.api.SecretManagerClientService;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.steps.environment.EnvironmentOutcome;

import software.wings.beans.TaskType;
import software.wings.utils.RepositoryFormat;

import com.google.common.collect.ImmutableMap;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@OwnedBy(CDP)
public class AzureWebAppStepHelperTest extends CDNGTestBase {
  private static final String TEST_TASK_SELECTOR = "test-selector";
  private static final String ACCOUNT_ID = "accountId";
  private static final String PROJECT_ID = "project";
  private static final String ORG_ID = "org";
  private static final String FILE_STORE_TEST_PATH = "/Root/test/path";
  private static final String INFRA_CONNECTOR_REF = "account.azure";
  private static final String SUBSCRIPTION_ID = "subscriptionId";
  private static final String RESOURCE_GROUP = "resourceGroup";
  private static final String APP_NAME = "appName";
  private static final String DEPLOYMENT_SLOT = "deploymentSlot";
  private static final String STAGE_EXECUTION_ID = "stageExecutionId";

  @Mock private OutcomeService outcomeService;
  @Mock private FileStoreService fileStoreService;
  @Mock private CDStepHelper cdStepHelper;
  @Mock private AzureHelperService azureHelperService;
  @Mock private EngineExpressionService engineExpressionService;
  @Mock private CDExpressionResolver cdExpressionResolver;
  @Mock private SecretManagerClientService secretManagerClientService;
  @Mock private ExecutionSweepingOutputService executionSweepingOutputService;
  @Mock private NGEncryptedDataService ngEncryptedDataService;
  @Mock private StageExecutionInfoService stageExecutionInfoService;
  @Mock private CDFeatureFlagHelper cdFeatureFlagHelper;
  @Mock private ExecutionSweepingOutputService sweepingOutputService;

  @InjectMocks private AzureWebAppStepHelper stepHelper;

  private final Ambiance ambiance = Ambiance.newBuilder()
                                        .putSetupAbstractions(SetupAbstractionKeys.accountId, ACCOUNT_ID)
                                        .putSetupAbstractions(SetupAbstractionKeys.projectIdentifier, PROJECT_ID)
                                        .putSetupAbstractions(SetupAbstractionKeys.orgIdentifier, ORG_ID)
                                        .setStageExecutionId(STAGE_EXECUTION_ID)
                                        .build();

  @Before
  public void setup() {
    doAnswer(invocation -> invocation.getArgument(1))
        .when(engineExpressionService)
        .renderExpression(eq(ambiance), anyString());
    doAnswer(invocation -> invocation.getArgument(1)).when(cdExpressionResolver).updateExpressions(eq(ambiance), any());
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testGetExecutionInfoKey() {
    String appName = "appName";
    String deploymentSlot = "deploymentSlot";
    String subscription = "subscription";
    String resourceGroup = "resourceGroup";
    AzureWebAppInfrastructureOutcome azureWebAppInfrastructureOutcome =
        AzureWebAppInfrastructureOutcome.builder()
            .resourceGroup(resourceGroup)
            .environment(EnvironmentOutcome.builder().identifier("environmentId").build())
            .subscription(subscription)
            .build();
    azureWebAppInfrastructureOutcome.setInfraIdentifier("infraId");

    when(outcomeService.resolve(any(), any())).thenReturn(ServiceStepOutcome.builder().identifier("serviceId").build());
    doReturn(azureWebAppInfrastructureOutcome).when(cdStepHelper).getInfrastructureOutcome(any());

    ExecutionInfoKey executionInfoKey = stepHelper.getExecutionInfoKey(
        ambiance, AzureWebAppInfraDelegateConfig.builder().appName(appName).deploymentSlot(deploymentSlot).build());

    assertThat(executionInfoKey.getServiceIdentifier()).isEqualTo("serviceId");
    assertThat(executionInfoKey.getInfraIdentifier()).isEqualTo("infraId");
    assertThat(executionInfoKey.getEnvIdentifier()).isEqualTo("environmentId");

    Scope scope = executionInfoKey.getScope();
    assertThat(scope.getOrgIdentifier()).isEqualTo(ORG_ID);
    assertThat(scope.getAccountIdentifier()).isEqualTo(ACCOUNT_ID);
    assertThat(scope.getProjectIdentifier()).isEqualTo(PROJECT_ID);
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testGetDeploymentIdentifier() {
    String appName = "appName";
    String deploymentSlot = "deploymentSlot";
    String subscription = "subscription";
    String resourceGroup = "resourceGroup";
    AzureWebAppInfrastructureOutcome azureWebAppInfrastructureOutcome =
        AzureWebAppInfrastructureOutcome.builder().resourceGroup(resourceGroup).subscription(subscription).build();
    doReturn(azureWebAppInfrastructureOutcome).when(cdStepHelper).getInfrastructureOutcome(any());

    String deploymentIdentifier = stepHelper.getDeploymentIdentifier(ambiance, appName, deploymentSlot);
    assertThat(deploymentIdentifier)
        .isEqualTo(String.format("%s-%s-%s-%s", subscription, resourceGroup, appName, deploymentSlot));
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testGetPreDeploymentDataSweepingOutputNotFound() {
    doReturn(OptionalSweepingOutput.builder().found(false).build())
        .when(executionSweepingOutputService)
        .resolveOptional(any(), any());

    AzureAppServicePreDeploymentData azureAppServicePreDeploymentData =
        stepHelper.getPreDeploymentData(ambiance, "test");

    verify(executionSweepingOutputService).resolveOptional(any(), any());
    assertThat(azureAppServicePreDeploymentData).isNull();
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testGetPreDeploymentData() {
    AzureAppServicePreDeploymentData preDeploymentData = AzureAppServicePreDeploymentData.builder().build();
    doReturn(OptionalSweepingOutput.builder()
                 .output(AzureWebAppPreDeploymentDataOutput.builder().preDeploymentData(preDeploymentData).build())
                 .found(true)
                 .build())
        .when(executionSweepingOutputService)
        .resolveOptional(any(), any());

    AzureAppServicePreDeploymentData azureAppServicePreDeploymentData =
        stepHelper.getPreDeploymentData(ambiance, "test");
    verify(executionSweepingOutputService).resolveOptional(any(), any());
    assertThat(azureAppServicePreDeploymentData).isEqualTo(preDeploymentData);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testFetchWebAppConfig() {
    final StartupCommandOutcome startupCommandOutcome =
        StartupCommandOutcome.builder().store(createTestGitStore()).build();
    final ApplicationSettingsOutcome applicationSettingsOutcome =
        ApplicationSettingsOutcome.builder().store(createTestHarnessStore()).build();
    final ConnectionStringsOutcome connectionStringsOutcome =
        ConnectionStringsOutcome.builder().store(createTestGitStore()).build();

    doReturn(OptionalOutcome.builder().outcome(startupCommandOutcome).found(true).build())
        .when(outcomeService)
        .resolveOptional(ambiance, RefObjectUtils.getOutcomeRefObject(STARTUP_COMMAND));
    doReturn(OptionalOutcome.builder().outcome(applicationSettingsOutcome).found(true).build())
        .when(outcomeService)
        .resolveOptional(ambiance, RefObjectUtils.getOutcomeRefObject(APPLICATION_SETTINGS));
    doReturn(OptionalOutcome.builder().outcome(connectionStringsOutcome).found(true).build())
        .when(outcomeService)
        .resolveOptional(ambiance, RefObjectUtils.getOutcomeRefObject(CONNECTION_STRINGS));
    doReturn(OptionalSweepingOutput.builder().found(false).build())
        .when(sweepingOutputService)
        .resolveOptional(eq(ambiance), any());

    final Map<String, StoreConfig> webAppConfigs = stepHelper.fetchWebAppConfig(ambiance);

    assertThat(webAppConfigs).containsKeys(STARTUP_COMMAND, APPLICATION_SETTINGS, CONNECTION_STRINGS);
    assertThat(webAppConfigs.get(STARTUP_COMMAND).getKind()).isEqualTo(ManifestStoreType.GIT);
    assertThat(webAppConfigs.get(APPLICATION_SETTINGS).getKind()).isEqualTo(HARNESS_STORE_TYPE);
    assertThat(webAppConfigs.get(CONNECTION_STRINGS).getKind()).isEqualTo(ManifestStoreType.GIT);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testFetchWebAppConfigEmpty() {
    doReturn(OptionalOutcome.builder().found(false).build())
        .when(outcomeService)
        .resolveOptional(ambiance, RefObjectUtils.getOutcomeRefObject(STARTUP_COMMAND));
    doReturn(OptionalOutcome.builder().found(false).build())
        .when(outcomeService)
        .resolveOptional(ambiance, RefObjectUtils.getOutcomeRefObject(APPLICATION_SETTINGS));
    doReturn(OptionalOutcome.builder().found(false).build())
        .when(outcomeService)
        .resolveOptional(ambiance, RefObjectUtils.getOutcomeRefObject(CONNECTION_STRINGS));
    doReturn(OptionalSweepingOutput.builder().found(false).build())
        .when(sweepingOutputService)
        .resolveOptional(eq(ambiance), any());

    final Map<String, StoreConfig> webAppConfigs = stepHelper.fetchWebAppConfig(ambiance);

    assertThat(webAppConfigs).isEmpty();
  }

  @Test
  @Owner(developers = VLAD)
  @Category(UnitTests.class)
  public void testFetchWebAppConfigWithOverrides() {
    final StartupCommandOutcome startupCommandOutcome =
        StartupCommandOutcome.builder().store(createTestGitStore()).build();
    final ApplicationSettingsOutcome applicationSettingsOutcome =
        ApplicationSettingsOutcome.builder().store(createTestHarnessStore()).build();
    final ConnectionStringsOutcome connectionStringsOutcome =
        ConnectionStringsOutcome.builder().store(createTestGitStore()).build();

    doReturn(OptionalOutcome.builder().outcome(startupCommandOutcome).found(true).build())
        .when(outcomeService)
        .resolveOptional(ambiance, RefObjectUtils.getOutcomeRefObject(STARTUP_COMMAND));
    doReturn(OptionalOutcome.builder().outcome(applicationSettingsOutcome).found(true).build())
        .when(outcomeService)
        .resolveOptional(ambiance, RefObjectUtils.getOutcomeRefObject(APPLICATION_SETTINGS));
    doReturn(OptionalOutcome.builder().outcome(connectionStringsOutcome).found(true).build())
        .when(outcomeService)
        .resolveOptional(ambiance, RefObjectUtils.getOutcomeRefObject(CONNECTION_STRINGS));

    doReturn(OptionalSweepingOutput.builder()
                 .found(true)
                 .output(NgAppSettingsSweepingOutput.builder().store(StoreConfigWrapper.builder().build()).build())
                 .build())
        .when(sweepingOutputService)
        .resolveOptional(eq(ambiance),
            eq(RefObjectUtils.getOutcomeRefObject(ServiceStepV3Constants.SERVICE_APP_SETTINGS_SWEEPING_OUTPUT)));

    doReturn(
        OptionalSweepingOutput.builder()
            .found(true)
            .output(NgConnectionStringsSweepingOutput.builder().store(StoreConfigWrapper.builder().build()).build())
            .build())
        .when(sweepingOutputService)
        .resolveOptional(eq(ambiance),
            eq(RefObjectUtils.getOutcomeRefObject(ServiceStepV3Constants.SERVICE_CONNECTION_STRINGS_SWEEPING_OUTPUT)));

    final Map<String, StoreConfig> webAppConfigs = stepHelper.fetchWebAppConfig(ambiance);

    assertThat(webAppConfigs).containsKeys(STARTUP_COMMAND, APPLICATION_SETTINGS, CONNECTION_STRINGS);
    assertThat(webAppConfigs.get(STARTUP_COMMAND).getKind()).isEqualTo(ManifestStoreType.GIT);
    assertThat(webAppConfigs.get(APPLICATION_SETTINGS).getKind()).isEqualTo(HARNESS_STORE_TYPE);
    assertThat(webAppConfigs.get(CONNECTION_STRINGS).getKind()).isEqualTo(ManifestStoreType.GIT);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testFilterAndMapConfigs() {
    final Map<String, StoreConfig> source =
        ImmutableMap.of("git1", createTestGitStore(), "harness1", createTestHarnessStore(), "git2",
            createTestGitStore(), "git3", createTestGitStore(), "harness2", createTestHarnessStore());

    Map<String, GitStoreConfig> gitStoreResult =
        AzureWebAppStepHelper.filterAndMapConfigs(source, ManifestStoreType::isInGitSubset);
    Map<String, HarnessStore> harnessStoreResult =
        AzureWebAppStepHelper.filterAndMapConfigs(source, HARNESS_STORE_TYPE::equals);

    //    assertThat(gitStoreResult).containsKeys("git1", "git2", "git3");
    assertThat(harnessStoreResult).containsKeys("harness1", "harness2");
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testGetConfigDifference() {
    final Map<String, StoreConfig> source1 =
        ImmutableMap.of("git1", createTestGitStore(), "harness1", createTestHarnessStore(), "harness2",
            createTestHarnessStore(), "harness3", createTestHarnessStore(), "git2", createTestGitStore());

    final Map<String, StoreConfig> source2 = ImmutableMap.of(
        "git1", createTestGitStore(), "harness1", createTestHarnessStore(), "harness3", createTestHarnessStore());

    final Map<String, StoreConfig> result = AzureWebAppStepHelper.getConfigDifference(source1, source2);
    assertThat(result).containsKeys("harness2", "git2");
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testGetConfigDifferenceViolation() {
    final Map<String, StoreConfig> source1 =
        ImmutableMap.of("git1", createTestGitStore(), "harness1", createTestHarnessStore(), "harness2",
            createTestHarnessStore(), "harness3", createTestHarnessStore(), "git2", createTestGitStore());

    final Map<String, StoreConfig> source2 = ImmutableMap.of(
        "git3", createTestGitStore(), "harness4", createTestHarnessStore(), "harness5", createTestHarnessStore());

    final Map<String, StoreConfig> result = AzureWebAppStepHelper.getConfigDifference(source1, source2);
    assertThat(result).containsKeys("git1", "harness1", "harness2", "harness3", "git2");
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testPrepareGitFetchTaskRequest() {
    final StepElementParameters stepElementParameters = createTestStepElementParameters();

    final GitFetchFilesConfig resultGitFetchFilesConfig = GitFetchFilesConfig.builder().build();
    final Map<String, GitStoreConfig> gitStoreConfigs =
        ImmutableMap.of("git1", createTestGitStore(), "git2", createTestGitStore(), "git3", createTestGitStore());

    doReturn(resultGitFetchFilesConfig)
        .when(cdStepHelper)
        .getGitFetchFilesConfig(eq(ambiance), any(GitStoreConfig.class), anyString(), anyString());

    stepHelper.prepareGitFetchTaskRequest(stepElementParameters, ambiance, gitStoreConfigs, emptyList());

    verifyTaskRequest(stepElementParameters, taskData -> {
      assertThat(taskData.getParameters()).hasSize(1);
      GitFetchRequest taskParameters = (GitFetchRequest) taskData.getParameters()[0];
      assertThat(taskParameters.getGitFetchFilesConfigs())
          .containsExactly(resultGitFetchFilesConfig, resultGitFetchFilesConfig, resultGitFetchFilesConfig);
      return true;
    });
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testPrepareTaskRequest() {
    final StepElementParameters stepElementParameters = createTestStepElementParameters();
    final AzureWebAppFetchPreDeploymentDataRequest taskParameters =
        AzureWebAppFetchPreDeploymentDataRequest.builder().accountId(ACCOUNT_ID).build();
    final List<String> units = singletonList("test unit");

    stepHelper.prepareTaskRequest(
        stepElementParameters, ambiance, taskParameters, TaskType.AZURE_WEB_APP_TASK_NG, units);

    verifyTaskRequest(stepElementParameters, taskData -> {
      assertThat(taskData.getParameters()).hasSize(1);
      assertThat(taskData.getTaskType()).isEqualTo(TaskType.AZURE_WEB_APP_TASK_NG.name());
      assertThat(taskData.getParameters()[0]).isInstanceOf(AzureWebAppFetchPreDeploymentDataRequest.class);
      return true;
    });
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testFetchWebAppConfigsFromHarnessStore() {
    final FileNodeDTO projectFileNode = FileNodeDTO.builder().content(PROJECT_ID).build();
    final FileNodeDTO orgFileNode = FileNodeDTO.builder().content(ORG_ID).build();
    final FileNodeDTO accountFileNode = FileNodeDTO.builder().content(ACCOUNT_ID).build();
    final Map<String, HarnessStore> harnessStoreConfigs = ImmutableMap.of("project", createTestHarnessStore("project"),
        "org", createTestHarnessStore("org"), "account", createTestHarnessStore("account"));

    doReturn(Optional.of(projectFileNode))
        .when(fileStoreService)
        .getWithChildrenByPath(ACCOUNT_ID, ORG_ID, PROJECT_ID, FILE_STORE_TEST_PATH, true);
    doReturn(Optional.of(orgFileNode))
        .when(fileStoreService)
        .getWithChildrenByPath(ACCOUNT_ID, ORG_ID, null, FILE_STORE_TEST_PATH, true);
    doReturn(Optional.of(accountFileNode))
        .when(fileStoreService)
        .getWithChildrenByPath(ACCOUNT_ID, null, null, FILE_STORE_TEST_PATH, true);

    Map<String, AppSettingsFile> result = stepHelper.fetchWebAppConfigsFromHarnessStore(ambiance, harnessStoreConfigs);

    verify(cdExpressionResolver, times(3)).updateExpressions(eq(ambiance), any());
    assertThat(result).containsKeys("project", "org", "account");
    assertThat(result.get("project").fetchFileContent()).isEqualTo(PROJECT_ID);
    assertThat(result.get("org").fetchFileContent()).isEqualTo(ORG_ID);
    assertThat(result.get("account").fetchFileContent()).isEqualTo(ACCOUNT_ID);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testFetchWebAppConfigsFromSecretFile() {
    final Map<String, HarnessStore> harnessStoreConfigs = ImmutableMap.of("test", createTestHarnessStoreSecretFiles());
    final List<EncryptedDataDetail> encryptedDataDetails = emptyList();

    doReturn(encryptedDataDetails).when(ngEncryptedDataService).getEncryptionDetails(any(), any());

    Map<String, AppSettingsFile> result = stepHelper.fetchWebAppConfigsFromHarnessStore(ambiance, harnessStoreConfigs);
    verify(cdExpressionResolver, times(1)).updateExpressions(eq(ambiance), any());
    assertThat(result.get("test").getEncryptedFile().getSecretFileReference().getIdentifier())
        .isEqualTo(FILE_STORE_TEST_PATH);
    assertThat(result.get("test").getEncryptedDataDetails()).isSameAs(encryptedDataDetails);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testGetConfigValuesFromGitFetchResponse() {
    final String expectedStringContent = "fileContent";
    final GitFetchResponse gitFetchResponse =
        GitFetchResponse.builder()
            .filesFromMultipleRepo(ImmutableMap.of("test",
                FetchFilesResult.builder()
                    .files(singletonList(GitFile.builder().fileContent(expectedStringContent).build()))
                    .build()))
            .build();

    Map<String, AppSettingsFile> result = stepHelper.getConfigValuesFromGitFetchResponse(ambiance, gitFetchResponse);

    verify(engineExpressionService).renderExpression(ambiance, expectedStringContent);

    assertThat(result).containsKeys("test");
    assertThat(result.get("test").fetchFileContent()).isEqualTo(expectedStringContent);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testGetInfraDelegateConfig() {
    final AzureWebAppInfrastructureOutcome infrastructureOutcome = AzureWebAppInfrastructureOutcome.builder()
                                                                       .connectorRef(INFRA_CONNECTOR_REF)
                                                                       .subscription(SUBSCRIPTION_ID)
                                                                       .resourceGroup(RESOURCE_GROUP)
                                                                       .build();
    final AzureConnectorDTO azureConnectorDTO = AzureConnectorDTO.builder().build();
    final ConnectorInfoDTO connectorInfoDTO =
        ConnectorInfoDTO.builder().connectorConfig(azureConnectorDTO).connectorType(ConnectorType.AWS).build();
    final List<EncryptedDataDetail> encryptionDetails = singletonList(EncryptedDataDetail.builder().build());

    doReturn(infrastructureOutcome).when(cdStepHelper).getInfrastructureOutcome(ambiance);
    doReturn(connectorInfoDTO).when(cdStepHelper).getConnector(INFRA_CONNECTOR_REF, ambiance);
    doReturn(encryptionDetails)
        .when(azureHelperService)
        .getEncryptionDetails(eq(azureConnectorDTO), any(NGAccess.class));

    AzureWebAppInfraDelegateConfig infraDelegateConfig =
        stepHelper.getInfraDelegateConfig(ambiance, APP_NAME, DEPLOYMENT_SLOT);
    assertThat(infraDelegateConfig.getAzureConnectorDTO()).isSameAs(azureConnectorDTO);
    assertThat(infraDelegateConfig.getSubscription()).isEqualTo(SUBSCRIPTION_ID);
    assertThat(infraDelegateConfig.getDeploymentSlot()).isEqualTo(DEPLOYMENT_SLOT);
    assertThat(infraDelegateConfig.getAppName()).isEqualTo(APP_NAME.toLowerCase());
    assertThat(infraDelegateConfig.getEncryptionDataDetails()).isSameAs(encryptionDetails);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testGetInfraDelegateConfigInvalidOutcome() {
    final InfrastructureOutcome randomInfraOutcome = mock(InfrastructureOutcome.class);

    doReturn(randomInfraOutcome).when(cdStepHelper).getInfrastructureOutcome(ambiance);

    assertThatThrownBy(() -> stepHelper.getInfraDelegateConfig(ambiance, APP_NAME, DEPLOYMENT_SLOT))
        .isInstanceOf(InvalidArgumentsException.class);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testGetInfraDelegateConfigInvalidConnector() {
    final AzureWebAppInfrastructureOutcome infrastructureOutcome = AzureWebAppInfrastructureOutcome.builder()
                                                                       .connectorRef(INFRA_CONNECTOR_REF)
                                                                       .subscription(SUBSCRIPTION_ID)
                                                                       .resourceGroup(RESOURCE_GROUP)
                                                                       .build();
    final ConnectorConfigDTO randomConnectorConfig = mock(ConnectorConfigDTO.class);
    final ConnectorInfoDTO connectorInfoDTO =
        ConnectorInfoDTO.builder().connectorConfig(randomConnectorConfig).connectorType(ConnectorType.AWS).build();

    doReturn(infrastructureOutcome).when(cdStepHelper).getInfrastructureOutcome(ambiance);
    doReturn(connectorInfoDTO).when(cdStepHelper).getConnector(INFRA_CONNECTOR_REF, ambiance);

    assertThatThrownBy(() -> stepHelper.getInfraDelegateConfig(ambiance, APP_NAME, DEPLOYMENT_SLOT))
        .isInstanceOf(InvalidArgumentsException.class);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testGetPrimaryArtifactConfigDocker() {
    final DockerArtifactOutcome dockerArtifactOutcome = DockerArtifactOutcome.builder()
                                                            .connectorRef("docker")
                                                            .image("harness")
                                                            .tag("test")
                                                            .type(DOCKER_REGISTRY_NAME)
                                                            .build();
    final DockerConnectorDTO dockerConnectorDTO =
        DockerConnectorDTO.builder()
            .auth(DockerAuthenticationDTO.builder().authType(DockerAuthType.ANONYMOUS).build())
            .build();
    final ConnectorInfoDTO connectorInfoDTO =
        ConnectorInfoDTO.builder().connectorType(ConnectorType.DOCKER).connectorConfig(dockerConnectorDTO).build();

    doReturn(connectorInfoDTO).when(cdStepHelper).getConnector("docker", ambiance);

    AzureArtifactConfig azureArtifactConfig = stepHelper.getPrimaryArtifactConfig(ambiance, dockerArtifactOutcome);
    assertThat(azureArtifactConfig.getArtifactType()).isEqualTo(AzureArtifactType.CONTAINER);
    AzureContainerArtifactConfig containerArtifactConfig = (AzureContainerArtifactConfig) azureArtifactConfig;
    assertThat(containerArtifactConfig.getConnectorConfig()).isEqualTo(dockerConnectorDTO);
    assertThat(containerArtifactConfig.getImage()).isEqualTo("harness");
    assertThat(containerArtifactConfig.getTag()).isEqualTo("test");
    assertThat(containerArtifactConfig.getRegistryType()).isEqualTo(AzureRegistryType.DOCKER_HUB_PUBLIC);
    assertThat(containerArtifactConfig.getEncryptedDataDetails()).isEmpty();
    verify(secretManagerClientService, never()).getEncryptionDetails(any(), any());
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testGetPrimaryArtifactConfigArtifactory() {
    final ArtifactoryArtifactOutcome artifactoryArtifactOutcome = ArtifactoryArtifactOutcome.builder()
                                                                      .connectorRef("artifactory")
                                                                      .image("harness")
                                                                      .tag("test")
                                                                      .type(ARTIFACTORY_REGISTRY_NAME)
                                                                      .build();
    final ArtifactoryUsernamePasswordAuthDTO usernamePasswordAuthDTO =
        ArtifactoryUsernamePasswordAuthDTO.builder().build();
    final ArtifactoryConnectorDTO artifactoryConnectorDTO = ArtifactoryConnectorDTO.builder()
                                                                .auth(ArtifactoryAuthenticationDTO.builder()
                                                                          .authType(ArtifactoryAuthType.USER_PASSWORD)
                                                                          .credentials(usernamePasswordAuthDTO)
                                                                          .build())
                                                                .build();
    final ConnectorInfoDTO connectorInfoDTO = ConnectorInfoDTO.builder()
                                                  .connectorType(ConnectorType.ARTIFACTORY)
                                                  .connectorConfig(artifactoryConnectorDTO)
                                                  .build();
    final List<EncryptedDataDetail> encryptedDataDetails = singletonList(EncryptedDataDetail.builder().build());

    doReturn(connectorInfoDTO).when(cdStepHelper).getConnector("artifactory", ambiance);
    doReturn(encryptedDataDetails)
        .when(secretManagerClientService)
        .getEncryptionDetails(any(NGAccess.class), eq(usernamePasswordAuthDTO));

    AzureArtifactConfig azureArtifactConfig = stepHelper.getPrimaryArtifactConfig(ambiance, artifactoryArtifactOutcome);
    assertThat(azureArtifactConfig.getArtifactType()).isEqualTo(AzureArtifactType.CONTAINER);
    AzureContainerArtifactConfig containerArtifactConfig = (AzureContainerArtifactConfig) azureArtifactConfig;
    assertThat(containerArtifactConfig.getConnectorConfig()).isEqualTo(artifactoryConnectorDTO);
    assertThat(containerArtifactConfig.getImage()).isEqualTo("harness");
    assertThat(containerArtifactConfig.getTag()).isEqualTo("test");
    assertThat(containerArtifactConfig.getRegistryType()).isEqualTo(AzureRegistryType.ARTIFACTORY_PRIVATE_REGISTRY);
    assertThat(containerArtifactConfig.getEncryptedDataDetails()).isEqualTo(encryptedDataDetails);
  }

  @Test
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testGetPrimaryPackageArtifactConfigAwsS3() {
    final S3ArtifactOutcome s3ArtifactOutcome = S3ArtifactOutcome.builder()
                                                    .connectorRef("s3awsconnector")
                                                    .bucketName("testBucketName")
                                                    .region("testRegion")
                                                    .filePath("test_app.war")
                                                    .type(AMAZON_S3_NAME)
                                                    .build();

    final AwsCredentialDTO awsCredentialDTO = AwsCredentialDTO.builder()
                                                  .awsCredentialType(AwsCredentialType.MANUAL_CREDENTIALS)
                                                  .config(AwsManualConfigSpecDTO.builder().build())
                                                  .build();

    final AwsConnectorDTO awsConnectorDTO = AwsConnectorDTO.builder().credential(awsCredentialDTO).build();
    final ConnectorInfoDTO connectorInfoDTO =
        ConnectorInfoDTO.builder().connectorType(ConnectorType.AWS).connectorConfig(awsConnectorDTO).build();
    final List<EncryptedDataDetail> encryptedDataDetails = singletonList(EncryptedDataDetail.builder().build());

    doReturn(connectorInfoDTO).when(cdStepHelper).getConnector("s3awsconnector", ambiance);
    doReturn(encryptedDataDetails).when(secretManagerClientService).getEncryptionDetails(any(NGAccess.class), any());

    AzureArtifactConfig azureArtifactConfig = stepHelper.getPrimaryArtifactConfig(ambiance, s3ArtifactOutcome);
    assertThat(azureArtifactConfig.getArtifactType()).isEqualTo(AzureArtifactType.PACKAGE);
    AzurePackageArtifactConfig packageArtifactConfig = (AzurePackageArtifactConfig) azureArtifactConfig;

    AwsS3AzureArtifactRequestDetails azureArtifactRequestDetails =
        (AwsS3AzureArtifactRequestDetails) packageArtifactConfig.getArtifactDetails();

    assertThat(packageArtifactConfig.getConnectorConfig()).isEqualTo(awsConnectorDTO);
    assertThat(azureArtifactRequestDetails.getBucketName()).isEqualTo("testBucketName");
    assertThat(azureArtifactRequestDetails.getFilePath()).isEqualTo("test_app.war");
    assertThat(azureArtifactRequestDetails.getRegion()).isEqualTo("testRegion");
    assertThat(packageArtifactConfig.getSourceType()).isEqualTo(AMAZONS3);
    assertThat(packageArtifactConfig.getEncryptedDataDetails()).isEqualTo(encryptedDataDetails);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testGetPrimaryPackageArtifactConfigNexus() {
    final Map<String, String> metadata = ImmutableMap.of("url", "https://nexus.dev/repo/abc/def");
    final NexusArtifactOutcome nexusArtifactOutcome = NexusArtifactOutcome.builder()
                                                          .repositoryFormat("maven")
                                                          .type(NEXUS3_REGISTRY_NAME)
                                                          .primaryArtifact(true)
                                                          .metadata(metadata)
                                                          .identifier("primary")
                                                          .connectorRef("nexusconnector")
                                                          .build();
    final NexusConnectorDTO nexusConnectorDTO =
        NexusConnectorDTO.builder()
            .auth(NexusAuthenticationDTO.builder().authType(NexusAuthType.ANONYMOUS).build())
            .build();
    final ConnectorInfoDTO connectorInfoDTO =
        ConnectorInfoDTO.builder().connectorType(ConnectorType.NEXUS).connectorConfig(nexusConnectorDTO).build();
    final List<EncryptedDataDetail> encryptedDataDetails = singletonList(EncryptedDataDetail.builder().build());

    doReturn(connectorInfoDTO).when(cdStepHelper).getConnector("nexusconnector", ambiance);
    doReturn(encryptedDataDetails).when(secretManagerClientService).getEncryptionDetails(any(NGAccess.class), any());

    AzureArtifactConfig azureArtifactConfig = stepHelper.getPrimaryArtifactConfig(ambiance, nexusArtifactOutcome);
    assertThat(azureArtifactConfig.getArtifactType()).isEqualTo(AzureArtifactType.PACKAGE);
    AzurePackageArtifactConfig packageArtifactConfig = (AzurePackageArtifactConfig) azureArtifactConfig;

    NexusAzureArtifactRequestDetails nexusRequestDetails =
        (NexusAzureArtifactRequestDetails) packageArtifactConfig.getArtifactDetails();
    assertThat(packageArtifactConfig.getConnectorConfig()).isEqualTo(nexusConnectorDTO);
    assertThat(nexusRequestDetails.getArtifactUrl()).isEqualTo("https://nexus.dev/repo/abc/def");
    assertThat(nexusRequestDetails.getRepositoryFormat()).isEqualTo("maven");
    assertThat(nexusRequestDetails.isCertValidationRequired()).isFalse();
    assertThat(nexusRequestDetails.getMetadata()).isEqualTo(metadata);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testFindLastSuccessfulStageExecutionDetails() {
    final AzureWebAppsStageExecutionDetails stageExecutionDetails =
        AzureWebAppsStageExecutionDetails.builder().pipelineExecutionId("pipeline").build();
    final List<StageExecutionInfo> stageExecutionInfos =
        singletonList(StageExecutionInfo.builder().executionDetails(stageExecutionDetails).build());

    testFindLastSuccessfulStageExecutionDetails(stageExecutionInfos, stageExecutionDetails);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testFindLastSuccessfulStageExecutionDetailsWithTargetSlot() {
    final AzureWebAppsStageExecutionDetails stageExecutionDetails1 =
        AzureWebAppsStageExecutionDetails.builder().pipelineExecutionId("pipeline1").targetSlot("slot1").build();
    final AzureWebAppsStageExecutionDetails stageExecutionDetails2 =
        AzureWebAppsStageExecutionDetails.builder().pipelineExecutionId("pipeline2").targetSlot("slot2").build();
    final List<StageExecutionInfo> stageExecutionInfos =
        Arrays.asList(StageExecutionInfo.builder().executionDetails(stageExecutionDetails1).build(),
            StageExecutionInfo.builder().executionDetails(stageExecutionDetails2).build());

    testFindLastSuccessfulStageExecutionDetails(stageExecutionInfos, stageExecutionDetails2);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testGetTaskTypeVersion() {
    final DockerArtifactOutcome dockerArtifactOutcome =
        DockerArtifactOutcome.builder().type(DOCKER_REGISTRY_NAME).build();
    final NexusArtifactOutcome nexusArtifactOutcome = NexusArtifactOutcome.builder()
                                                          .type(NEXUS3_REGISTRY_NAME)
                                                          .repositoryFormat(RepositoryFormat.maven.name())
                                                          .build();

    assertThat(stepHelper.getTaskTypeVersion(dockerArtifactOutcome)).isEqualTo(TaskType.AZURE_WEB_APP_TASK_NG.name());
    assertThat(stepHelper.getTaskTypeVersion(nexusArtifactOutcome)).isEqualTo(TaskType.AZURE_WEB_APP_TASK_NG_V2.name());
  }

  @Test
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testGetTaskTypeVersionJenkins() {
    final DockerArtifactOutcome dockerArtifactOutcome =
        DockerArtifactOutcome.builder().type(DOCKER_REGISTRY_NAME).build();
    final JenkinsArtifactOutcome jenkinsArtifactOutcome = JenkinsArtifactOutcome.builder().type(JENKINS_NAME).build();

    assertThat(stepHelper.getTaskTypeVersion(dockerArtifactOutcome)).isEqualTo(TaskType.AZURE_WEB_APP_TASK_NG.name());
    assertThat(stepHelper.getTaskTypeVersion(jenkinsArtifactOutcome))
        .isEqualTo(TaskType.AZURE_WEB_APP_TASK_NG_V2.name());
  }

  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testGetPrimaryPackageArtifactConfigAzureArtifacts() {
    final AzureArtifactsOutcome azureArtifactsOutcome = AzureArtifactsOutcome.builder()
                                                            .connectorRef("azureConenectorRef")
                                                            .scope("org")
                                                            .feed("testFeed")
                                                            .packageType("maven")
                                                            .packageName("com.test.my")
                                                            .version("testVersion")
                                                            .type(AZURE_ARTIFACTS_NAME)
                                                            .build();

    AzureArtifactsCredentialsDTO azureArtifactsCredentialsDTO =
        AzureArtifactsCredentialsDTO.builder()
            .type(AzureArtifactsAuthenticationType.PERSONAL_ACCESS_TOKEN)
            .credentialsSpec(AzureArtifactsTokenDTO.builder().tokenRef(SecretRefData.builder().build()).build())
            .build();

    AzureArtifactsConnectorDTO azureArtifactsConnectorDTO =
        AzureArtifactsConnectorDTO.builder()
            .azureArtifactsUrl("dummyDevopsAzureURL")
            .auth(AzureArtifactsAuthenticationDTO.builder().credentials(azureArtifactsCredentialsDTO).build())

            .build();

    final ConnectorInfoDTO connectorInfoDTO = ConnectorInfoDTO.builder()
                                                  .connectorType(ConnectorType.AZURE_ARTIFACTS)
                                                  .connectorConfig(azureArtifactsConnectorDTO)
                                                  .build();

    final List<EncryptedDataDetail> encryptedDataDetails = singletonList(EncryptedDataDetail.builder().build());
    doReturn(connectorInfoDTO).when(cdStepHelper).getConnector("azureConenectorRef", ambiance);
    doReturn(encryptedDataDetails).when(secretManagerClientService).getEncryptionDetails(any(NGAccess.class), any());

    AzureArtifactConfig azureArtifactConfig = stepHelper.getPrimaryArtifactConfig(ambiance, azureArtifactsOutcome);
    assertThat(azureArtifactConfig.getArtifactType()).isEqualTo(AzureArtifactType.PACKAGE);
    AzurePackageArtifactConfig packageArtifactConfig = (AzurePackageArtifactConfig) azureArtifactConfig;

    AzureDevOpsArtifactRequestDetails azureArtifactRequestDetails =
        (AzureDevOpsArtifactRequestDetails) packageArtifactConfig.getArtifactDetails();

    assertThat(packageArtifactConfig.getConnectorConfig()).isEqualTo(azureArtifactsConnectorDTO);
    assertThat(azureArtifactRequestDetails.getFeed()).isEqualTo("testFeed");
    assertThat(azureArtifactRequestDetails.getScope()).isEqualTo("org");
    assertThat(azureArtifactRequestDetails.getPackageType()).isEqualTo("maven");
    assertThat(azureArtifactRequestDetails.getPackageName()).isEqualTo("com.test.my");
    assertThat(azureArtifactRequestDetails.getVersion()).isEqualTo("testVersion");
    assertThat(packageArtifactConfig.getSourceType()).isEqualTo(AZURE_ARTIFACTS);
    assertThat(packageArtifactConfig.getEncryptedDataDetails()).isEqualTo(encryptedDataDetails);
  }

  @Test
  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testAMap() {
    Map<String, String> map = new HashMap<>();
    addtomap(map);
    assertThat(map.size()).isEqualTo(1);
  }

  public void addtomap(Map<String, String> mymap) {
    mymap.put("aaa", "aaa");
  }

  private void testFindLastSuccessfulStageExecutionDetails(
      List<StageExecutionInfo> stageExecutionInfos, AzureWebAppsStageExecutionDetails expectedExecutionDetails) {
    final AzureWebAppInfraDelegateConfig infraDelegateConfig =
        AzureWebAppInfraDelegateConfig.builder().appName(APP_NAME).deploymentSlot(DEPLOYMENT_SLOT).build();
    final ServiceStepOutcome serviceStepOutcome = ServiceStepOutcome.builder().identifier("service1").build();
    final AzureWebAppInfrastructureOutcome infrastructureOutcome =
        AzureWebAppInfrastructureOutcome.builder()
            .environment(EnvironmentOutcome.builder().identifier("env").build())
            .subscription(SUBSCRIPTION_ID)
            .resourceGroup(RESOURCE_GROUP)
            .build();

    doReturn(infrastructureOutcome).when(cdStepHelper).getInfrastructureOutcome(ambiance);

    final ExecutionInfoKey expectedExecutionInfoKey =
        ExecutionInfoKey.builder()
            .scope(Scope.builder()
                       .accountIdentifier(ACCOUNT_ID)
                       .orgIdentifier(ORG_ID)
                       .projectIdentifier(PROJECT_ID)
                       .build())
            .envIdentifier("env")
            .serviceIdentifier("service1")
            .deploymentIdentifier(stepHelper.getDeploymentIdentifier(ambiance, APP_NAME, DEPLOYMENT_SLOT))
            .build();

    doReturn(serviceStepOutcome)
        .when(outcomeService)
        .resolve(ambiance, RefObjectUtils.getOutcomeRefObject(OutcomeExpressionConstants.SERVICE));

    doReturn(stageExecutionInfos)
        .when(stageExecutionInfoService)
        .listLatestSuccessfulStageExecutionInfo(expectedExecutionInfoKey, STAGE_EXECUTION_ID, 2);

    AzureWebAppsStageExecutionDetails executionDetails =
        stepHelper.findLastSuccessfulStageExecutionDetails(ambiance, infraDelegateConfig);

    verify(stageExecutionInfoService)
        .listLatestSuccessfulStageExecutionInfo(expectedExecutionInfoKey, STAGE_EXECUTION_ID, 2);

    assertThat(executionDetails).isNotNull();
    assertThat(executionDetails).isEqualTo(expectedExecutionDetails);
  }

  @Owner(developers = VLICA)
  @Category(UnitTests.class)
  public void testGetPrimaryPackageArtifactConfigJenkins() {
    final JenkinsArtifactOutcome jenkinsArtifactOutcome = JenkinsArtifactOutcome.builder()
                                                              .connectorRef("jenkinsConnector")
                                                              .artifactPath("testArtifact")
                                                              .jobName("testJobName")
                                                              .build("testBuild")
                                                              .identifier("testIdentifier")
                                                              .type(JENKINS_NAME)
                                                              .build();

    final JenkinsUserNamePasswordDTO awsCredentialDTO = JenkinsUserNamePasswordDTO.builder()
                                                            .username("testUsername")
                                                            .passwordRef(SecretRefData.builder().build())
                                                            .build();

    final JenkinsConnectorDTO jenkinsConnectorDTO = JenkinsConnectorDTO.builder()
                                                        .jenkinsUrl("testJenkinsUrl")
                                                        .auth(JenkinsAuthenticationDTO.builder()
                                                                  .authType(JenkinsAuthType.USER_PASSWORD)
                                                                  .credentials(awsCredentialDTO)
                                                                  .build())
                                                        .build();

    final ConnectorInfoDTO connectorInfoDTO =
        ConnectorInfoDTO.builder().connectorType(ConnectorType.JENKINS).connectorConfig(jenkinsConnectorDTO).build();
    final List<EncryptedDataDetail> encryptedDataDetails = singletonList(EncryptedDataDetail.builder().build());

    doReturn(connectorInfoDTO).when(cdStepHelper).getConnector("jenkinsConnector", ambiance);
    doReturn(encryptedDataDetails).when(secretManagerClientService).getEncryptionDetails(any(NGAccess.class), any());
    doReturn(true).when(cdFeatureFlagHelper).isEnabled(anyString(), any());
    AzureArtifactConfig azureArtifactConfig = stepHelper.getPrimaryArtifactConfig(ambiance, jenkinsArtifactOutcome);
    assertThat(azureArtifactConfig.getArtifactType()).isEqualTo(AzureArtifactType.PACKAGE);
    AzurePackageArtifactConfig packageArtifactConfig = (AzurePackageArtifactConfig) azureArtifactConfig;

    JenkinsAzureArtifactRequestDetails azureArtifactRequestDetails =
        (JenkinsAzureArtifactRequestDetails) packageArtifactConfig.getArtifactDetails();

    assertThat(packageArtifactConfig.getConnectorConfig()).isEqualTo(jenkinsConnectorDTO);
    assertThat(azureArtifactRequestDetails.getArtifactPath()).isEqualTo("testArtifact");
    assertThat(azureArtifactRequestDetails.getBuild()).isEqualTo("testBuild");
    assertThat(azureArtifactRequestDetails.getJobName()).isEqualTo("testJobName");
    assertThat(packageArtifactConfig.getSourceType()).isEqualTo(JENKINS);
    assertThat(packageArtifactConfig.getEncryptedDataDetails()).isEqualTo(encryptedDataDetails);
  }

  private GitStore createTestGitStore() {
    return GitStore.builder()
        .branch(ParameterField.createValueField("branch"))
        .gitFetchType(FetchType.BRANCH)
        .connectorRef(ParameterField.createValueField("connector"))
        .paths(ParameterField.createValueField(singletonList("test")))
        .build();
  }

  private HarnessStore createTestHarnessStore() {
    return createTestHarnessStore("project");
  }
  private HarnessStore createTestHarnessStore(String scope) {
    return HarnessStore.builder()
        .files(ParameterField.createValueField(
            singletonList(scope.equals("project") ? FILE_STORE_TEST_PATH : scope + ":" + FILE_STORE_TEST_PATH)))
        .build();
  }

  private HarnessStore createTestHarnessStoreSecretFiles() {
    return HarnessStore.builder()
        .secretFiles(ParameterField.createValueField(singletonList(FILE_STORE_TEST_PATH)))
        .build();
  }

  private StepElementParameters createTestStepElementParameters() {
    return StepElementParameters.builder()
        .timeout(ParameterField.createValueField("10m"))
        .spec(AzureWebAppSlotDeploymentStepParameters.infoBuilder()
                  .delegateSelectors(
                      ParameterField.createValueField(singletonList(new TaskSelectorYaml(TEST_TASK_SELECTOR))))
                  .build())
        .build();
  }

  private void verifyTaskRequest(StepElementParameters stepElementParameters, Predicate<TaskData> taskDataTest) {
    ArgumentCaptor<TaskData> taskDataArgumentCaptor = ArgumentCaptor.forClass(TaskData.class);
    long expectedTimeoutMillis = CDStepHelper.getTimeoutInMillis(stepElementParameters);

    verify(cdStepHelper)
        .prepareTaskRequest(any(Ambiance.class), taskDataArgumentCaptor.capture(), anyList(), anyString(), anyList());
    TaskData taskData = taskDataArgumentCaptor.getValue();
    assertThat(taskData.getTimeout()).isEqualTo(expectedTimeoutMillis);
    taskDataTest.test(taskData);
  }
}