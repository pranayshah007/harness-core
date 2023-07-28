/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.plugininfoproviders;
import static io.harness.common.ParameterFieldHelper.getParameterFieldValue;
import static io.harness.connector.ConnectorModule.DEFAULT_CONNECTOR_SERVICE;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.exception.WingsException.USER;
import static io.harness.k8s.manifest.ManifestHelper.normalizeFolderPath;

import static java.lang.String.format;
import static java.util.Objects.isNull;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.IdentifierRef;
import io.harness.cdng.artifact.outcome.ArtifactOutcome;
import io.harness.cdng.artifact.outcome.ArtifactoryGenericArtifactOutcome;
import io.harness.cdng.artifact.outcome.ArtifactsOutcome;
import io.harness.cdng.artifact.outcome.EcrArtifactOutcome;
import io.harness.cdng.artifact.outcome.S3ArtifactOutcome;
import io.harness.cdng.expressions.CDExpressionResolver;
import io.harness.cdng.infra.beans.InfrastructureOutcome;
import io.harness.cdng.infra.beans.ServerlessAwsLambdaInfrastructureOutcome;
import io.harness.cdng.manifest.ManifestStoreType;
import io.harness.cdng.manifest.ManifestType;
import io.harness.cdng.manifest.steps.outcome.ManifestsOutcome;
import io.harness.cdng.manifest.yaml.GitStoreConfig;
import io.harness.cdng.manifest.yaml.ManifestOutcome;
import io.harness.cdng.manifest.yaml.ServerlessAwsLambdaManifestOutcome;
import io.harness.cdng.manifest.yaml.ValuesManifestOutcome;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfig;
import io.harness.cdng.serverless.ArtifactType;
import io.harness.cdng.serverless.ServerlessEntityHelper;
import io.harness.cdng.serverless.container.steps.ServerlessAwsLambdaDeployV2StepInfo;
import io.harness.cdng.serverless.container.steps.ServerlessAwsLambdaPackageV2StepInfo;
import io.harness.cdng.serverless.container.steps.ServerlessAwsLambdaV2BaseStepInfo;
import io.harness.cdng.stepsdependency.constants.OutcomeExpressionConstants;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.connector.services.ConnectorService;
import io.harness.delegate.beans.connector.ConnectorConfigDTO;
import io.harness.delegate.beans.connector.artifactoryconnector.ArtifactoryAuthCredentialsDTO;
import io.harness.delegate.beans.connector.artifactoryconnector.ArtifactoryAuthenticationDTO;
import io.harness.delegate.beans.connector.artifactoryconnector.ArtifactoryConnectorDTO;
import io.harness.delegate.beans.connector.artifactoryconnector.ArtifactoryUsernamePasswordAuthDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsConnectorDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsCredentialDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsCredentialSpecDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsManualConfigSpecDTO;
import io.harness.delegate.task.serverless.ServerlessAwsLambdaInfraConfig;
import io.harness.delegate.task.serverless.ServerlessInfraConfig;
import io.harness.encryption.SecretRefData;
import io.harness.exception.GeneralException;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.NGAccess;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.data.OptionalOutcome;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outcome.OutcomeService;
import io.harness.pms.yaml.ParameterField;
import io.harness.utils.IdentifierRefHelper;
import io.harness.yaml.utils.NGVariablesUtils;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.jooq.tools.StringUtils;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true,
    components = {HarnessModuleComponent.CDS_SERVERLESS, HarnessModuleComponent.CDS_PIPELINE,
        HarnessModuleComponent.CDS_GITX, HarnessModuleComponent.CDS_ECS, HarnessModuleComponent.CDS_FIRST_GEN})
public class ServerlessV2PluginInfoProviderHelper {
  @Inject private CDExpressionResolver cdExpressionResolver;
  @Inject private OutcomeService outcomeService;

  @Inject private ServerlessEntityHelper serverlessEntityHelper;

  @Named(DEFAULT_CONNECTOR_SERVICE) @Inject private ConnectorService connectorService;

  @Inject PluginInfoProviderUtils pluginInfoProviderUtils;
  private static final String PLUGIN_PATH_PREFIX = "harness";

