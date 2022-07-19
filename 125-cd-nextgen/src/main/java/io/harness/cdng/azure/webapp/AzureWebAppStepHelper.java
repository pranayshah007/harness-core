/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.azure.webapp;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.cdng.stepsdependency.constants.OutcomeExpressionConstants.APPLICATION_SETTINGS;
import static io.harness.cdng.stepsdependency.constants.OutcomeExpressionConstants.CONNECTION_STRINGS;
import static io.harness.cdng.stepsdependency.constants.OutcomeExpressionConstants.STARTUP_SCRIPT;
import static io.harness.data.structure.CollectionUtils.emptyIfNull;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.delegate.beans.connector.docker.DockerAuthType.ANONYMOUS;
import static io.harness.delegate.task.artifacts.ArtifactSourceConstants.ACR_NAME;
import static io.harness.delegate.task.artifacts.ArtifactSourceConstants.ARTIFACTORY_REGISTRY_NAME;
import static io.harness.delegate.task.artifacts.ArtifactSourceConstants.DOCKER_REGISTRY_NAME;
import static io.harness.delegate.task.artifacts.ArtifactSourceConstants.ECR_NAME;
import static io.harness.delegate.task.artifacts.ArtifactSourceConstants.GCR_NAME;

import static java.lang.String.format;

import io.harness.annotations.dev.OwnedBy;
import io.harness.azure.utility.AzureResourceUtility;
import io.harness.beans.FileReference;
import io.harness.cdng.CDStepHelper;
import io.harness.cdng.artifact.outcome.AcrArtifactOutcome;
import io.harness.cdng.artifact.outcome.ArtifactOutcome;
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
import io.harness.cdng.manifest.yaml.GitStoreConfig;
import io.harness.cdng.manifest.yaml.harness.HarnessStore;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfig;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.beans.azure.registry.AzureRegistryType;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.beans.connector.azureconnector.AzureConnectorDTO;
import io.harness.delegate.beans.connector.docker.DockerConnectorDTO;
import io.harness.delegate.task.TaskParameters;
import io.harness.delegate.task.azure.appservice.AzureAppServicePreDeploymentData;
import io.harness.delegate.task.azure.appservice.webapp.ng.AzureWebAppInfraDelegateConfig;
import io.harness.delegate.task.azure.artifact.AzureArtifactConfig;
import io.harness.delegate.task.azure.artifact.AzureContainerArtifactConfig;
import io.harness.delegate.task.git.GitFetchFilesConfig;
import io.harness.delegate.task.git.GitFetchRequest;
import io.harness.delegate.task.git.GitFetchResponse;
import io.harness.exception.InvalidArgumentsException;
import io.harness.filestore.dto.node.FileNodeDTO;
import io.harness.filestore.dto.node.FileStoreNodeDTO;
import io.harness.filestore.dto.node.FolderNodeDTO;
import io.harness.filestore.service.FileStoreService;
import io.harness.ng.core.NGAccess;
import io.harness.ng.core.infrastructure.InfrastructureKind;
import io.harness.plancreator.steps.TaskSelectorYaml;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.tasks.TaskRequest;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.expression.EngineExpressionService;
import io.harness.pms.sdk.core.data.OptionalOutcome;
import io.harness.pms.sdk.core.data.OptionalSweepingOutput;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outcome.OutcomeService;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.secretmanagerclient.services.api.SecretManagerClientService;

import software.wings.beans.TaskType;

import com.google.common.collect.Sets;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;

@Singleton
@Slf4j
@OwnedBy(CDP)
public class AzureWebAppStepHelper {
  @Inject private OutcomeService outcomeService;
  @Inject private FileStoreService fileStoreService;
  @Inject private CDStepHelper cdStepHelper;
  @Inject private AzureHelperService azureHelperService;
  @Inject private EngineExpressionService engineExpressionService;
  @Inject private CDExpressionResolver cdExpressionResolver;
  @Inject private ExecutionSweepingOutputService executionSweepingOutputService;
  @Named("PRIVILEGED") @Inject private SecretManagerClientService secretManagerClientService;

  public AzureAppServicePreDeploymentData getPreDeploymentData(Ambiance ambiance, String sweepingOutputName) {
    OptionalSweepingOutput sweepingOutput = executionSweepingOutputService.resolveOptional(
        ambiance, RefObjectUtils.getSweepingOutputRefObject(sweepingOutputName));
    if (sweepingOutput.isFound()) {
      return ((AzureWebAppPreDeploymentDataOutput) sweepingOutput.getOutput()).getPreDeploymentData();
    }
    return null;
  }

