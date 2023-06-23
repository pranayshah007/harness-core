/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.plugininfoproviders;

import static io.harness.common.ParameterFieldHelper.getParameterFieldValue;
import static io.harness.connector.ConnectorModule.DEFAULT_CONNECTOR_SERVICE;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.exception.WingsException.USER;
import static io.harness.k8s.manifest.ManifestHelper.normalizeFolderPath;

import static java.lang.String.format;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
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
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfig;
import io.harness.cdng.pipeline.executions.CDPluginInfoProvider;
import io.harness.cdng.pipeline.steps.CdAbstractStepNode;
import io.harness.cdng.serverless.ArtifactType;
import io.harness.cdng.serverless.ServerlessEntityHelper;
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
import io.harness.executions.steps.StepSpecTypeConstants;
import io.harness.ng.core.NGAccess;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.plan.ImageDetails;
import io.harness.pms.contracts.plan.PluginContainerResources;
import io.harness.pms.contracts.plan.PluginCreationRequest;
import io.harness.pms.contracts.plan.PluginCreationResponse;
import io.harness.pms.contracts.plan.PluginCreationResponseWrapper;
import io.harness.pms.contracts.plan.PluginDetails;
import io.harness.pms.contracts.plan.PluginDetails.Builder;
import io.harness.pms.contracts.plan.StepInfoProto;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.data.OptionalOutcome;
import io.harness.pms.sdk.core.plugin.ContainerPluginParseException;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outcome.OutcomeService;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YamlUtils;
import io.harness.steps.container.execution.plugin.StepImageConfig;
import io.harness.utils.IdentifierRefHelper;
import io.harness.yaml.extended.ci.container.ContainerResource;
import io.harness.yaml.utils.NGVariablesUtils;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.hibernate.validator.constraints.NotEmpty;
import org.jooq.tools.StringUtils;

@OwnedBy(HarnessTeam.CDP)
public class ServerlessAwsLambdaPackageV2PluginInfoProvider implements CDPluginInfoProvider {
  @Inject private CDExpressionResolver cdExpressionResolver;
  @Inject private OutcomeService outcomeService;
  @Inject private ServerlessEntityHelper serverlessEntityHelper;
  @Inject private ServerlessV2PluginInfoProviderHelper serverlessV2PluginInfoProviderHelper;

  @Inject PluginExecutionConfig pluginExecutionConfig;

  @Named(DEFAULT_CONNECTOR_SERVICE) @Inject private ConnectorService connectorService;

  @Override
  public PluginCreationResponseWrapper getPluginInfo(
      PluginCreationRequest request, Set<Integer> usedPorts, Ambiance ambiance) {
    String stepJsonNode = request.getStepJsonNode();
    CdAbstractStepNode cdAbstractStepNode;

    try {
      cdAbstractStepNode = getRead(stepJsonNode);
    } catch (IOException e) {
      throw new ContainerPluginParseException(
          String.format("Error in parsing CD step for step type [%s]", request.getType()), e);
    }

    ServerlessAwsLambdaPackageV2StepInfo serverlessAwsLambdaPackageV2StepInfo =
        (ServerlessAwsLambdaPackageV2StepInfo) cdAbstractStepNode.getStepSpecType();

    Builder pluginDetailsBuilder = getPluginDetailsBuilder(serverlessAwsLambdaPackageV2StepInfo.getResources(),
        serverlessAwsLambdaPackageV2StepInfo.getRunAsUser(), usedPorts);

    ImageDetails imageDetails = null;

    if (ParameterField.isNotNull(serverlessAwsLambdaPackageV2StepInfo.getConnectorRef())
        || isNotEmpty(serverlessAwsLambdaPackageV2StepInfo.getConnectorRef().getValue())) {
      imageDetails = getImageDetails(serverlessAwsLambdaPackageV2StepInfo);

    } else {
      // todo: If image is not provided by user, default to an harness provided image
      StepImageConfig stepImageConfig = pluginExecutionConfig.getServerlessAwsLambdaPackageV2StepImageConfig();
    }

    pluginDetailsBuilder.setImageDetails(imageDetails);

    pluginDetailsBuilder.putAllEnvVariables(getEnvironmentVariables(ambiance, serverlessAwsLambdaPackageV2StepInfo));
    PluginCreationResponse response = getPluginCreationResponse(pluginDetailsBuilder);
    StepInfoProto stepInfoProto = StepInfoProto.newBuilder()
                                      .setIdentifier(cdAbstractStepNode.getIdentifier())
                                      .setName(cdAbstractStepNode.getName())
                                      .setUuid(cdAbstractStepNode.getUuid())
                                      .build();
    return getPluginCreationResponseWrapper(response, stepInfoProto);
  }

