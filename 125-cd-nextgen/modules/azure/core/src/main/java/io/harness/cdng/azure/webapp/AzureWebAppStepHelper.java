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
import static io.harness.cdng.stepsdependency.constants.OutcomeExpressionConstants.STARTUP_COMMAND;
import static io.harness.data.structure.CollectionUtils.emptyIfNull;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.delegate.beans.connector.docker.DockerAuthType.ANONYMOUS;
import static io.harness.delegate.task.artifacts.ArtifactSourceConstants.ACR_NAME;
import static io.harness.delegate.task.artifacts.ArtifactSourceConstants.AMAZON_S3_NAME;
import static io.harness.delegate.task.artifacts.ArtifactSourceConstants.ARTIFACTORY_REGISTRY_NAME;
import static io.harness.delegate.task.artifacts.ArtifactSourceConstants.AZURE_ARTIFACTS_NAME;
import static io.harness.delegate.task.artifacts.ArtifactSourceConstants.DOCKER_REGISTRY_NAME;
import static io.harness.delegate.task.artifacts.ArtifactSourceConstants.ECR_NAME;
import static io.harness.delegate.task.artifacts.ArtifactSourceConstants.GCR_NAME;
import static io.harness.delegate.task.artifacts.ArtifactSourceConstants.JENKINS_NAME;
import static io.harness.delegate.task.artifacts.ArtifactSourceConstants.NEXUS3_REGISTRY_NAME;
import static io.harness.delegate.task.artifacts.ArtifactSourceType.AMAZONS3;
import static io.harness.delegate.task.artifacts.ArtifactSourceType.AZURE_ARTIFACTS;
import static io.harness.delegate.task.artifacts.ArtifactSourceType.JENKINS;
import static io.harness.delegate.task.artifacts.ArtifactSourceType.NEXUS3_REGISTRY;

import static java.lang.String.format;
import static java.util.Collections.singletonList;

import io.harness.annotations.dev.OwnedBy;
import io.harness.azure.utility.AzureResourceUtility;
import io.harness.beans.DecryptableEntity;
import io.harness.beans.FeatureName;
import io.harness.beans.FileReference;
import io.harness.beans.IdentifierRef;
import io.harness.beans.Scope;
import io.harness.cdng.CDStepHelper;
import io.harness.cdng.artifact.outcome.AcrArtifactOutcome;
import io.harness.cdng.artifact.outcome.ArtifactOutcome;
import io.harness.cdng.artifact.outcome.ArtifactoryArtifactOutcome;
import io.harness.cdng.artifact.outcome.ArtifactoryGenericArtifactOutcome;
import io.harness.cdng.artifact.outcome.AzureArtifactsOutcome;
import io.harness.cdng.artifact.outcome.DockerArtifactOutcome;
import io.harness.cdng.artifact.outcome.EcrArtifactOutcome;
import io.harness.cdng.artifact.outcome.GcrArtifactOutcome;
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
import io.harness.cdng.manifest.yaml.GitStoreConfig;
import io.harness.cdng.manifest.yaml.harness.HarnessStore;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfig;
import io.harness.cdng.service.steps.ServiceStepOutcome;
import io.harness.cdng.service.steps.constants.ServiceStepV3Constants;
import io.harness.cdng.stepsdependency.constants.OutcomeExpressionConstants;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.beans.azure.registry.AzureRegistryType;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.beans.connector.azureconnector.AzureConnectorDTO;
import io.harness.delegate.beans.connector.docker.DockerConnectorDTO;
import io.harness.delegate.task.TaskParameters;
import io.harness.delegate.task.artifacts.ArtifactSourceType;
import io.harness.delegate.task.azure.appservice.AzureAppServicePreDeploymentData;
import io.harness.delegate.task.azure.appservice.settings.AppSettingsFile;
import io.harness.delegate.task.azure.appservice.settings.EncryptedAppSettingsFile;
import io.harness.delegate.task.azure.appservice.webapp.ng.AzureWebAppInfraDelegateConfig;
import io.harness.delegate.task.azure.artifact.ArtifactoryAzureArtifactRequestDetails;
import io.harness.delegate.task.azure.artifact.AwsS3AzureArtifactRequestDetails;
import io.harness.delegate.task.azure.artifact.AzureArtifactConfig;
import io.harness.delegate.task.azure.artifact.AzureContainerArtifactConfig;
import io.harness.delegate.task.azure.artifact.AzureContainerArtifactConfig.AzureContainerArtifactConfigBuilder;
import io.harness.delegate.task.azure.artifact.AzureDevOpsArtifactRequestDetails;
import io.harness.delegate.task.azure.artifact.AzurePackageArtifactConfig;
import io.harness.delegate.task.azure.artifact.AzurePackageArtifactConfig.AzurePackageArtifactConfigBuilder;
import io.harness.delegate.task.azure.artifact.JenkinsAzureArtifactRequestDetails;
import io.harness.delegate.task.azure.artifact.NexusAzureArtifactRequestDetails;
import io.harness.delegate.task.git.GitFetchFilesConfig;
import io.harness.delegate.task.git.GitFetchRequest;
import io.harness.delegate.task.git.GitFetchResponse;
import io.harness.encryption.SecretRefHelper;
import io.harness.eraro.ErrorCode;
import io.harness.exception.AccessDeniedException;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.WingsException;
import io.harness.filestore.dto.node.FileNodeDTO;
import io.harness.filestore.dto.node.FileStoreNodeDTO;
import io.harness.filestore.dto.node.FolderNodeDTO;
import io.harness.filestore.service.FileStoreService;
import io.harness.ng.core.NGAccess;
import io.harness.ng.core.api.NGEncryptedDataService;
import io.harness.ng.core.infrastructure.InfrastructureKind;
import io.harness.ng.core.serviceoverride.services.ServiceOverrideService;
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
import io.harness.pms.yaml.ParameterField;
import io.harness.secretmanagerclient.services.api.SecretManagerClientService;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.utils.IdentifierRefHelper;

