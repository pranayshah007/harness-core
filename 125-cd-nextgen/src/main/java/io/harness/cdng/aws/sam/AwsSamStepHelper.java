/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.aws.sam;

import static io.harness.beans.sweepingoutputs.StageInfraDetails.STAGE_INFRA_DETAILS;
import static io.harness.common.ParameterFieldHelper.getParameterFieldValue;

import static io.serializer.HObjectMapper.NG_DEFAULT_OBJECT_MAPPER;
import static java.lang.String.format;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.sweepingoutputs.K8StageInfraDetails;
import io.harness.beans.sweepingoutputs.StageInfraDetails;
import io.harness.beans.yaml.extended.infrastrucutre.Infrastructure;
import io.harness.beans.yaml.extended.infrastrucutre.K8sDirectInfraYaml;
import io.harness.cdng.aws.sam.beans.AwsSamValuesYamlDataOutcome;
import io.harness.cdng.infra.beans.AwsSamInfrastructureOutcome;
import io.harness.cdng.infra.beans.InfrastructureOutcome;
import io.harness.cdng.manifest.ManifestType;
import io.harness.cdng.manifest.yaml.AwsSamDirectoryManifestOutcome;
import io.harness.cdng.manifest.yaml.GitStoreConfig;
import io.harness.cdng.manifest.yaml.ManifestOutcome;
import io.harness.cdng.manifest.yaml.ValuesManifestOutcome;
import io.harness.cdng.stepsdependency.constants.OutcomeExpressionConstants;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.beans.instancesync.ServerInstanceInfo;
import io.harness.delegate.beans.instancesync.info.AwsSamServerInstanceInfo;
import io.harness.delegate.task.stepstatus.StepExecutionStatus;
import io.harness.delegate.task.stepstatus.StepMapOutput;
import io.harness.delegate.task.stepstatus.StepOutput;
import io.harness.delegate.task.stepstatus.StepStatusTaskResponseData;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.ngexception.CIStageExecutionException;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.sdk.core.data.OptionalSweepingOutput;
import io.harness.pms.sdk.core.plugin.ContainerStepExecutionResponseHelper;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outcome.OutcomeService;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.yaml.ParameterField;
import io.harness.tasks.ResponseData;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_ECS})
@Slf4j
@OwnedBy(HarnessTeam.CDP)
public class AwsSamStepHelper {
  @Inject protected OutcomeService outcomeService;
  @Inject private ExecutionSweepingOutputService executionSweepingOutputService;

  @Inject private ContainerStepExecutionResponseHelper containerStepExecutionResponseHelper;