  public ManifestOutcome getServerlessAwsLambdaDirectoryManifestOutcome(Collection<ManifestOutcome> manifestOutcomes) {
    List<ManifestOutcome> manifestOutcomeList =
        manifestOutcomes.stream()
            .filter(manifestOutcome -> ManifestType.ServerlessAwsLambda.equals(manifestOutcome.getType()))
            .collect(Collectors.toList());
    if (manifestOutcomeList.size() != 1) {
      throw new InvalidRequestException("One ServerlessAwsLambda Manifest is Required in Service Step");
    }
    return manifestOutcomeList.isEmpty() ? null : manifestOutcomeList.get(0);
  }

  public ManifestOutcome getServerlessAwsLambdaValuesManifestOutcome(Collection<ManifestOutcome> manifestOutcomes) {
    List<ManifestOutcome> manifestOutcomeList =
        manifestOutcomes.stream()
            .filter(manifestOutcome -> ManifestType.VALUES.equals(manifestOutcome.getType()))
            .collect(Collectors.toList());
    if (manifestOutcomeList.size() > 1) {
      throw new InvalidRequestException("Not more than one Values Manifest should be configured in service step");
    }
    return manifestOutcomeList.isEmpty() ? null : manifestOutcomeList.get(0);
  }

  public String getServerlessAwsLambdaDirectoryPathFromManifestOutcome(
      ServerlessAwsLambdaManifestOutcome serverlessAwsLambdaManifestOutcome) {
    GitStoreConfig gitStoreConfig = (GitStoreConfig) serverlessAwsLambdaManifestOutcome.getStore();

    String path = String.format("/%s/%s/%s", PLUGIN_PATH_PREFIX,
        removeExtraSlashesInString(serverlessAwsLambdaManifestOutcome.getIdentifier()),
        removeExtraSlashesInString(gitStoreConfig.getPaths().getValue().get(0)));
    return removeTrailingSlashesInString(path);
  }

  public String removeExtraSlashesInString(String path) {
    return path.replaceAll("^/|/$", "");
  }

  public String removeTrailingSlashesInString(String path) {
    return path.replaceAll("/$", "");
  }

