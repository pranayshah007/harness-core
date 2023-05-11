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
import io.harness.cdng.aws.sam.AwsSamDeployStepInfo;
import io.harness.cdng.expressions.CDExpressionResolver;
import io.harness.cdng.infra.beans.AwsSamInfrastructureOutcome;
import io.harness.cdng.infra.beans.InfrastructureOutcome;
import io.harness.cdng.pipeline.executions.CDPluginInfoProvider;
import io.harness.cdng.pipeline.steps.CdAbstractStepNode;
import io.harness.cdng.stepsdependency.constants.OutcomeExpressionConstants;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.connector.services.ConnectorService;
import io.harness.delegate.beans.connector.ConnectorConfigDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsConnectorDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsCredentialDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsCredentialSpecDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsManualConfigSpecDTO;
import io.harness.executions.steps.StepSpecTypeConstants;
import io.harness.ng.core.NGAccess;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.plan.ImageDetails;
import io.harness.pms.contracts.plan.PluginCreationRequest;
import io.harness.pms.contracts.plan.PluginCreationResponse;
import io.harness.pms.contracts.plan.PluginDetails;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.plugin.ContainerPluginParseException;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outcome.OutcomeService;
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
public class AwsSamDeployPluginInfoProvider implements CDPluginInfoProvider {
  @Inject private CDExpressionResolver cdExpressionResolver;
  @Inject private OutcomeService outcomeService;

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

    AwsSamDeployStepInfo awsSamDeployStepInfo = (AwsSamDeployStepInfo) cdAbstractStepNode.getStepSpecType();

    PluginDetails.Builder pluginDetailsBuilder = PluginInfoProviderHelper.buildPluginDetails(
        request, awsSamDeployStepInfo.getResources(), awsSamDeployStepInfo.getRunAsUser());

    ImageDetails imageDetails = null;

    if (ParameterField.isNotNull(awsSamDeployStepInfo.getConnectorRef())
        || isNotEmpty(awsSamDeployStepInfo.getConnectorRef().getValue())) {
      imageDetails = PluginInfoProviderHelper.getImageDetails(awsSamDeployStepInfo.getConnectorRef(),
          awsSamDeployStepInfo.getImage(), awsSamDeployStepInfo.getImagePullPolicy());

    } else {
      // todo: If image is not provided by user, default to an harness provided image
      StepImageConfig stepImageConfig = pluginExecutionConfig.getSamDeployStepImageConfig();
    }

    pluginDetailsBuilder.setImageDetails(imageDetails);

    pluginDetailsBuilder.putAllEnvVariables(getEnvironmentVariables(request.getAmbiance(), awsSamDeployStepInfo));

    return PluginCreationResponse.newBuilder().setPluginDetails(pluginDetailsBuilder.build()).build();
  }

  @Override
  public boolean isSupported(String stepType) {
    if (stepType.equals(StepSpecTypeConstants.AWS_SAM_DEPLOY)) {
      return true;
    }
    return false;
  }

  private Map<String, String> getEnvironmentVariables(Ambiance ambiance, AwsSamDeployStepInfo awsSamDeployStepInfo) {
    ParameterField<Map<String, String>> envVariables = awsSamDeployStepInfo.getEnvVariables();
    ParameterField<List<String>> deployCommandOptions = awsSamDeployStepInfo.getDeployCommandOptions();
    ParameterField<String> stackName = awsSamDeployStepInfo.getStackName();

    // Resolve Expressions
    cdExpressionResolver.updateExpressions(ambiance, deployCommandOptions);

    // todo: Fetch from infrastructure outcome
    InfrastructureOutcome infrastructureOutcome = (InfrastructureOutcome) outcomeService.resolve(
        ambiance, RefObjectUtils.getOutcomeRefObject(OutcomeExpressionConstants.INFRASTRUCTURE_OUTCOME));

    AwsSamInfrastructureOutcome awsSamInfrastructureOutcome = (AwsSamInfrastructureOutcome) infrastructureOutcome;

    String awsConnectorRef = awsSamInfrastructureOutcome.getConnectorRef();

    String awsAccessKey = null;
    String awsSecretKey = null;
    String region = ((AwsSamInfrastructureOutcome) infrastructureOutcome).getRegion();

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

    HashMap<String, String> samDeployEnvironmentVariablesMap = new HashMap<>();
    samDeployEnvironmentVariablesMap.put("PLUGIN_SAM_DIR", "");
    samDeployEnvironmentVariablesMap.put(
        "PLUGIN_DEPLOY_COMMAND_OPTIONS", String.join(" ", deployCommandOptions.getValue()));
    samDeployEnvironmentVariablesMap.put("PLUGIN_STACK_NAME", stackName.getValue());

    if (awsAccessKey != null) {
      samDeployEnvironmentVariablesMap.put("PLUGIN_AWS_ACCESS_KEY", awsAccessKey);
    }

    if (awsSecretKey != null) {
      samDeployEnvironmentVariablesMap.put("PLUGIN_AWS_SECRET_KEY", awsSecretKey);
    }

    if (region != null) {
      samDeployEnvironmentVariablesMap.put("PLUGIN_REGION", region);
    }

    if (envVariables != null && envVariables.getValue() != null) {
      samDeployEnvironmentVariablesMap.putAll(envVariables.getValue());
    }

    return samDeployEnvironmentVariablesMap;
  }
}
