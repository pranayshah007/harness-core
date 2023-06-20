/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.aws.sam;

import static io.harness.beans.sweepingoutputs.StageInfraDetails.STAGE_INFRA_DETAILS;
import static io.harness.connector.ConnectorModule.DEFAULT_CONNECTOR_SERVICE;

import static io.serializer.HObjectMapper.NG_DEFAULT_OBJECT_MAPPER;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.IdentifierRef;
import io.harness.beans.sweepingoutputs.K8StageInfraDetails;
import io.harness.beans.sweepingoutputs.StageInfraDetails;
import io.harness.beans.yaml.extended.infrastrucutre.Infrastructure;
import io.harness.beans.yaml.extended.infrastrucutre.K8sDirectInfraYaml;
import io.harness.cdng.aws.sam.beans.AwsSamValuesYamlDataOutcome;
import io.harness.cdng.infra.beans.AwsSamInfrastructureOutcome;
import io.harness.cdng.infra.beans.InfrastructureOutcome;
import io.harness.cdng.manifest.steps.outcome.ManifestsOutcome;
import io.harness.cdng.manifest.yaml.AwsSamDirectoryManifestOutcome;
import io.harness.cdng.plugininfoproviders.AwsSamPluginInfoProviderHelper;
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
import io.harness.delegate.beans.instancesync.info.AwsSamServerInstanceInfo;
import io.harness.delegate.task.stepstatus.StepExecutionStatus;
import io.harness.delegate.task.stepstatus.StepMapOutput;
import io.harness.delegate.task.stepstatus.StepOutput;
import io.harness.delegate.task.stepstatus.StepStatusTaskResponseData;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.ngexception.CIStageExecutionException;
import io.harness.ng.core.NGAccess;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.data.OptionalSweepingOutput;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outcome.OutcomeService;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.yaml.ParameterField;
import io.harness.tasks.ResponseData;
import io.harness.utils.IdentifierRefHelper;
import io.harness.yaml.utils.NGVariablesUtils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@Slf4j
@OwnedBy(HarnessTeam.CDP)
public class AwsSamStepHelper {
  @Inject protected OutcomeService outcomeService;
  @Inject private ExecutionSweepingOutputService executionSweepingOutputService;
  @Inject private AwsSamPluginInfoProviderHelper awsSamPluginInfoProviderHelper;

  @Named(DEFAULT_CONNECTOR_SERVICE) @Inject private ConnectorService connectorService;

  ObjectMapper objectMapper = NG_DEFAULT_OBJECT_MAPPER;

  public void verifyPluginImageIsProvider(ParameterField<String> image) {
    if (ParameterField.isNull(image) || image.getValue() == null) {
      throw new InvalidRequestException("Plugin Image must be provided");
    }
  }

  public InfrastructureOutcome getInfrastructureOutcome(Ambiance ambiance) {
    return (InfrastructureOutcome) outcomeService.resolve(
        ambiance, RefObjectUtils.getOutcomeRefObject(OutcomeExpressionConstants.INFRASTRUCTURE_OUTCOME));
  }

  public void updateServerInstanceInfoList(
      List<ServerInstanceInfo> serverInstanceInfoList, InfrastructureOutcome infrastructureOutcome) {
    if (serverInstanceInfoList != null) {
      for (ServerInstanceInfo serverInstanceInfo : serverInstanceInfoList) {
        AwsSamServerInstanceInfo awsSamServerInstanceInfo = (AwsSamServerInstanceInfo) serverInstanceInfo;
        awsSamServerInstanceInfo.setInfraStructureKey(infrastructureOutcome.getInfrastructureKey());
        AwsSamInfrastructureOutcome awsSamInfrastructureOutcome = (AwsSamInfrastructureOutcome) infrastructureOutcome;
        awsSamServerInstanceInfo.setRegion(awsSamInfrastructureOutcome.getRegion());
      }
    }
  }