  public Map<String, String> getEnvironmentVariables(
      Ambiance ambiance, ServerlessAwsLambdaV2BaseStepInfo serverlessAwsLambdaV2BaseStepInfo) {
    ParameterField<Map<String, String>> envVariables = serverlessAwsLambdaV2BaseStepInfo.getEnvVariables();

    ManifestsOutcome manifestsOutcome = fetchManifestsOutcome(ambiance);
    ManifestOutcome serverlessManifestOutcome = pluginInfoProviderUtils.getServerlessManifestOutcome(
        manifestsOutcome.values(), ManifestType.ServerlessAwsLambda);
    StoreConfig storeConfig = serverlessManifestOutcome.getStore();
    if (!ManifestStoreType.isInGitSubset(storeConfig.getKind())) {
      throw new InvalidRequestException("Invalid kind of storeConfig for Serverless step", USER);
    }
    String configOverridePath = getConfigOverridePath(serverlessManifestOutcome);
    if (isNull(configOverridePath)) {
      configOverridePath = "";
    }

    GitStoreConfig gitStoreConfig = (GitStoreConfig) storeConfig;
    List<String> gitPaths = getFolderPathsForManifest(gitStoreConfig);

    if (isEmpty(gitPaths)) {
      throw new InvalidRequestException("Atleast one git path need to be specified", USER);
    }

    String serverlessDirectory = getServerlessAwsLambdaDirectoryPathFromManifestOutcome(
        (ServerlessAwsLambdaManifestOutcome) serverlessManifestOutcome);

    InfrastructureOutcome infrastructureOutcome = (InfrastructureOutcome) outcomeService.resolve(
        ambiance, RefObjectUtils.getOutcomeRefObject(OutcomeExpressionConstants.INFRASTRUCTURE_OUTCOME));
    ServerlessAwsLambdaInfraConfig serverlessAwsLambdaInfraConfig =
        (ServerlessAwsLambdaInfraConfig) getServerlessInfraConfig(infrastructureOutcome, ambiance);
    String stageName = serverlessAwsLambdaInfraConfig.getStage();
    String region = serverlessAwsLambdaInfraConfig.getRegion();

    ServerlessAwsLambdaInfrastructureOutcome serverlessAwsLambdaInfrastructureOutcome =
        (ServerlessAwsLambdaInfrastructureOutcome) infrastructureOutcome;

    String awsConnectorRef = serverlessAwsLambdaInfrastructureOutcome.getConnectorRef();

    String awsAccessKey = null;
    String awsSecretKey = null;

    if (awsConnectorRef != null) {
      NGAccess ngAccess = getNgAccess(ambiance);

      IdentifierRef identifierRef = getIdentifierRef(awsConnectorRef, ngAccess);

      Optional<ConnectorResponseDTO> connectorDTO = connectorService.get(identifierRef.getAccountIdentifier(),
          identifierRef.getOrgIdentifier(), identifierRef.getProjectIdentifier(), identifierRef.getIdentifier());

      ConnectorInfoDTO connectorInfoDTO = connectorDTO.get().getConnector();
      ConnectorConfigDTO connectorConfigDTO = connectorInfoDTO.getConnectorConfig();
      AwsConnectorDTO awsConnectorDTO = (AwsConnectorDTO) connectorConfigDTO;
      AwsCredentialDTO awsCredentialDTO = awsConnectorDTO.getCredential();
      AwsCredentialSpecDTO awsCredentialSpecDTO = awsCredentialDTO.getConfig();

      if (awsCredentialSpecDTO instanceof AwsManualConfigSpecDTO) {
        AwsManualConfigSpecDTO awsManualConfigSpecDTO = (AwsManualConfigSpecDTO) awsCredentialSpecDTO;

        if (!StringUtils.isEmpty(awsManualConfigSpecDTO.getAccessKey())) {
          awsAccessKey = awsManualConfigSpecDTO.getAccessKey();
        } else {
          awsAccessKey = getKey(ambiance, awsManualConfigSpecDTO.getAccessKeyRef());
        }

        awsSecretKey = getKey(ambiance, awsManualConfigSpecDTO.getSecretKeyRef());
      }
    }

    HashMap<String, String> environmentVariablesMap = new HashMap<>();

    populateCommandOptions(ambiance, serverlessAwsLambdaV2BaseStepInfo, environmentVariablesMap);

    environmentVariablesMap.put("PLUGIN_SERVERLESS_DIR", serverlessDirectory);
    environmentVariablesMap.put("PLUGIN_SERVERLESS_YAML_CUSTOM_PATH", configOverridePath);
    environmentVariablesMap.put("PLUGIN_SERVERLESS_STAGE", stageName);

    if (awsAccessKey != null) {
      environmentVariablesMap.put("PLUGIN_AWS_ACCESS_KEY", awsAccessKey);
    }

    if (awsSecretKey != null) {
      environmentVariablesMap.put("PLUGIN_AWS_SECRET_KEY", awsSecretKey);
    }

    if (region != null) {
      environmentVariablesMap.put("PLUGIN_REGION", region);
    }

    Optional<ArtifactsOutcome> artifactsOutcome = getArtifactsOutcome(ambiance);
    if (artifactsOutcome.isPresent()) {
      if (artifactsOutcome.get().getPrimary() != null) {
        populateArtifactEnvironmentVariables(artifactsOutcome.get().getPrimary(), ambiance, environmentVariablesMap);
      }
    }

    if (envVariables != null && envVariables.getValue() != null) {
      environmentVariablesMap.putAll(envVariables.getValue());
    }

    return environmentVariablesMap;
  }

  public String getValuesPathFromValuesManifestOutcome(ValuesManifestOutcome valuesManifestOutcome) {
    GitStoreConfig gitStoreConfig = (GitStoreConfig) valuesManifestOutcome.getStore();
    String path = String.format("/%s/%s/%s", PLUGIN_PATH_PREFIX,
        removeExtraSlashesInString(valuesManifestOutcome.getIdentifier()),
        removeExtraSlashesInString(gitStoreConfig.getPaths().getValue().get(0)));
    return removeTrailingSlashesInString(path);
  }