  private static String SAM_BUILD_DEFAULT_IMAGE = "harnessdev/sam-build:1.82.0-latest";
  private static String SAM_DEPLOY_DEFAULT_IMAGE = "harnessdev/sam-deploy:1.82.0-latest";

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
  }

  public List<ServerInstanceInfo> fetchServerInstanceInfoFromDelegateResponse(
      Map<String, ResponseData> responseDataMap) {
    StepStatusTaskResponseData stepStatusTaskResponseData =
        containerStepExecutionResponseHelper.filterK8StepResponse(responseDataMap);

    List<ServerInstanceInfo> serverInstanceInfoList = new ArrayList<>();

    if (stepStatusTaskResponseData == null) {
      log.info("Aws Sam Deploy :  Received stepStatusTaskResponseData as null");
      return serverInstanceInfoList;
    } else {
      log.info(String.format("Aws Sam Deploy :  Received stepStatusTaskResponseData with status %s",
          stepStatusTaskResponseData.getStepStatus().getStepExecutionStatus()));
    }

    if (stepStatusTaskResponseData.getStepStatus().getStepExecutionStatus() == StepExecutionStatus.SUCCESS) {
      StepOutput stepOutput = stepStatusTaskResponseData.getStepStatus().getOutput();
      String instances = null;

      if (stepOutput instanceof StepMapOutput) {
        StepMapOutput stepMapOutput = (StepMapOutput) stepOutput;
        String instancesByte64 = stepMapOutput.getMap().get("instances");
        if (EmptyPredicate.isEmpty(instancesByte64)) {
          log.info("No AWS SAM Deploy instances found");
          return serverInstanceInfoList;
        }
        instances = new String(Base64.getDecoder().decode(instancesByte64));
        log.info(String.format("AWS SAM Deploy instances %s", instances));
      }
      try {
        serverInstanceInfoList = Arrays.asList(objectMapper.readValue(instances, AwsSamServerInstanceInfo[].class));
      } catch (Exception e) {
        log.error(String.format("Error while parsing AWS SAM instances %s", instances), e);
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
  }

  public ManifestOutcome getAwsSamDirectoryManifestOutcome(Collection<ManifestOutcome> manifestOutcomes) {
    List<ManifestOutcome> manifestOutcomeList =
        manifestOutcomes.stream()
            .filter(manifestOutcome -> ManifestType.AwsSamDirectory.equals(manifestOutcome.getType()))
            .collect(Collectors.toList());

    if (manifestOutcomeList.isEmpty() || manifestOutcomeList.size() > 1) {
      String errorMessage = String.format(
          "Exactly one manifest of type AwsSamDirectory is required. %s found.", manifestOutcomeList.size());
      log.error(errorMessage);
      throw new InvalidRequestException(errorMessage);
    }

    return manifestOutcomeList.get(0);
  }

  public ManifestOutcome getAwsSamValuesManifestOutcome(Collection<ManifestOutcome> manifestOutcomes) {
    List<ManifestOutcome> manifestOutcomeList =
        manifestOutcomes.stream()
            .filter(manifestOutcome -> ManifestType.VALUES.equals(manifestOutcome.getType()))
            .collect(Collectors.toList());

    if (manifestOutcomeList.size() > 1) {
      String errorMessage = String.format(
          "At most one manifest of type VALUES yaml can be configured. %s found.", manifestOutcomeList.size());
      log.error(errorMessage);
      throw new InvalidRequestException(errorMessage);
    }

    return manifestOutcomeList.isEmpty() ? null : manifestOutcomeList.get(0);
  }

  public String getValuesPathFromValuesManifestOutcome(ValuesManifestOutcome valuesManifestOutcome) {
    GitStoreConfig gitStoreConfig = (GitStoreConfig) valuesManifestOutcome.getStore();
    return "/harness/" + valuesManifestOutcome.getIdentifier() + "/" + gitStoreConfig.getPaths().getValue().get(0);
  }

  public String getSamDirectoryPathFromAwsSamDirectoryManifestOutcome(
      AwsSamDirectoryManifestOutcome awsSamDirectoryManifestOutcome) {
    GitStoreConfig gitStoreConfig = (GitStoreConfig) awsSamDirectoryManifestOutcome.getStore();

    return awsSamDirectoryManifestOutcome.getIdentifier() + "/" + gitStoreConfig.getPaths().getValue().get(0);
  }

  public String getSamTemplateFilePath(ManifestOutcome manifestOutcome) {
    if (manifestOutcome instanceof AwsSamDirectoryManifestOutcome) {
      AwsSamDirectoryManifestOutcome awsSamDirectoryManifestOutcome = (AwsSamDirectoryManifestOutcome) manifestOutcome;
      return getParameterFieldValue(awsSamDirectoryManifestOutcome.getSamTemplateFile());
    }
    throw new UnsupportedOperationException(format("Unsupported sam manifest type: [%s]", manifestOutcome.getType()));
  }

  public ParameterField<String> getImage(AwsSamBaseStepInfo awsSamBaseStepInfo) {
    if (awsSamBaseStepInfo instanceof AwsSamBuildStepInfo || awsSamBaseStepInfo instanceof AwsSamBuildStepParameters) {
      if (awsSamBaseStepInfo.getImage() != null
          && EmptyPredicate.isNotEmpty(awsSamBaseStepInfo.getImage().getValue())) {
        return awsSamBaseStepInfo.getImage();
      } else {
        return ParameterField.createValueField(SAM_BUILD_DEFAULT_IMAGE);
      }
    } else if (awsSamBaseStepInfo instanceof AwsSamDeployStepInfo
        || awsSamBaseStepInfo instanceof AwsSamDeployStepParameters) {
      if (awsSamBaseStepInfo.getImage() != null
          && EmptyPredicate.isNotEmpty(awsSamBaseStepInfo.getImage().getValue())) {
        return awsSamBaseStepInfo.getImage();
      } else {
        return ParameterField.createValueField(SAM_DEPLOY_DEFAULT_IMAGE);
      }
    } else {
      throw new InvalidRequestException("Default Images for SAM Build and SAM Deploy Step only supported");
    }
  }
}