  public PluginCreationResponseWrapper getPluginCreationResponseWrapper(
      PluginCreationResponse response, StepInfoProto stepInfoProto) {
    return PluginCreationResponseWrapper.newBuilder().setResponse(response).setStepInfo(stepInfoProto).build();
  }

  public PluginCreationResponse getPluginCreationResponse(Builder pluginDetailsBuilder) {
    return PluginCreationResponse.newBuilder().setPluginDetails(pluginDetailsBuilder.build()).build();
  }

  public ImageDetails getImageDetails(ServerlessAwsLambdaV2BaseStepInfo serverlessAwsLambdaV2BaseStepInfo) {
    return PluginInfoProviderHelper.getImageDetails(serverlessAwsLambdaV2BaseStepInfo.getConnectorRef(),
        serverlessAwsLambdaV2BaseStepInfo.getImage(), serverlessAwsLambdaV2BaseStepInfo.getImagePullPolicy());
  }

  public Builder getPluginDetailsBuilder(
      ContainerResource resources, ParameterField<Integer> runAsUser, Set<Integer> usedPorts) {
    Builder pluginDetailsBuilder = PluginDetails.newBuilder();

    PluginContainerResources pluginContainerResources = PluginContainerResources.newBuilder()
                                                            .setCpu(PluginInfoProviderHelper.getCPU(resources))
                                                            .setMemory(PluginInfoProviderHelper.getMemory(resources))
                                                            .build();

    pluginDetailsBuilder.setResource(pluginContainerResources);

    if (runAsUser != null && runAsUser.getValue() != null) {
      pluginDetailsBuilder.setRunAsUser(runAsUser.getValue());
    }

    // Set used port and available port information
    PluginInfoProviderHelper.setPortDetails(usedPorts, pluginDetailsBuilder);

    return pluginDetailsBuilder;
  }

  public CdAbstractStepNode getRead(String stepJsonNode) throws IOException {
    return YamlUtils.read(stepJsonNode, CdAbstractStepNode.class);
  }

  @Override
  public boolean isSupported(String stepType) {
    if (stepType.equals(StepSpecTypeConstants.SERVERLESS_AWS_LAMBDA_PACKAGE_V2)) {
      return true;
    }
    return false;
  }

  public Map<String, String> getEnvironmentVariables(
      Ambiance ambiance, ServerlessAwsLambdaPackageV2StepInfo serverlessAwsLambdaPackageV2StepInfo) {
    ParameterField<Map<String, String>> envVariables = serverlessAwsLambdaPackageV2StepInfo.getEnvVariables();

    ManifestsOutcome manifestsOutcome = resolveServerlessManifestsOutcome(ambiance);
    ServerlessAwsLambdaManifestOutcome serverlessManifestOutcome =
        (ServerlessAwsLambdaManifestOutcome) getServerlessManifestOutcome(manifestsOutcome.values());
    StoreConfig storeConfig = serverlessManifestOutcome.getStore();
    if (!ManifestStoreType.isInGitSubset(storeConfig.getKind())) {
      throw new InvalidRequestException("Invalid kind of storeConfig for Serverless step", USER);
    }
    String configOverridePath = getConfigOverridePath(serverlessManifestOutcome);
    GitStoreConfig gitStoreConfig = (GitStoreConfig) storeConfig;
    List<String> gitPaths = getFolderPathsForManifest(gitStoreConfig);

    if (isEmpty(gitPaths)) {
      throw new InvalidRequestException("Atleast one git path need to be specified", USER);
    }

    String serverlessDirectory =
        serverlessV2PluginInfoProviderHelper.getServerlessAwsLambdaDirectoryPathFromManifestOutcome(
            serverlessManifestOutcome);

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

    HashMap<String, String> serverlessPrepareRollbackEnvironmentVariablesMap = new HashMap<>();

    Optional<ArtifactsOutcome> artifactsOutcome = getArtifactsOutcome(ambiance);
    if (artifactsOutcome.isPresent()) {
      if (artifactsOutcome.get().getPrimary() != null) {
        populateArtifactEnvironmentVariables(
            artifactsOutcome.get().getPrimary(), ambiance, serverlessPrepareRollbackEnvironmentVariablesMap);
      }
    }

    ParameterField<List<String>> packageCommandOptions =
        serverlessAwsLambdaPackageV2StepInfo.getPackageCommandOptions();

    serverlessPrepareRollbackEnvironmentVariablesMap.put("PLUGIN_SERVERLESS_DIR", serverlessDirectory);
    serverlessPrepareRollbackEnvironmentVariablesMap.put("PLUGIN_SERVERLESS_YAML_CUSTOM_PATH", configOverridePath);
    serverlessPrepareRollbackEnvironmentVariablesMap.put("PLUGIN_SERVERLESS_STAGE", stageName);
    if (packageCommandOptions != null) {
      serverlessPrepareRollbackEnvironmentVariablesMap.put(
          "PLUGIN_PACKAGE_COMMAND_OPTIONS", String.join(" ", packageCommandOptions.getValue()));
    }

    if (awsAccessKey != null) {
      serverlessPrepareRollbackEnvironmentVariablesMap.put("PLUGIN_AWS_ACCESS_KEY", awsAccessKey);
    }

    if (awsSecretKey != null) {
      serverlessPrepareRollbackEnvironmentVariablesMap.put("PLUGIN_AWS_SECRET_KEY", awsSecretKey);
    }

    if (region != null) {
      serverlessPrepareRollbackEnvironmentVariablesMap.put("PLUGIN_REGION", region);
    }

    if (envVariables != null && envVariables.getValue() != null) {
      serverlessPrepareRollbackEnvironmentVariablesMap.putAll(envVariables.getValue());
    }

    return serverlessPrepareRollbackEnvironmentVariablesMap;
  }