  public void populateCommandOptions(Ambiance ambiance,
      ServerlessAwsLambdaV2BaseStepInfo serverlessAwsLambdaV2BaseStepInfo,
      HashMap<String, String> serverlessPrepareRollbackEnvironmentVariablesMap) {
    if (serverlessAwsLambdaV2BaseStepInfo instanceof ServerlessAwsLambdaDeployV2StepInfo) {
      ServerlessAwsLambdaDeployV2StepInfo serverlessAwsLambdaDeployV2StepInfo =
          (ServerlessAwsLambdaDeployV2StepInfo) serverlessAwsLambdaV2BaseStepInfo;
      ParameterField<List<String>> deployCommandOptions = serverlessAwsLambdaDeployV2StepInfo.getDeployCommandOptions();
      if (deployCommandOptions != null) {
        cdExpressionResolver.updateExpressions(ambiance, deployCommandOptions);
        serverlessPrepareRollbackEnvironmentVariablesMap.put(
            "PLUGIN_DEPLOY_COMMAND_OPTIONS", String.join(" ", deployCommandOptions.getValue()));
      }
    } else if (serverlessAwsLambdaV2BaseStepInfo instanceof ServerlessAwsLambdaPackageV2StepInfo) {
      ServerlessAwsLambdaPackageV2StepInfo serverlessAwsLambdaPackageV2StepInfo =
          (ServerlessAwsLambdaPackageV2StepInfo) serverlessAwsLambdaV2BaseStepInfo;
      ParameterField<List<String>> packageCommandOptions =
          serverlessAwsLambdaPackageV2StepInfo.getPackageCommandOptions();
      if (packageCommandOptions != null) {
        cdExpressionResolver.updateExpressions(ambiance, packageCommandOptions);
        serverlessPrepareRollbackEnvironmentVariablesMap.put(
            "PLUGIN_PACKAGE_COMMAND_OPTIONS", String.join(" ", packageCommandOptions.getValue()));
      }
    }
  }

  public Optional<ArtifactsOutcome> getArtifactsOutcome(Ambiance ambiance) {
    OptionalOutcome artifactsOutcomeOption = outcomeService.resolveOptional(
        ambiance, RefObjectUtils.getOutcomeRefObject(OutcomeExpressionConstants.ARTIFACTS));
    if (artifactsOutcomeOption.isFound()) {
      ArtifactsOutcome artifactsOutcome = (ArtifactsOutcome) artifactsOutcomeOption.getOutcome();
      cdExpressionResolver.updateExpressions(ambiance, artifactsOutcome);
      return Optional.of(artifactsOutcome);
    }
    return Optional.empty();
  }

  public ServerlessInfraConfig getServerlessInfraConfig(InfrastructureOutcome infrastructure, Ambiance ambiance) {
    NGAccess ngAccess = AmbianceUtils.getNgAccess(ambiance);
    return serverlessEntityHelper.getServerlessInfraConfig(infrastructure, ngAccess);
  }

  public ManifestsOutcome fetchManifestsOutcome(Ambiance ambiance) {
    OptionalOutcome manifestsOutcome = outcomeService.resolveOptional(
        ambiance, RefObjectUtils.getOutcomeRefObject(OutcomeExpressionConstants.MANIFESTS));

    if (!manifestsOutcome.isFound()) {
      String stageName =
          AmbianceUtils.getStageLevelFromAmbiance(ambiance).map(Level::getIdentifier).orElse("Deployment stage");
      String stepType =
          Optional.ofNullable(AmbianceUtils.getCurrentStepType(ambiance)).map(StepType::getType).orElse("Serverless");
      throw new GeneralException(
          format("No manifests found in stage %s. %s step requires a manifest defined in stage service definition",
              stageName, stepType));
    }
    ManifestsOutcome outcome = (ManifestsOutcome) manifestsOutcome.getOutcome();
    cdExpressionResolver.updateExpressions(ambiance, outcome);
    return outcome;
  }