import software.wings.beans.TaskType;
import software.wings.utils.RepositoryFormat;

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
import javax.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.NotNull;

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
  @Inject private NGEncryptedDataService ngEncryptedDataService;
  @Inject private CDFeatureFlagHelper cdFeatureFlagHelper;
  @Named("PRIVILEGED") @Inject private SecretManagerClientService secretManagerClientService;
  @Inject private StageExecutionInfoService stageExecutionInfoService;
  @Inject private ServiceOverrideService serviceOverrideService;
  @Inject private ExecutionSweepingOutputService sweepingOutputService;

  public ExecutionInfoKey getExecutionInfoKey(Ambiance ambiance, AzureWebAppInfraDelegateConfig infraDelegateConfig) {
    ServiceStepOutcome serviceOutcome = (ServiceStepOutcome) outcomeService.resolve(
        ambiance, RefObjectUtils.getOutcomeRefObject(OutcomeExpressionConstants.SERVICE));

    AzureWebAppInfrastructureOutcome infrastructure = getAzureWebAppInfrastructureOutcome(ambiance);

    Scope scope = Scope.builder()
                      .accountIdentifier(AmbianceUtils.getAccountId(ambiance))
                      .orgIdentifier(AmbianceUtils.getOrgIdentifier(ambiance))
                      .projectIdentifier(AmbianceUtils.getProjectIdentifier(ambiance))
                      .build();
    return ExecutionInfoKey.builder()
        .scope(scope)
        .deploymentIdentifier(getDeploymentIdentifier(
            ambiance, infraDelegateConfig.getAppName(), infraDelegateConfig.getDeploymentSlot()))
        .envIdentifier(infrastructure.getEnvironment().getIdentifier())
        .infraIdentifier(infrastructure.getInfraIdentifier())
        .serviceIdentifier(serviceOutcome.getIdentifier())
        .build();
  }

  public String getDeploymentIdentifier(Ambiance ambiance, String appName, String deploymentSlot) {
    AzureWebAppInfrastructureOutcome infrastructureOutcome = getAzureWebAppInfrastructureOutcome(ambiance);
    return String.format("%s-%s-%s-%s", infrastructureOutcome.getSubscription(),
        infrastructureOutcome.getResourceGroup(), appName, deploymentSlot);
  }

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
    OptionalOutcome startupCommandOutcome =
        outcomeService.resolveOptional(ambiance, RefObjectUtils.getOutcomeRefObject(STARTUP_COMMAND));
    OptionalOutcome applicationSettingsOutcome =
        outcomeService.resolveOptional(ambiance, RefObjectUtils.getOutcomeRefObject(APPLICATION_SETTINGS));
    OptionalOutcome connectionStringsOutcome =
        outcomeService.resolveOptional(ambiance, RefObjectUtils.getOutcomeRefObject(CONNECTION_STRINGS));

    if (startupCommandOutcome.isFound()) {
      StartupCommandOutcome startupCommand = (StartupCommandOutcome) startupCommandOutcome.getOutcome();
      settingsConfig.put(STARTUP_COMMAND, startupCommand.getStore());
    }

    NgAppSettingsSweepingOutput appSettingsOutput = fetchNgAppSettingsMetadataFromSweepingOutput(ambiance);

    if (appSettingsOutput != null && appSettingsOutput.getStore() != null
        && appSettingsOutput.getStore().getSpec() != null) {
      StoreConfig storeConfig = appSettingsOutput.getStore().getSpec();
      cdExpressionResolver.updateExpressions(ambiance, storeConfig);
      settingsConfig.put(APPLICATION_SETTINGS, storeConfig);
    } else if (applicationSettingsOutcome.isFound()) {
      ApplicationSettingsOutcome applicationSettings =
          (ApplicationSettingsOutcome) applicationSettingsOutcome.getOutcome();
      settingsConfig.put(APPLICATION_SETTINGS, applicationSettings.getStore());
    }

    NgConnectionStringsSweepingOutput connectionStringsOutput =
        fetchNgConnectionStringsMetadataFromSweepingOutput(ambiance);

    if (connectionStringsOutput != null && connectionStringsOutput.getStore() != null
        && connectionStringsOutput.getStore().getSpec() != null) {
      StoreConfig storeConfig = connectionStringsOutput.getStore().getSpec();
      cdExpressionResolver.updateExpressions(ambiance, storeConfig);
      settingsConfig.put(CONNECTION_STRINGS, storeConfig);
    } else if (connectionStringsOutcome.isFound()) {
      ConnectionStringsOutcome connectionStrings = (ConnectionStringsOutcome) connectionStringsOutcome.getOutcome();
      settingsConfig.put(CONNECTION_STRINGS, connectionStrings.getStore());
    }

    return settingsConfig;
  }

  private NgConnectionStringsSweepingOutput fetchNgConnectionStringsMetadataFromSweepingOutput(Ambiance ambiance) {
    final OptionalSweepingOutput resolveOptional = sweepingOutputService.resolveOptional(ambiance,
        RefObjectUtils.getOutcomeRefObject(ServiceStepV3Constants.SERVICE_CONNECTION_STRINGS_SWEEPING_OUTPUT));
    return resolveOptional.isFound() ? (NgConnectionStringsSweepingOutput) resolveOptional.getOutput()
                                     : NgConnectionStringsSweepingOutput.builder().build();
  }

  private NgAppSettingsSweepingOutput fetchNgAppSettingsMetadataFromSweepingOutput(Ambiance ambiance) {
    final OptionalSweepingOutput resolveOptional = sweepingOutputService.resolveOptional(
        ambiance, RefObjectUtils.getOutcomeRefObject(ServiceStepV3Constants.SERVICE_APP_SETTINGS_SWEEPING_OUTPUT));
    return resolveOptional.isFound() ? (NgAppSettingsSweepingOutput) resolveOptional.getOutput()
                                     : NgAppSettingsSweepingOutput.builder().build();
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
    return prepareTaskRequest(
        stepElementParameters, ambiance, taskParameters, taskType, taskType.getDisplayName(), units);
  }

  public TaskRequest prepareTaskRequest(StepElementParameters stepElementParameters, Ambiance ambiance,
      TaskParameters taskParameters, TaskType taskType, String displayName, List<String> units) {
    AzureWebAppStepParameters stepSpec = (AzureWebAppStepParameters) stepElementParameters.getSpec();
    List<TaskSelectorYaml> taskSelectors = stepSpec.getDelegateSelectors().getValue();

    final TaskData taskData = TaskData.builder()
                                  .async(true)
                                  .timeout(CDStepHelper.getTimeoutInMillis(stepElementParameters))
                                  .taskType(taskType.name())
                                  .parameters(new Object[] {taskParameters})
                                  .build();

    return cdStepHelper.prepareTaskRequest(
        ambiance, taskData, units, displayName, TaskSelectorYaml.toTaskSelector(emptyIfNull(taskSelectors)));
  }

  public Map<String, AppSettingsFile> fetchWebAppConfigsFromHarnessStore(
      Ambiance ambiance, Map<String, HarnessStore> harnessStoreConfigs) {
    return harnessStoreConfigs.entrySet().stream().collect(Collectors.toMap(
        Map.Entry::getKey, entry -> fetchFileContentFromHarnessStore(ambiance, entry.getKey(), entry.getValue())));
  }

  public AzureWebAppInfraDelegateConfig getInfraDelegateConfig(
      Ambiance ambiance, String webApp, String deploymentSlot) {
    AzureWebAppInfrastructureOutcome infrastructure = getAzureWebAppInfrastructureOutcome(ambiance);
    return getInfraDelegateConfig(ambiance, infrastructure, webApp, deploymentSlot);
  }

  @NotNull
  private AzureWebAppInfrastructureOutcome getAzureWebAppInfrastructureOutcome(Ambiance ambiance) {
    InfrastructureOutcome infrastructureOutcome = cdStepHelper.getInfrastructureOutcome(ambiance);
    if (!(infrastructureOutcome instanceof AzureWebAppInfrastructureOutcome)) {
      throw new InvalidArgumentsException(Pair.of("infrastructure",
          format("Invalid infrastructure type: %s, expected: %s", infrastructureOutcome.getKind(),
              InfrastructureKind.AZURE_WEB_APP)));
    }
    return (AzureWebAppInfrastructureOutcome) infrastructureOutcome;
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
        .appName(webApp.toLowerCase())
        .deploymentSlot(AzureResourceUtility.fixDeploymentSlotName(deploymentSlot, webApp))
        .encryptionDataDetails(azureHelperService.getEncryptionDetails(azureConnectorDTO, ngAccess))
        .build();
  }

  public ArtifactOutcome getPrimaryArtifactOutcome(Ambiance ambiance) {
    return cdStepHelper.resolveArtifactsOutcome(ambiance).orElseThrow(
        () -> new InvalidArgumentsException(Pair.of("artifacts", "Primary artifact is required for Azure WebApp")));
  }

  public AzureArtifactConfig getPrimaryArtifactConfig(Ambiance ambiance, ArtifactOutcome artifactOutcome) {
    switch (artifactOutcome.getArtifactType()) {
      case DOCKER_REGISTRY_NAME:
      case ECR_NAME:
      case GCR_NAME:
      case ACR_NAME:
      case NEXUS3_REGISTRY_NAME:
      case ARTIFACTORY_REGISTRY_NAME:
      case AMAZON_S3_NAME:
      case JENKINS_NAME:
      case AZURE_ARTIFACTS_NAME:
        if (isPackageArtifactType(artifactOutcome)) {
          return getAzurePackageArtifactConfig(ambiance, artifactOutcome);
        } else {
          return getAzureContainerArtifactConfig(ambiance, artifactOutcome);
        }
      default:
        throw new InvalidArgumentsException(Pair.of("artifacts",
            format("Artifact type %s is not yet supported in Azure WebApp", artifactOutcome.getArtifactType())));
    }
  }

  public Map<String, AppSettingsFile> getConfigValuesFromGitFetchResponse(
      Ambiance ambiance, GitFetchResponse gitFetchResponse) {
    return gitFetchResponse.getFilesFromMultipleRepo()
        .entrySet()
        .stream()
        .filter(entry -> isNotEmpty(entry.getValue().getFiles()))
        .collect(Collectors.toMap(Map.Entry::getKey,
            entry
            -> AppSettingsFile.create(engineExpressionService.renderExpression(
                ambiance, entry.getValue().getFiles().get(0).getFileContent()))));
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

  public boolean isPackageArtifactType(ArtifactOutcome artifactOutcome) {
    switch (artifactOutcome.getArtifactType()) {
      case ARTIFACTORY_REGISTRY_NAME:
        return !(artifactOutcome instanceof ArtifactoryArtifactOutcome);
      case AMAZON_S3_NAME:
        return artifactOutcome instanceof S3ArtifactOutcome;
      case NEXUS3_REGISTRY_NAME:
        NexusArtifactOutcome nexusArtifactOutcome = (NexusArtifactOutcome) artifactOutcome;
        return !RepositoryFormat.docker.name().equals(nexusArtifactOutcome.getRepositoryFormat());
      case JENKINS_NAME:
        return artifactOutcome instanceof JenkinsArtifactOutcome;
      case AZURE_ARTIFACTS_NAME:
        return artifactOutcome instanceof AzureArtifactsOutcome;
      default:
        return false;
    }
  }

  private AzureArtifactConfig getAzureContainerArtifactConfig(Ambiance ambiance, ArtifactOutcome artifactOutcome) {
    ConnectorInfoDTO connectorInfo;
    AzureContainerArtifactConfigBuilder artifactConfigBuilder = AzureContainerArtifactConfig.builder();

    switch (artifactOutcome.getArtifactType()) {
      case DOCKER_REGISTRY_NAME:
        DockerArtifactOutcome dockerArtifactOutcome = (DockerArtifactOutcome) artifactOutcome;
        connectorInfo = cdStepHelper.getConnector(dockerArtifactOutcome.getConnectorRef(), ambiance);
        artifactConfigBuilder.registryType(
            getAzureRegistryType((DockerConnectorDTO) connectorInfo.getConnectorConfig()));
        artifactConfigBuilder.image(dockerArtifactOutcome.getImage());
        artifactConfigBuilder.tag(dockerArtifactOutcome.getTag());
        break;
      case ACR_NAME:
        AcrArtifactOutcome acrArtifactOutcome = (AcrArtifactOutcome) artifactOutcome;
        connectorInfo = cdStepHelper.getConnector(acrArtifactOutcome.getConnectorRef(), ambiance);
        artifactConfigBuilder.registryType(AzureRegistryType.ACR);
        artifactConfigBuilder.image(acrArtifactOutcome.getImage());
        artifactConfigBuilder.tag(acrArtifactOutcome.getTag());
        artifactConfigBuilder.registryHostname(acrArtifactOutcome.getRegistry());
        break;
      case ECR_NAME:
        EcrArtifactOutcome ecrArtifactOutcome = (EcrArtifactOutcome) artifactOutcome;
        connectorInfo = cdStepHelper.getConnector(ecrArtifactOutcome.getConnectorRef(), ambiance);
        artifactConfigBuilder.registryType(AzureRegistryType.ECR);
        artifactConfigBuilder.image(ecrArtifactOutcome.getImage());
        artifactConfigBuilder.tag(ecrArtifactOutcome.getTag());
        artifactConfigBuilder.region(ecrArtifactOutcome.getRegion());
        break;
      case GCR_NAME:
        GcrArtifactOutcome gcrArtifactOutcome = (GcrArtifactOutcome) artifactOutcome;
        connectorInfo = cdStepHelper.getConnector(gcrArtifactOutcome.getConnectorRef(), ambiance);
        artifactConfigBuilder.registryType(AzureRegistryType.GCR);
        artifactConfigBuilder.image(gcrArtifactOutcome.getImage());
        artifactConfigBuilder.tag(gcrArtifactOutcome.getTag());
        artifactConfigBuilder.registryHostname(gcrArtifactOutcome.getRegistryHostname());
        break;
      case NEXUS3_REGISTRY_NAME:
        NexusArtifactOutcome nexusArtifactOutcome = (NexusArtifactOutcome) artifactOutcome;
        connectorInfo = cdStepHelper.getConnector(nexusArtifactOutcome.getConnectorRef(), ambiance);
        artifactConfigBuilder.registryType(AzureRegistryType.NEXUS_PRIVATE_REGISTRY);
        artifactConfigBuilder.image(nexusArtifactOutcome.getImage());
        artifactConfigBuilder.tag(nexusArtifactOutcome.getTag());
        artifactConfigBuilder.registryHostname(nexusArtifactOutcome.getRegistryHostname());
        break;
      case ARTIFACTORY_REGISTRY_NAME:
        ArtifactoryArtifactOutcome artifactoryArtifactOutcome = (ArtifactoryArtifactOutcome) artifactOutcome;
        connectorInfo = cdStepHelper.getConnector(artifactoryArtifactOutcome.getConnectorRef(), ambiance);
        artifactConfigBuilder.registryType(AzureRegistryType.ARTIFACTORY_PRIVATE_REGISTRY);
        artifactConfigBuilder.image(artifactoryArtifactOutcome.getImage());
        artifactConfigBuilder.tag(artifactoryArtifactOutcome.getTag());
        artifactConfigBuilder.registryHostname(artifactoryArtifactOutcome.getRegistryHostname());
        break;
      default:
        throw new InvalidArgumentsException(
            Pair.of("artifacts", format("Unsupported artifact type %s", artifactOutcome.getArtifactType())));
    }

    NGAccess ngAccess = AmbianceUtils.getNgAccess(ambiance);
    List<EncryptedDataDetail> encryptedDataDetails = new ArrayList<>();
    List<DecryptableEntity> decryptableEntities = connectorInfo.getConnectorConfig().getDecryptableEntities();
    if (decryptableEntities != null) {
      for (DecryptableEntity decryptableEntity : decryptableEntities) {
        encryptedDataDetails.addAll(secretManagerClientService.getEncryptionDetails(ngAccess, decryptableEntity));
      }
    }

    return artifactConfigBuilder.connectorConfig(connectorInfo.getConnectorConfig())
        .encryptedDataDetails(encryptedDataDetails)
        .build();
  }

  private AzurePackageArtifactConfig getAzurePackageArtifactConfig(Ambiance ambiance, ArtifactOutcome artifactOutcome) {
    final AzurePackageArtifactConfigBuilder artifactConfigBuilder = AzurePackageArtifactConfig.builder();
    ConnectorInfoDTO connectorInfoDTO;
    switch (artifactOutcome.getArtifactType()) {
      case ARTIFACTORY_REGISTRY_NAME:
        ArtifactoryGenericArtifactOutcome artifactoryArtifactOutcome =
            (ArtifactoryGenericArtifactOutcome) artifactOutcome;
        artifactConfigBuilder.sourceType(ArtifactSourceType.ARTIFACTORY_REGISTRY);
        artifactConfigBuilder.artifactDetails(
            ArtifactoryAzureArtifactRequestDetails.builder()
                .repository(artifactoryArtifactOutcome.getRepositoryName())
                .repositoryFormat(artifactoryArtifactOutcome.getRepositoryFormat())
                .artifactPaths(new ArrayList<>(singletonList(artifactoryArtifactOutcome.getArtifactPath())))
                .build());
        connectorInfoDTO = cdStepHelper.getConnector(artifactoryArtifactOutcome.getConnectorRef(), ambiance);
        break;
      case AMAZON_S3_NAME:
        S3ArtifactOutcome s3ArtifactOutcome = (S3ArtifactOutcome) artifactOutcome;
        artifactConfigBuilder.sourceType(AMAZONS3);
        artifactConfigBuilder.artifactDetails(AwsS3AzureArtifactRequestDetails.builder()
                                                  .region(s3ArtifactOutcome.getRegion())
                                                  .bucketName(s3ArtifactOutcome.getBucketName())
                                                  .filePath(s3ArtifactOutcome.getFilePath())
                                                  .identifier(s3ArtifactOutcome.getIdentifier())
                                                  .build());
        connectorInfoDTO = cdStepHelper.getConnector(s3ArtifactOutcome.getConnectorRef(), ambiance);
        break;
      case NEXUS3_REGISTRY_NAME:
        NexusArtifactOutcome nexusArtifactOutcome = (NexusArtifactOutcome) artifactOutcome;
        if (!cdFeatureFlagHelper.isEnabled(
                AmbianceUtils.getAccountId(ambiance), FeatureName.AZURE_WEB_APP_NG_NEXUS_PACKAGE)) {
          throw new AccessDeniedException(
              format(
                  "Nexus artifact source with repository format '%s' not enabled for account '%s'. Please contact harness customer care to enable FF '%s'.",
                  nexusArtifactOutcome.getRepositoryFormat(), AmbianceUtils.getAccountId(ambiance),
                  FeatureName.AZURE_WEB_APP_NG_NEXUS_PACKAGE.name()),
              ErrorCode.NG_ACCESS_DENIED, WingsException.USER);
        }
        artifactConfigBuilder.sourceType(NEXUS3_REGISTRY);
        artifactConfigBuilder.artifactDetails(NexusAzureArtifactRequestDetails.builder()
                                                  .identifier(nexusArtifactOutcome.getIdentifier())
                                                  .certValidationRequired(false)
                                                  .artifactUrl(nexusArtifactOutcome.getMetadata().get("url"))
                                                  .metadata(nexusArtifactOutcome.getMetadata())
                                                  .repositoryFormat(nexusArtifactOutcome.getRepositoryFormat())
                                                  .build());
        connectorInfoDTO = cdStepHelper.getConnector(nexusArtifactOutcome.getConnectorRef(), ambiance);
        break;
      case JENKINS_NAME:
        if (!cdFeatureFlagHelper.isEnabled(
                AmbianceUtils.getAccountId(ambiance), FeatureName.AZURE_WEBAPP_NG_JENKINS_ARTIFACTS)) {
          throw new AccessDeniedException("The Jenkins artifact source in NG is not enabled for this account."
                  + " Please contact harness customer care.",
              ErrorCode.NG_ACCESS_DENIED, WingsException.USER);
        }
        JenkinsArtifactOutcome jenkinsArtifactOutcome = (JenkinsArtifactOutcome) artifactOutcome;
        artifactConfigBuilder.sourceType(JENKINS);
        artifactConfigBuilder.artifactDetails(JenkinsAzureArtifactRequestDetails.builder()
                                                  .artifactPath(jenkinsArtifactOutcome.getArtifactPath())
                                                  .jobName(jenkinsArtifactOutcome.getJobName())
                                                  .build(jenkinsArtifactOutcome.getBuild())
                                                  .identifier(jenkinsArtifactOutcome.getIdentifier())
                                                  .build());
        connectorInfoDTO = cdStepHelper.getConnector(jenkinsArtifactOutcome.getConnectorRef(), ambiance);
        break;
      case AZURE_ARTIFACTS_NAME:
        AzureArtifactsOutcome azureArtifactsOutcome = (AzureArtifactsOutcome) artifactOutcome;
        artifactConfigBuilder.sourceType(AZURE_ARTIFACTS);
        artifactConfigBuilder.artifactDetails(AzureDevOpsArtifactRequestDetails.builder()
                                                  .packageType(azureArtifactsOutcome.getPackageType())
                                                  .packageName(azureArtifactsOutcome.getPackageName())
                                                  .project(azureArtifactsOutcome.getProject())
                                                  .feed(azureArtifactsOutcome.getFeed())
                                                  .scope(azureArtifactsOutcome.getScope())
                                                  .version(azureArtifactsOutcome.getVersion())
                                                  .identifier(azureArtifactsOutcome.getIdentifier())
                                                  .versionRegex(azureArtifactsOutcome.getVersionRegex())
                                                  .build());
        connectorInfoDTO = cdStepHelper.getConnector(azureArtifactsOutcome.getConnectorRef(), ambiance);
        break;
      default:
        throw new InvalidArgumentsException(
            Pair.of("artifacts", format("Unsupported artifact type %s", artifactOutcome.getArtifactType())));
    }

    NGAccess ngAccess = AmbianceUtils.getNgAccess(ambiance);
    List<EncryptedDataDetail> encryptedDataDetails = new ArrayList<>();
    if (connectorInfoDTO.getConnectorConfig().getDecryptableEntities() != null) {
      for (DecryptableEntity decryptableEntity : connectorInfoDTO.getConnectorConfig().getDecryptableEntities()) {
        encryptedDataDetails.addAll(secretManagerClientService.getEncryptionDetails(ngAccess, decryptableEntity));
      }
    }

    return artifactConfigBuilder.connectorConfig(connectorInfoDTO.getConnectorConfig())
        .encryptedDataDetails(encryptedDataDetails)
        .build();
  }

  private AzureRegistryType getAzureRegistryType(DockerConnectorDTO dockerConfig) {
    if (dockerConfig.getAuth().getAuthType().equals(ANONYMOUS)) {
      return AzureRegistryType.DOCKER_HUB_PUBLIC;
    } else {
      return AzureRegistryType.DOCKER_HUB_PRIVATE;
    }
  }

  public AppSettingsFile fetchFileContentFromHarnessStore(
      Ambiance ambiance, String settingsType, HarnessStore harnessStore) {
    HarnessStore renderedHarnessStore = (HarnessStore) cdExpressionResolver.updateExpressions(ambiance, harnessStore);
    if (!ParameterField.isNull(renderedHarnessStore.getFiles())
        && isNotEmpty(renderedHarnessStore.getFiles().getValue())) {
      List<String> harnessStoreFiles = renderedHarnessStore.getFiles().getValue();
      String firstFile = harnessStoreFiles.stream().findFirst().orElseThrow(
          () -> new InvalidArgumentsException(Pair.of(settingsType, "No file configured for harness file store")));
      return fetchFileContentFromFileStore(ambiance, settingsType, firstFile);
    } else if (!ParameterField.isNull(renderedHarnessStore.getSecretFiles())
        && isNotEmpty(renderedHarnessStore.getSecretFiles().getValue())) {
      List<String> harnessStoreSecretFiles = renderedHarnessStore.getSecretFiles().getValue();
      String firstSecretFile = harnessStoreSecretFiles.stream().findFirst().orElseThrow(
          () -> new InvalidArgumentsException(Pair.of(settingsType, "No secret file configured for harness store")));
      return fetchSecretFile(ambiance, settingsType, firstSecretFile);
    }

    throw new InvalidArgumentsException(Pair.of(settingsType, "Either 'files' or 'secretFiles' is required"));
  }

  @Nullable
  public AzureWebAppsStageExecutionDetails findLastSuccessfulStageExecutionDetails(
      Ambiance ambiance, AzureWebAppInfraDelegateConfig infraDelegateConfig) {
    ExecutionInfoKey executionInfoKey = getExecutionInfoKey(ambiance, infraDelegateConfig);
    List<StageExecutionInfo> stageExecutionInfoList = stageExecutionInfoService.listLatestSuccessfulStageExecutionInfo(
        executionInfoKey, ambiance.getStageExecutionId(), 2);

    if (isNotEmpty(stageExecutionInfoList)) {
      AzureWebAppsStageExecutionDetails executionDetails =
          (AzureWebAppsStageExecutionDetails) stageExecutionInfoList.get(0).getExecutionDetails();
      log.info(
          "Last successful deployment found with pipeline executionId: {}", executionDetails.getPipelineExecutionId());
      if (isNotEmpty(executionDetails.getTargetSlot())) {
        if (stageExecutionInfoList.size() == 2) {
          executionDetails = (AzureWebAppsStageExecutionDetails) stageExecutionInfoList.get(1).getExecutionDetails();
          log.info("Pre last successful deployment found with pipeline executionId: {}",
              executionDetails.getPipelineExecutionId());
        } else {
          executionDetails = null;
        }
      }

      return executionDetails;
    }

    return null;
  }

  public String getTaskTypeVersion(ArtifactOutcome artifactOutcome) {
    switch (artifactOutcome.getArtifactType()) {
      case NEXUS3_REGISTRY_NAME:
      case JENKINS_NAME:
      case AZURE_ARTIFACTS_NAME:
        return isPackageArtifactType(artifactOutcome) ? TaskType.AZURE_WEB_APP_TASK_NG_V2.name()
                                                      : TaskType.AZURE_WEB_APP_TASK_NG.name();
      default:
        return TaskType.AZURE_WEB_APP_TASK_NG.name();
    }
  }

  private AppSettingsFile fetchFileContentFromFileStore(Ambiance ambiance, String settingsType, String filePath) {
    NGAccess ngAccess = AmbianceUtils.getNgAccess(ambiance);
    FileReference fileReference = FileReference.of(
        filePath, ngAccess.getAccountIdentifier(), ngAccess.getOrgIdentifier(), ngAccess.getProjectIdentifier());
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
        return AppSettingsFile.create(engineExpressionService.renderExpression(ambiance, fileNode.getContent()));
      }

      log.warn("Received empty or null content for file: {}", fileStoreNodeDTO.getPath());
      return AppSettingsFile.create("");
    }

    log.error("Unknown file store node: {}", fileStoreNodeDTO.getClass().getSimpleName());
    throw new InvalidArgumentsException(Pair.of(settingsType, "Unsupported file store node"));
  }

  private AppSettingsFile fetchSecretFile(Ambiance ambiance, String settingsType, String secretRef) {
    NGAccess ngAccess = AmbianceUtils.getNgAccess(ambiance);
    IdentifierRef fileRef = IdentifierRefHelper.getIdentifierRef(
        secretRef, ngAccess.getAccountIdentifier(), ngAccess.getOrgIdentifier(), ngAccess.getProjectIdentifier());
    EncryptedAppSettingsFile encryptedAppSettingsFile =
        EncryptedAppSettingsFile.builder()
            .secretFileReference(SecretRefHelper.createSecretRef(fileRef.getIdentifier()))
            .build();
    List<EncryptedDataDetail> encryptedDataDetails =
        ngEncryptedDataService.getEncryptionDetails(ngAccess, encryptedAppSettingsFile);
    if (encryptedDataDetails == null) {
      throw new InvalidArgumentsException(
          Pair.of(settingsType, format("No encrypted data details found for secret file %s", secretRef)));
    }

    return AppSettingsFile.create(encryptedAppSettingsFile, encryptedDataDetails);
  }
}
