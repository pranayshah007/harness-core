/*

 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

 */

package io.harness.cdng.provision.terraformcloud;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.cdng.provision.terraformcloud.TerraformCloudConstants.TFC_DESTROY_PLAN_NAME_PREFIX_NG;
import static io.harness.cdng.provision.terraformcloud.TerraformCloudConstants.TFC_OUTPUT_FORMAT;
import static io.harness.cdng.provision.terraformcloud.TerraformCloudConstants.TFC_PLAN_NAME_PREFIX_NG;
import static io.harness.cdng.provision.terraformcloud.TerraformCloudRunType.APPLY;
import static io.harness.cdng.provision.terraformcloud.TerraformCloudRunType.PLAN;
import static io.harness.cdng.provision.terraformcloud.TerraformCloudRunType.PLAN_AND_APPLY;
import static io.harness.cdng.provision.terraformcloud.TerraformCloudRunType.PLAN_AND_DESTROY;
import static io.harness.cdng.provision.terraformcloud.TerraformCloudRunType.PLAN_ONLY;
import static io.harness.data.structure.EmptyPredicate.isEmpty;

import static java.lang.String.format;

import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.CDStepHelper;
import io.harness.cdng.provision.terraform.executions.TerraformPlanExectionDetailsService;
import io.harness.cdng.provision.terraform.executions.TerraformPlanExecutionDetails;
import io.harness.cdng.provision.terraformcloud.dal.TerraformCloudConfig;
import io.harness.cdng.provision.terraformcloud.dal.TerraformCloudConfigDAL;
import io.harness.cdng.provision.terraformcloud.output.TerraformCloudPlanOutput;
import io.harness.cdng.provision.terraformcloud.params.TerraformCloudApplySpecParameters;
import io.harness.cdng.provision.terraformcloud.params.TerraformCloudPlanAndApplySpecParameters;
import io.harness.cdng.provision.terraformcloud.params.TerraformCloudPlanAndDestroySpecParameters;
import io.harness.cdng.provision.terraformcloud.params.TerraformCloudPlanOnlySpecParameters;
import io.harness.cdng.provision.terraformcloud.params.TerraformCloudPlanSpecParameters;
import io.harness.common.ParameterFieldHelper;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.delegate.beans.FileBucket;
import io.harness.delegate.beans.connector.ConnectorConfigDTO;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.beans.connector.terraformcloudconnector.TerraformCloudConnectorDTO;
import io.harness.delegate.task.terraformcloud.response.TerraformCloudRunTaskResponse;
import io.harness.exception.InvalidRequestException;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.data.OptionalSweepingOutput;
import io.harness.pms.sdk.core.plan.creation.yaml.StepOutcomeGroup;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.yaml.ParameterField;
import io.harness.validator.NGRegexValidatorConstants;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;

@Slf4j
@Singleton
@OwnedBy(CDP)
public class TerraformCloudStepHelper {
  @Inject private CDStepHelper cdStepHelper;
  @Inject private ExecutionSweepingOutputService executionSweepingOutputService;
  @Inject TerraformPlanExectionDetailsService terraformPlanExectionDetailsService;
  @Inject public TerraformCloudConfigDAL terraformCloudConfigDAL;

  public String generateFullIdentifier(String provisionerIdentifier, Ambiance ambiance) {
    if (Pattern.matches(NGRegexValidatorConstants.IDENTIFIER_PATTERN, provisionerIdentifier)) {
      return format("%s/%s/%s/%s", AmbianceUtils.getAccountId(ambiance), AmbianceUtils.getOrgIdentifier(ambiance),
          AmbianceUtils.getProjectIdentifier(ambiance), provisionerIdentifier);
    } else {
      throw new InvalidRequestException(
          format("Provisioner Identifier cannot contain special characters or spaces: [%s]", provisionerIdentifier));
    }
  }

  public TerraformCloudConnectorDTO getTerraformCloudConnector(
      TerraformCloudRunSpecParameters terraformCloudRunSpecParameters, Ambiance ambiance) {
    ConnectorConfigDTO connectorConfigDTO;
    if (terraformCloudRunSpecParameters.getType() != TerraformCloudRunType.APPLY) {
      connectorConfigDTO =
          terraformCloudRunSpecParameters.extractConnectorRefs()
              .stream()
              .map(connectorField
                  -> cdStepHelper.getConnector(ParameterFieldHelper.getParameterFieldValue(connectorField), ambiance))
              .filter(connectorInfoDTO -> connectorInfoDTO.getConnectorType() == ConnectorType.TERRAFORM_CLOUD)
              .map(ConnectorInfoDTO::getConnectorConfig)
              .findFirst()
              .orElse(null);
    } else {
      TerraformCloudPlanOutput savedTerraformCloudPlanOutput = getSavedTerraformCloudOutput(
          getProvisionIdentifier(terraformCloudRunSpecParameters), PLAN.getDisplayName(), ambiance);

      connectorConfigDTO =
          cdStepHelper.getConnector(savedTerraformCloudPlanOutput.getTerraformCloudConnectorRef(), ambiance)
              .getConnectorConfig();
    }
    return (TerraformCloudConnectorDTO) connectorConfigDTO;
  }