  public void populateArtifactEnvironmentVariables(ArtifactOutcome artifactOutcome, Ambiance ambiance,
      HashMap<String, String> serverlessPrepareRollbackEnvironmentVariablesMap) {
    ConnectorInfoDTO connectorDTO;
    NGAccess ngAccess = AmbianceUtils.getNgAccess(ambiance);

    if (artifactOutcome instanceof ArtifactoryGenericArtifactOutcome) {
      String artifactoryUserName = null;
      String artifactoryPassword = null;

      ArtifactoryGenericArtifactOutcome artifactoryGenericArtifactOutcome =
          (ArtifactoryGenericArtifactOutcome) artifactOutcome;
      connectorDTO =
          serverlessEntityHelper.getConnectorInfoDTO(artifactoryGenericArtifactOutcome.getConnectorRef(), ngAccess);
      ConnectorConfigDTO connectorConfigDTO = connectorDTO.getConnectorConfig();
      ArtifactoryConnectorDTO artifactoryConnectorDTO = (ArtifactoryConnectorDTO) connectorConfigDTO;
      ArtifactoryAuthenticationDTO artifactoryAuthenticationDTO = artifactoryConnectorDTO.getAuth();
      ArtifactoryAuthCredentialsDTO artifactoryAuthCredentialsDTO = artifactoryAuthenticationDTO.getCredentials();

      serverlessPrepareRollbackEnvironmentVariablesMap.put(
          "PLUGIN_REPOSITORY_NAME", artifactoryGenericArtifactOutcome.getRepositoryName());
      serverlessPrepareRollbackEnvironmentVariablesMap.put(
          "PLUGIN_FILE_PATH", artifactoryGenericArtifactOutcome.getArtifactPath());
      serverlessPrepareRollbackEnvironmentVariablesMap.put(
          "PLUGIN_ARTIFACT_IDENTIFIER", artifactoryGenericArtifactOutcome.getIdentifier());
      serverlessPrepareRollbackEnvironmentVariablesMap.put(
          "PLUGIN_ARTIFACT_DIRECTORY", artifactoryGenericArtifactOutcome.getArtifactDirectory());
      serverlessPrepareRollbackEnvironmentVariablesMap.put(
          "PLUGIN_ARTIFACTORY_URL", artifactoryConnectorDTO.getArtifactoryServerUrl());

      if (artifactoryAuthCredentialsDTO instanceof ArtifactoryUsernamePasswordAuthDTO) {
        ArtifactoryUsernamePasswordAuthDTO artifactoryUsernamePasswordAuthDTO =
            (ArtifactoryUsernamePasswordAuthDTO) artifactoryAuthCredentialsDTO;

        if (!StringUtils.isEmpty(artifactoryUsernamePasswordAuthDTO.getUsername())) {
          artifactoryUserName = artifactoryUsernamePasswordAuthDTO.getUsername();
        } else {
          artifactoryUserName = getKey(ambiance, artifactoryUsernamePasswordAuthDTO.getUsernameRef());
        }

        artifactoryPassword = getKey(ambiance, artifactoryUsernamePasswordAuthDTO.getPasswordRef());
      }

      serverlessPrepareRollbackEnvironmentVariablesMap.put("PLUGIN_ARTIFACTORY_USERNAME", artifactoryUserName);
      serverlessPrepareRollbackEnvironmentVariablesMap.put("PLUGIN_ARTIFACTORY_PASSWORD", artifactoryPassword);
      serverlessPrepareRollbackEnvironmentVariablesMap.put(
          "PLUGIN_PRIMARY_ARTIFACT_TYPE", ArtifactType.ARTIFACTORY.getArtifactType());

    } else if (artifactOutcome instanceof EcrArtifactOutcome) {
      EcrArtifactOutcome ecrArtifactOutcome = (EcrArtifactOutcome) artifactOutcome;
      serverlessPrepareRollbackEnvironmentVariablesMap.put("PLUGIN_IMAGE_PATH", ecrArtifactOutcome.getImage());
      serverlessPrepareRollbackEnvironmentVariablesMap.put(
          "PLUGIN_PRIMARY_ARTIFACT_TYPE", ArtifactType.ECR.getArtifactType());

    } else if (artifactOutcome instanceof S3ArtifactOutcome) {
      String s3AwsAccessKey = null;
      String s3AwsSecretKey = null;

      S3ArtifactOutcome s3ArtifactOutcome = (S3ArtifactOutcome) artifactOutcome;
      connectorDTO = serverlessEntityHelper.getConnectorInfoDTO(s3ArtifactOutcome.getConnectorRef(), ngAccess);
      ConnectorConfigDTO connectorConfigDTO = connectorDTO.getConnectorConfig();
      AwsConnectorDTO awsConnectorDTO = (AwsConnectorDTO) connectorConfigDTO;
      AwsCredentialDTO awsCredentialDTO = awsConnectorDTO.getCredential();
      AwsCredentialSpecDTO awsCredentialSpecDTO = awsCredentialDTO.getConfig();

      serverlessPrepareRollbackEnvironmentVariablesMap.put("PLUGIN_BUCKET_NAME", s3ArtifactOutcome.getBucketName());
      serverlessPrepareRollbackEnvironmentVariablesMap.put("PLUGIN_FILE_PATH", s3ArtifactOutcome.getFilePath());
      serverlessPrepareRollbackEnvironmentVariablesMap.put(
          "PLUGIN_ARTIFACT_IDENTIFIER", s3ArtifactOutcome.getIdentifier());

      if (awsCredentialSpecDTO instanceof AwsManualConfigSpecDTO) {
        AwsManualConfigSpecDTO awsManualConfigSpecDTO = (AwsManualConfigSpecDTO) awsCredentialSpecDTO;

        if (!StringUtils.isEmpty(awsManualConfigSpecDTO.getAccessKey())) {
          s3AwsAccessKey = awsManualConfigSpecDTO.getAccessKey();
        } else {
          s3AwsAccessKey = getKey(ambiance, awsManualConfigSpecDTO.getAccessKeyRef());
        }

        s3AwsSecretKey = getKey(ambiance, awsManualConfigSpecDTO.getSecretKeyRef());
      }

      serverlessPrepareRollbackEnvironmentVariablesMap.put("PLUGIN_S3_AWS_ACCESS_KEY", s3AwsAccessKey);
      serverlessPrepareRollbackEnvironmentVariablesMap.put("PLUGIN_S3_AWS_SECRET_KEY", s3AwsSecretKey);
      serverlessPrepareRollbackEnvironmentVariablesMap.put(
          "PLUGIN_PRIMARY_ARTIFACT_TYPE", ArtifactType.S3.getArtifactType());

    } else {
      throw new UnsupportedOperationException(
          format("Unsupported Artifact type: [%s]", artifactOutcome.getArtifactType()));
    }
  }

