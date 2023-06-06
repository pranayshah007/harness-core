/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.plugininfoproviders;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.IdentifierRef;
import io.harness.cdng.expressions.CDExpressionResolver;
import io.harness.cdng.infra.beans.InfrastructureOutcome;
import io.harness.cdng.infra.beans.ServerlessAwsLambdaInfrastructureOutcome;
import io.harness.cdng.manifest.ManifestStoreType;
import io.harness.cdng.manifest.steps.outcome.ManifestsOutcome;
import io.harness.cdng.manifest.yaml.GitStoreConfig;
import io.harness.cdng.manifest.yaml.ManifestOutcome;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfig;
import io.harness.cdng.pipeline.executions.CDPluginInfoProvider;
import io.harness.cdng.pipeline.steps.CdAbstractStepNode;
import io.harness.cdng.plugininfoproviders.PluginExecutionConfig;
import io.harness.cdng.plugininfoproviders.PluginInfoProviderHelper;
import io.harness.cdng.serverless.ServerlessAwsLambdaStepHelper;
import io.harness.cdng.serverless.ServerlessStepCommonHelper;
import io.harness.cdng.serverless.container.steps.ServerlessAwsLambdaPrepareRollbackContainerStepInfo;
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
import io.harness.exception.InvalidRequestException;
import io.harness.executions.steps.StepSpecTypeConstants;
import io.harness.ng.core.NGAccess;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.plan.ImageDetails;
import io.harness.pms.contracts.plan.PluginCreationRequest;
import io.harness.pms.contracts.plan.PluginCreationResponse;
import io.harness.pms.contracts.plan.PluginCreationResponseWrapper;
import io.harness.pms.contracts.plan.PluginDetails;
import io.harness.pms.contracts.plan.StepInfoProto;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.plugin.ContainerPluginParseException;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outcome.OutcomeService;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YamlUtils;
import io.harness.steps.container.execution.plugin.StepImageConfig;
import io.harness.utils.IdentifierRefHelper;
import io.harness.yaml.utils.NGVariablesUtils;
import org.jooq.tools.StringUtils;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static io.harness.connector.ConnectorModule.DEFAULT_CONNECTOR_SERVICE;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.exception.WingsException.USER;

@OwnedBy(HarnessTeam.CDP)
public class ServelessPrepareRollbackPluginInfoProvider implements CDPluginInfoProvider {
  @Inject private CDExpressionResolver cdExpressionResolver;
  @Inject private ServerlessStepCommonHelper serverlessStepCommonHelper;
  @Inject private OutcomeService outcomeService;
  @Inject private ServerlessAwsLambdaStepHelper serverlessAwsLambdaStepHelper;

  @Inject
  PluginExecutionConfig pluginExecutionConfig;

  @Named(DEFAULT_CONNECTOR_SERVICE) @Inject private ConnectorService connectorService;

  @Override
  public PluginCreationResponseWrapper getPluginInfo(PluginCreationRequest request, Set<Integer> usedPorts) {
    String stepJsonNode = request.getStepJsonNode();
    CdAbstractStepNode cdAbstractStepNode;

    try {
      cdAbstractStepNode = YamlUtils.read(stepJsonNode, CdAbstractStepNode.class);
    } catch (IOException e) {
      throw new ContainerPluginParseException(
          String.format("Error in parsing CD step for step type [%s]", request.getType()), e);
    }

    ServerlessAwsLambdaPrepareRollbackContainerStepInfo serverlessAwsLambdaPrepareRollbackContainerStepInfo = (ServerlessAwsLambdaPrepareRollbackContainerStepInfo) cdAbstractStepNode.getStepSpecType();

    PluginDetails.Builder pluginDetailsBuilder = PluginInfoProviderHelper.buildPluginDetails(
        request, serverlessAwsLambdaPrepareRollbackContainerStepInfo.getResources(), serverlessAwsLambdaPrepareRollbackContainerStepInfo.getRunAsUser());

    ImageDetails imageDetails = null;

    if (ParameterField.isNotNull(serverlessAwsLambdaPrepareRollbackContainerStepInfo.getConnectorRef())
        || isNotEmpty(serverlessAwsLambdaPrepareRollbackContainerStepInfo.getConnectorRef().getValue())) {
      imageDetails = PluginInfoProviderHelper.getImageDetails(serverlessAwsLambdaPrepareRollbackContainerStepInfo.getConnectorRef(),
              serverlessAwsLambdaPrepareRollbackContainerStepInfo.getImage(), serverlessAwsLambdaPrepareRollbackContainerStepInfo.getImagePullPolicy());

    } else {
      // todo: If image is not provided by user, default to an harness provided image
      StepImageConfig stepImageConfig = pluginExecutionConfig.getServerlessPrepareRollbackContainerStepImageConfig();
    }

    pluginDetailsBuilder.setImageDetails(imageDetails);

    pluginDetailsBuilder.putAllEnvVariables(getEnvironmentVariables(request.getAmbiance(), serverlessAwsLambdaPrepareRollbackContainerStepInfo));
    PluginCreationResponse response =
        PluginCreationResponse.newBuilder().setPluginDetails(pluginDetailsBuilder.build()).build();
    StepInfoProto stepInfoProto = StepInfoProto.newBuilder()
                                      .setIdentifier(cdAbstractStepNode.getIdentifier())
                                      .setName(cdAbstractStepNode.getName())
                                      .setUuid(cdAbstractStepNode.getUuid())
                                      .build();
    return PluginCreationResponseWrapper.newBuilder().setResponse(response).setStepInfo(stepInfoProto).build();
  }

