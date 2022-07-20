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
import static io.harness.cdng.stepsdependency.constants.OutcomeExpressionConstants.STARTUP_SCRIPT;
import static io.harness.delegate.task.artifacts.ArtifactSourceConstants.ARTIFACTORY_REGISTRY_NAME;
import static io.harness.delegate.task.artifacts.ArtifactSourceConstants.DOCKER_REGISTRY_NAME;
import static io.harness.rule.OwnerRule.ABOSII;
import static io.harness.rule.OwnerRule.TMACARI;

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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.cdng.CDNGTestBase;
import io.harness.cdng.CDStepHelper;
import io.harness.cdng.artifact.outcome.ArtifactoryArtifactOutcome;
import io.harness.cdng.artifact.outcome.DockerArtifactOutcome;
import io.harness.cdng.azure.AzureHelperService;
import io.harness.cdng.azure.config.ApplicationSettingsOutcome;
import io.harness.cdng.azure.config.ConnectionStringsOutcome;
import io.harness.cdng.azure.config.StartupScriptOutcome;
import io.harness.cdng.azure.webapp.beans.AzureWebAppPreDeploymentDataOutput;
import io.harness.cdng.expressions.CDExpressionResolver;
import io.harness.cdng.infra.beans.AzureWebAppInfrastructureOutcome;
import io.harness.cdng.infra.beans.InfrastructureOutcome;
import io.harness.cdng.manifest.ManifestStoreType;
import io.harness.cdng.manifest.yaml.GitStore;
import io.harness.cdng.manifest.yaml.GitStoreConfig;
import io.harness.cdng.manifest.yaml.harness.HarnessStore;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfig;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.beans.azure.registry.AzureRegistryType;
import io.harness.delegate.beans.connector.ConnectorConfigDTO;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.beans.connector.artifactoryconnector.ArtifactoryAuthenticationDTO;
import io.harness.delegate.beans.connector.artifactoryconnector.ArtifactoryConnectorDTO;
import io.harness.delegate.beans.connector.azureconnector.AzureConnectorDTO;
import io.harness.delegate.beans.connector.docker.DockerAuthType;
import io.harness.delegate.beans.connector.docker.DockerAuthenticationDTO;
import io.harness.delegate.beans.connector.docker.DockerConnectorDTO;
import io.harness.delegate.beans.storeconfig.FetchType;
import io.harness.delegate.task.azure.appservice.AzureAppServicePreDeploymentData;
import io.harness.delegate.task.azure.appservice.settings.AppSettingsFile;
import io.harness.delegate.task.azure.appservice.webapp.ng.AzureWebAppInfraDelegateConfig;
import io.harness.delegate.task.azure.appservice.webapp.ng.request.AzureWebAppFetchPreDeploymentDataRequest;
import io.harness.delegate.task.azure.artifact.AzureArtifactConfig;
import io.harness.delegate.task.azure.artifact.AzureArtifactType;
import io.harness.delegate.task.azure.artifact.AzureContainerArtifactConfig;
import io.harness.delegate.task.git.GitFetchFilesConfig;
import io.harness.delegate.task.git.GitFetchRequest;
import io.harness.delegate.task.git.GitFetchResponse;
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

import software.wings.beans.TaskType;