  public TerraformCloudConnectorDTO getTerraformCloudConnectorWithRef(String connectorRef, Ambiance ambiance) {
    return (TerraformCloudConnectorDTO) cdStepHelper.getConnector(connectorRef, ambiance).getConnectorConfig();
  }

  public void saveTerraformCloudPlanOutput(TerraformCloudPlanSpecParameters planSpecParameters,
      TerraformCloudRunTaskResponse cloudRunTaskResponse, Ambiance ambiance) {
    planSpecParameters.validate();

    TerraformCloudPlanOutput terraformCloudPlanOutput =
        TerraformCloudPlanOutput.builder()
            .runId(cloudRunTaskResponse.getRunId())
            .terraformCloudConnectorRef(
                ParameterFieldHelper.getParameterFieldValue(planSpecParameters.getConnectorRef()))
            .build();

    String fullEntityId = generateFullIdentifier(
        ParameterFieldHelper.getParameterFieldValue(planSpecParameters.getProvisionerIdentifier()), ambiance);
    String outputName = format(TFC_OUTPUT_FORMAT, PLAN.getDisplayName(), fullEntityId);
    executionSweepingOutputService.consume(
        ambiance, outputName, terraformCloudPlanOutput, StepOutcomeGroup.STAGE.name());
  }

  public String getTerraformPlanName(PlanType planType, Ambiance ambiance, String provisionId) {
    String prefix = PlanType.APPLY == planType ? TFC_PLAN_NAME_PREFIX_NG : TFC_DESTROY_PLAN_NAME_PREFIX_NG;
    return format(prefix, ambiance.getPlanExecutionId(), provisionId).replace("_", "-");
  }

  public String getProvisionIdentifier(TerraformCloudRunSpecParameters runSpec) {
    ParameterField<String> provisionerIdentifier;
    switch (runSpec.getType()) {
      case PLAN_ONLY:
        TerraformCloudPlanOnlySpecParameters planOnlySpecParameters = (TerraformCloudPlanOnlySpecParameters) runSpec;
        provisionerIdentifier = planOnlySpecParameters.getProvisionerIdentifier();
        break;
      case PLAN_AND_APPLY:
        TerraformCloudPlanAndApplySpecParameters planAndApplySpecParameters =
            (TerraformCloudPlanAndApplySpecParameters) runSpec;
        provisionerIdentifier = planAndApplySpecParameters.getProvisionerIdentifier();
        break;
      case PLAN_AND_DESTROY:
        TerraformCloudPlanAndDestroySpecParameters planAndDestroySpecParameters =
            (TerraformCloudPlanAndDestroySpecParameters) runSpec;
        provisionerIdentifier = planAndDestroySpecParameters.getProvisionerIdentifier();
        break;
      case PLAN:
        TerraformCloudPlanSpecParameters planSpecParameters = (TerraformCloudPlanSpecParameters) runSpec;
        provisionerIdentifier = planSpecParameters.getProvisionerIdentifier();
        break;
      case APPLY:
        TerraformCloudApplySpecParameters applySpecParameters = (TerraformCloudApplySpecParameters) runSpec;
        provisionerIdentifier = applySpecParameters.getProvisionerIdentifier();
        break;
      default:
        throw new InvalidRequestException(format("Can't get provisioner for type: [%s]", runSpec.getType()));
    }
    return ParameterFieldHelper.getParameterFieldValue(provisionerIdentifier);
  }

  public boolean isExportTfPlanJson(TerraformCloudRunSpecParameters runSpec) {
    TerraformCloudRunType runType = runSpec.getType();
    if (runType == PLAN_ONLY) {
      TerraformCloudPlanOnlySpecParameters planOnlySpecParameters = (TerraformCloudPlanOnlySpecParameters) runSpec;
      return ParameterFieldHelper.getBooleanParameterFieldValue(planOnlySpecParameters.getExportTerraformPlanJson());
    } else if (PLAN == runType) {
      TerraformCloudPlanSpecParameters planSpecParameters = (TerraformCloudPlanSpecParameters) runSpec;
      return ParameterFieldHelper.getBooleanParameterFieldValue(planSpecParameters.getExportTerraformPlanJson());
    } else {
      return false;
    }
  }

