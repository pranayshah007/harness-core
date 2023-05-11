/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.plugininfoproviders;

import static io.harness.connector.ConnectorModule.DEFAULT_CONNECTOR_SERVICE;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.IdentifierRef;
import io.harness.cdng.aws.sam.AwsSamBuildStepInfo;
import io.harness.cdng.expressions.CDExpressionResolver;
import io.harness.cdng.pipeline.executions.CDPluginInfoProvider;
import io.harness.cdng.pipeline.steps.CdAbstractStepNode;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.connector.services.ConnectorService;
import io.harness.delegate.beans.connector.ConnectorConfigDTO;
import io.harness.delegate.beans.connector.docker.DockerAuthCredentialsDTO;
import io.harness.delegate.beans.connector.docker.DockerConnectorDTO;
import io.harness.delegate.beans.connector.docker.DockerUserNamePasswordDTO;
import io.harness.executions.steps.StepSpecTypeConstants;
import io.harness.ng.core.NGAccess;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.plan.ImageDetails;
import io.harness.pms.contracts.plan.PluginCreationRequest;
import io.harness.pms.contracts.plan.PluginCreationResponse;
import io.harness.pms.contracts.plan.PluginDetails;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.plugin.ContainerPluginParseException;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YamlUtils;
import io.harness.steps.container.execution.plugin.StepImageConfig;
import io.harness.utils.IdentifierRefHelper;
import io.harness.yaml.utils.NGVariablesUtils;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jooq.tools.StringUtils;

@OwnedBy(HarnessTeam.CDP)
public class AwsSamBuildPluginInfoProvider implements CDPluginInfoProvider {
  @Inject private CDExpressionResolver cdExpressionResolver;
  @Inject PluginExecutionConfig pluginExecutionConfig;
  @Named(DEFAULT_CONNECTOR_SERVICE) @Inject private ConnectorService connectorService;

  @Override
  public PluginCreationResponse getPluginInfo(PluginCreationRequest request) {
    String stepJsonNode = request.getStepJsonNode();
    CdAbstractStepNode cdAbstractStepNode;

    try {
      cdAbstractStepNode = YamlUtils.read(stepJsonNode, CdAbstractStepNode.class);
    } catch (IOException e) {
      throw new ContainerPluginParseException(
          String.format("Error in parsing CD step for step type [%s]", request.getType()), e);
    }

    AwsSamBuildStepInfo awsSamBuildStepInfo = (AwsSamBuildStepInfo) cdAbstractStepNode.getStepSpecType();

    PluginDetails.Builder pluginDetailsBuilder = PluginInfoProviderHelper.buildPluginDetails(
        request, awsSamBuildStepInfo.getResources(), awsSamBuildStepInfo.getRunAsUser());

    ImageDetails imageDetails = null;

    if (ParameterField.isNotNull(awsSamBuildStepInfo.getConnectorRef())
        || isNotEmpty(awsSamBuildStepInfo.getConnectorRef().getValue())) {
      imageDetails = PluginInfoProviderHelper.getImageDetails(awsSamBuildStepInfo.getConnectorRef(),
          awsSamBuildStepInfo.getImage(), awsSamBuildStepInfo.getImagePullPolicy());

    } else {
      // todo: If image is not provided by user, default to an harness provided image
      StepImageConfig stepImageConfig = pluginExecutionConfig.getSamBuildStepImageConfig();
    }

    pluginDetailsBuilder.setImageDetails(imageDetails);

    pluginDetailsBuilder.putAllEnvVariables(getEnvironmentVariables(request.getAmbiance(), awsSamBuildStepInfo));

    return PluginCreationResponse.newBuilder().setPluginDetails(pluginDetailsBuilder.build()).build();
  }

  @Override
  public boolean isSupported(String stepType) {
    if (stepType.equals(StepSpecTypeConstants.AWS_SAM_BUILD)) {
      return true;
    }
    return false;
  }