  public Map<String, StoreConfig> fetchWebAppConfig(Ambiance ambiance) {
    Map<String, StoreConfig> settingsConfig = new HashMap<>();
    OptionalOutcome startupScriptOutcome =
        outcomeService.resolveOptional(ambiance, RefObjectUtils.getOutcomeRefObject(STARTUP_SCRIPT));
    OptionalOutcome applicationSettingsOutcome =
        outcomeService.resolveOptional(ambiance, RefObjectUtils.getOutcomeRefObject(APPLICATION_SETTINGS));
    OptionalOutcome connectionStringsOutcome =
        outcomeService.resolveOptional(ambiance, RefObjectUtils.getOutcomeRefObject(CONNECTION_STRINGS));

    if (startupScriptOutcome.isFound()) {
      StartupScriptOutcome startupScript = (StartupScriptOutcome) startupScriptOutcome.getOutcome();
      settingsConfig.put(STARTUP_SCRIPT, startupScript.getStore());
    }

    if (applicationSettingsOutcome.isFound()) {
      ApplicationSettingsOutcome applicationSettings =
          (ApplicationSettingsOutcome) applicationSettingsOutcome.getOutcome();
      settingsConfig.put(APPLICATION_SETTINGS, applicationSettings.getStore());
    }

    if (connectionStringsOutcome.isFound()) {
      ConnectionStringsOutcome connectionStrings = (ConnectionStringsOutcome) connectionStringsOutcome.getOutcome();
      settingsConfig.put(CONNECTION_STRINGS, connectionStrings.getStore());
    }

    return settingsConfig;
  }

  public TaskRequest prepareGitFetchTaskRequest(StepElementParameters stepElementParameters, Ambiance ambiance,
      Map<String, GitStoreConfig> gitStoreConfigs, List<String> units) {
    List<GitFetchFilesConfig> gitFetchFilesConfigs = new ArrayList<>();
    for (Map.Entry<String, GitStoreConfig> configEntry : gitStoreConfigs.entrySet()) {
      gitFetchFilesConfigs.add(cdStepHelper.getGitFetchFilesConfig(
          ambiance, configEntry.getValue(), configEntry.getKey(), configEntry.getKey()));
    }

    String accountId = AmbianceUtils.getAccountId(ambiance);
    GitFetchRequest gitFetchRequest = GitFetchRequest.builder()
                                          .accountId(accountId)
                                          .gitFetchFilesConfigs(gitFetchFilesConfigs)
                                          .shouldOpenLogStream(true)
                                          .closeLogStream(true)
                                          .build();

    return prepareTaskRequest(
        stepElementParameters, ambiance, gitFetchRequest, TaskType.GIT_FETCH_NEXT_GEN_TASK, units);
  }

  public TaskRequest prepareTaskRequest(StepElementParameters stepElementParameters, Ambiance ambiance,
      TaskParameters taskParameters, TaskType taskType, List<String> units) {
    AzureWebAppStepParameters stepSpec = (AzureWebAppStepParameters) stepElementParameters.getSpec();
    List<TaskSelectorYaml> taskSelectors = stepSpec.getDelegateSelectors().getValue();

    final TaskData taskData = TaskData.builder()
                                  .async(true)
                                  .timeout(CDStepHelper.getTimeoutInMillis(stepElementParameters))
                                  .taskType(taskType.name())
                                  .parameters(new Object[] {taskParameters})
                                  .build();

    return cdStepHelper.prepareTaskRequest(ambiance, taskData, units, taskType.getDisplayName(),
        TaskSelectorYaml.toTaskSelector(emptyIfNull(taskSelectors)));
  }

  public Map<String, String> fetchWebAppConfigsFromHarnessStore(
      Ambiance ambiance, Map<String, HarnessStore> harnessStoreConfigs) {
    return harnessStoreConfigs.entrySet().stream().collect(Collectors.toMap(
        Map.Entry::getKey, entry -> fetchFileContentFromHarnessStore(ambiance, entry.getKey(), entry.getValue())));
  }