  public void putValuesYamlEnvVars(
      Ambiance ambiance, AwsSamBuildStepParameters awsSamBuildStepParameters, Map<String, String> envVarMap) {
    OptionalSweepingOutput awsSamValuesYamlDataOptionalOutput = executionSweepingOutputService.resolveOptional(ambiance,
        RefObjectUtils.getSweepingOutputRefObject(awsSamBuildStepParameters.getDownloadManifestsFqn() + "."
            + OutcomeExpressionConstants.AWS_SAM_VALUES_YAML_DATA_OUTCOME));

    if (awsSamValuesYamlDataOptionalOutput.isFound()) {
      AwsSamValuesYamlDataOutcome awsSamValuesYamlDataOutcome =
          (AwsSamValuesYamlDataOutcome) awsSamValuesYamlDataOptionalOutput.getOutput();

      String valuesYamlContent = awsSamValuesYamlDataOutcome.getValuesYamlContent();
      String valuesYamlPath = awsSamValuesYamlDataOutcome.getValuesYamlPath();

      if (StringUtils.isNotBlank(valuesYamlContent) && StringUtils.isNotBlank(valuesYamlPath)) {
        envVarMap.put("PLUGIN_VALUES_YAML_CONTENT", valuesYamlContent);
        envVarMap.put("PLUGIN_VALUES_YAML_FILE_PATH", valuesYamlPath);
      }
    }

    ManifestsOutcome manifestsOutcome =
        (ManifestsOutcome) outcomeService
            .resolveOptional(ambiance, RefObjectUtils.getOutcomeRefObject(OutcomeExpressionConstants.MANIFESTS))
            .getOutcome();

    AwsSamDirectoryManifestOutcome awsSamDirectoryManifestOutcome =
        (AwsSamDirectoryManifestOutcome) awsSamPluginInfoProviderHelper.getAwsSamDirectoryManifestOutcome(
            manifestsOutcome.values());

    String samDir = awsSamPluginInfoProviderHelper.getSamDirectoryPathFromAwsSamDirectoryManifestOutcome(
        awsSamDirectoryManifestOutcome);

    envVarMap.put("PLUGIN_SAM_DIR", samDir);
  }

  public List<ServerInstanceInfo> fetchServerInstanceInfoFromDelegateResponse(
      Map<String, ResponseData> responseDataMap) {
    String instances = null;

    StepStatusTaskResponseData stepStatusTaskResponseData = null;

    for (Map.Entry<String, ResponseData> entry : responseDataMap.entrySet()) {
      ResponseData responseData = entry.getValue();
      if (responseData instanceof StepStatusTaskResponseData) {
        stepStatusTaskResponseData = (StepStatusTaskResponseData) responseData;
      }
    }

    List<ServerInstanceInfo> serverInstanceInfoList = null;

    if (stepStatusTaskResponseData != null
        && stepStatusTaskResponseData.getStepStatus().getStepExecutionStatus() == StepExecutionStatus.SUCCESS) {
      StepOutput stepOutput = stepStatusTaskResponseData.getStepStatus().getOutput();

      if (stepOutput instanceof StepMapOutput) {
        StepMapOutput stepMapOutput = (StepMapOutput) stepOutput;
        String instancesByte64 = stepMapOutput.getMap().get("instances");
        instances = new String(Base64.getDecoder().decode(instancesByte64));
      }

      try {
        serverInstanceInfoList = Arrays.asList(objectMapper.readValue(instances, AwsSamServerInstanceInfo[].class));
      } catch (Exception e) {
        log.error("Error while parsing AWS SAM instances", e);
      }
    }

    return serverInstanceInfoList;
  }

  public void putK8sServiceAccountEnvVars(Ambiance ambiance, Map<String, String> samDeployEnvironmentVariablesMap) {
    OptionalSweepingOutput optionalSweepingOutput = executionSweepingOutputService.resolveOptional(
        ambiance, RefObjectUtils.getSweepingOutputRefObject(STAGE_INFRA_DETAILS));

    if (!optionalSweepingOutput.isFound()) {
      throw new CIStageExecutionException("Stage infra details sweeping output cannot be empty");
    }

    StageInfraDetails stageInfraDetails = (StageInfraDetails) optionalSweepingOutput.getOutput();

    if (stageInfraDetails.getType() == StageInfraDetails.Type.K8) {
      K8StageInfraDetails k8StageInfraDetails = (K8StageInfraDetails) stageInfraDetails;
      Infrastructure infrastructure = k8StageInfraDetails.getInfrastructure();
      if (infrastructure instanceof K8sDirectInfraYaml) {
        K8sDirectInfraYaml k8sDirectInfraYaml = (K8sDirectInfraYaml) infrastructure;
        if (!org.jooq.tools.StringUtils.isEmpty(k8sDirectInfraYaml.getSpec().getServiceAccountName().getValue())) {
          samDeployEnvironmentVariablesMap.put("PLUGIN_USE_IRSA", "true");
        } else {
          samDeployEnvironmentVariablesMap.put("PLUGIN_USE_IRSA", "false");
        }
      }
    }

    ManifestsOutcome manifestsOutcome =
        (ManifestsOutcome) outcomeService
            .resolveOptional(ambiance, RefObjectUtils.getOutcomeRefObject(OutcomeExpressionConstants.MANIFESTS))
            .getOutcome();

    AwsSamDirectoryManifestOutcome awsSamDirectoryManifestOutcome =
        (AwsSamDirectoryManifestOutcome) awsSamPluginInfoProviderHelper.getAwsSamDirectoryManifestOutcome(
            manifestsOutcome.values());

    String samDir = awsSamPluginInfoProviderHelper.getSamDirectoryPathFromAwsSamDirectoryManifestOutcome(
        awsSamDirectoryManifestOutcome);

    samDeployEnvironmentVariablesMap.put("PLUGIN_SAM_DIR", samDir);
  }
}