  // todo: moving this function to a common class with Serverless 1.0
  public List<String> getFolderPathsForManifest(GitStoreConfig gitStoreConfig) {
    List<String> folderPaths = new ArrayList<>();

    List<String> paths = getParameterFieldValue(gitStoreConfig.getPaths());
    if ((paths != null) && (!paths.isEmpty())) {
      folderPaths.add(normalizeFolderPath(paths.get(0)));
    } else {
      folderPaths.add(normalizeFolderPath(getParameterFieldValue(gitStoreConfig.getFolderPath())));
    }
    return folderPaths;
  }

  // todo: moving this function to a common class with Serverless 1.0
  public String getConfigOverridePath(ManifestOutcome manifestOutcome) {
    if (manifestOutcome instanceof ServerlessAwsLambdaManifestOutcome) {
      ServerlessAwsLambdaManifestOutcome serverlessAwsLambdaManifestOutcome =
          (ServerlessAwsLambdaManifestOutcome) manifestOutcome;
      return getParameterFieldValue(serverlessAwsLambdaManifestOutcome.getConfigOverridePath());
    }
    throw new UnsupportedOperationException(
        format("Unsupported serverless manifest type: [%s]", manifestOutcome.getType()));
  }

  public String getKey(Ambiance ambiance, SecretRefData secretRefData) {
    return NGVariablesUtils.fetchSecretExpressionWithExpressionToken(
        secretRefData.toSecretRefStringValue(), ambiance.getExpressionFunctorToken());
  }

  public IdentifierRef getIdentifierRef(String awsConnectorRef, NGAccess ngAccess) {
    return IdentifierRefHelper.getIdentifierRef(
        awsConnectorRef, ngAccess.getAccountIdentifier(), ngAccess.getOrgIdentifier(), ngAccess.getProjectIdentifier());
  }

  public NGAccess getNgAccess(Ambiance ambiance) {
    return AmbianceUtils.getNgAccess(ambiance);
  }
}