  public AzureWebAppInfraDelegateConfig getInfraDelegateConfig(
      Ambiance ambiance, String webApp, String deploymentSlot) {
    InfrastructureOutcome infrastructureOutcome = cdStepHelper.getInfrastructureOutcome(ambiance);
    if (!(infrastructureOutcome instanceof AzureWebAppInfrastructureOutcome)) {
      throw new InvalidArgumentsException(Pair.of("infrastructure",
          format("Invalid infrastructure type: %s, expected: %s", infrastructureOutcome.getKind(),
              InfrastructureKind.AZURE_WEB_APP)));
    }

    AzureWebAppInfrastructureOutcome infrastructure = (AzureWebAppInfrastructureOutcome) infrastructureOutcome;
    return getInfraDelegateConfig(ambiance, infrastructure, webApp, deploymentSlot);
  }

  public AzureWebAppInfraDelegateConfig getInfraDelegateConfig(
      Ambiance ambiance, AzureWebAppInfrastructureOutcome infrastructure, String webApp, String deploymentSlot) {
    ConnectorInfoDTO connectorInfo = cdStepHelper.getConnector(infrastructure.getConnectorRef(), ambiance);
    if (!(connectorInfo.getConnectorConfig() instanceof AzureConnectorDTO)) {
      throw new InvalidArgumentsException(Pair.of("infrastructure",
          format("Invalid infrastructure connector type: %s, expected: %s",
              connectorInfo.getConnectorType().getDisplayName(), ConnectorType.AZURE.getDisplayName())));
    }

    AzureConnectorDTO azureConnectorDTO = (AzureConnectorDTO) connectorInfo.getConnectorConfig();
    NGAccess ngAccess = AmbianceUtils.getNgAccess(ambiance);
    return AzureWebAppInfraDelegateConfig.builder()
        .azureConnectorDTO(azureConnectorDTO)
        .subscription(infrastructure.getSubscription())
        .resourceGroup(infrastructure.getResourceGroup())
        .appName(webApp)
        .deploymentSlot(AzureResourceUtility.fixDeploymentSlotName(deploymentSlot, webApp))
        .encryptionDataDetails(azureHelperService.getEncryptionDetails(azureConnectorDTO, ngAccess))
        .build();
  }

  public AzureArtifactConfig getPrimaryArtifactConfig(Ambiance ambiance) {
    ArtifactOutcome artifactOutcome = cdStepHelper.resolveArtifactsOutcome(ambiance).orElseThrow(
        () -> new InvalidArgumentsException(Pair.of("artifacts", "Artifact is required for Azure WebApp")));
    switch (artifactOutcome.getArtifactType()) {
      case DOCKER_REGISTRY_NAME:
      case ECR_NAME:
      case GCR_NAME:
      case ACR_NAME:
      case ARTIFACTORY_REGISTRY_NAME:
        return getAzureContainerArtifactConfig(ambiance, artifactOutcome);

      default:
        throw new InvalidArgumentsException(Pair.of("artifacts",
            format("Artifact type %s is not yet supported in Azure WebApp", artifactOutcome.getArtifactType())));
    }
  }

  public Map<String, String> getConfigValuesFromGitFetchResponse(Ambiance ambiance, GitFetchResponse gitFetchResponse) {
    return gitFetchResponse.getFilesFromMultipleRepo()
        .entrySet()
        .stream()
        .filter(entry -> isNotEmpty(entry.getValue().getFiles()))
        .collect(Collectors.toMap(Map.Entry::getKey,
            entry
            -> engineExpressionService.renderExpression(
                ambiance, entry.getValue().getFiles().get(0).getFileContent())));
  }

  public static <T extends StoreConfig> Map<String, T> filterAndMapConfigs(
      Map<String, StoreConfig> configs, Predicate<String> kindTest) {
    return configs.entrySet()
        .stream()
        .filter(entry -> kindTest.test(entry.getValue().getKind()))
        .collect(Collectors.toMap(Map.Entry::getKey, entry -> (T) entry.getValue()));
  }

  public static <T extends StoreConfig, U extends StoreConfig> Map<String, StoreConfig> getConfigDifference(
      Map<String, T> aConfigs, Map<String, U> bConfigs) {
    return Sets.difference(aConfigs.entrySet(), bConfigs.entrySet())
        .stream()
        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
  }