  public void saveTerraformPlanExecutionDetails(
      Ambiance ambiance, String planFileJsonId, String policyCheckFileJsonId, String provisionerIdentifier) {
    String planExecutionId = ambiance.getPlanExecutionId();
    String accountId = AmbianceUtils.getAccountId(ambiance);
    String projectId = AmbianceUtils.getProjectIdentifier(ambiance);
    String orgId = AmbianceUtils.getOrgIdentifier(ambiance);
    String stageExecutionId = ambiance.getStageExecutionId();

    TerraformPlanExecutionDetails terraformPlanExecutionDetails =
        TerraformPlanExecutionDetails.builder()
            .accountIdentifier(accountId)
            .orgIdentifier(orgId)
            .projectIdentifier(projectId)
            .pipelineExecutionId(planExecutionId)
            .stageExecutionId(stageExecutionId)
            .provisionerId(provisionerIdentifier)
            .tfPlanJsonFieldId(planFileJsonId)
            .tfPlanFileBucket(FileBucket.TERRAFORM_PLAN_JSON.name())
            .tfcPolicyChecksFileId(policyCheckFileJsonId)
            .tfcPolicyChecksFileBucket(FileBucket.TERRAFORM_CLOUD_POLICY_CHECKS.name())
            .build();

    terraformPlanExectionDetailsService.save(terraformPlanExecutionDetails);
  }

  private TerraformCloudPlanOutput getSavedTerraformCloudOutput(
      String provisionerIdentifier, String command, Ambiance ambiance) {
    String fullEntityId = generateFullIdentifier(provisionerIdentifier, ambiance);
    OptionalSweepingOutput output = executionSweepingOutputService.resolveOptional(
        ambiance, RefObjectUtils.getSweepingOutputRefObject(format(TFC_OUTPUT_FORMAT, command, fullEntityId)));
    if (!output.isFound()) {
      throw new InvalidRequestException(
          format("Did not find any Plan step for provisioner identifier: [%s]", provisionerIdentifier));
    }

    return (TerraformCloudPlanOutput) output.getOutput();
  }

  public String getPlanRunId(String provisionerId, Ambiance ambiance) {
    return getSavedTerraformCloudOutput(provisionerId, PLAN.getDisplayName(), ambiance).getRunId();
  }

  public Map<String, Object> parseTerraformOutputs(String terraformOutputString) {
    Map<String, Object> outputs = new LinkedHashMap<>();
    if (isEmpty(terraformOutputString)) {
      return outputs;
    }
    try {
      TypeReference<HashMap<String, Object>> typeRef = new TypeReference<HashMap<String, Object>>() {};
      Map<String, Object> json = new ObjectMapper().readValue(IOUtils.toInputStream(terraformOutputString), typeRef);

      json.forEach((key, object) -> outputs.put(key, ((Map<String, Object>) object).get("value")));

    } catch (IOException exception) {
      log.error("", exception);
    }
    return outputs;
  }

  public void saveTerraformCloudConfig(
      TerraformCloudRunSpecParameters spec, String workspaceId, String lastSuccessfulRun, Ambiance ambiance) {
    String connectorRef;
    String provisionIdentifier = getProvisionIdentifier(spec);
    TerraformCloudRunType type = spec.getType();
    if (type == PLAN_AND_APPLY) {
      TerraformCloudPlanAndApplySpecParameters planAndApplySpecParameters =
          (TerraformCloudPlanAndApplySpecParameters) spec;
      connectorRef = ParameterFieldHelper.getParameterFieldValue(planAndApplySpecParameters.getConnectorRef());
    } else if (type == PLAN_AND_DESTROY) {
      TerraformCloudPlanAndDestroySpecParameters planAndDestroySpecParameters =
          (TerraformCloudPlanAndDestroySpecParameters) spec;
      connectorRef = ParameterFieldHelper.getParameterFieldValue(planAndDestroySpecParameters.getConnectorRef());
    } else if (type == APPLY) {
      TerraformCloudPlanOutput savedTerraformCloudPlanOutput =
          getSavedTerraformCloudOutput(provisionIdentifier, PLAN.getDisplayName(), ambiance);
      connectorRef = savedTerraformCloudPlanOutput.getTerraformCloudConnectorRef();
    } else {
      throw new InvalidRequestException(format("Can't save Terraform Cloud Config for type: [%s]", type));
    }
    terraformCloudConfigDAL.saveTerraformCloudConfig(TerraformCloudConfig.builder()
                                                         .accountId(AmbianceUtils.getAccountId(ambiance))
                                                         .orgId(AmbianceUtils.getOrgIdentifier(ambiance))
                                                         .projectId(AmbianceUtils.getProjectIdentifier(ambiance))
                                                         .provisionerIdentifier(provisionIdentifier)
                                                         .stageExecutionId(ambiance.getStageExecutionId())
                                                         .connectorRef(connectorRef)
                                                         .lastSuccessfulRun(lastSuccessfulRun)
                                                         .workspaceId(workspaceId)
                                                         .build());
  }
}