import com.google.common.collect.ImmutableMap;
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

  @Mock private OutcomeService outcomeService;
  @Mock private FileStoreService fileStoreService;
  @Mock private CDStepHelper cdStepHelper;
  @Mock private AzureHelperService azureHelperService;
  @Mock private EngineExpressionService engineExpressionService;
  @Mock private CDExpressionResolver cdExpressionResolver;
  @Mock private SecretManagerClientService secretManagerClientService;
  @Mock private ExecutionSweepingOutputService executionSweepingOutputService;
  @Mock private NGEncryptedDataService ngEncryptedDataService;

  @InjectMocks private AzureWebAppStepHelper stepHelper;

  private final Ambiance ambiance = Ambiance.newBuilder()
                                        .putSetupAbstractions(SetupAbstractionKeys.accountId, ACCOUNT_ID)
                                        .putSetupAbstractions(SetupAbstractionKeys.projectIdentifier, PROJECT_ID)
                                        .putSetupAbstractions(SetupAbstractionKeys.orgIdentifier, ORG_ID)
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
    final StartupScriptOutcome startupScriptOutcome =
        StartupScriptOutcome.builder().store(createTestGitStore()).build();
    final ApplicationSettingsOutcome applicationSettingsOutcome =
        ApplicationSettingsOutcome.builder().store(createTestHarnessStore()).build();
    final ConnectionStringsOutcome connectionStringsOutcome =
        ConnectionStringsOutcome.builder().store(createTestGitStore()).build();

    doReturn(OptionalOutcome.builder().outcome(startupScriptOutcome).found(true).build())
        .when(outcomeService)
        .resolveOptional(ambiance, RefObjectUtils.getOutcomeRefObject(STARTUP_SCRIPT));
    doReturn(OptionalOutcome.builder().outcome(applicationSettingsOutcome).found(true).build())
        .when(outcomeService)
        .resolveOptional(ambiance, RefObjectUtils.getOutcomeRefObject(APPLICATION_SETTINGS));
    doReturn(OptionalOutcome.builder().outcome(connectionStringsOutcome).found(true).build())
        .when(outcomeService)
        .resolveOptional(ambiance, RefObjectUtils.getOutcomeRefObject(CONNECTION_STRINGS));

    final Map<String, StoreConfig> webAppConfigs = stepHelper.fetchWebAppConfig(ambiance);

    assertThat(webAppConfigs).containsKeys(STARTUP_SCRIPT, APPLICATION_SETTINGS, CONNECTION_STRINGS);
    assertThat(webAppConfigs.get(STARTUP_SCRIPT).getKind()).isEqualTo(ManifestStoreType.GIT);
    assertThat(webAppConfigs.get(APPLICATION_SETTINGS).getKind()).isEqualTo(HARNESS_STORE_TYPE);
    assertThat(webAppConfigs.get(CONNECTION_STRINGS).getKind()).isEqualTo(ManifestStoreType.GIT);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testFetchWebAppConfigEmpty() {
    doReturn(OptionalOutcome.builder().found(false).build())
        .when(outcomeService)
        .resolveOptional(ambiance, RefObjectUtils.getOutcomeRefObject(STARTUP_SCRIPT));
    doReturn(OptionalOutcome.builder().found(false).build())
        .when(outcomeService)
        .resolveOptional(ambiance, RefObjectUtils.getOutcomeRefObject(APPLICATION_SETTINGS));
    doReturn(OptionalOutcome.builder().found(false).build())
        .when(outcomeService)
        .resolveOptional(ambiance, RefObjectUtils.getOutcomeRefObject(CONNECTION_STRINGS));

    final Map<String, StoreConfig> webAppConfigs = stepHelper.fetchWebAppConfig(ambiance);

    assertThat(webAppConfigs).isEmpty();
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
    assertThat(infraDelegateConfig.getAppName()).isEqualTo(APP_NAME);
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
    final List<EncryptedDataDetail> encryptedDataDetails = singletonList(EncryptedDataDetail.builder().build());

    doReturn(Optional.of(dockerArtifactOutcome)).when(cdStepHelper).resolveArtifactsOutcome(ambiance);
    doReturn(connectorInfoDTO).when(cdStepHelper).getConnector("docker", ambiance);
    doReturn(encryptedDataDetails)
        .when(secretManagerClientService)
        .getEncryptionDetails(any(NGAccess.class), eq(dockerConnectorDTO));

    AzureArtifactConfig azureArtifactConfig = stepHelper.getPrimaryArtifactConfig(ambiance);
    assertThat(azureArtifactConfig.getArtifactType()).isEqualTo(AzureArtifactType.CONTAINER);
    AzureContainerArtifactConfig containerArtifactConfig = (AzureContainerArtifactConfig) azureArtifactConfig;
    assertThat(containerArtifactConfig.getConnectorConfig()).isEqualTo(dockerConnectorDTO);
    assertThat(containerArtifactConfig.getImage()).isEqualTo("harness");
    assertThat(containerArtifactConfig.getTag()).isEqualTo("test");
    assertThat(containerArtifactConfig.getRegistryType()).isEqualTo(AzureRegistryType.DOCKER_HUB_PUBLIC);
    assertThat(containerArtifactConfig.getEncryptedDataDetails()).isEqualTo(encryptedDataDetails);
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
    final ArtifactoryConnectorDTO artifactoryConnectorDTO =
        ArtifactoryConnectorDTO.builder().auth(ArtifactoryAuthenticationDTO.builder().build()).build();
    final ConnectorInfoDTO connectorInfoDTO = ConnectorInfoDTO.builder()
                                                  .connectorType(ConnectorType.ARTIFACTORY)
                                                  .connectorConfig(artifactoryConnectorDTO)
                                                  .build();
    final List<EncryptedDataDetail> encryptedDataDetails = singletonList(EncryptedDataDetail.builder().build());

    doReturn(Optional.of(artifactoryArtifactOutcome)).when(cdStepHelper).resolveArtifactsOutcome(ambiance);
    doReturn(connectorInfoDTO).when(cdStepHelper).getConnector("artifactory", ambiance);
    doReturn(encryptedDataDetails)
        .when(secretManagerClientService)
        .getEncryptionDetails(any(NGAccess.class), eq(artifactoryConnectorDTO));

    AzureArtifactConfig azureArtifactConfig = stepHelper.getPrimaryArtifactConfig(ambiance);
    assertThat(azureArtifactConfig.getArtifactType()).isEqualTo(AzureArtifactType.CONTAINER);
    AzureContainerArtifactConfig containerArtifactConfig = (AzureContainerArtifactConfig) azureArtifactConfig;
    assertThat(containerArtifactConfig.getConnectorConfig()).isEqualTo(artifactoryConnectorDTO);
    assertThat(containerArtifactConfig.getImage()).isEqualTo("harness");
    assertThat(containerArtifactConfig.getTag()).isEqualTo("test");
    assertThat(containerArtifactConfig.getRegistryType()).isEqualTo(AzureRegistryType.ARTIFACTORY_PRIVATE_REGISTRY);
    assertThat(containerArtifactConfig.getEncryptedDataDetails()).isEqualTo(encryptedDataDetails);
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