  private AzureArtifactConfig getAzureContainerArtifactConfig(Ambiance ambiance, ArtifactOutcome artifactOutcome) {
    ConnectorInfoDTO connectorInfo;
    AzureRegistryType azureRegistryType;
    String image;
    String tag;

    switch (artifactOutcome.getArtifactType()) {
      case DOCKER_REGISTRY_NAME:
        DockerArtifactOutcome dockerArtifactOutcome = (DockerArtifactOutcome) artifactOutcome;
        connectorInfo = cdStepHelper.getConnector(dockerArtifactOutcome.getConnectorRef(), ambiance);
        azureRegistryType = getAzureRegistryType((DockerConnectorDTO) connectorInfo.getConnectorConfig());
        image = dockerArtifactOutcome.getImage();
        tag = dockerArtifactOutcome.getTag();
        break;
      case ACR_NAME:
        AcrArtifactOutcome acrArtifactOutcome = (AcrArtifactOutcome) artifactOutcome;
        connectorInfo = cdStepHelper.getConnector(acrArtifactOutcome.getConnectorRef(), ambiance);
        azureRegistryType = AzureRegistryType.ACR;
        image = acrArtifactOutcome.getImage();
        tag = acrArtifactOutcome.getTag();
        break;
      case ARTIFACTORY_REGISTRY_NAME:
        ArtifactoryArtifactOutcome artifactoryArtifactOutcome = (ArtifactoryArtifactOutcome) artifactOutcome;
        connectorInfo = cdStepHelper.getConnector(artifactoryArtifactOutcome.getConnectorRef(), ambiance);
        azureRegistryType = AzureRegistryType.ARTIFACTORY_PRIVATE_REGISTRY;
        image = artifactoryArtifactOutcome.getImage();
        tag = artifactoryArtifactOutcome.getTag();
        break;
      default:
        throw new InvalidArgumentsException(
            Pair.of("artifacts", format("Unsupported artifact type %s", artifactOutcome.getArtifactType())));
    }

    NGAccess ngAccess = AmbianceUtils.getNgAccess(ambiance);

    return AzureContainerArtifactConfig.builder()
        .connectorConfig(connectorInfo.getConnectorConfig())
        .registryType(azureRegistryType)
        .image(image)
        .tag(tag)
        .encryptedDataDetails(
            secretManagerClientService.getEncryptionDetails(ngAccess, connectorInfo.getConnectorConfig()))
        .build();
  }

  private AzureRegistryType getAzureRegistryType(DockerConnectorDTO dockerConfig) {
    if (dockerConfig.getAuth().getAuthType().equals(ANONYMOUS)) {
      return AzureRegistryType.DOCKER_HUB_PUBLIC;
    } else {
      return AzureRegistryType.DOCKER_HUB_PRIVATE;
    }
  }

  private String fetchFileContentFromHarnessStore(Ambiance ambiance, String settingsType, HarnessStore harnessStore) {
    NGAccess ngAccess = AmbianceUtils.getNgAccess(ambiance);
    HarnessStore renderedHarnessStore = (HarnessStore) cdExpressionResolver.updateExpressions(ambiance, harnessStore);
    List<String> harnessStoreFiles = renderedHarnessStore.getFiles().getValue();
    String firstFile = harnessStoreFiles.stream().findFirst().orElseThrow(
        () -> new InvalidArgumentsException(Pair.of(settingsType, "No file configured for harness file store")));

    FileReference fileReference = FileReference.of(
        firstFile, ngAccess.getAccountIdentifier(), ngAccess.getOrgIdentifier(), ngAccess.getProjectIdentifier());
    FileStoreNodeDTO fileStoreNodeDTO =
        fileStoreService
            .getWithChildrenByPath(fileReference.getAccountIdentifier(), fileReference.getOrgIdentifier(),
                fileReference.getProjectIdentifier(), fileReference.getPath(), true)
            .orElseThrow(()
                             -> new InvalidArgumentsException(
                                 Pair.of(settingsType, format("File '%s' doesn't exists", fileReference.getPath()))));

    if (fileStoreNodeDTO instanceof FolderNodeDTO) {
      throw new InvalidArgumentsException(
          Pair.of(settingsType, format("Provided path '%s' is a folder, expecting a file", fileReference.getPath())));
    }

    if (fileStoreNodeDTO instanceof FileNodeDTO) {
      FileNodeDTO fileNode = (FileNodeDTO) fileStoreNodeDTO;
      if (isNotEmpty(fileNode.getContent())) {
        return engineExpressionService.renderExpression(ambiance, fileNode.getContent());
      }

      log.warn("Received empty or null content for file: {}", fileStoreNodeDTO.getPath());
      return "";
    }

    log.error("Unknown file store node: {}", fileStoreNodeDTO.getClass().getSimpleName());
    throw new InvalidArgumentsException(Pair.of(settingsType, "Unsupported file store node"));
  }
}
