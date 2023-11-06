/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.aws.sam;

import static io.harness.connector.ConnectorModule.DEFAULT_CONNECTOR_SERVICE;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.IdentifierRef;
import io.harness.callback.DelegateCallbackToken;
import io.harness.cdng.expressions.CDExpressionResolver;
import io.harness.cdng.infra.beans.AwsSamInfrastructureOutcome;
import io.harness.cdng.infra.beans.InfrastructureOutcome;
import io.harness.cdng.instance.info.InstanceInfoService;
import io.harness.cdng.manifest.steps.outcome.ManifestsOutcome;
import io.harness.cdng.manifest.yaml.AwsSamDirectoryManifestOutcome;
import io.harness.cdng.stepsdependency.constants.OutcomeExpressionConstants;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.connector.services.ConnectorService;
import io.harness.delegate.beans.connector.ConnectorConfigDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsConnectorDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsCredentialDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsCredentialSpecDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsManualConfigSpecDTO;
import io.harness.delegate.beans.instancesync.ServerInstanceInfo;
import io.harness.executions.steps.ExecutionNodeType;
import io.harness.helper.SerializedResponseDataHelper;
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
import io.harness.serializer.KryoSerializer;
import io.harness.tasks.BinaryResponseData;
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
import lombok.extern.slf4j.Slf4j;
import org.jooq.tools.StringUtils;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_ECS})
@OwnedBy(HarnessTeam.CDP)
@Slf4j
public class AwsSamDeployStep extends AbstractContainerStepV2<StepElementParameters> {
  @Inject Supplier<DelegateCallbackToken> delegateCallbackTokenSupplier;

  @Inject CDExpressionResolver cdExpressionResolver;

  @Inject AwsSamStepHelper awsSamStepHelper;

  @Inject private InstanceInfoService instanceInfoService;

  @Inject private SerializedResponseDataHelper serializedResponseDataHelper;

  @Inject private KryoSerializer referenceFalseKryoSerializer;
  @Inject private OutcomeService outcomeService;

  @Named(DEFAULT_CONNECTOR_SERVICE) @Inject private ConnectorService connectorService;

  public static final StepType STEP_TYPE = StepType.newBuilder()
                                               .setType(ExecutionNodeType.AWS_SAM_DEPLOY.getYamlType())
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
    AwsSamDeployStepParameters awsSamDeployStepParameters =
        (AwsSamDeployStepParameters) stepElementParameters.getSpec();

    // Check if image exists
    awsSamStepHelper.verifyPluginImageIsProvider(awsSamStepHelper.getImage(awsSamDeployStepParameters));

    Map<String, String> samDeployEnvironmentVariablesMap = new HashMap<>();

    awsSamStepHelper.putK8sServiceAccountEnvVars(ambiance, samDeployEnvironmentVariablesMap);

    samDeployEnvironmentVariablesMap.putAll(getEnvironmentVariables(ambiance, awsSamDeployStepParameters));