  private Map<String, String> getEnvironmentVariables(Ambiance ambiance, AwsSamBuildStepInfo awsSamBuildStepInfo) {
    ParameterField<List<String>> buildCommandOptions = awsSamBuildStepInfo.getBuildCommandOptions();
    ParameterField<Map<String, String>> envVariables = awsSamBuildStepInfo.getEnvVariables();
    ParameterField<String> samBuildDockerRegistryConnectorRef =
        awsSamBuildStepInfo.getSamBuildDockerRegistryConnectorRef();

    HashMap<String, String> samBuildEnvironmentVariablesMap = new HashMap<>();

    if (samBuildDockerRegistryConnectorRef != null && samBuildDockerRegistryConnectorRef.getValue() != null) {
      NGAccess ngAccess = AmbianceUtils.getNgAccess(ambiance);

      IdentifierRef identifierRef = IdentifierRefHelper.getIdentifierRef(samBuildDockerRegistryConnectorRef.getValue(),
          ngAccess.getAccountIdentifier(), ngAccess.getOrgIdentifier(), ngAccess.getProjectIdentifier());

      Optional<ConnectorResponseDTO> connectorDTO = connectorService.get(identifierRef.getAccountIdentifier(),
          identifierRef.getOrgIdentifier(), identifierRef.getProjectIdentifier(), identifierRef.getIdentifier());

      ConnectorInfoDTO connectorInfoDTO = connectorDTO.get().getConnector();
      ConnectorConfigDTO connectorConfigDTO = connectorInfoDTO.getConnectorConfig();
      String registryUrl = null;
      String userName = null;
      String password = null;

      if (connectorConfigDTO instanceof DockerConnectorDTO) {
        DockerConnectorDTO dockerConnectorDTO = (DockerConnectorDTO) connectorConfigDTO;
        registryUrl = dockerConnectorDTO.getDockerRegistryUrl();
        DockerAuthCredentialsDTO dockerAuthCredentialsDTO = dockerConnectorDTO.getAuth().getCredentials();
        if (dockerAuthCredentialsDTO instanceof DockerUserNamePasswordDTO) {
          DockerUserNamePasswordDTO dockerUserNamePasswordDTO = (DockerUserNamePasswordDTO) dockerAuthCredentialsDTO;
          if (!StringUtils.isEmpty(dockerUserNamePasswordDTO.getUsername())) {
            userName = dockerUserNamePasswordDTO.getUsername();
          } else {
            userName = NGVariablesUtils.fetchSecretExpressionWithExpressionToken(
                dockerUserNamePasswordDTO.getUsernameRef().toSecretRefStringValue(),
                ambiance.getExpressionFunctorToken());
          }
          password = NGVariablesUtils.fetchSecretExpressionWithExpressionToken(
              dockerUserNamePasswordDTO.getPasswordRef().toSecretRefStringValue(),
              ambiance.getExpressionFunctorToken());
        }
      }

      if (!StringUtils.isEmpty(registryUrl) && !StringUtils.isEmpty(userName) && !StringUtils.isEmpty(password)) {
        samBuildEnvironmentVariablesMap.put("PLUGIN_PRIVATE_REGISTRY_URL", registryUrl);
        samBuildEnvironmentVariablesMap.put("PLUGIN_PRIVATE_REGISTRY_USERNAME", userName);
        samBuildEnvironmentVariablesMap.put("PLUGIN_PRIVATE_REGISTRY_PASSWORD", password);
      }
    }

    // Resolve Expressions
    // cdExpressionResolver.updateExpressions(ambiance, buildCommandOptions);

    samBuildEnvironmentVariablesMap.put("PLUGIN_SAM_DIR", "");
    samBuildEnvironmentVariablesMap.put(
        "PLUGIN_BUILD_COMMAND_OPTIONS", String.join(" ", buildCommandOptions.getValue()));

    if (envVariables != null && envVariables.getValue() != null) {
      samBuildEnvironmentVariablesMap.putAll(envVariables.getValue());
    }

    return samBuildEnvironmentVariablesMap;
  }
}
