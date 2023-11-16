/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.aws.sam;

import static io.harness.connector.ConnectorModule.DEFAULT_CONNECTOR_SERVICE;
import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.IdentifierRef;
import io.harness.callback.DelegateCallbackToken;
import io.harness.cdng.expressions.CDExpressionResolver;
import io.harness.cdng.manifest.steps.outcome.ManifestsOutcome;
import io.harness.cdng.manifest.yaml.AwsSamDirectoryManifestOutcome;
import io.harness.cdng.stepsdependency.constants.OutcomeExpressionConstants;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.connector.services.ConnectorService;
import io.harness.delegate.beans.connector.ConnectorConfigDTO;
import io.harness.delegate.beans.connector.docker.DockerAuthCredentialsDTO;
import io.harness.delegate.beans.connector.docker.DockerConnectorDTO;
import io.harness.delegate.beans.connector.docker.DockerUserNamePasswordDTO;
import io.harness.exception.InvalidRequestException;
import io.harness.executions.steps.ExecutionNodeType;
import io.harness.ng.core.NGAccess;
import io.harness.plancreator.steps.common.StepElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.plugin.AbstractContainerStepV2;
import io.harness.pms.sdk.core.plugin.ContainerUnitStepUtils;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outcome.OutcomeService;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.yaml.ParameterField;
import io.harness.product.ci.engine.proto.UnitStep;
import io.harness.tasks.ResponseData;
import io.harness.utils.IdentifierRefHelper;
import io.harness.yaml.core.timeout.Timeout;
import io.harness.yaml.utils.NGVariablesUtils;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import org.apache.commons.lang3.StringUtils;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true,
    components = {HarnessModuleComponent.CDS_ECS, HarnessModuleComponent.CDS_GITOPS})
@OwnedBy(HarnessTeam.CDP)
public class AwsSamBuildStep extends AbstractContainerStepV2<StepElementParameters> {
  @Inject Supplier<DelegateCallbackToken> delegateCallbackTokenSupplier;
  @Inject AwsSamStepHelper awsSamStepHelper;

  @Inject CDExpressionResolver cdExpressionResolver;

  @Named(DEFAULT_CONNECTOR_SERVICE) @Inject private ConnectorService connectorService;
  @Inject private OutcomeService outcomeService;

  public static final StepType STEP_TYPE = StepType.newBuilder()
                                               .setType(ExecutionNodeType.AWS_SAM_BUILD.getYamlType())
                                               .setStepCategory(StepCategory.STEP)
                                               .build();

  @Override
  public Class<StepElementParameters> getStepParametersClass() {
    return StepElementParameters.class;
  }

  @Override
  public long getTimeout(Ambiance ambiance, StepElementParameters stepElementParameters) {
    return Timeout.fromString((String) stepElementParameters.getTimeout().fetchFinalValue()).getTimeoutInMillis();
  }

  @Override
  public UnitStep getSerialisedStep(Ambiance ambiance, StepElementParameters stepElementParameters, String accountId,
      String logKey, long timeout, String parkedTaskId) {
    AwsSamBuildStepParameters awsSamBuildStepParameters = (AwsSamBuildStepParameters) stepElementParameters.getSpec();

    // Check if image exists
    awsSamStepHelper.verifyPluginImageIsProvider(awsSamStepHelper.getImage(awsSamBuildStepParameters));

    Map<String, String> envVarMap = new HashMap<>();

    awsSamStepHelper.putValuesYamlEnvVars(ambiance, awsSamBuildStepParameters, envVarMap);

    Map<String, String> envVars = getEnvironmentVariables(ambiance, awsSamBuildStepParameters);
    awsSamStepHelper.removeAllEnvVarsWithSecretRef(envVars);
    envVarMap.putAll(awsSamStepHelper.validateEnvVariables(envVars));

    return ContainerUnitStepUtils.serializeStepWithStepParameters(
        getPort(ambiance, stepElementParameters.getIdentifier()), parkedTaskId, logKey,
        stepElementParameters.getIdentifier(), getTimeout(ambiance, stepElementParameters), accountId,
        stepElementParameters.getName(), delegateCallbackTokenSupplier, ambiance, envVarMap,
        awsSamStepHelper.getImage(awsSamBuildStepParameters).getValue(), Collections.EMPTY_LIST);
  }