    return ContainerUnitStepUtils.serializeStepWithStepParameters(
        getPort(ambiance, stepElementParameters.getIdentifier()), parkedTaskId, logKey,
        stepElementParameters.getIdentifier(), getTimeout(ambiance, stepElementParameters), accountId,
        stepElementParameters.getName(), delegateCallbackTokenSupplier, ambiance, samDeployEnvironmentVariablesMap,
        awsSamStepHelper.getImage(awsSamDeployStepParameters).getValue(), Collections.EMPTY_LIST);
  }

  @Override
  public StepResponse.StepOutcome getAnyOutComeForStep(
      Ambiance ambiance, StepElementParameters stepParameters, Map<String, ResponseData> responseDataMap) {
    // If any of the responses are in serialized format, deserialize them
    for (Map.Entry<String, ResponseData> entry : responseDataMap.entrySet()) {
      log.info(String.format("AWS SAM Deploy: ResponseData with class %s received", entry.getValue().getClass()));
      entry.setValue(serializedResponseDataHelper.deserialize(entry.getValue()));
      if (entry.getValue() instanceof BinaryResponseData) {
        entry.setValue((ResponseData) referenceFalseKryoSerializer.asInflatedObject(
            ((BinaryResponseData) entry.getValue()).getData()));
      }
    }

    StepResponse.StepOutcome stepOutcome = null;

    List<ServerInstanceInfo> serverInstanceInfoList =
        awsSamStepHelper.fetchServerInstanceInfoFromDelegateResponse(responseDataMap);

    if (serverInstanceInfoList != null) {
      InfrastructureOutcome infrastructureOutcome = awsSamStepHelper.getInfrastructureOutcome(ambiance);

      awsSamStepHelper.updateServerInstanceInfoList(serverInstanceInfoList, infrastructureOutcome);
      log.info("Saving AWS SAM server instances into sweeping output");
      stepOutcome = instanceInfoService.saveServerInstancesIntoSweepingOutput(ambiance, serverInstanceInfoList);
    } else {
      log.info("No instances were received in SAM Deploy Response");
    }

    return stepOutcome;
  }

  private Map<String, String> getEnvironmentVariables(
      Ambiance ambiance, AwsSamDeployStepParameters awsSamDeployStepParameters) {
    ParameterField<Map<String, String>> envVariables = awsSamDeployStepParameters.getEnvVariables();
    ParameterField<List<String>> deployCommandOptions = awsSamDeployStepParameters.getDeployCommandOptions();
    ParameterField<String> stackName = awsSamDeployStepParameters.getStackName();

    // Resolve Expressions
    cdExpressionResolver.updateExpressions(ambiance, deployCommandOptions);

    // todo: Fetch from infrastructure outcome
    InfrastructureOutcome infrastructureOutcome = (InfrastructureOutcome) outcomeService.resolve(
        ambiance, RefObjectUtils.getOutcomeRefObject(OutcomeExpressionConstants.INFRASTRUCTURE_OUTCOME));

    AwsSamInfrastructureOutcome awsSamInfrastructureOutcome = (AwsSamInfrastructureOutcome) infrastructureOutcome;

    String awsConnectorRef = awsSamInfrastructureOutcome.getConnectorRef();
    String crossAccountRoleArn = null;
    String externalId = null;

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

      if (awsCredentialDTO.getCrossAccountAccess() != null) {
        crossAccountRoleArn = awsCredentialDTO.getCrossAccountAccess().getCrossAccountRoleArn();
        externalId = awsCredentialDTO.getCrossAccountAccess().getExternalId();
      }
    }

    HashMap<String, String> samDeployEnvironmentVariablesMap = new HashMap<>();

    ManifestsOutcome manifestsOutcome =
        (ManifestsOutcome) outcomeService
            .resolveOptional(ambiance, RefObjectUtils.getOutcomeRefObject(OutcomeExpressionConstants.MANIFESTS))
            .getOutcome();
    // @todo(hinger) render manifests here

    AwsSamDirectoryManifestOutcome awsSamDirectoryManifestOutcome =
        (AwsSamDirectoryManifestOutcome) awsSamStepHelper.getAwsSamDirectoryManifestOutcome(manifestsOutcome.values());

    String samDir =
        awsSamStepHelper.getSamDirectoryPathFromAwsSamDirectoryManifestOutcome(awsSamDirectoryManifestOutcome);

    samDeployEnvironmentVariablesMap.put("PLUGIN_SAM_DIR", samDir);

    if (ParameterField.isNotNull(deployCommandOptions)) {
      samDeployEnvironmentVariablesMap.put(
          "PLUGIN_DEPLOY_COMMAND_OPTIONS", String.join(" ", deployCommandOptions.getValue()));
    }

    samDeployEnvironmentVariablesMap.put("PLUGIN_STACK_NAME", stackName.getValue());

    if (awsAccessKey != null) {
      samDeployEnvironmentVariablesMap.put("PLUGIN_AWS_ACCESS_KEY", awsAccessKey);
    }

    if (awsSecretKey != null) {
      samDeployEnvironmentVariablesMap.put("PLUGIN_AWS_SECRET_KEY", awsSecretKey);
    }

    if (crossAccountRoleArn != null) {
      samDeployEnvironmentVariablesMap.put("PLUGIN_AWS_ROLE_ARN", crossAccountRoleArn);
    }

    if (externalId != null) {
      samDeployEnvironmentVariablesMap.put("PLUGIN_AWS_STS_EXTERNAL_ID", externalId);
    }

    if (region != null) {
      samDeployEnvironmentVariablesMap.put("PLUGIN_REGION", region);
    }

    if (envVariables != null && envVariables.getValue() != null) {
      samDeployEnvironmentVariablesMap.putAll(envVariables.getValue());
    }

    return samDeployEnvironmentVariablesMap;
  }

  @Override
  public void validateResources(Ambiance ambiance, StepElementParameters stepParameters) {
    // we need to check if rbac check is req or not.
  }
}