  @Override
  public boolean isSupported(String stepType) {
    if (stepType.equals(StepSpecTypeConstants.SERVERLESS_PREPARE_ROLLBACK)) {
      return true;
    }
    return false;
  }

  private Map<String, String> getEnvironmentVariables(Ambiance ambiance, ServerlessAwsLambdaPrepareRollbackContainerStepInfo serverlessAwsLambdaPrepareRollbackContainerStepInfo) {
    ParameterField<Map<String, String>> envVariables = serverlessAwsLambdaPrepareRollbackContainerStepInfo.getEnvVariables();
    ParameterField<List<String>> deployCommandOptions = serverlessAwsLambdaPrepareRollbackContainerStepInfo.getDeployCommandOptions();

    // Resolve Expressions
    cdExpressionResolver.updateExpressions(ambiance, deployCommandOptions);

    ManifestsOutcome manifestsOutcome = serverlessStepCommonHelper.resolveServerlessManifestsOutcome(ambiance);
    ManifestOutcome serverlessManifestOutcome =
            serverlessStepCommonHelper.getServerlessManifestOutcome(manifestsOutcome.values(), serverlessAwsLambdaStepHelper);
    StoreConfig storeConfig = serverlessManifestOutcome.getStore();
    if (!ManifestStoreType.isInGitSubset(storeConfig.getKind())) {
      throw new InvalidRequestException("Invalid kind of storeConfig for Serverless step", USER);
    }
    String configOverridePath = serverlessAwsLambdaStepHelper.getConfigOverridePath(serverlessManifestOutcome);
    GitStoreConfig gitStoreConfig = (GitStoreConfig) storeConfig;
    List<String> gitPaths = serverlessStepCommonHelper.getFolderPathsForManifest(gitStoreConfig);

    InfrastructureOutcome infrastructureOutcome = (InfrastructureOutcome) outcomeService.resolve(
        ambiance, RefObjectUtils.getOutcomeRefObject(OutcomeExpressionConstants.INFRASTRUCTURE_OUTCOME));
    ServerlessAwsLambdaInfraConfig serverlessAwsLambdaInfraConfig = (ServerlessAwsLambdaInfraConfig) serverlessStepCommonHelper.getServerlessInfraConfig(infrastructureOutcome, ambiance);
    String stageName = serverlessAwsLambdaInfraConfig.getStage();
    String region = serverlessAwsLambdaInfraConfig.getRegion();

    ServerlessAwsLambdaInfrastructureOutcome serverlessAwsLambdaInfrastructureOutcome = (ServerlessAwsLambdaInfrastructureOutcome) infrastructureOutcome;

    String awsConnectorRef = serverlessAwsLambdaInfrastructureOutcome.getConnectorRef();

    String awsAccessKey = null;
    String awsSecretKey = null;

    if (awsConnectorRef != null) {
      NGAccess ngAccess = AmbianceUtils.getNgAccess(ambiance);

      IdentifierRef identifierRef = IdentifierRefHelper.getIdentifierRef(awsConnectorRef,
          ngAccess.getAccountIdentifier(), ngAccess.getOrgIdentifier(), ngAccess.getProjectIdentifier());

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
          awsAccessKey = NGVariablesUtils.fetchSecretExpressionWithExpressionToken(
              awsManualConfigSpecDTO.getAccessKeyRef().toSecretRefStringValue(), ambiance.getExpressionFunctorToken());
        }

        awsSecretKey = NGVariablesUtils.fetchSecretExpressionWithExpressionToken(
            awsManualConfigSpecDTO.getSecretKeyRef().toSecretRefStringValue(), ambiance.getExpressionFunctorToken());
      }
    }

    HashMap<String, String> serverlessPrepareRollbackEnvironmentVariablesMap = new HashMap<>();
    serverlessPrepareRollbackEnvironmentVariablesMap.put("PLUGIN_SERVERLESS_DIR", gitPaths.get(0));
    serverlessPrepareRollbackEnvironmentVariablesMap.put(
        "PLUGIN_SERVERLESS_YAML_CUSTOM_PATH", configOverridePath);
    serverlessPrepareRollbackEnvironmentVariablesMap.put("PLUGIN_SERVERLESS_STAGE", stageName);

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
}
