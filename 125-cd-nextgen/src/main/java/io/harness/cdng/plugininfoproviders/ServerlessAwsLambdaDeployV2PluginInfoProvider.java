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
import io.harness.cdng.serverless.ServerlessEntityHelper;
import io.harness.cdng.serverless.container.steps.ServerlessAwsLambdaDeployV2StepInfo;
import io.harness.cdng.serverless.container.steps.ServerlessAwsLambdaV2BaseStepInfo;
import io.harness.cdng.stepsdependency.constants.OutcomeExpressionConstants;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.connector.services.ConnectorService;
import io.harness.delegate.beans.connector.ConnectorConfigDTO;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.jooq.tools.StringUtils;

@OwnedBy(HarnessTeam.CDP)
public class ServerlessAwsLambdaDeployV2PluginInfoProvider implements CDPluginInfoProvider {
  @Inject private CDExpressionResolver cdExpressionResolver;
  @Inject private OutcomeService outcomeService;
  @Inject private ServerlessEntityHelper serverlessEntityHelper;
  @Inject private ServerlessV2PluginInfoProviderHelper serverlessV2PluginInfoProviderHelper;
  @Inject PluginExecutionConfig pluginExecutionConfig;
  @Inject PluginInfoProviderUtils pluginInfoProviderUtils;

  @Named(DEFAULT_CONNECTOR_SERVICE) @Inject private ConnectorService connectorService;

  @Override
  public PluginCreationResponseWrapper getPluginInfo(

      // todo: Merge this with SamPluginInfoProvider

      PluginCreationRequest request, Set<Integer> usedPorts, Ambiance ambiance) {
    String stepJsonNode = request.getStepJsonNode();
    CdAbstractStepNode cdAbstractStepNode;

    try {
      cdAbstractStepNode = getRead(stepJsonNode);
    } catch (IOException e) {
      throw new ContainerPluginParseException(
          String.format("Error in parsing CD step for step type [%s]", request.getType()), e);
    }

    ServerlessAwsLambdaDeployV2StepInfo serverlessAwsLambdaDeployV2StepInfo =
        (ServerlessAwsLambdaDeployV2StepInfo) cdAbstractStepNode.getStepSpecType();

    Builder pluginDetailsBuilder = getPluginDetailsBuilder(serverlessAwsLambdaDeployV2StepInfo.getResources(),
        serverlessAwsLambdaDeployV2StepInfo.getRunAsUser(), usedPorts);

    ImageDetails imageDetails = null;

    if (ParameterField.isNotNull(serverlessAwsLambdaDeployV2StepInfo.getConnectorRef())
        || isNotEmpty(serverlessAwsLambdaDeployV2StepInfo.getConnectorRef().getValue())) {
      imageDetails = getImageDetails(serverlessAwsLambdaDeployV2StepInfo);

    } else {
      // todo: If image is not provided by user, default to an harness provided image
      StepImageConfig stepImageConfig = pluginExecutionConfig.getServerlessAwsLambdaDeployV2StepImageConfig();
    }

    pluginDetailsBuilder.setImageDetails(imageDetails);

    pluginDetailsBuilder.putAllEnvVariables(getEnvironmentVariables(ambiance, serverlessAwsLambdaDeployV2StepInfo));
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
    PluginDetails.Builder pluginDetailsBuilder = PluginDetails.newBuilder();

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
    if (stepType.equals(StepSpecTypeConstants.SERVERLESS_AWS_LAMBDA_DEPLOY_V2)) {
      return true;
    }
    return false;
  }

  public Map<String, String> getEnvironmentVariables(
      Ambiance ambiance, ServerlessAwsLambdaDeployV2StepInfo serverlessAwsLambdaDeployV2StepInfo) {
    ParameterField<Map<String, String>> envVariables = serverlessAwsLambdaDeployV2StepInfo.getEnvVariables();

    ManifestsOutcome manifestsOutcome = resolveServerlessManifestsOutcome(ambiance);
    ManifestOutcome serverlessManifestOutcome = pluginInfoProviderUtils.getServerlessManifestOutcome(
        manifestsOutcome.values(), ManifestType.ServerlessAwsLambda);
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

    ParameterField<List<String>> deployCommandOptions = serverlessAwsLambdaDeployV2StepInfo.getDeployCommandOptions();

    HashMap<String, String> serverlessPrepareRollbackEnvironmentVariablesMap = new HashMap<>();
    serverlessPrepareRollbackEnvironmentVariablesMap.put("PLUGIN_SERVERLESS_DIR", serverlessDirectory);
    serverlessPrepareRollbackEnvironmentVariablesMap.put("PLUGIN_SERVERLESS_YAML_CUSTOM_PATH", configOverridePath);
    serverlessPrepareRollbackEnvironmentVariablesMap.put("PLUGIN_SERVERLESS_STAGE", stageName);
    if (deployCommandOptions != null) {
      serverlessPrepareRollbackEnvironmentVariablesMap.put(
          "PLUGIN_DEPLOY_COMMAND_OPTIONS", String.join(" ", deployCommandOptions.getValue()));
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

  public ServerlessInfraConfig getServerlessInfraConfig(InfrastructureOutcome infrastructure, Ambiance ambiance) {
    NGAccess ngAccess = AmbianceUtils.getNgAccess(ambiance);
    return serverlessEntityHelper.getServerlessInfraConfig(infrastructure, ngAccess);
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