  public String getConfigOverridePath(ManifestOutcome manifestOutcome) {
    if (manifestOutcome instanceof ServerlessAwsLambdaManifestOutcome) {
      ServerlessAwsLambdaManifestOutcome serverlessAwsLambdaManifestOutcome =
          (ServerlessAwsLambdaManifestOutcome) manifestOutcome;
      return getParameterFieldValue(serverlessAwsLambdaManifestOutcome.getConfigOverridePath());
    }
    throw new UnsupportedOperationException(
        format("Unsupported serverless manifest type: [%s]", manifestOutcome.getType()));
  }

  public ServerlessInfraConfig getServerlessInfraConfig(InfrastructureOutcome infrastructure, Ambiance ambiance) {
    NGAccess ngAccess = AmbianceUtils.getNgAccess(ambiance);
    return serverlessEntityHelper.getServerlessInfraConfig(infrastructure, ngAccess);
  }

  public List<String> getFolderPathsForManifest(GitStoreConfig gitStoreConfig) {
    List<String> folderPaths = new ArrayList<>();

    List<String> paths = getParameterFieldValue(gitStoreConfig.getPaths());
    if ((paths != null) && (!paths.isEmpty())) {
      folderPaths.add(normalizeFolderPath(paths.get(0)));
    } else {
      folderPaths.add(normalizeFolderPath(getParameterFieldValue(gitStoreConfig.getFolderPath())));
    }
    return folderPaths;
    // todo: add error handling
  }

  public ManifestsOutcome resolveServerlessManifestsOutcome(Ambiance ambiance) {
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
    return (ManifestsOutcome) manifestsOutcome.getOutcome();
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

  public String getKey(Ambiance ambiance, SecretRefData secretRefData) {
    return NGVariablesUtils.fetchSecretExpressionWithExpressionToken(
        secretRefData.toSecretRefStringValue(), ambiance.getExpressionFunctorToken());
  }

  public ManifestOutcome getServerlessManifestOutcome(@NotEmpty Collection<ManifestOutcome> manifestOutcomes) {
    List<ManifestOutcome> serverlessManifests =
        manifestOutcomes.stream()
            .filter(manifestOutcome -> ManifestType.ServerlessAwsLambda.equals(manifestOutcome.getType()))
            .collect(Collectors.toList());
    if (isEmpty(serverlessManifests)) {
      throw new InvalidRequestException("Manifests are mandatory for Serverless Aws Lambda step", USER);
    }
    if (serverlessManifests.size() > 1) {
      throw new InvalidRequestException("There can be only a single manifest for Serverless Aws Lambda step", USER);
    }
    return serverlessManifests.get(0);
  }

  public Optional<ArtifactsOutcome> getArtifactsOutcome(Ambiance ambiance) {
    OptionalOutcome artifactsOutcomeOption = outcomeService.resolveOptional(
        ambiance, RefObjectUtils.getOutcomeRefObject(OutcomeExpressionConstants.ARTIFACTS));
    if (artifactsOutcomeOption.isFound()) {
      ArtifactsOutcome artifactsOutcome = (ArtifactsOutcome) artifactsOutcomeOption.getOutcome();
      return Optional.of(artifactsOutcome);
    }
    return Optional.empty();
  }

  public IdentifierRef getIdentifierRef(String awsConnectorRef, NGAccess ngAccess) {
    return IdentifierRefHelper.getIdentifierRef(
        awsConnectorRef, ngAccess.getAccountIdentifier(), ngAccess.getOrgIdentifier(), ngAccess.getProjectIdentifier());
  }

  public NGAccess getNgAccess(Ambiance ambiance) {
    return AmbianceUtils.getNgAccess(ambiance);
  }
}