  public Map<String, String> getEnvironmentVariables(
      Ambiance ambiance, AwsSamBuildStepParameters awsSamBuildStepParameters) {
    ParameterField<List<String>> buildCommandOptions = awsSamBuildStepParameters.getBuildCommandOptions();
    ParameterField<Map<String, String>> envVariables = awsSamBuildStepParameters.getEnvVariables();
    ParameterField<String> samBuildDockerRegistryConnectorRef =
        awsSamBuildStepParameters.getSamBuildDockerRegistryConnectorRef();

    cdExpressionResolver.updateExpressions(ambiance, buildCommandOptions);

    HashMap<String, String> samBuildEnvironmentVariablesMap = new HashMap<>();

    if (samBuildDockerRegistryConnectorRef != null && samBuildDockerRegistryConnectorRef.getValue() != null) {
      NGAccess ngAccess = AmbianceUtils.getNgAccess(ambiance);

      Optional<ConnectorResponseDTO> connectorDTO =
          getOptionalConnectorResponseDTO(samBuildDockerRegistryConnectorRef, ngAccess);

      if (connectorDTO.isEmpty()) {
        String message =
            "Please check the SAM Build Docker Container Registry specified in SAM Build Step configuration";
        throw new InvalidRequestException(message);
      }

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
      } else {
        String errorMessage =
            String.format("Only Docker Registry type connector is supported in SAM Build Docker Registry");
        throw new InvalidRequestException(errorMessage);
      }

      if (!StringUtils.isEmpty(registryUrl) && !StringUtils.isEmpty(userName) && !StringUtils.isEmpty(password)) {
        samBuildEnvironmentVariablesMap.put("PLUGIN_PRIVATE_REGISTRY_URL", registryUrl);
        samBuildEnvironmentVariablesMap.put("PLUGIN_PRIVATE_REGISTRY_USERNAME", userName);
        samBuildEnvironmentVariablesMap.put("PLUGIN_PRIVATE_REGISTRY_PASSWORD", password);
      }
    }

    ManifestsOutcome manifestsOutcome =
        (ManifestsOutcome) outcomeService
            .resolveOptional(ambiance, RefObjectUtils.getOutcomeRefObject(OutcomeExpressionConstants.MANIFESTS))
            .getOutcome();
    AwsSamDirectoryManifestOutcome awsSamDirectoryManifestOutcome =
        (AwsSamDirectoryManifestOutcome) awsSamStepHelper.getAwsSamDirectoryManifestOutcome(manifestsOutcome.values());

    String samDir =
        awsSamStepHelper.getSamDirectoryPathFromAwsSamDirectoryManifestOutcome(awsSamDirectoryManifestOutcome);
    String samTemplateFilePath = awsSamStepHelper.getSamTemplateFilePath(awsSamDirectoryManifestOutcome);

    samBuildEnvironmentVariablesMap.put("PLUGIN_SAM_DIR", samDir);

    if (ParameterField.isNotNull(buildCommandOptions)) {
      samBuildEnvironmentVariablesMap.put(
          "PLUGIN_BUILD_COMMAND_OPTIONS", String.join(" ", buildCommandOptions.getValue()));
    }

    if (isEmpty(samTemplateFilePath)) {
      samBuildEnvironmentVariablesMap.put("PLUGIN_SAM_TEMPLATE_FILE_PATH", "template.yaml");
    } else {
      samBuildEnvironmentVariablesMap.put("PLUGIN_SAM_TEMPLATE_FILE_PATH", samTemplateFilePath);
    }

    if (envVariables != null && envVariables.getValue() != null) {
      samBuildEnvironmentVariablesMap.putAll(envVariables.getValue());
    }

    return samBuildEnvironmentVariablesMap;
  }

  private Optional<ConnectorResponseDTO> getOptionalConnectorResponseDTO(
      ParameterField<String> samBuildDockerRegistryConnectorRef, NGAccess ngAccess) {
    IdentifierRef identifierRef = IdentifierRefHelper.getIdentifierRef(samBuildDockerRegistryConnectorRef.getValue(),
        ngAccess.getAccountIdentifier(), ngAccess.getOrgIdentifier(), ngAccess.getProjectIdentifier());

    return connectorService.get(identifierRef.getAccountIdentifier(), identifierRef.getOrgIdentifier(),
        identifierRef.getProjectIdentifier(), identifierRef.getIdentifier());
  }

  @Override
  public StepResponse.StepOutcome getAnyOutComeForStep(
      Ambiance ambiance, StepElementParameters stepParameters, Map<String, ResponseData> responseDataMap) {
    // todo: if required to consume any output do it here.
    return null;
  }

  @Override
  public void validateResources(Ambiance ambiance, StepElementParameters stepParameters) {
    // we need to check if rbac check is req or not.
  }
}
